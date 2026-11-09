package com.laimengchao.sso.ehr.module.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.module.approval.support.ApprovalLauncher;
import com.laimengchao.sso.ehr.module.employee.constant.EmployeeStatus;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.org.dto.OrgNodeRequest;
import com.laimengchao.sso.ehr.module.org.entity.LeaderChange;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.entity.OrgType;
import com.laimengchao.sso.ehr.module.org.mapper.LeaderChangeMapper;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.ehr.module.org.vo.LeaderChangeVO;
import com.laimengchao.sso.ehr.module.org.vo.OrgNodeVO;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.settings.client.api.ApprovalApi;
import com.laimengchao.sso.settings.client.dto.ApprovalInstanceDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 组织架构服务：一棵自引用树 + 一套父子类型白名单。
 * <p>
 * 三条不变量（每次写操作都校验，前端提示只是辅助）：
 * <ol>
 *   <li>根节点只能是集团；</li>
 *   <li>子类型必须在父类型的 allowedChildren 内；</li>
 *   <li>path 永远是「祖先链 + 自身」，移动节点时整棵子树同步重算。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgService {

    private final OrgNodeMapper orgMapper;
    private final LeaderChangeMapper leaderChangeMapper;
    private final EmployeeMapper employeeMapper;
    private final ApprovalApi approvalApi;
    /** 统一的三段式发起：仅在途才回写实例号，避免回调竞态 */
    private final ApprovalLauncher approvalLauncher;

    /** 负责人变更审批场景编码，对应 settings 的 cfg_biz_scene.biz_type */
    /**
     * 部门设置负责人审批场景。原 {@code ehr.org_leader} 已停用（模板保留仅供历史单据查询），
     * 新流程用 {@code ehr.dept_leader}：审批链同为「上级负责人 → 人力资源中心」，
     * 但独立成一条流程，配置不再与旧模板纠缠。
     */
    private static final String BIZ_ORG_LEADER = "ehr.dept_leader";
    /** 终审结果接收地址：settings 审批结束后回调它（必须是 settings 能直连的地址） */
    private static final String CALLBACK_PATH = "/internal/ehr/approval-callback";

    /** 根编码前缀：顶级节点从 XC0001 起 */
    private static final String ORG_CODE_PREFIX = "XC";
    /** 每段同级序号固定 4 位：0001 ~ 9999 */
    private static final int ORG_CODE_SEGMENT_LEN = 4;
    private static final int ORG_CODE_MAX_SEQ = 9999;
    /** 规范编码：XC + 若干个 4 位数字段，如 XC0001 / XC00010001 / XC000100010001 */
    private static final Pattern ORG_CODE_PATTERN = Pattern.compile("^XC(?:\\d{4})+$");
    private static final Pattern ORG_CODE_SEGMENT = Pattern.compile("^\\d{4}$");

    @Value("${ehr.self-base-url:http://127.0.0.1:9006}")
    private String selfBaseUrl;

    /* ---------------- 查询 ---------------- */

    /** 完整组织树（根节点列表，子节点挂在 children 上） */
    public List<OrgNodeVO> tree(String keyword) {
        List<OrgNode> all = orgMapper.selectList(new LambdaQueryWrapper<OrgNode>()
                .orderByAsc(OrgNode::getSort).orderByAsc(OrgNode::getId));
        Map<Long, OrgCounts> counts = employeeCounts();

        // keyword 只做「保留命中的节点及其祖先链」，避免搜索时把树结构拍平成列表
        Set<Long> keep = null;
        if (keyword != null && !keyword.isBlank()) {
            keep = new HashSet<>();
            String kw = keyword.trim().toLowerCase();
            Map<Long, OrgNode> byId = new HashMap<>();
            for (OrgNode n : all) {
                byId.put(n.getId(), n);
            }
            for (OrgNode n : all) {
                if (matches(n, kw)) {
                    // 命中节点连同祖先一起保留，树上就能顺藤摸瓜看到位置
                    for (Long id : parsePath(n.getPath())) {
                        keep.add(id);
                    }
                    keep.add(n.getId());
                }
            }
            // 兜底：byId 只是为了让 keep 里的 id 都能在后续被访问到
            keep.retainAll(byId.keySet());
        }

        Map<Long, OrgNodeVO> voMap = new LinkedHashMap<>();
        for (OrgNode n : all) {
            if (keep != null && !keep.contains(n.getId())) {
                continue;
            }
            voMap.put(n.getId(), toVO(n, counts.getOrDefault(n.getId(), OrgCounts.EMPTY)));
        }
        List<OrgNodeVO> roots = new ArrayList<>();
        for (OrgNode n : all) {
            OrgNodeVO vo = voMap.get(n.getId());
            if (vo == null) {
                continue;
            }
            OrgNodeVO parent = n.getParentId() == null ? null : voMap.get(n.getParentId());
            if (parent == null) {
                roots.add(vo);
                vo.setParentName("");
            } else {
                parent.getChildren().add(vo);
                vo.setParentName(parent.getName());
            }
        }
        voMap.values().forEach(vo -> vo.setChildCount((long) vo.getChildren().size()));
        // 最近一次负责人变更：列表的「审批中 / 已驳回」提示直接由变更记录驱动，
        // 不再靠反查审批中心（审批单的 bizId 现在是记录行 ID，按组织 ID 查不到了）
        fillLatestLeaderChange(voMap);
        sortTree(roots);
        roots.forEach(this::fillTotal);
        return roots;
    }

    /** 一次查全量变更记录，按组织取最新一条，回填到 VO 上 */
    private void fillLatestLeaderChange(Map<Long, OrgNodeVO> voMap) {
        if (voMap.isEmpty()) {
            return;
        }
        List<LeaderChange> rows = leaderChangeMapper.selectList(new LambdaQueryWrapper<LeaderChange>()
                .in(LeaderChange::getOrgId, voMap.keySet())
                .ne(LeaderChange::getStatus, LeaderChange.STATUS_CANCELLED)
                .orderByDesc(LeaderChange::getCreatedAt)
                .orderByDesc(LeaderChange::getId));
        Map<Long, LeaderChange> latest = new HashMap<>();
        for (LeaderChange r : rows) {
            latest.putIfAbsent(r.getOrgId(), r);
        }
        voMap.forEach((orgId, vo) -> {
            LeaderChange r = latest.get(orgId);
            if (r == null) {
                return;
            }
            vo.setLeaderChangeStatus(r.getStatus());
            vo.setLeaderChangeStatusName(changeStatusName(r.getStatus()));
            vo.setLeaderChangeInstanceId(r.getApprovalInstanceId());
            vo.setLeaderChangeAt(r.getCreatedAt());
            vo.setLeaderChangeReason(r.getReason());
        });
    }

    /** 自底向上累计「含下级总人数」：必须从根往下递归，map 的插入顺序不保证子先于父 */
    private long fillTotal(OrgNodeVO vo) {
        long total = vo.getEmployeeCount() == null ? 0L : vo.getEmployeeCount();
        for (OrgNodeVO c : vo.getChildren()) {
            total += fillTotal(c);
        }
        vo.setTotalEmployeeCount(total);
        return total;
    }

    /** 扁平列表（按树序展开），前端级联下拉用 */
    public List<OrgNodeVO> flatten(String keyword) {
        List<OrgNodeVO> out = new ArrayList<>();
        walk(tree(keyword), 1, out);
        return out;
    }

    public OrgNodeVO detail(Long id) {
        OrgNode node = require(id);
        Map<Long, OrgCounts> counts = employeeCounts();
        OrgCounts self = counts.getOrDefault(id, OrgCounts.EMPTY);
        OrgNodeVO vo = toVO(node, self);
        if (node.getParentId() != null) {
            OrgNode parent = orgMapper.selectById(node.getParentId());
            vo.setParentName(parent == null ? "" : parent.getName());
        } else {
            vo.setParentName("");
        }
        vo.setChildCount(childCount(id));
        // 含下级总人数：path 前缀命中的整棵子树，各自的直属人数累加
        long total = self.working();
        if (node.getPath() != null && !node.getPath().isBlank()) {
            for (OrgNode d : orgMapper.selectList(new LambdaQueryWrapper<OrgNode>()
                    .likeRight(OrgNode::getPath, node.getPath() + "/"))) {
                total += counts.getOrDefault(d.getId(), OrgCounts.EMPTY).working();
            }
        }
        vo.setTotalEmployeeCount(total);
        fillLatestLeaderChange(Map.of(id, vo));
        return vo;
    }

    /* ---------------- 写入 ---------------- */

    /**
     * 新增组织。编码<b>不再由前端指定</b>，按层级规则自动生成（见 {@link #generateOrgCode}），
     * 请求里即便带了 code 也会被忽略——避免手填编码与自动编码两套口径打架。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrgNodeVO create(OrgNodeRequest req) {
        OrgType type = requireType(req.getType());
        String name = requireName(req.getName());
        OrgNode parent = null;
        if (req.getParentId() != null) {
            parent = require(req.getParentId());
            checkParentAllows(parent, type);
        } else if (!type.canBeRoot()) {
            throw new BizException(EhrErrorCode.ORG_PARENT_NOT_ALLOWED,
                    "根节点只能是「集团」；" + type.getLabel() + "必须挂在上级节点下");
        }
        checkNameUnique(parent == null ? null : parent.getId(), name, null);

        // 先取全表行锁（事务提交才释放），再算序号：并发新增不会拿到同一个编码
        orgMapper.lockForOrgCode();
        String code = generateOrgCode(parent);

        OrgNode node = new OrgNode();
        node.setId(IdWorker.getId());
        node.setParentId(parent == null ? null : parent.getId());
        node.setType(type.name());
        node.setName(name);
        node.setCode(code);
        node.setShortName(nvl(req.getShortName()));
        node.setEmail(nvl(req.getEmail()));
        node.setAddress(nvl(req.getAddress()));
        node.setHeadcount(req.getHeadcount());
        node.setCostCenter(nvl(req.getCostCenter()));
        node.setEstablishDate(parseDate(req.getEstablishDate()));
        node.setLeader(leaderNameOf(req.getLeaderEmployeeId(), req.getLeader()));
        node.setLeaderEmployeeId(req.getLeaderEmployeeId());
        node.setPhone(nvl(req.getPhone()));
        node.setSort(req.getSort() == null ? 0 : req.getSort());
        node.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        node.setRemark(nvl(req.getRemark()));
        node.setDepth(parent == null ? 1 : parent.getDepth() + 1);
        node.setPath(parent == null ? String.valueOf(node.getId())
                : parent.getPath() + "/" + node.getId());
        orgMapper.insert(node);
        // 新建时直接带了负责人（接口调用方 / 历史脚本）：不走审批，但必须留痕，
        // 否则「负责人变更记录」会出现无法解释的断档。
        if (node.getLeaderEmployeeId() != null) {
            Employee emp = employeeMapper.selectById(node.getLeaderEmployeeId());
            insertDirectRecord(node, null, "", emp, LeaderChange.TYPE_SET, "新建部门时直接指定负责人");
        }
        return toVO(node, OrgCounts.EMPTY);
    }

    /**
     * 编辑节点：名称/简称/负责人/排序/办公信息/备注。
     * 「上级」和「编码」是一次性决策，编辑接口不接变更——挪节点要重算整棵子树的
     * path/depth，编码是外部系统认的键，两者改动成本都远高于收益。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrgNodeVO update(Long id, OrgNodeRequest req) {
        OrgNode node = require(id);
        // 负责人变更要留痕：先把旧值记下来，落库后比对
        Long oldLeaderId = node.getLeaderEmployeeId();
        String oldLeaderName = nvl(node.getLeader());
        OrgType oldType = node.typeEnum();
        OrgType newType = req.getType() == null || req.getType().isBlank() ? oldType : requireType(req.getType());
        String name = req.getName() == null || req.getName().isBlank() ? node.getName() : requireName(req.getName());

        assertImmutableFields(node, req);

        if (newType != oldType) {
            long children = childCount(id);
            if (children > 0) {
                throw new BizException(EhrErrorCode.ORG_TYPE_INVALID,
                        "该节点下还有子节点，不能直接改类型；请先调整子节点");
            }
            // 类型变了要复核「当前上级还接不接受这个类型」
            if (node.getParentId() != null) {
                checkParentAllows(require(node.getParentId()), newType);
            } else if (!newType.canBeRoot()) {
                throw new BizException(EhrErrorCode.ORG_PARENT_NOT_ALLOWED,
                        "根节点只能是「集团」；" + newType.getLabel() + "必须挂在上级节点下");
            }
        }
        checkNameUnique(node.getParentId(), name, id);

        node.setType(newType.name());
        node.setName(name);
        if (req.getShortName() != null) {
            node.setShortName(nvl(req.getShortName()));
        }
        if (req.getEmail() != null) {
            node.setEmail(nvl(req.getEmail()));
        }
        if (req.getAddress() != null) {
            node.setAddress(nvl(req.getAddress()));
        }
        if (req.getHeadcount() != null) {
            node.setHeadcount(req.getHeadcount());
        }
        if (req.getCostCenter() != null) {
            node.setCostCenter(nvl(req.getCostCenter()));
        }
        if (req.getEstablishDate() != null) {
            node.setEstablishDate(parseDate(req.getEstablishDate()));
        }
        // 负责人只有一个真相源：选了员工就以档案姓名为准，避免「姓名快照」与「审批人 ID」两处打架
        if (req.getLeaderEmployeeId() != null && req.getLeaderEmployeeId() > 0L) {
            node.setLeader(nvl(requireLeaderCandidate(node, req.getLeaderEmployeeId()).getName()));
        } else if (req.getLeader() != null) {
            node.setLeader(nvl(req.getLeader()));
        }
        // 0 表示清空负责人（updateById 会跳过 null，清空要单独走 update wrapper）
        if (req.getLeaderEmployeeId() != null) {
            if (req.getLeaderEmployeeId() == 0L) {
                node.setLeaderEmployeeId(null);
                orgMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OrgNode>()
                        .eq(OrgNode::getId, id)
                        .set(OrgNode::getLeaderEmployeeId, null));
            } else {
                node.setLeaderEmployeeId(req.getLeaderEmployeeId());
            }
        }
        if (req.getPhone() != null) {
            node.setPhone(nvl(req.getPhone()));
        }
        if (req.getStatus() != null && !Objects.equals(node.getStatus(), req.getStatus())) {
            // 状态变更要过「有下级/有员工不允许禁用」的校验，统一走 setStatus，避免从编辑接口绕过
            throw new BizException(ErrorCode.PARAM_ERROR, "节点状态请使用「启用 / 禁用」操作修改");
        }
        if (req.getSort() != null) {
            node.setSort(req.getSort());
        }
        if (req.getStatus() != null) {
            node.setStatus(req.getStatus());
        }
        if (req.getRemark() != null) {
            node.setRemark(nvl(req.getRemark()));
        }
        orgMapper.updateById(node);
        // 编辑弹窗正常不发 leaderEmployeeId；接口/脚本直接改负责人时走「直接维护」留痕（不发起审批，
        // 与既有行为一致），保证变更记录里每条负责人的变化都有出处。
        if (!Objects.equals(oldLeaderId, node.getLeaderEmployeeId())) {
            Employee emp = node.getLeaderEmployeeId() == null
                    ? null : employeeMapper.selectById(node.getLeaderEmployeeId());
            String type;
            String cause;
            if (node.getLeaderEmployeeId() == null) {
                type = LeaderChange.TYPE_CLEAR;
                cause = "部门信息维护时清除负责人（直接生效）";
            } else if (oldLeaderId == null) {
                type = LeaderChange.TYPE_SET;
                cause = "部门信息维护时直接指定负责人（未经审批）";
            } else {
                type = LeaderChange.TYPE_CHANGE;
                cause = "部门信息维护时直接更换负责人（未经审批）";
            }
            insertDirectRecord(node, oldLeaderId, oldLeaderName, emp, type, cause);
        }
        return toVO(node, employeeCounts().getOrDefault(id, OrgCounts.EMPTY));
    }

    /**
     * 「直接维护」型变更留痕：状态直接记已生效、不给审批单号。
     * 用于新增/编辑组织时顺带指定负责人这类老入口——不发起审批，但记录表不能缺行。
     */
    private void insertDirectRecord(OrgNode node, Long oldLeaderId, String oldLeaderName,
                                    Employee emp, String changeType, String reason) {
        LeaderChange rec = baseRecord(node, reason);
        rec.setChangeType(changeType);
        rec.setOldLeaderId(oldLeaderId);
        rec.setOldLeaderName(oldLeaderId == null ? "" : nvl(oldLeaderName));
        if (emp != null) {
            rec.setNewLeaderId(emp.getId());
            rec.setNewLeaderName(nvl(emp.getName()));
            rec.setNewLeaderEmpNo(nvl(emp.getEmpNo()));
            rec.setNewLeaderPosition(nvl(emp.getPosition()));
        }
        rec.setStatus(LeaderChange.STATUS_EFFECTIVE);
        rec.setEffectiveAt(LocalDateTime.now());
        leaderChangeMapper.insert(rec);
    }

    /** 上级与编码创建即锁定：请求只要带了不同的值就拦下，避免绕过前端只读态 */
    private void assertImmutableFields(OrgNode node, OrgNodeRequest req) {
        if (req.getParentId() != null && !Objects.equals(req.getParentId(), node.getParentId())) {
            throw new BizException(EhrErrorCode.ORG_FIELD_LOCKED,
                    "不支持调整上级；如需换挂靠位置，请在目标上级下新建节点");
        }
        if (req.getCode() != null && !Objects.equals(nvl(req.getCode()), nvl(node.getCode()))) {
            throw new BizException(EhrErrorCode.ORG_FIELD_LOCKED, "组织编码创建后不可修改");
        }
    }

    /**
     * 组织编码自动生成规则（业务约定）：
     * <pre>
     *   顶级集团            XC0001            （第二个集团 XC0002，依此类推）
     *   顶级下第 1 个子节点   XC00010001
     *   顶级下第 2 个子节点   XC00010002
     *   再下一级第 1 个       XC000100010001
     *   再下一级第 2 个       XC000100010002
     * </pre>
     * 即：编码 = 父编码 + 4 位同级序号；序号取「同级已用最大序号 + 1」。
     * <p>
     * 三点刻意设计：
     * <ol>
     *   <li>序号查的是<b>全表</b>（含逻辑删除），编码一经生成永不复用，删了节点号也不退回去；</li>
     *   <li>上级编码必须是规范的 XC... 形态，否则拒绝挂下级，避免出现 CX0001 这种半新半旧的编码；</li>
     *   <li>序号上限 9999，超了直接报错，不静默溢出成 5 位。</li>
     * </ol>
     * 调用方必须已在事务内取过 {@link OrgNodeMapper#lockForOrgCode()} 行锁。
     */
    private String generateOrgCode(OrgNode parent) {
        String prefix;
        if (parent == null) {
            prefix = ORG_CODE_PREFIX;
        } else {
            String parentCode = nvl(parent.getCode());
            if (!ORG_CODE_PATTERN.matcher(parentCode).matches()) {
                throw new BizException(EhrErrorCode.ORG_CODE_INVALID,
                        "上级节点「" + parent.getName() + "」的编码「" + parentCode
                                + "」不是规范的 XC 层级编码，请先执行组织编码回填");
            }
            prefix = parentCode;
        }
        int expectedLen = prefix.length() + ORG_CODE_SEGMENT_LEN;
        int max = 0;
        for (String code : orgMapper.selectCodesByPrefix(prefix)) {
            if (code == null || code.length() != expectedLen || !code.startsWith(prefix)) {
                continue;
            }
            String tail = code.substring(prefix.length());
            if (!ORG_CODE_SEGMENT.matcher(tail).matches()) {
                continue;
            }
            try {
                max = Math.max(max, Integer.parseInt(tail));
            } catch (NumberFormatException ignored) {
                // 4 位纯数字一定可 parse，这里只是防御
            }
        }
        int next = max + 1;
        if (next > ORG_CODE_MAX_SEQ) {
            throw new BizException(EhrErrorCode.ORG_CODE_EXHAUSTED,
                    "「" + prefix + "」下的组织编码序号已用尽（最大 9999），无法再新增同级节点");
        }
        return prefix + String.format("%04d", next);
    }

    /**
     * 设置 / 更换负责人（不带原因的兼容入口）。
     *
     * @deprecated 指定新负责人必须说明原因，请用 {@link #setLeader(Long, Long, String)}；
     *             这个重载只保留给「清除负责人」的历史调用方，指定人选时会因缺原因被拒。
     */
    @Deprecated
    public OrgNodeVO setLeader(Long id, Long employeeId) {
        return setLeader(id, employeeId, null);
    }

    /**
     * 设置 / 更换负责人。负责人是「建部门之后的事」：新增节点时不填，
     * 建完再从员工档案里挑人，避免建部门时档案还没录全。
     * <p>
     * <b>每一次「设置 / 更换 / 清除」都留一行记录</b>（{@code ehr_leader_change}）：
     * 记原负责人、新负责人、变更原因、发起人和结果，组织详情里能直接看变更履历。
     * <p>
     * 指定新负责人<b>不再直接生效</b>：先落一条待审批记录行并<b>独立提交</b>，再发起
     * 「部门设置负责人审批」（{@code ehr.dept_leader}）；审批单的 bizId 就是这条记录行的 id，
     * 终审回调按它回填「已生效 / 已驳回」并把新负责人写回组织节点。
     * 传 0 / null（清除负责人）仍直接生效——那是纠错动作，同样留痕，状态直接标记为已生效。
     *
     * @param employeeId 员工档案 ID；null 或 0 表示清除负责人
     * @param reason     变更原因；指定新负责人时必填
     */
    public OrgNodeVO setLeader(Long id, Long employeeId, String reason) {
        OrgNode node = require(id);
        String cause = reason == null ? "" : reason.trim();
        if (employeeId == null || employeeId <= 0L) {
            return clearLeader(node, cause);
        }
        Employee emp = requireLeaderCandidate(node, employeeId);
        if (Objects.equals(node.getLeaderEmployeeId(), employeeId)) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "「" + nvl(emp.getName()) + "」已经是「" + nvl(node.getName()) + "」的负责人，无需重复提交");
        }
        requireNoLeaderApprovalInFlight(node);
        if (cause.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请填写负责人变更原因（会记入变更记录并给审批人看）");
        }
        // ① 记录行先独立提交：settings 的 start() 在模板无审批人时会在调用内就回调，
        //    回调线程必须能读到这行，否则会按「单据不存在」正常返回，记录永远卡在审批中。
        LeaderChange record = newLeaderRecord(node, emp, cause);
        approvalLauncher.inTx(() -> leaderChangeMapper.insert(record));
        // ② 远程发起；③ 只有实例仍在途才回写实例号（记录行 + 组织节点上的「审批中」标记）。
        //    发起即终态时绝不回挂，否则组织列表会一直显示「审批中」。
        approvalLauncher.launch(
                () -> buildLeaderStart(node, emp, record, cause),
                instanceId -> { /* 实例号已退役：在途判断统一查审批中心 */ },
                // 本地已提交的记录行不能物理删（要留痕），标记成「已撤销」并写明原因
                () -> leaderChangeMapper.update(null, new LambdaUpdateWrapper<LeaderChange>()
                        .eq(LeaderChange::getId, record.getId())
                        .eq(LeaderChange::getStatus, LeaderChange.STATUS_PENDING)
                        .set(LeaderChange::getStatus, LeaderChange.STATUS_CANCELLED)
                        .set(LeaderChange::getRemark, "审批发起失败，本次变更未进入审批流程")));
        return detail(id);
    }

    /**
     * 清除负责人：纠错动作，直接生效不发起审批，但同样写一行记录（change_type=CLEAR）。
     * 本来就没有负责人时按幂等处理——不报错也不留空记录。
     */
    private OrgNodeVO clearLeader(OrgNode node, String reason) {
        if (node.getLeaderEmployeeId() == null) {
            return detail(node.getId());
        }
        requireNoLeaderApprovalInFlight(node);
        Long orgId = node.getId();
        Long oldLeaderId = node.getLeaderEmployeeId();
        String oldLeaderName = nvl(node.getLeader());
        String cause = reason.isBlank() ? "清除负责人（纠错）" : reason;
        approvalLauncher.inTx(() -> {
            orgMapper.update(null, new LambdaUpdateWrapper<OrgNode>()
                    .eq(OrgNode::getId, orgId)
                    .set(OrgNode::getLeader, "")
                    .set(OrgNode::getLeaderEmployeeId, null));
            LeaderChange rec = baseRecord(node, cause);
            rec.setChangeType(LeaderChange.TYPE_CLEAR);
            rec.setOldLeaderId(oldLeaderId);
            rec.setOldLeaderName(oldLeaderName);
            rec.setStatus(LeaderChange.STATUS_EFFECTIVE);
            rec.setEffectiveAt(LocalDateTime.now());
            leaderChangeMapper.insert(rec);
        });
        log.info("负责人已清除 orgId={} 原负责人={} 原因={}", orgId, oldLeaderName, cause);
        return detail(orgId);
    }

    /** 同一部门一次只允许一条在途的负责人变更：两条并行会让「后审完的」覆盖「先审完的」 */
    private void requireNoLeaderApprovalInFlight(OrgNode node) {
        if (approvalLauncher.hasRunning(String.valueOf(node.getId()), null)) {
            throw new BizException(EhrErrorCode.ORG_LEADER_APPROVING,
                    "「" + nvl(node.getName()) + "」已有负责人变更审批在进行中，请等审批结束后再操作");
        }
    }

    /** 记录行的公共快照部分（组织信息 + 发起人） */
    private LeaderChange baseRecord(OrgNode node, String reason) {
        SecurityUser user = SecurityUtils.currentUser().orElse(null);
        LeaderChange rec = new LeaderChange();
        rec.setOrgId(node.getId());
        rec.setOrgName(nvl(node.getName()));
        rec.setOrgPath(orgPathName(node));
        rec.setOrgType(node.getType());
        rec.setReason(reason);
        rec.setApplicantAccountId(user == null ? null : user.getAccountId());
        rec.setApplicantName(user == null ? "系统" : approverName(user));
        return rec;
    }

    /** 指定新负责人的记录行：首次设置与更换靠「原来有没有负责人」区分 */
    private LeaderChange newLeaderRecord(OrgNode node, Employee emp, String reason) {
        LeaderChange rec = baseRecord(node, reason);
        boolean hadLeader = node.getLeaderEmployeeId() != null;
        rec.setChangeType(hadLeader ? LeaderChange.TYPE_CHANGE : LeaderChange.TYPE_SET);
        rec.setOldLeaderId(node.getLeaderEmployeeId());
        rec.setOldLeaderName(hadLeader ? nvl(node.getLeader()) : "");
        rec.setNewLeaderId(emp.getId());
        rec.setNewLeaderName(nvl(emp.getName()));
        rec.setNewLeaderEmpNo(nvl(emp.getEmpNo()));
        rec.setNewLeaderPosition(nvl(emp.getPosition()));
        rec.setStatus(LeaderChange.STATUS_PENDING);
        return rec;
    }

    /**
     * 某部门的负责人变更记录（新→旧）。给组织详情的时间线用，按提交时间倒序。
     */
    public List<LeaderChangeVO> leaderChanges(Long orgId) {
        require(orgId);
        return leaderChangeMapper.selectList(new LambdaQueryWrapper<LeaderChange>()
                        .eq(LeaderChange::getOrgId, orgId)
                        .ne(LeaderChange::getStatus, LeaderChange.STATUS_CANCELLED)
                        .orderByDesc(LeaderChange::getCreatedAt)
                        .orderByDesc(LeaderChange::getId))
                .stream().map(this::toLeaderChangeVO).collect(Collectors.toList());
    }

    private LeaderChangeVO toLeaderChangeVO(LeaderChange rec) {
        LeaderChangeVO vo = new LeaderChangeVO();
        vo.setId(rec.getId());
        vo.setOrgId(rec.getOrgId());
        vo.setOrgName(rec.getOrgName());
        vo.setOrgPath(rec.getOrgPath());
        vo.setOrgType(rec.getOrgType());
        vo.setChangeType(rec.getChangeType());
        vo.setChangeTypeName(changeTypeName(rec.getChangeType()));
        vo.setOldLeaderId(rec.getOldLeaderId());
        vo.setOldLeaderName(rec.getOldLeaderName());
        vo.setNewLeaderId(rec.getNewLeaderId());
        vo.setNewLeaderName(rec.getNewLeaderName());
        vo.setNewLeaderEmpNo(rec.getNewLeaderEmpNo());
        vo.setNewLeaderPosition(rec.getNewLeaderPosition());
        vo.setReason(rec.getReason());
        vo.setStatus(rec.getStatus());
        vo.setStatusName(changeStatusName(rec.getStatus()));
        vo.setApplicantName(rec.getApplicantName());
        vo.setApprovalInstanceId(rec.getApprovalInstanceId());
        vo.setAppliedAt(rec.getCreatedAt());
        vo.setEffectiveAt(rec.getEffectiveAt());
        vo.setRemark(rec.getRemark());
        return vo;
    }

    private String changeTypeName(String type) {
        if (LeaderChange.TYPE_SET.equals(type)) {
            return "首次设置";
        }
        if (LeaderChange.TYPE_CLEAR.equals(type)) {
            return "清除负责人";
        }
        return "更换负责人";
    }

    private String changeStatusName(Integer status) {
        int s = status == null ? -1 : status;
        switch (s) {
            case LeaderChange.STATUS_PENDING:
                return "审批中";
            case LeaderChange.STATUS_EFFECTIVE:
                return "已生效";
            case LeaderChange.STATUS_REJECTED:
                return "已驳回";
            case LeaderChange.STATUS_CANCELLED:
                return "已撤销";
            default:
                return "未知";
        }
    }

    /**
     * 纯构造「负责人变更」发起请求。applicantOrgId 刻意传<b>目标部门</b>而不是操作人的部门：
     * 模板里「上级负责人」规则按发起人组织上溯，语义上要的是「这个部门的上级」，
     * 谁在页面上点的按钮与此无关。远程调用统一交给 ApprovalLauncher。
     */
    private ApprovalStartDTO buildLeaderStart(OrgNode node, Employee emp, LeaderChange record, String reason) {
        SecurityUser user = SecurityUtils.currentUser().orElse(null);
        ApprovalStartDTO dto = new ApprovalStartDTO();
        dto.setBizType(BIZ_ORG_LEADER);
        // bizId 指向变更记录行而不是组织节点：记录行是不可变快照，回调落点稳定；
        // 组织节点可能在审批期间被改名/移动/删除，回调按它找会丢单。
        dto.setBizId(String.valueOf(record.getId()));
        dto.setBizNo(nvl(node.getCode()));
        dto.setTitle("部门设置负责人审批 · " + node.getName());
        dto.setApplicantId(user == null ? null : user.getAccountId());
        dto.setApplicantName(user == null ? "系统" : approverName(user));
        dto.setApplicantOrgId(node.getId());
        dto.setOrgPath(orgPathName(node));
        dto.setFormData(leaderFormData(node, emp, record, reason));
        dto.setCallbackUrl(selfBaseUrl + CALLBACK_PATH);
        return dto;
    }

    /** 审批表单快照：审批人关心「哪个部门、原来是谁、要换成谁、为什么换」 */
    private Map<String, Object> leaderFormData(OrgNode node, Employee emp, LeaderChange record, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("recordId", String.valueOf(record.getId()));
        m.put("orgId", String.valueOf(node.getId()));
        m.put("orgName", nvl(node.getName()));
        m.put("orgPath", orgPathName(node));
        m.put("orgType", node.getType() == null ? "" : String.valueOf(node.getType()));
        m.put("depth", node.getDepth() == null ? 0 : node.getDepth());
        boolean hadLeader = node.getLeaderEmployeeId() != null;
        m.put("changeType", record.getChangeType());
        m.put("changeTypeName", changeTypeName(record.getChangeType()));
        m.put("hasLeader", hadLeader ? "是" : "否");
        m.put("oldLeader", hadLeader ? nvl(node.getLeader()) : "未设置");
        m.put("oldLeaderEmployeeId", node.getLeaderEmployeeId() == null ? "" : String.valueOf(node.getLeaderEmployeeId()));
        m.put("newLeader", nvl(emp.getName()));
        m.put("newLeaderEmployeeId", String.valueOf(emp.getId()));
        m.put("newLeaderEmpNo", nvl(emp.getEmpNo()));
        m.put("newLeaderPosition", nvl(emp.getPosition()));
        // 变更原因是必需信息：审批人据此判断这次换人是否合理
        m.put("reason", reason);
        return m;
    }

    /** 组织全路径，如「晨星科技集团 / 西安子公司 / 研发部」 */
    private String orgPathName(OrgNode node) {
        Map<Long, OrgNode> byId = new HashMap<>();
        for (OrgNode n : orgMapper.selectList(new LambdaQueryWrapper<OrgNode>())) {
            byId.put(n.getId(), n);
        }
        List<Long> ids = parsePath(node.getPath());
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            OrgNode n = byId.get(id);
            if (n != null) {
                if (sb.length() > 0) {
                    sb.append(" / ");
                }
                sb.append(nvl(n.getName()));
            }
        }
        return sb.toString();
    }

    /**
     * 负责人变更审批终审回调。<b>必须幂等</b>：回调失败会退避重试，同一结果可能投递多次。
     * <p>
     * 新流程的 bizId = {@code ehr_leader_change.id}（记录行）；历史在途单的 bizId 还是组织ID，
     * 查不到记录行时走 {@link #handleLegacyLeaderApproval} 兼容分支，老单不会被卡住。
     * <p>
     * 通过 → 记录行置「已生效」+ 生效时间，并把新负责人写回组织节点；
     * 驳回 / 撤回 / 终止 → 记录行置终态，负责人保持原样，同时清掉组织上的「审批中」标记。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleLeaderApprovalResult(ApprovalCallbackRequest req) {
        Long bizId = parseLongOrNull(req.getBizId());
        Long instId = parseLongOrNull(req.getInstanceId());
        if (bizId == null) {
            log.warn("负责人变更审批回调缺少业务ID：{}", req.getBizId());
            return;
        }
        // 存量单 bizId 是组织 ID（SUBJECT），新单是变更记录 ID（DOC）；
        // 按 bizIdKind 显式分流，不再「记录行查不到就当组织 ID」。
        if (!req.docBizId()) {
            handleLegacyLeaderApproval(req, bizId, instId);
            return;
        }
        LeaderChange record = leaderChangeMapper.selectById(bizId);
        if (record == null) {
            log.warn("负责人变更审批回调对应的变更记录不存在 bizId={}", req.getBizId());
            return;
        }
        if (record.getApprovalInstanceId() != null && !record.getApprovalInstanceId().equals(instId)) {
            log.info("忽略过期的负责人变更回调 recordId={} 当前单={} 回调单={}",
                    bizId, record.getApprovalInstanceId(), instId);
            return;
        }
        int st = record.getStatus() == null ? -1 : record.getStatus();
        if (st != LeaderChange.STATUS_PENDING) {
            log.info("负责人变更回调重复投递，忽略 recordId={} status={}", bizId, st);
            return;
        }
        if (!Boolean.TRUE.equals(req.getApproved())) {
            boolean cancelled = req.getResult() != null && (req.getResult() == 3 || req.getResult() == 4);
            record.setStatus(cancelled ? LeaderChange.STATUS_CANCELLED : LeaderChange.STATUS_REJECTED);
            record.setRemark(nvl(req.getResultName()));
            leaderChangeMapper.updateById(record);
            clearLeaderApproval(record.getOrgId());
            log.info("负责人变更未通过，维持原负责人 recordId={} orgId={} result={}",
                    record.getId(), record.getOrgId(), req.getResultName());
            return;
        }
        Long newLeaderId = record.getNewLeaderId();
        if (newLeaderId == null && req.getFormData() != null) {
            Object raw = req.getFormData().get("newLeaderEmployeeId");
            newLeaderId = parseLongOrNull(raw == null ? null : String.valueOf(raw));
        }
        Employee emp = newLeaderId == null ? null : employeeMapper.selectById(newLeaderId);
        if (emp == null) {
            record.setStatus(LeaderChange.STATUS_REJECTED);
            record.setRemark("审批已通过，但新负责人档案已不存在，变更未生效");
            leaderChangeMapper.updateById(record);
            clearLeaderApproval(record.getOrgId());
            log.error("负责人变更审批通过但新负责人已不存在 recordId={} employeeId={}",
                    record.getId(), newLeaderId);
            return;
        }
        orgMapper.update(null, new LambdaUpdateWrapper<OrgNode>()
                .eq(OrgNode::getId, record.getOrgId())
                .set(OrgNode::getLeader, emp.getName() == null ? "" : emp.getName())
                .set(OrgNode::getLeaderEmployeeId, emp.getId())
                .set(OrgNode::getLeaderApprovalInstanceId, null));
        record.setNewLeaderId(emp.getId());
        record.setNewLeaderName(nvl(emp.getName()));
        record.setStatus(LeaderChange.STATUS_EFFECTIVE);
        record.setEffectiveAt(LocalDateTime.now());
        leaderChangeMapper.updateById(record);
        log.info("负责人变更审批通过，已生效 recordId={} orgId={} leader={}",
                record.getId(), record.getOrgId(), emp.getName());
    }

    /**
     * 兼容历史在途单：那时候 bizId 直接是组织ID，没有变更记录行。
     * 逻辑与旧实现一致（通过→落库新负责人；驳回→维持原样），只为让老单能正常走完。
     */
    private void handleLegacyLeaderApproval(ApprovalCallbackRequest req, Long orgId, Long instId) {
        OrgNode node = orgMapper.selectById(orgId);
        if (node == null) {
            log.warn("负责人变更审批回调对应的组织不存在 bizId={}", req.getBizId());
            return;
        }
        if (node.getLeaderApprovalInstanceId() != null
                && !node.getLeaderApprovalInstanceId().equals(instId)) {
            log.info("忽略过期的负责人变更审批回调 orgId={} 当前单={} 回调单={}",
                    orgId, node.getLeaderApprovalInstanceId(), instId);
            return;
        }
        if (!Boolean.TRUE.equals(req.getApproved())) {
            log.info("负责人变更审批未通过，维持原负责人 orgId={} result={}", orgId, req.getResultName());
            clearLeaderApproval(orgId);
            return;
        }
        Object newIdRaw = req.getFormData() == null ? null : req.getFormData().get("newLeaderEmployeeId");
        Long newLeaderId = parseLongOrNull(newIdRaw == null ? null : String.valueOf(newIdRaw));
        if (newLeaderId == null) {
            log.error("负责人变更审批回调缺少 newLeaderEmployeeId，无法落库 bizId={}", req.getBizId());
            clearLeaderApproval(orgId);
            return;
        }
        Employee emp = employeeMapper.selectById(newLeaderId);
        if (emp == null) {
            log.error("负责人变更审批回调对应的新负责人已不存在，放弃落库 orgId={} employeeId={}", orgId, newLeaderId);
            clearLeaderApproval(orgId);
            return;
        }
        orgMapper.update(null, new LambdaUpdateWrapper<OrgNode>()
                .eq(OrgNode::getId, orgId)
                .set(OrgNode::getLeader, emp.getName() == null ? "" : emp.getName())
                .set(OrgNode::getLeaderEmployeeId, newLeaderId)
                .set(OrgNode::getLeaderApprovalInstanceId, null));
        log.info("负责人变更审批通过，已生效（历史单据） orgId={} leader={}", orgId, emp.getName());
    }

    /** updateById 跳过 null 字段，清在途标记必须用 UpdateWrapper 显式 set null */
    private void clearLeaderApproval(Long orgId) {
        orgMapper.update(null, new LambdaUpdateWrapper<OrgNode>()
                .eq(OrgNode::getId, orgId)
                .set(OrgNode::getLeaderApprovalInstanceId, null));
    }

    private Long parseLongOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 发起人显示名：优先取档案姓名，取不到回落登录名 */
    private String approverName(SecurityUser user) {
        Employee me = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, user.getAccountId()).last("limit 1"));
        return me == null ? user.getUsername() : me.getName();
    }

    /**
     * 校验候选人是否可担任该节点负责人：必须**直接挂在**本节点下（下级部门的人不行），
     * 且处于试用期/在职。负责人是「本部门的事」，跨部门挂会让请假、离职的审批链路挂到不相干的人身上。
     */
    private Employee requireLeaderCandidate(OrgNode node, Long employeeId) {
        Employee emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工档案不存在，请刷新后重试");
        }
        EmployeeStatus status = EmployeeStatus.of(emp.getStatus());
        if (status == null || !status.isWorking()) {
            throw new BizException(EhrErrorCode.ORG_LEADER_INVALID,
                    "只能选试用期或在职员工担任负责人；「" + emp.getName() + "」当前不是在职状态");
        }
        if (!Objects.equals(emp.getOrgId(), node.getId())) {
            throw new BizException(EhrErrorCode.ORG_LEADER_NOT_IN_NODE,
                    "负责人只能选直接挂在「" + node.getName() + "」下的员工；「" + emp.getName()
                            + "」不在该部门");
        }
        return emp;
    }

    /**
     * 启用 / 禁用节点。禁用比删除温和，但仍然拒绝「有下级」或「有人」的节点：
     * 否则子节点挂在一个停用的父节点下，审批链路会静默断掉。
     *
     * @param status 1 启用、2 禁用
     */
    @Transactional(rollbackFor = Exception.class)
    public OrgNodeVO setStatus(Long id, Integer status) {
        if (status == null || (status != 1 && status != 2)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "状态只能是 1（启用）或 2（禁用）");
        }
        OrgNode node = require(id);
        if (Objects.equals(node.getStatus(), status)) {
            return detail(id);
        }
        if (status == 2) {
            if (childCount(id) > 0) {
                throw new BizException(EhrErrorCode.ORG_HAS_CHILDREN,
                        "该节点下还有子节点，不能禁用；请先处理下级节点");
            }
            long emp = notLeftEmployeeCount(id);
            if (emp > 0) {
                throw new BizException(EhrErrorCode.ORG_HAS_EMPLOYEES,
                        "该节点下还有 " + emp + " 名员工，不能禁用；请先调离或删除");
            }
        }
        orgMapper.update(null, new LambdaUpdateWrapper<OrgNode>()
                .eq(OrgNode::getId, id)
                .set(OrgNode::getStatus, status));
        return detail(id);
    }

    /** 删除节点：有子节点或有人，一律拒绝——组织树错删比错建难恢复得多 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        if (childCount(id) > 0) {
            throw new BizException(EhrErrorCode.ORG_HAS_CHILDREN, "该节点下还有子节点，不能删除");
        }
        long emp = notLeftEmployeeCount(id);
        if (emp > 0) {
            throw new BizException(EhrErrorCode.ORG_HAS_EMPLOYEES,
                    "该节点下还有 " + emp + " 名员工，不能删除；请先调离或删除");
        }
        orgMapper.deleteById(id);
    }

    /** 未归档员工数（含待入职/审批中/试用/在职/离职中）：删除与禁用的统一口径 */
    private long notLeftEmployeeCount(Long orgId) {
        Long n = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getOrgId, orgId)
                .ne(Employee::getStatus, EmployeeStatus.LEFT.getCode()));
        return n == null ? 0L : n;
    }

    /* ---------------- 内部工具 ---------------- */

    public OrgNode require(Long id) {
        OrgNode node = orgMapper.selectById(id);
        if (node == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织节点不存在");
        }
        return node;
    }

    private OrgType requireType(String raw) {
        OrgType type = OrgType.of(raw);
        if (type == null) {
            throw new BizException(EhrErrorCode.ORG_TYPE_INVALID,
                    "组织类型只能是 集团/子公司/部门/项目/子项目");
        }
        return type;
    }

    private String requireName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "节点名称必填");
        }
        if (name.length() > 128) {
            throw new BizException(ErrorCode.PARAM_ERROR, "节点名称不能超过 128 字");
        }
        return name;
    }

    /** 父类型是否允许挂这个子类型，报错信息直接把「允许挂什么」说清楚 */
    private void checkParentAllows(OrgNode parent, OrgType child) {
        OrgType parentType = parent.typeEnum();
        if (parentType == null) {
            throw new BizException(EhrErrorCode.ORG_TYPE_INVALID, "上级节点类型异常");
        }
        if (parentType.allowedChildren().contains(child)) {
            return;
        }
        String allowed = parentType.allowedChildren().isEmpty()
                ? "不能再挂任何节点"
                : "只能挂 " + parentType.allowedChildren().stream()
                .map(OrgType::getLabel).reduce((a, b) -> a + "、" + b).orElse("");
        throw new BizException(EhrErrorCode.ORG_PARENT_NOT_ALLOWED,
                "「" + parent.getName() + "（" + parentType.getLabel() + "）」" + allowed + "，不能挂「" + child.getLabel() + "」");
    }

    private void checkNameUnique(Long parentId, String name, Long excludeId) {
        LambdaQueryWrapper<OrgNode> w = new LambdaQueryWrapper<OrgNode>()
                .eq(OrgNode::getName, name);
        if (parentId == null) {
            w.isNull(OrgNode::getParentId);
        } else {
            w.eq(OrgNode::getParentId, parentId);
        }
        if (excludeId != null) {
            w.ne(OrgNode::getId, excludeId);
        }
        Long dup = orgMapper.selectCount(w);
        if (dup != null && dup > 0) {
            throw new BizException(EhrErrorCode.ORG_NAME_DUPLICATED,
                    "同级下已存在「" + name + "」");
        }
    }

    private long childCount(Long id) {
        Long n = orgMapper.selectCount(new LambdaQueryWrapper<OrgNode>().eq(OrgNode::getParentId, id));
        return n == null ? 0 : n;
    }

    /** 组织人数：working = 试用期 + 在职（编制口径），all = 未归档（删除/禁用校验口径） */
    private record OrgCounts(long working, long all) {
        static final OrgCounts EMPTY = new OrgCounts(0L, 0L);
    }

    private Map<Long, OrgCounts> employeeCounts() {
        Map<Long, OrgCounts> map = new HashMap<>();
        for (Map<String, Object> row : employeeMapper.countGroupByOrg()) {
            if (row.get("orgId") instanceof Number orgId) {
                map.put(orgId.longValue(), new OrgCounts(num(row.get("cnt")), num(row.get("cntAll"))));
            }
        }
        return map;
    }

    private static long num(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private OrgNodeVO toVO(OrgNode n, OrgCounts counts) {
        OrgNodeVO vo = new OrgNodeVO();
        vo.setId(n.getId());
        vo.setParentId(n.getParentId());
        vo.setType(n.getType());
        OrgType type = n.typeEnum();
        vo.setTypeName(type == null ? n.getType() : type.getLabel());
        vo.setCode(n.getCode());
        vo.setName(n.getName());
        vo.setShortName(n.getShortName());
        vo.setEmail(n.getEmail());
        vo.setAddress(n.getAddress());
        vo.setHeadcount(n.getHeadcount());
        vo.setCostCenter(n.getCostCenter());
        vo.setEstablishDate(n.getEstablishDate());
        vo.setCreatedAt(n.getCreatedAt());
        vo.setLeader(n.getLeader());
        vo.setLeaderEmployeeId(n.getLeaderEmployeeId());
        vo.setApprovalInstanceId(n.getLeaderApprovalInstanceId());
        vo.setApprovalBizType(n.getLeaderApprovalInstanceId() == null ? null : BIZ_ORG_LEADER);
        if (n.getLeaderEmployeeId() != null) {
            com.laimengchao.sso.ehr.module.employee.entity.Employee leader =
                    employeeMapper.selectById(n.getLeaderEmployeeId());
            vo.setLeaderEmployeeName(leader == null ? "" : leader.getName());
        } else {
            vo.setLeaderEmployeeName("");
        }
        vo.setPhone(n.getPhone());
        vo.setSort(n.getSort());
        vo.setStatus(n.getStatus());
        vo.setDepth(n.getDepth());
        vo.setPath(n.getPath());
        vo.setRemark(n.getRemark());
        vo.setEmployeeCount(counts.working());
        vo.setEmployeeCountAll(counts.all());
        if (type != null) {
            type.allowedChildren().forEach(child ->
                    vo.getAllowedChildTypes().add(new OrgNodeVO.ChildType(child.name(), child.getLabel())));
        }
        return vo;
    }

    private void sortTree(List<OrgNodeVO> nodes) {
        nodes.sort(Comparator
                .comparing((OrgNodeVO n) -> n.getSort() == null ? 0 : n.getSort())
                .thenComparing(OrgNodeVO::getId));
        nodes.forEach(n -> sortTree(n.getChildren()));
    }

    private void walk(List<OrgNodeVO> nodes, int level, List<OrgNodeVO> out) {
        for (OrgNodeVO n : nodes) {
            n.setDepth(level);
            out.add(n);
            walk(n.getChildren(), level + 1, out);
        }
    }

    private boolean matches(OrgNode n, String kw) {
        return (n.getName() != null && n.getName().toLowerCase().contains(kw))
                || (n.getCode() != null && n.getCode().toLowerCase().contains(kw))
                || (n.getLeader() != null && n.getLeader().toLowerCase().contains(kw));
    }

    private List<Long> parsePath(String path) {
        List<Long> ids = new ArrayList<>();
        if (path == null || path.isBlank()) {
            return ids;
        }
        for (String part : path.split("/")) {
            if (!part.isBlank()) {
                try {
                    ids.add(Long.valueOf(part));
                } catch (NumberFormatException ignored) {
                    // 脏数据跳过，不影响其余节点
                }
            }
        }
        return ids;
    }

    private String nvl(String s) {
        return s == null ? "" : s.trim();
    }

    /** 负责人姓名只是员工档案的展示快照：给了员工 ID 就以档案姓名为准，查不到才退回入参 */
    private String leaderNameOf(Long employeeId, String fallback) {
        if (employeeId == null || employeeId <= 0L) {
            return nvl(fallback);
        }
        Employee emp = employeeMapper.selectById(employeeId);
        return emp == null ? nvl(fallback) : nvl(emp.getName());
    }

    /** 成立日期：空串视为清空；格式错误直接拒绝，避免脏数据进库 */
    private java.time.LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "成立日期格式应为 yyyy-MM-dd");
        }
    }
}
