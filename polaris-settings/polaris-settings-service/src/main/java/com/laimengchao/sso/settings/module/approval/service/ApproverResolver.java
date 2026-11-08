package com.laimengchao.sso.settings.module.approval.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.laimengchao.sso.settings.module.approval.model.ApprovalSnapshot.ApproverRef;
import com.laimengchao.sso.settings.module.approval.model.ApprovalSnapshot.ApproverRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批人解析：把「规则」变成「具体的人」。
 * <p>
 * 五种规则取<b>并集去重</b>——一个分支可以配「组织负责人 + 角色 HRBP」，
 * 意思是这两拨人都是审批人，按分支的 approve_mode 决定或签还是会签。
 * <p>
 * 解析发生在<b>步骤激活那一刻</b>而不是发起时：员工发起后调了部门，
 * 下一步该由新部门的负责人审，这才符合直觉。冻结的是流程形状，不是人。
 */
@Slf4j
@Component
public class ApproverResolver {

    private final InternalHttpClient http;
    private final String ehrBaseUrl;
    private final String accountBaseUrl;

    public ApproverResolver(InternalHttpClient http,
                            @Value("${approval.ehr-base-url:http://127.0.0.1:9006}") String ehrBaseUrl,
                            @Value("${approval.account-base-url:http://127.0.0.1:9002}") String accountBaseUrl) {
        this.http = http;
        this.ehrBaseUrl = ehrBaseUrl;
        this.accountBaseUrl = accountBaseUrl;
    }

    /** 业务点字段来源前缀：source = FIELD:<业务点code>:<USER|ORG> */
    private static final String FIELD_PREFIX = "FIELD:";

    /**
     * @param rules      分支上的审批人规则
     * @param applicantId 发起人（APPLICANT 规则用，且 allowSelf=false 时要把本人排掉）
     * @param orgId      发起人组织（ORG_LEADER 且 source 为空时沿用）
     * @param allowSelf  是否允许审批自己的单
     * @param vars       审批表单冻结快照（formData），ORG_LEADER 的 FIELD 来源从中取值
     */
    public List<ApproverRef> resolve(List<ApproverRule> rules, Long applicantId, Long orgId, boolean allowSelf, Map<String, Object> vars) {
        Map<Long, ApproverRef> merged = new LinkedHashMap<>();
        if (rules == null || rules.isEmpty()) {
            return new ArrayList<>();
        }
        for (ApproverRule rule : rules) {
            if (rule == null || rule.getType() == null) {
                continue;
            }
            switch (rule.getType().toUpperCase()) {
                case "USER" -> {
                    Long uid = parseUserId(rule.getUserId());
                    if (uid != null) {
                        merged.putIfAbsent(uid, new ApproverRef(uid, nameOfAccount(uid)));
                    }
                }
                case "APPLICANT" -> {
                    if (applicantId != null) {
                        merged.putIfAbsent(applicantId, new ApproverRef(applicantId, null));
                    }
                }
                case "ORG_LEADER" -> addAll(merged, resolveOrgLeader(rule, orgId, vars));
                case "ORG_LEADER_BY_NAME" -> addAll(merged, resolveOrgLeaderByName(rule.getCode()));
                case "ROLE" -> addAll(merged, resolveByRole(rule.getCode()));
                default -> log.warn("未知审批人类型：{}", rule.getType());
            }
        }
        if (!allowSelf && applicantId != null) {
            ApproverRef self = merged.remove(applicantId);
            // 「负责人自己请假」这类场景：本级负责人就是申请人，allow_self=0 把他排掉后
            // 节点无人可审。旧行为交给 onEmpty=SKIP 静默跳过（整单可能被自动判通过），
            // 现在逐级上溯到上级组织负责人，实在找不到人再由 onEmpty=BLOCK 明确报错。
            // 只在「确实移除了本人且没有其它审批人」时上溯，避免影响正常单据的审批人选。
            if (self != null && merged.isEmpty() && orgId != null) {
                addAll(merged, resolveUpwardLeader(applicantId, orgId));
            }
        }
        return new ArrayList<>(merged.values());
    }

    /** 组织层级最多 5 类（集团/公司/部门/项目/子项目），留一级余量 */
    private static final int MAX_UPWARD_LEVELS = 6;

    /**
     * 从上一级组织开始向上找「不是申请人本人」的负责人。
     * EHR 的 /internal/orgs/{id}/leader?levelOffset=N 本身会从该级继续向上找
     * 第一个设了负责人的节点，所以这里只需递增 offset，并过滤掉仍是本人的结果。
     */
    private List<ApproverRef> resolveUpwardLeader(Long applicantId, Long orgId) {
        for (int offset = 1; offset <= MAX_UPWARD_LEVELS; offset++) {
            List<ApproverRef> upper;
            try {
                upper = resolveOrgLeader(orgId, offset);
            } catch (Exception e) {
                log.warn("上溯审批人失败 orgId={} levelOffset={}：{}", orgId, offset, e.getMessage());
                break;
            }
            if (upper.isEmpty()) {
                break; // 已经越过根节点
            }
            List<ApproverRef> others = upper.stream()
                    .filter(r -> r != null && !applicantId.equals(r.getId()))
                    .toList();
            if (!others.isEmpty()) {
                log.info("审批人是申请人本人，改由上级组织负责人审批 orgId={} levelOffset={} count={}",
                        orgId, offset, others.size());
                return others;
            }
        }
        return List.of();
    }

    /**
     * 组织负责人。source 决定「取谁的组织」：
     * 空 = 申请人组织（原行为）；FIELD:xxx = 从表单字段取（ORG 字段值是组织 ID，USER 字段值是账号 ID）。
     * toRoot=true 忽略 levelOffset，直接取集团（根）负责人这一人。
     */
    private List<ApproverRef> resolveOrgLeader(ApproverRule rule, Long applicantOrgId, Map<String, Object> vars) {
        String source = rule.getSource();
        if (source != null && source.startsWith(FIELD_PREFIX)) {
            return resolveFieldOrgLeader(source, rule.getLevelOffset(), rule.getToRoot(), vars);
        }
        // 存量行为：申请人组织 + 上溯级数
        return resolveOrgLeader(applicantOrgId, rule.getLevelOffset());
    }

    /** 组织负责人，levelOffset=0 本级，1 上一级，2 上两级… */
    private List<ApproverRef> resolveOrgLeader(Long orgId, Integer levelOffset) {
        if (orgId == null) {
            return List.of();
        }
        int offset = levelOffset == null ? 0 : levelOffset;
        String url = ehrBaseUrl + "/internal/orgs/" + orgId + "/leader?levelOffset=" + offset;
        return parseApproverList(http.getData(url));
    }

    /**
     * 从表单字段解析组织来源再取负责人。source 形如 FIELD:<code>:<USER|ORG>。
     * ORG 字段值即组织 ID 或组织名称；USER 字段值即账号 ID 或工号（由 EHR 反查其所属组织）。
     * 字段缺失 / 解析不到值时返回空，交给模板 onEmpty 决定跳过还是阻塞。
     * <p>值原样传给 EHR（可能是数字 ID 也可能是中文名/工号），由对端判别，与 /leader-by-name 同一套传参约定。
     */
    private List<ApproverRef> resolveFieldOrgLeader(String source, Integer levelOffset, Boolean toRoot, Map<String, Object> vars) {
        String rest = source.substring(FIELD_PREFIX.length());
        int sep = rest.indexOf(':');
        if (sep <= 0) {
            log.warn("ORG_LEADER 的 FIELD 来源格式非法（缺字段类型）：{}", source);
            return List.of();
        }
        String key = rest.substring(0, sep);
        String ftype = rest.substring(sep + 1).toUpperCase();
        Object raw = vars == null ? null : vars.get(key);
        if (raw == null || (raw instanceof String s && s.isBlank())) {
            log.debug("ORG_LEADER 来源字段 {} 在表单中无有效值，跳过", key);
            return List.of();
        }
        // 数字原样转 long 字符串，其余（中文名/工号）toString；交给 EHR 判别 ID 还是名称
        String subject = raw instanceof Number n ? String.valueOf(n.longValue()) : raw.toString();
        String subjectType = "USER".equals(ftype) ? "EMPLOYEE" : "ORG";
        int offset = levelOffset == null ? 0 : levelOffset;
        boolean root = Boolean.TRUE.equals(toRoot);
        String url = ehrBaseUrl + "/internal/orgs/org-leader?subject=" + subject
                + "&subjectType=" + subjectType + "&levelOffset=" + offset + "&toRoot=" + root;
        return parseApproverList(http.getData(url));
    }

    /**
     * 按组织名称取负责人：规则里 code 存<b>组织名称</b>（如「集团人力资源中心」），
     * 与发起人是谁、在哪个部门都无关——职能线审批（HR、财务）用这种，
     * 因为「谁来审」取决于公司职能架构而不是申请人的汇报线。
     * 组织不存在或没设负责人时返回空，交给 onEmpty 决定跳过还是阻塞。
     * <p>
     * 注意中文参数<b>不要预先 URL 编码</b>：RestTemplate 会把 String URL 当模板再编码一次，
     * 预编码的 % 会变成 %25，对端收到的就是字面量乱码，永远查不到组织。
     */
    private List<ApproverRef> resolveOrgLeaderByName(String orgName) {
        if (orgName == null || orgName.isBlank()) {
            return List.of();
        }
        String url = ehrBaseUrl + "/internal/orgs/leader-by-name?name=" + orgName.trim();
        return parseApproverList(http.getData(url));
    }

    private List<ApproverRef> resolveByRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return List.of();
        }
        return parseApproverList(http.getData(accountBaseUrl + "/internal/roles/" + roleCode + "/accounts"));
    }

    /** 兼容「单个对象」与「数组」两种返回 */
    private List<ApproverRef> parseApproverList(JsonNode data) {
        List<ApproverRef> list = new ArrayList<>();
        if (data == null || data.isNull()) {
            return list;
        }
        if (data.isArray()) {
            for (JsonNode n : data) {
                ApproverRef ref = toRef(n);
                if (ref != null) {
                    list.add(ref);
                }
            }
        } else {
            ApproverRef ref = toRef(data);
            if (ref != null) {
                list.add(ref);
            }
        }
        return list;
    }

    private ApproverRef toRef(JsonNode n) {
        if (n == null || !n.has("id")) {
            return null;
        }
        long id = n.get("id").asLong();
        String name = n.hasNonNull("name") ? n.get("name").asText() : null;
        return new ApproverRef(id, name);
    }

    private void addAll(Map<Long, ApproverRef> merged, List<ApproverRef> refs) {
        for (ApproverRef r : refs) {
            merged.putIfAbsent(r.getId(), r);
        }
    }

    /** USER 规则只给了 ID，补一次名字；失败就留空，前端显示 ID */
    private String nameOfAccount(Long accountId) {
        JsonNode data = http.getData(accountBaseUrl + "/internal/accounts/" + accountId + "/principal");
        if (data == null) {
            return null;
        }
        return data.hasNonNull("name") ? data.get("name").asText()
                : (data.hasNonNull("nickname") ? data.get("nickname").asText() : null);
    }

    /** 规则里的 userId 是字符串（防 JS 精度丢失），这里转 Long；脏数据按空处理并告警 */
    private Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException e) {
            log.warn("USER 规则的 userId 不是合法数字：{}", userId);
            return null;
        }
    }
}
