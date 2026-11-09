package com.laimengchao.sso.settings.module.approval.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.settings.common.SettingsErrorCode;
import com.laimengchao.sso.settings.module.approval.constant.InstanceStatus;
import com.laimengchao.sso.settings.module.approval.constant.TaskStatus;
import com.laimengchao.sso.settings.module.approval.dto.SceneTableBindingsDto;
import com.laimengchao.sso.settings.module.approval.entity.ApprInstance;
import com.laimengchao.sso.settings.module.approval.entity.ApprTask;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalPage;
import com.laimengchao.sso.settings.module.approval.entity.BizScene;
import com.laimengchao.sso.settings.module.approval.mapper.ApprInstanceMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprTaskMapper;
import com.laimengchao.sso.settings.module.approval.mapper.BizSceneMapper;
import com.laimengchao.sso.settings.module.approval.vo.ApprovalContextVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批弹窗的渲染上下文：把「页面配置」与「字段值」一次算好交给前端。
 * <p>
 * 五类字段来源在这里收口（对应设计文档第 10 章）：
 * <ul>
 *   <li><b>FORM</b> —— 读提交时冻结的表单快照。审批人看到的是提交那一刻的值，
 *       业务方后续改了职位也不会影响在途单的判断依据；</li>
 *   <li><b>ENTITY</b> —— 读快照里的键（历史「假绑定」）。真正的实时读业务库由 <b>TABLE</b> 承担；</li>
 *   <li><b>TABLE</b> —— <b>真绑定</b>：按发起时冻结在实例上的绑定（appr_instance.table_bindings，
 *       老单回落场景配置 cfg_biz_scene.table_bindings）的表，回业务库捞当下真实行。
 *       主体表按快照里的 subjectKey（未配置时按所属应用兜底）取，子表按 fk 反查，写入 {@code 表名.列名} 与
 *       整表列表；</li>
 *   <li><b>DERIVED</b> —— 派生值。本服务不执行业务表达式，改由业务方在发起时算好塞进快照
 *       （如入职的「试用期汇总」「档案完整度」），这里只做透明传递；</li>
 *   <li><b>SYSTEM</b> —— 实例自身的属性（提交人、发起时间等）。</li>
 * </ul>
 * 前端拿到 {@code values} 直接铺开，不做任何计算——口径只有一处，改版式改不出错数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalContextService {

    private final ApprInstanceMapper instanceMapper;
    private final ApprTaskMapper taskMapper;
    private final ApprovalConfigService configService;
    private final ObjectMapper objectMapper;
    private final BizSceneMapper sceneMapper;
    private final EntityDataClient entityDataClient;

    /**
     * @param viewerId 当前查看人，用来回填「我的待办」——前端据此决定通过/驳回按钮是否可用
     */
    public ApprovalContextVO context(Long instanceId, Long viewerId, String view) {
        ApprInstance inst = instanceMapper.selectById(instanceId);
        if (inst == null) {
            throw new BizException(SettingsErrorCode.APPROVAL_INSTANCE_NOT_FOUND);
        }
        ApprovalContextVO vo = new ApprovalContextVO();
        vo.setInstanceId(inst.getId());
        vo.setBizType(inst.getBizType());
        vo.setBizName(inst.getBizName());
        vo.setTitle(inst.getTitle());
        vo.setApplicantName(inst.getApplicantName());
        vo.setStatus(inst.getStatus());
        InstanceStatus st = InstanceStatus.of(inst.getStatus());
        vo.setStatusName(st == null ? "" : st.getLabel());

        // 1. 先铺表单快照：FORM / ENTITY / DERIVED 三类都从这里取，键名即字段 code
        Map<String, Object> values = new LinkedHashMap<>();
        values.putAll(readFormData(inst));

        // 2. 再补系统字段（允许覆盖同名的快照键，以实例为准）
        values.put("applicantName", inst.getApplicantName());
        values.put("submitter", inst.getApplicantName());
        values.put("bizName", inst.getBizName());
        values.put("bizNo", inst.getBizNo());
        values.put("title", inst.getTitle());
        values.put("submitTime", inst.getCreatedAt() == null ? null
                : inst.getCreatedAt().toString().replace('T', ' '));
        // orgPath 快照里可能没有（业务方没传），用实例上记录的组织路径兜底
        if (!values.containsKey("orgPath") || values.get("orgPath") == null) {
            values.put("orgPath", inst.getOrgPath());
        }

        // 2.5 真绑定：TABLE 来源字段——按实例冻结的绑定去业务库捞真实行，覆盖/补充快照值。
        // 与 FORM/ENTITY 读冻结快照不同，这里拿的是业务库当下的值（如人员档案被改后详情页即时反映）；
        // 绑定关系本身是冻结的，事后改场景绑定不影响这张在途单。
        Map<String, String> tableModes = new LinkedHashMap<>();
        resolveTableBindings(inst, values, tableModes);
        vo.setValues(values);
        vo.setTableModes(tableModes);

        // 3. 页面配置：没配就返回 null，前端走内置默认版式（四级降级链的最底一层）
        ApprovalPage page = configService.getPageByBizType(inst.getBizType(), view);
        if (page != null) {
            vo.setRenderMode(page.getRenderMode());
            Map<String, Object> parsed = parseConfig(page.getConfig());
            vo.setPage(parsed);
            vo.setActions(readActions(parsed));
            // ROUTE 版式的自定义页面地址一并带上，前端按 renderMode 分支
            if ("ROUTE".equalsIgnoreCase(page.getRenderMode())) {
                vo.getPage().put("routePath", page.getRoutePath());
            }
        }

        // 4. 我的待办
        if (viewerId != null) {
            ApprTask mine = taskMapper.selectOne(Wrappers.<ApprTask>lambdaQuery()
                    .eq(ApprTask::getInstanceId, instanceId)
                    .eq(ApprTask::getApproverId, viewerId)
                    .eq(ApprTask::getStatus, TaskStatus.PENDING.getCode())
                    .eq(ApprTask::getTaskKind, "APPROVE")
                    .orderByAsc(ApprTask::getSeqInNode)
                    .last("limit 1"));
            vo.setMyTaskId(mine == null ? null : mine.getId());
        }
        return vo;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String config) {
        if (config == null || config.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(config, Map.class);
        } catch (Exception e) {
            // 配置写坏了不能让审批人白屏：记 warn 后按未配置处理，前端退回默认版式
            log.warn("审批页面配置解析失败，降级为默认版式：{}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private List<String> readActions(Map<String, Object> page) {
        List<String> actions = new ArrayList<>();
        Object raw = page == null ? null : page.get("actions");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    actions.add(String.valueOf(o));
                }
            }
        }
        return actions;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readFormData(ApprInstance inst) {
        if (inst.getFormData() == null || inst.getFormData().isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(inst.getFormData(), Map.class);
        } catch (Exception e) {
            log.warn("表单快照解析失败 instance={} err={}", inst.getId(), e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * TABLE 真绑定：按<b>实例冻结的</b>数据表绑定，回业务库捞真实行写入 values。
     * <p>
     * 绑定来源优先级：实例快照 {@code appr_instance.table_bindings}（发起时冻结） →
     * 场景当前配置 {@code cfg_biz_scene.table_bindings}（补列前的老单兜底）。
     * 因此管理端事后改场景绑定（含所属应用）只影响新单，在途单继续按旧绑定渲染。
     * <p>
     * <b>去哪个库取数</b>由绑定的 appCode 决定（ehr / cms / blog / sso-center / settings 各自一套库）：
     * 优先用快照里的 appCode，老单没有就回落场景的 appCode。表名只在各自库内唯一，
     * 所以这一步不能省，也不能拿当前场景配置去覆盖快照。
     * <p>
     * 主体表（main=true）按 formData 里的 subjectKey（未配置时按所属应用兜底）取一行，列以
     * {@code 表名.列名} 命名塞进 values（如 ehr_employee.name），同时整行另存 values[表名] 方便前端。
     * 子表按各自 fk 列反查出<b>多行</b>，整张表作为列表存 values[表名]（List&lt;Map&gt;）：
     * 页面配置里的表格控件直接渲染它；某一列被单独放进普通表单时，前端取该列表首行的对应列值。
     * 失败按「取不到」降级（不阻断审批弹窗）。
     */
    private void resolveTableBindings(ApprInstance inst, Map<String, Object> values,
                                      Map<String, String> tableModes) {
        String raw = inst.getTableBindings();
        BizScene scene = null;
        if (raw == null || raw.isBlank()) {
            scene = findScene(inst.getBizType());
            raw = scene == null ? null : scene.getTableBindings();
        }
        if (raw == null || raw.isBlank()) {
            return;
        }
        SceneTableBindingsDto bind;
        try {
            bind = objectMapper.readValue(raw, SceneTableBindingsDto.class);
        } catch (Exception e) {
            log.warn("数据表绑定解析失败 bizType={} instance={} err={}", inst.getBizType(), inst.getId(), e.getMessage());
            return;
        }
        if (bind.getTables() == null || bind.getTables().isEmpty()) {
            return;
        }
        if (scene == null) {
            scene = findScene(inst.getBizType());
        }
        String appCode = bind.getAppCode();
        if (appCode == null || appCode.isBlank()) {
            appCode = scene == null ? null : scene.getAppCode();
        }
        // 主体键优先用绑定快照，其次场景配置，最后按 appCode 兜底
        String subjectKey = bind.getSubjectKey();
        if (subjectKey == null || subjectKey.isBlank()) {
            subjectKey = scene == null || scene.getSubjectField() == null || scene.getSubjectField().isBlank()
                    ? defaultSubjectKey(appCode) : scene.getSubjectField();
        }
        Object rawId = inst.getSubjectId();
        if (rawId == null || String.valueOf(rawId).isBlank()) {
            rawId = values.get(subjectKey);
        }
        if (rawId == null) {
            log.debug("审批单 {} 缺少主体 ID（列 subject_id 与快照键 {} 都为空），跳过 TABLE 真绑定",
                    inst.getId(), subjectKey);
            return;
        }
        String subjectId = String.valueOf(rawId).trim();
        if (subjectId.isEmpty()) {
            return;
        }
        String bizId = inst.getBizId();
        // 存量单没标 biz_id_kind：主体 ID 与 bizId 相同即老口径 SUBJECT
        String kind = inst.getBizIdKind();
        if (kind == null || kind.isBlank()) {
            kind = subjectId.equals(bizId) ? "SUBJECT" : "DOC";
        }
        boolean isDoc = "DOC".equalsIgnoreCase(kind);

        for (SceneTableBindingsDto.BoundTable t : bind.getTables()) {
            if (t.getTable() == null || t.getTable().isBlank()) {
                continue;
            }
            String mode = t.effectiveMode();
            // 存量 SUBJECT 单（bizId 就是主体 ID，没有独立申请单）没有「本次单据行」可查，
            // BIZ_ROW 退化为按外键取列表，保证老单仍能渲染出数据。
            if (SceneTableBindingsDto.MODE_BIZ_ROW.equals(mode) && !isDoc) {
                mode = t.getFk() == null || t.getFk().isBlank()
                        ? SceneTableBindingsDto.MODE_MAIN_ROW : SceneTableBindingsDto.MODE_FK_LIST;
            }
            tableModes.put(t.getTable(), mode);
            if (SceneTableBindingsDto.MODE_FK_LIST.equals(mode)) {
                String fk = t.getFk();
                if (fk == null || fk.isBlank()) {
                    continue;
                }
                JsonNode rows = entityDataClient.rowsByFk(appCode, t.getTable(), fk, subjectId);
                if (rows == null || !rows.isArray()) {
                    continue;
                }
                List<Map<String, Object>> list = new ArrayList<>();
                for (JsonNode r : rows) {
                    list.add(objectMapper.convertValue(r, Map.class));
                }
                values.put(t.getTable(), list);
                continue;
            }
            // MAIN_ROW 按主体 ID；BIZ_ROW 按本次单据 ID——绝不按主体外键捞历史全部
            String rowId = SceneTableBindingsDto.MODE_BIZ_ROW.equals(mode) ? bizId : subjectId;
            if (rowId == null || rowId.isBlank()) {
                continue;
            }
            JsonNode row = entityDataClient.row(appCode, t.getTable(), rowId);
            if (row == null || !row.isObject()) {
                continue;
            }
            Map<String, Object> rowMap = objectMapper.convertValue(row, Map.class);
            for (Map.Entry<String, Object> e : rowMap.entrySet()) {
                values.put(t.getTable() + "." + e.getKey(), e.getValue());
            }
            if (SceneTableBindingsDto.MODE_BIZ_ROW.equals(mode)) {
                // 表格控件仍可显示「本次单据」一行，但只有一行，不会把历史全堆出来
                values.put(t.getTable(), List.of(rowMap));
            } else {
                values.put(t.getTable(), rowMap);
            }
        }
    }

    /**
     * 各应用的「主体主键」默认键名（表单快照里的字段名）。
     * <p>
     * 绑定里没显式写 {@code subjectKey} 时按<b>所属应用</b>兜底：ehr 的单据快照带 employeeId，
     * cms / blog 用 articleId，账号与配置类用 id。
     * <p>
     * 历史上这里写死 employeeId——非 EHR 场景在没配 subjectKey 时主表根本取不到行，
     * 且管理端看不出到底按哪个键取。现在前端不再预填，这里也改成按应用兜底。
     */
    private static final Map<String, String> DEFAULT_SUBJECT_KEYS = Map.of(
            "ehr", "employeeId",
            "cms", "articleId",
            "blog", "articleId",
            "sso-center", "id",
            "settings", "id");

    /** 绑定未写 subjectKey 时的兜底键名。appCode 缺失的老单仍按 employeeId 处理，保持兼容。 */
    private static String defaultSubjectKey(String appCode) {
        if (appCode == null || appCode.isBlank()) {
            return "employeeId";
        }
        return DEFAULT_SUBJECT_KEYS.getOrDefault(appCode.trim().toLowerCase(), "id");
    }

    /** 按 bizType 取场景（绑定与 appCode 的兜底来源） */
    private BizScene findScene(String bizType) {
        if (bizType == null || bizType.isBlank()) {
            return null;
        }
        return sceneMapper.selectOne(Wrappers.<BizScene>lambdaQuery()
                .eq(BizScene::getBizType, bizType).last("limit 1"));
    }
}
