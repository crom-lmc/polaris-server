package com.laimengchao.sso.settings.module.approval.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.settings.common.SettingsErrorCode;
import com.laimengchao.sso.settings.module.approval.constant.InstanceStatus;
import com.laimengchao.sso.settings.module.approval.dto.SceneTableBindingsDto;
import com.laimengchao.sso.settings.module.approval.dto.TemplateDetailVO;
import com.laimengchao.sso.settings.module.approval.dto.TemplateSaveRequest;
import com.laimengchao.sso.settings.module.approval.entity.ApprInstance;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalNode;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalPage;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalStep;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalTemplate;
import com.laimengchao.sso.settings.module.approval.entity.BizScene;
import com.laimengchao.sso.settings.module.approval.mapper.ApprInstanceMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprovalNodeMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprovalPageMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprovalStepMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprovalTemplateMapper;
import com.laimengchao.sso.settings.module.approval.mapper.BizSceneMapper;
import com.laimengchao.sso.settings.module.approval.model.ApprovalSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审批模板与页面配置的读写。
 * <p>
 * 保存是<b>整树替换</b>：前端把按 DFS 前序展平的步骤树提交上来，后端物理删旧插新、version + 1。
 * <p>
 * <b>v5 两阶段保存</b>：前端用本地 clientId 表达「这一步挂在哪个分支下」，而落库的
 * parent_node_id / branch_path 必须是真实雪花 ID。所以先给所有 NodeDto 预分配 ID，
 * 再按提交顺序（DFS）把 parentNodeRef 换成真实 ID、重算 branchPath / depth / seq。
 * 前端的本地 id 一律不落库，也不信任前端算好的路径与深度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalConfigService {

    /** 条件网关嵌套深度上限（用户 2026-11-05 拍板：<= 3 层） */
    public static final int MAX_DEPTH = 3;

    private final ApprovalTemplateMapper templateMapper;
    private final ApprovalStepMapper stepMapper;
    private final ApprovalNodeMapper nodeMapper;
    private final ApprovalPageMapper pageMapper;
    private final BizSceneMapper sceneMapper;
    private final ApprInstanceMapper instanceMapper;
    private final ConditionEvaluator conditionEvaluator;
    private final BizPointReferenceService bizPointReferences;
    private final ObjectMapper objectMapper;

    // ================================================================ 模板

    public List<TemplateDetailVO> list() {
        List<ApprovalTemplate> templates = templateMapper.selectList(Wrappers.<ApprovalTemplate>lambdaQuery()
                .orderByDesc(ApprovalTemplate::getUpdatedAt));
        List<ApprovalStep> allSteps = stepMapper.selectList(null);
        List<ApprovalNode> allNodes = nodeMapper.selectList(null);
        List<TemplateDetailVO> list = new ArrayList<>();
        for (ApprovalTemplate t : templates) {
            TemplateDetailVO vo = baseVO(t);
            int stepCount = (int) allSteps.stream().filter(s -> t.getId().equals(s.getTemplateId())).count();
            int nodeCount = (int) allNodes.stream().filter(n -> t.getId().equals(n.getTemplateId())).count();
            vo.setStepCount(stepCount);
            vo.setNodeCount(nodeCount);
            Long running = instanceMapper.selectCount(Wrappers.<ApprInstance>lambdaQuery()
                    .eq(ApprInstance::getTemplateId, t.getId())
                    .eq(ApprInstance::getStatus, InstanceStatus.RUNNING.getCode()));
            vo.setRunningCount(running == null ? 0 : running.intValue());
            list.add(vo);
        }
        return list;
    }

    public TemplateDetailVO detail(Long id) {
        ApprovalTemplate tpl = templateMapper.selectById(id);
        if (tpl == null) {
            throw new BizException(SettingsErrorCode.APPROVAL_TEMPLATE_NOT_FOUND);
        }
        TemplateDetailVO vo = baseVO(tpl);
        List<ApprovalStep> steps = stepMapper.selectList(Wrappers.<ApprovalStep>lambdaQuery()
                .eq(ApprovalStep::getTemplateId, id)
                .orderByAsc(ApprovalStep::getSeq));
        List<ApprovalNode> nodes = nodeMapper.selectList(Wrappers.<ApprovalNode>lambdaQuery()
                .eq(ApprovalNode::getTemplateId, id)
                .orderByAsc(ApprovalNode::getSeq));
        for (ApprovalStep s : steps) {
            TemplateDetailVO.StepVO sv = new TemplateDetailVO.StepVO();
            sv.setId(s.getId());
            sv.setSeq(s.getSeq());
            sv.setName(s.getName());
            sv.setConditionExpr(s.getConditionExpr());
            sv.setConditionDesc(s.getConditionDesc());
            sv.setRejectAction(s.getRejectAction());
            sv.setParentNodeId(s.getParentNodeId());
            sv.setBranchPath(empty(s.getBranchPath()));
            sv.setDepth(s.getDepth() == null ? 0 : s.getDepth());
            sv.setUiKind(empty(s.getUiKind()));
            for (ApprovalNode n : nodes) {
                if (!s.getId().equals(n.getStepId())) {
                    continue;
                }
                TemplateDetailVO.NodeVO nv = new TemplateDetailVO.NodeVO();
                nv.setId(n.getId());
                nv.setSeq(n.getSeq());
                nv.setName(n.getName());
                nv.setConditionExpr(n.getConditionExpr());
                nv.setConditionDesc(n.getConditionDesc());
                nv.setApprovers(n.getApprovers());
                nv.setApproveMode(n.getApproveMode());
                nv.setAllowSelf(n.getAllowSelf());
                nv.setSkipSame(n.getSkipSame());
                nv.setOnEmpty(n.getOnEmpty());
                nv.setTimeoutHours(n.getTimeoutHours());
                nv.setIsFallback(n.getIsFallback() == null ? 0 : n.getIsFallback());
                nv.setChildChain(n.getChildChain() == null ? 0 : n.getChildChain());
                sv.getNodes().add(nv);
            }
            sv.setIsGateway(isGatewayVO(sv.getNodes()));
            vo.getSteps().add(sv);
        }
        ApprovalPage page = pageMapper.selectOne(Wrappers.<ApprovalPage>lambdaQuery()
                .eq(ApprovalPage::getTemplateId, id)
                .eq(ApprovalPage::getView, "APPROVAL")
                .last("limit 1"));
        if (page != null) {
            TemplateDetailVO.PageVO pv = new TemplateDetailVO.PageVO();
            pv.setView(page.getView());
            pv.setRenderMode(page.getRenderMode());
            pv.setConfig(page.getConfig());
            pv.setRoutePath(page.getRoutePath());
            pv.setVersion(page.getVersion());
            vo.setPage(pv);
        }
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public TemplateDetailVO save(TemplateSaveRequest req) {
        List<Plan> plans = prepare(req);

        ApprovalTemplate tpl;
        if (req.getId() == null) {
            Long dup = templateMapper.selectCount(Wrappers.<ApprovalTemplate>lambdaQuery()
                    .eq(ApprovalTemplate::getBizType, req.getBizType()));
            if (dup != null && dup > 0) {
                throw new BizException(SettingsErrorCode.APPROVAL_TEMPLATE_DISABLED, "该业务场景已存在模板");
            }
            tpl = new ApprovalTemplate();
            tpl.setCode(isBlank(req.getCode()) ? buildCode(req.getBizType()) : req.getCode());
            tpl.setVersion(1);
            tpl.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
            fillTemplate(tpl, req);
            templateMapper.insert(tpl);
        } else {
            tpl = templateMapper.selectById(req.getId());
            if (tpl == null) {
                throw new BizException(SettingsErrorCode.APPROVAL_TEMPLATE_NOT_FOUND);
            }
            tpl.setVersion((tpl.getVersion() == null ? 1 : tpl.getVersion()) + 1);
            if (req.getEnabled() != null) {
                tpl.setEnabled(req.getEnabled());
            }
            fillTemplate(tpl, req);
            templateMapper.updateById(tpl);
        }

        // 整树替换：先物理清掉旧的步骤与分支
        stepMapper.purgeByTemplate(tpl.getId());
        nodeMapper.purgeByTemplate(tpl.getId());

        for (Plan p : plans) {
            TemplateSaveRequest.StepDto sd = p.dto;
            ApprovalStep s = new ApprovalStep();
            s.setTemplateId(tpl.getId());
            s.setSeq(p.seq);
            s.setName(empty(sd.getName()));
            s.setConditionExpr(empty(sd.getConditionExpr()));
            s.setConditionDesc(empty(sd.getConditionDesc()));
            // v5：驳回统一整单 END，不再落 PREV / NODE
            s.setRejectAction("END");
            s.setParentNodeId(p.parentNodeId);
            s.setBranchPath(p.branchPath);
            s.setDepth(p.depth);
            s.setUiKind(isBlank(sd.getUiKind()) ? "APPROVER" : sd.getUiKind());
            stepMapper.insert(s);

            for (TemplateSaveRequest.NodeDto nd : sd.getNodes()) {
                ApprovalNode n = new ApprovalNode();
                n.setId(p.nodeIds.get(nd.getClientId()));
                n.setTemplateId(tpl.getId());
                n.setStepId(s.getId());
                n.setSeq(nd.getSeq());
                n.setName(empty(nd.getName()));
                n.setConditionExpr(empty(nd.getConditionExpr()));
                n.setConditionDesc(empty(nd.getConditionDesc()));
                n.setApprovers(writeJson(nd.getApprovers()));
                n.setApproveMode(isBlank(nd.getApproveMode()) ? "ANY" : nd.getApproveMode());
                n.setAllowSelf(nd.getAllowSelf() == null ? 0 : nd.getAllowSelf());
                n.setSkipSame(nd.getSkipSame() == null ? 1 : nd.getSkipSame());
                n.setOnEmpty(isBlank(nd.getOnEmpty()) ? "SKIP" : nd.getOnEmpty());
                n.setTimeoutHours(nd.getTimeoutHours());
                n.setIsFallback(Integer.valueOf(1).equals(nd.getIsFallback()) ? 1 : 0);
                n.setChildChain(Integer.valueOf(1).equals(nd.getChildChain()) ? 1 : 0);
                nodeMapper.insert(n);
            }
        }

        if (req.getPage() != null) {
            savePage(tpl.getId(), req.getPage());
        }
        log.info("审批模板保存：{} v{}，{} 步（最深 {} 层）", tpl.getName(), tpl.getVersion(),
                plans.size(), plans.stream().mapToInt(p -> p.depth).max().orElse(0));
        return detail(tpl.getId());
    }

    /**
     * 校验 + 预分配 ID + 重算路径。前端提交顺序必须是 DFS 前序：
     * 父网关一定排在它派生的子链步骤之前，一遍扫描就能算出全部路径。
     */
    private List<Plan> prepare(TemplateSaveRequest req) {
        if (isBlank(req.getBizType()) || isBlank(req.getBizName())) {
            throw new BizException(SettingsErrorCode.APPROVAL_STEP_INVALID, "业务场景与名称不能为空");
        }
        List<TemplateSaveRequest.StepDto> steps = req.getSteps();
        if (steps == null || steps.isEmpty()) {
            throw new BizException(SettingsErrorCode.APPROVAL_STEP_INVALID, "至少需要配置 1 个审批步骤");
        }
        Map<String, Object> sample = new HashMap<>();

        // 阶段一：为每个分支节点预分配雪花 ID（前端本地 clientId -> 真实 ID）
        Map<String, Long> ids = new HashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            TemplateSaveRequest.StepDto sd = steps.get(i);
            if (sd.getNodes() == null) {
                sd.setNodes(new ArrayList<>());
            }
            for (int j = 0; j < sd.getNodes().size(); j++) {
                TemplateSaveRequest.NodeDto nd = sd.getNodes().get(j);
                String key = isBlank(nd.getClientId()) ? "auto_" + i + "_" + j : nd.getClientId();
                nd.setClientId(key);
                if (ids.putIfAbsent(key, IdWorker.getId()) != null) {
                    throw new BizException(SettingsErrorCode.APPROVAL_STEP_INVALID,
                            "分支本地 ID 重复：" + key);
                }
            }
        }

        // 阶段二：按 DFS 顺序算 seq / parentNodeId / branchPath / depth
        Map<String, Owner> owners = new HashMap<>();
        List<Plan> plans = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            TemplateSaveRequest.StepDto sd = steps.get(i);
            Plan p = new Plan();
            p.dto = sd;
            p.seq = i + 1;
            p.nodeIds = ids;

            String ref = sd.getParentNodeRef();
            if (!isBlank(ref)) {
                Owner owner = owners.get(ref);
                if (owner == null) {
                    throw new BizException(SettingsErrorCode.APPROVAL_STEP_INVALID,
                            "步骤「" + sd.getName() + "」引用的父分支不存在，或提交顺序不是 DFS 前序");
                }
                if (!owner.gateway) {
                    throw new BizException(SettingsErrorCode.APPROVAL_STEP_INVALID,
                            "步骤「" + sd.getName() + "」只能挂在条件网关的分支下");
                }
                Long realParent = ids.get(ref);
                p.parentNodeId = realParent;
                p.branchPath = ApprovalSnapshot.childPath(owner.path, realParent);
                p.depth = owner.depth + 1;
                if (p.depth > MAX_DEPTH) {
                    throw new BizException(SettingsErrorCode.APPROVAL_STEP_INVALID,
                            "条件分支嵌套最多 " + MAX_DEPTH + " 层，当前第 " + p.depth + " 层");
                }
            } else {
                p.parentNodeId = null;
                p.branchPath = "";
                p.depth = 0;
            }

            // 基础校验
            if (sd.getNodes().isEmpty()) {
                throw new BizException(SettingsErrorCode.APPROVAL_STEP_INVALID,
                        "步骤「" + sd.getName() + "」至少要有一个审批分支（空并行组不能导出）");
            }
            String err = conditionEvaluator.validate(sd.getConditionExpr(), sample);
            if (err != null) {
                throw new BizException(SettingsErrorCode.APPROVAL_CONDITION_INVALID,
                        "步骤「" + sd.getName() + "」条件不合法：" + err);
            }
            boolean gateway = isGatewayDto(sd.getNodes());
            int fallbacks = 0;
            for (int j = 0; j < sd.getNodes().size(); j++) {
                TemplateSaveRequest.NodeDto nd = sd.getNodes().get(j);
                nd.setSeq(j + 1);
                boolean fallback = Integer.valueOf(1).equals(nd.getIsFallback());
                if (fallback) {
                    fallbacks++;
                }
                if (nd.getApprovers() == null || nd.getApprovers().isEmpty()) {
                    throw new BizException(SettingsErrorCode.APPROVAL_STEP_INVALID,
                            "步骤「" + sd.getName() + "」有分支没有配置审批人");
                }
                String e2 = conditionEvaluator.validate(nd.getConditionExpr(), sample);
                if (e2 != null) {
                    throw new BizException(SettingsErrorCode.APPROVAL_CONDITION_INVALID,
                            "步骤「" + sd.getName() + "」分支条件不合法：" + e2);
                }
                if (gateway && !fallback && isBlank(nd.getConditionExpr())) {
                    throw new BizException(SettingsErrorCode.APPROVAL_CONDITION_INVALID,
                            "步骤「" + sd.getName() + "」的非兜底分支必须配置生效条件");
                }
            }
            if (gateway && fallbacks > 1) {
                throw new BizException(SettingsErrorCode.APPROVAL_STEP_INVALID,
                        "步骤「" + sd.getName() + "」最多只能有 1 个兜底分支");
            }

            for (TemplateSaveRequest.NodeDto nd : sd.getNodes()) {
                owners.put(nd.getClientId(), new Owner(p.seq, p.branchPath, p.depth, gateway));
            }
            plans.add(p);
        }
        return plans;
    }

    private boolean isGatewayDto(List<TemplateSaveRequest.NodeDto> nodes) {
        if (nodes.size() <= 1) {
            return false;
        }
        for (TemplateSaveRequest.NodeDto n : nodes) {
            if (Integer.valueOf(1).equals(n.getIsFallback()) || !isBlank(n.getConditionExpr())) {
                return true;
            }
        }
        return false;
    }

    private boolean isGatewayVO(List<TemplateDetailVO.NodeVO> nodes) {
        if (nodes.size() <= 1) {
            return false;
        }
        for (TemplateDetailVO.NodeVO n : nodes) {
            if (Integer.valueOf(1).equals(n.getIsFallback()) || !isBlank(n.getConditionExpr())) {
                return true;
            }
        }
        return false;
    }

    private void fillTemplate(ApprovalTemplate tpl, TemplateSaveRequest req) {
        tpl.setName(req.getName());
        tpl.setAppCode(isBlank(req.getAppCode()) ? guessApp(req.getBizType()) : req.getAppCode());
        tpl.setBizType(req.getBizType());
        tpl.setBizName(req.getBizName());
        tpl.setRemark(empty(req.getRemark()));
    }

    public void toggle(Long id, Integer enabled) {
        ApprovalTemplate tpl = templateMapper.selectById(id);
        if (tpl == null) {
            throw new BizException(SettingsErrorCode.APPROVAL_TEMPLATE_NOT_FOUND);
        }
        tpl.setEnabled(enabled == null ? 0 : enabled);
        templateMapper.updateById(tpl);
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        Long running = instanceMapper.selectCount(Wrappers.<ApprInstance>lambdaQuery()
                .eq(ApprInstance::getTemplateId, id)
                .eq(ApprInstance::getStatus, InstanceStatus.RUNNING.getCode()));
        if (running != null && running > 0) {
            throw new BizException(SettingsErrorCode.APPROVAL_TEMPLATE_IN_USE);
        }
        stepMapper.purgeByTemplate(id);
        nodeMapper.purgeByTemplate(id);
        pageMapper.delete(Wrappers.<ApprovalPage>lambdaQuery().eq(ApprovalPage::getTemplateId, id));
        templateMapper.deleteById(id);
    }

    // ================================================================ 页面

    public ApprovalPage getPage(Long templateId, String view) {
        return pageMapper.selectOne(Wrappers.<ApprovalPage>lambdaQuery()
                .eq(ApprovalPage::getTemplateId, templateId)
                .eq(ApprovalPage::getView, isBlank(view) ? "APPROVAL" : view)
                .last("limit 1"));
    }

    /** 按业务场景取页面配置（业务侧渲染审批弹窗用） */
    public ApprovalPage getPageByBizType(String bizType, String view) {
        ApprovalTemplate tpl = templateMapper.selectOne(Wrappers.<ApprovalTemplate>lambdaQuery()
                .eq(ApprovalTemplate::getBizType, bizType)
                .orderByDesc(ApprovalTemplate::getEnabled)
                .last("limit 1"));
        if (tpl == null) {
            return null;
        }
        return getPage(tpl.getId(), view);
    }

    @Transactional(rollbackFor = Exception.class)
    public ApprovalPage savePage(Long templateId, TemplateSaveRequest.PageDto dto) {
        String view = isBlank(dto.getView()) ? "APPROVAL" : dto.getView();
        ApprovalPage page = pageMapper.selectOne(Wrappers.<ApprovalPage>lambdaQuery()
                .eq(ApprovalPage::getTemplateId, templateId)
                .eq(ApprovalPage::getView, view)
                .last("limit 1"));
        if (page == null) {
            page = new ApprovalPage();
            page.setTemplateId(templateId);
            page.setView(view);
            page.setVersion(1);
        } else {
            page.setVersion((page.getVersion() == null ? 1 : page.getVersion()) + 1);
        }
        page.setRenderMode(isBlank(dto.getRenderMode()) ? "SECTIONS" : dto.getRenderMode());
        page.setConfig(dto.getConfig() == null ? "{}" : dto.getConfig());
        page.setRoutePath(empty(dto.getRoutePath()));
        if (page.getId() == null) {
            pageMapper.insert(page);
        } else {
            pageMapper.updateById(page);
        }
        return page;
    }

    // ================================================================ 场景

    public List<BizScene> scenes() {
        return sceneMapper.selectList(Wrappers.<BizScene>lambdaQuery()
                .orderByAsc(BizScene::getSort)
                .orderByAsc(BizScene::getBizType));
    }

    /**
     * 业务方启动时注册场景（幂等 upsert），字段字典是配置器的数据来源。
     * <p>
     * 业务点没有独立表，就藏在 formSchema 里，前端保存是<b>整列覆盖</b>。
     * 所以这里不只是「写字典」，还是删除业务点的唯一入口：必须拿新旧编码差集
     * 做一次引用反查，被引用的编码一律拒绝删除。前端把按钮置灰只是提示，
     * 真正兜底在这一层——绕过界面直接 POST /scenes/register 也删不掉。
     */
    @Transactional(rollbackFor = Exception.class)
    public BizScene registerScene(BizScene scene) {
        if (isBlank(scene.getAppCode()) || isBlank(scene.getBizType())) {
            throw new BizException(SettingsErrorCode.APPROVAL_SCENE_NOT_FOUND, "appCode / bizType 不能为空");
        }
        BizScene exist = sceneMapper.selectOne(Wrappers.<BizScene>lambdaQuery()
                .eq(BizScene::getBizType, scene.getBizType())
                .last("limit 1"));
        if (exist == null) {
            scene.setSource(isBlank(scene.getSource()) ? "REGISTER" : scene.getSource());
            scene.setSort(scene.getSort() == null ? 0 : scene.getSort());
            sceneMapper.insert(scene);
            return scene;
        }

        // formSchema 传 null 视为「不修改这份字典」（注册方可能只想更新场景名）；
        // 传 "" 或 "[]" 则是明确清空，仍要走删除保护。
        if (scene.getFormSchema() != null) {
            Set<String> removed = new LinkedHashSet<>(bizPointReferences.parsePointCodes(exist.getFormSchema()));
            removed.removeAll(bizPointReferences.parsePointCodes(scene.getFormSchema()));
            if (!removed.isEmpty()) {
                Map<String, List<String>> refs = bizPointReferences.of(scene.getBizType());
                for (String code : removed) {
                    List<String> hits = refs.get(BizPointReferenceService.key(scene.getBizType(), code));
                    if (hits == null || hits.isEmpty()) {
                        continue;
                    }
                    String detail = String.join("；", hits.size() > 3 ? hits.subList(0, 3) : hits);
                    throw new BizException(SettingsErrorCode.APPROVAL_BIZ_POINT_IN_USE,
                            "业务点「" + code + "」已被引用，不能删除：" + detail
                                    + (hits.size() > 3 ? " 等共 " + hits.size() + " 处" : "")
                                    + "。请先修改对应流程配置再删除");
                }
            }
            exist.setFormSchema(scene.getFormSchema());
        }
        exist.setBizName(scene.getBizName());
        if (scene.getFieldSchema() != null) {
            exist.setFieldSchema(scene.getFieldSchema());
        }
        // tableBindings 与 formSchema/fieldSchema 同款约定：传 null 视为「不改动」，由管理端单独接口设置，
        // 业务方注册场景时不会被误覆盖。
        if (scene.getTableBindings() != null) {
            exist.setTableBindings(scene.getTableBindings());
        }
        exist.setPermHint(empty(scene.getPermHint()));
        // 换应用 = 换库：原绑定的表名只在旧库里才有意义，留着只会渲染成「取不到」。
        // 直接清空，让管理员按新库重选；在途单的绑定已冻结在实例上，不受影响。
        if (!java.util.Objects.equals(exist.getAppCode(), scene.getAppCode())) {
            exist.setTableBindings(null);
        }
        exist.setAppCode(scene.getAppCode());
        sceneMapper.updateById(exist);
        return exist;
    }

    /**
     * 设置场景的数据表绑定（审批页面「与表绑定 / 真绑定」）。仅由管理端调用——
     * 业务方 registerScene 走 null 不覆盖约定，不会碰这一列。
     * <p>
     * 校验：整个场景最多一个主体表（main=true）；子表必须指定 fk 外键列（如 employee_id）。
     * 存的是 {@link SceneTableBindingsDto} 序列化后的 JSON 字符串，渲染期由 ApprovalContextService 解析。
     */
    @Transactional(rollbackFor = Exception.class)
    public BizScene saveTableBindings(String bizType, SceneTableBindingsDto dto) {
        BizScene exist = sceneMapper.selectOne(Wrappers.<BizScene>lambdaQuery()
                .eq(BizScene::getBizType, bizType).last("limit 1"));
        if (exist == null) {
            throw new BizException(SettingsErrorCode.APPROVAL_SCENE_NOT_FOUND, "场景不存在：" + bizType);
        }
        boolean hasMain = false;
        for (SceneTableBindingsDto.BoundTable t : dto.getTables()) {
            String mode = t.effectiveMode();
            // 显式声明定位模式并落库：旧配置（无 mode）也在这里补成新结构
            if (!mode.equals(t.getMode())) {
                t.setMode(mode);
            }
            if (SceneTableBindingsDto.MODE_MAIN_ROW.equals(mode)) {
                if (hasMain) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "只能有一个主体表（MAIN_ROW）");
                }
                hasMain = true;
            } else if (SceneTableBindingsDto.MODE_FK_LIST.equals(mode)
                    && (t.getFk() == null || t.getFk().isBlank())) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "档案子表「" + t.getTable() + "」的 FK_LIST 必须指定 fk 外键列");
            }
            // main 是旧字段，按 mode 同步，避免以后又出现两套口径
            t.setMain(SceneTableBindingsDto.MODE_MAIN_ROW.equals(mode));
        }
        // 场景级主体元数据：配置器传了就更新，没传保留库里的值
        if (dto.getSubjectField() != null && !dto.getSubjectField().isBlank()) {
            String f = dto.getSubjectField().trim();
            if (!f.matches("^[A-Za-z_][A-Za-z0-9_]*$")) {
                throw new BizException(ErrorCode.PARAM_ERROR, "主体键只能是标识符：" + f);
            }
            exist.setSubjectField(f);
        }
        if (dto.getBizIdKind() != null && !dto.getBizIdKind().isBlank()) {
            String k = dto.getBizIdKind().trim().toUpperCase();
            if (!"SUBJECT".equals(k) && !"DOC".equals(k)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "bizIdKind 只能是 SUBJECT 或 DOC");
            }
            exist.setBizIdKind(k);
        }
        // 所属应用以场景为准：表名只在各自库里唯一，让前端传 appCode 就有「把 ehr 的表绑到 cms 库」
        // 的可能。这里覆盖而不是报错——前端选的是场景应用，本来就该一致。
        dto.setAppCode(exist.getAppCode());
        try {
            exist.setTableBindings(objectMapper.writeValueAsString(dto));
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "绑定 JSON 序列化失败：" + e.getMessage());
        }
        sceneMapper.updateById(exist);
        return exist;
    }

    // ================================================================ 工具

    /** 保存计划：一个 StepDto 对应的权威落库值 */
    private static class Plan {
        private TemplateSaveRequest.StepDto dto;
        private int seq;
        private Long parentNodeId;
        private String branchPath;
        private int depth;
        private Map<String, Long> nodeIds;
    }

    /** 某个分支节点所属步骤的信息，供子步骤解析父路径 */
    private static class Owner {
        private final int seq;
        private final String path;
        private final int depth;
        private final boolean gateway;

        private Owner(int seq, String path, int depth, boolean gateway) {
            this.seq = seq;
            this.path = path;
            this.depth = depth;
            this.gateway = gateway;
        }
    }

    private TemplateDetailVO baseVO(ApprovalTemplate t) {
        TemplateDetailVO vo = new TemplateDetailVO();
        vo.setId(t.getId());
        vo.setCode(t.getCode());
        vo.setName(t.getName());
        vo.setAppCode(t.getAppCode());
        vo.setBizType(t.getBizType());
        vo.setBizName(t.getBizName());
        vo.setEnabled(t.getEnabled());
        vo.setVersion(t.getVersion());
        vo.setRemark(t.getRemark());
        return vo;
    }

    private String buildCode(String bizType) {
        return bizType.replace('.', '_') + "_default";
    }

    private String guessApp(String bizType) {
        int i = bizType == null ? -1 : bizType.indexOf('.');
        return i > 0 ? bizType.substring(0, i) : "";
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o == null ? List.of() : o);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String empty(String s) {
        return s == null ? "" : s;
    }
}
