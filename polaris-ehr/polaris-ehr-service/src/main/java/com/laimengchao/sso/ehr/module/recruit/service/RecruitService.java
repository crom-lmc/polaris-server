package com.laimengchao.sso.ehr.module.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.approval.support.ApprovalLauncher;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.employee.service.EmployeeService;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.ehr.module.recruit.dto.CandidateRequest;
import com.laimengchao.sso.ehr.module.recruit.dto.OfferRequest;
import com.laimengchao.sso.ehr.module.recruit.entity.Candidate;
import com.laimengchao.sso.ehr.module.recruit.entity.Offer;
import com.laimengchao.sso.ehr.module.recruit.mapper.CandidateMapper;
import com.laimengchao.sso.ehr.module.recruit.mapper.OfferMapper;
import com.laimengchao.sso.ehr.module.recruit.vo.CandidateVO;
import com.laimengchao.sso.ehr.module.recruit.vo.OfferVO;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.settings.client.api.ApprovalApi;
import com.laimengchao.sso.settings.client.dto.ApprovalActionDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 招聘管道：候选人分阶段看板 + 录用 Offer 审批。
 * <p>
 * Offer 提交后走审批中心（薪资不超档位：用人部门 → HR；超档位加总经理终审），
 * 终审通过自动生成一条「待入职」员工草稿，驳回/撤销则候选人退回面试中。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitService {

    public static final String STAGE_RESUME = "RESUME";
    public static final String STAGE_INTERVIEW = "INTERVIEW";
    public static final String STAGE_OFFER = "OFFER";
    public static final String STAGE_REJECTED = "REJECTED";

    public static final String STATUS_APPROVING = "APPROVING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    /** 录用审批场景编码，对应 settings 的 cfg_biz_scene.biz_type */
    public static final String BIZ_OFFER = "ehr.recruit.offer";
    public static final String BIZ_OFFER_SENIOR = "ehr.recruit.offer.senior";

    private static final String CALLBACK_PATH = "/internal/ehr/approval-callback";
    private static final Set<String> STAGES = Set.of(STAGE_RESUME, STAGE_INTERVIEW, STAGE_OFFER, STAGE_REJECTED);

    private final CandidateMapper candidateMapper;
    private final OfferMapper offerMapper;
    private final EmployeeMapper employeeMapper;
    private final EmployeeService employeeService;
    private final OrgNodeMapper orgMapper;
    private final ApprovalLauncher approvalLauncher;
    private final ApprovalApi approvalApi;

    @Value("${ehr.self-base-url:http://127.0.0.1:9006}")
    private String selfBaseUrl;

    /** 薪资档位线：月薪超过该值需要总经理终审 */
    @Value("${ehr.recruit.salary-threshold:20000}")
    private BigDecimal salaryThreshold;

    /* ---------------- 候选人 ---------------- */

    public R.Page<CandidateVO> pageCandidates(String stage, String keyword, int page, int size) {
        LambdaQueryWrapper<Candidate> w = new LambdaQueryWrapper<>();
        if (stage != null && !stage.isBlank()) {
            w.eq(Candidate::getStage, stage);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            w.and(q -> q.like(Candidate::getName, kw)
                    .or().like(Candidate::getMobile, kw)
                    .or().like(Candidate::getPosition, kw));
        }
        w.orderByDesc(Candidate::getCreatedAt);
        IPage<Candidate> p = candidateMapper.selectPage(new Page<>(page, size), w);
        if (p.getRecords().isEmpty()) {
            return R.Page.of(List.of(), 0);
        }
        List<Long> ids = p.getRecords().stream().map(Candidate::getId).toList();
        Map<Long, Offer> offerMap = activeOffers(ids);
        Map<Long, OrgNode> orgMap = orgMap();
        List<CandidateVO> list = p.getRecords().stream()
                .map(c -> toCandidateVO(c, offerMap.get(c.getId()), orgMap))
                .toList();
        return R.Page.of(list, p.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public CandidateVO createCandidate(CandidateRequest req) {
        SecurityUser user = currentUser();
        Candidate c = new Candidate();
        applyCandidate(c, req, STAGE_RESUME);
        c.setOwnerId(user.getAccountId());
        c.setOwnerName(nvl(user.getUsername()));
        candidateMapper.insert(c);
        return toCandidateVO(c, null, orgMap());
    }

    @Transactional(rollbackFor = Exception.class)
    public CandidateVO updateCandidate(Long id, CandidateRequest req) {
        Candidate c = requireCandidate(id);
        String targetStage = normalizedStage(req.getStage(), c.getStage());
        if (STAGE_REJECTED.equals(targetStage) && hasActiveOffer(id)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该候选人已有审批中/已通过的 Offer，不能淘汰");
        }
        applyCandidate(c, req, targetStage);
        candidateMapper.updateById(c);
        return toCandidateVO(c, activeOffer(id), orgMap());
    }

    /** 淘汰：有审批中/已通过的 Offer 时不允许 */
    @Transactional(rollbackFor = Exception.class)
    public void eliminate(Long id, String remark) {
        Candidate c = requireCandidate(id);
        if (hasActiveOffer(id)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该候选人已有审批中/已通过的 Offer，请先处理 Offer");
        }
        c.setStage(STAGE_REJECTED);
        if (remark != null && !remark.isBlank()) {
            c.setRemark(remark.trim());
        }
        candidateMapper.updateById(c);
    }

    /** 恢复：已淘汰 → 面试中 */
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id) {
        Candidate c = requireCandidate(id);
        if (!STAGE_REJECTED.equals(c.getStage())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "只有已淘汰的候选人可以恢复");
        }
        c.setStage(STAGE_INTERVIEW);
        candidateMapper.updateById(c);
    }

    /* ---------------- Offer ---------------- */

    public R.Page<OfferVO> pageOffers(String status, String keyword, int page, int size) {
        LambdaQueryWrapper<Offer> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            w.eq(Offer::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            List<Long> candidateIds = candidateMapper.selectList(new LambdaQueryWrapper<Candidate>()
                            .like(Candidate::getName, kw).or().like(Candidate::getMobile, kw))
                    .stream().map(Candidate::getId).toList();
            if (candidateIds.isEmpty()) {
                return R.Page.of(List.of(), 0);
            }
            w.in(Offer::getCandidateId, candidateIds);
        }
        w.orderByDesc(Offer::getCreatedAt);
        IPage<Offer> p = offerMapper.selectPage(new Page<>(page, size), w);
        if (p.getRecords().isEmpty()) {
            return R.Page.of(List.of(), 0);
        }
        Map<Long, Candidate> candidates = candidateMapper.selectBatchIds(
                        p.getRecords().stream().map(Offer::getCandidateId).distinct().toList())
                .stream().collect(Collectors.toMap(Candidate::getId, Function.identity()));
        List<Long> empIds = p.getRecords().stream().map(Offer::getEmployeeId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Employee> employees = empIds.isEmpty() ? Map.of()
                : employeeMapper.selectBatchIds(empIds).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        Map<Long, OrgNode> orgMap = orgMap();
        List<OfferVO> list = p.getRecords().stream()
                .map(o -> toOfferVO(o, candidates.get(o.getCandidateId()), employees.get(o.getEmployeeId()), orgMap))
                .toList();
        return R.Page.of(list, p.getTotal());
    }

    /**
     * 发起录用审批：本地先落 APPROVING 并提交，再远程发起（三段式避免同步回调读到未提交数据）。
     * 远程发起失败且审批中心确认无在途单时，回滚本地 Offer 并还原候选人阶段。
     */
    public OfferVO createOffer(OfferRequest req) {
        SecurityUser user = currentUser();
        Candidate c = requireCandidate(req.getCandidateId());
        if (STAGE_REJECTED.equals(c.getStage())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "已淘汰的候选人不能发起 Offer");
        }
        if (hasActiveOffer(c.getId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该候选人已有审批中/已通过的 Offer");
        }
        LocalDate onboardDate = parseDate(req.getExpectedOnboardDate());
        OrgNode org = orgMapper.selectById(req.getOrgId());
        if (org == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "录用部门不存在");
        }

        Offer o = new Offer();
        o.setCandidateId(c.getId());
        o.setPosition(req.getPosition().trim());
        o.setSalary(req.getSalary());
        o.setExpectedOnboardDate(onboardDate);
        o.setOrgId(req.getOrgId());
        o.setStatus(STATUS_APPROVING);
        o.setRemark(nvl(req.getRemark()));

        String previousStage = c.getStage();
        approvalLauncher.inTx(() -> {
            offerMapper.insert(o);
            c.setStage(STAGE_OFFER);
            candidateMapper.updateById(c);
        });

        approvalLauncher.launch(() -> buildOfferStart(o, c, user), inst -> { }, () -> compensate(o.getId(), previousStage));
        return toOfferVO(o, c, null, orgMap());
    }

    /** 撤销审批中的 Offer：先撤审批中心的在途单，再回滚本地状态 */
    @Transactional(rollbackFor = Exception.class)
    public void cancelOffer(Long id) {
        Offer o = requireOffer(id);
        if (!STATUS_APPROVING.equals(o.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "只有审批中的 Offer 可以撤销");
        }
        Long instId = approvalLauncher.latestRunningId(BIZ_OFFER, String.valueOf(id));
        if (instId == null) {
            instId = approvalLauncher.latestRunningId(BIZ_OFFER_SENIOR, String.valueOf(id));
        }
        SecurityUser user = currentUser();
        if (instId != null) {
            ApprovalActionDTO action = new ApprovalActionDTO();
            action.setOperatorId(user.getAccountId());
            action.setOperatorName(user.getUsername());
            action.setNote("撤销录用 Offer");
            try {
                com.laimengchao.sso.settings.client.dto.R<com.laimengchao.sso.settings.client.dto.ApprovalInstanceDTO> resp =
                        approvalApi.withdraw(instId, action);
                if (resp == null || !resp.ok()) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "撤回审批失败，请稍后重试");
                }
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                throw new BizException(ErrorCode.PARAM_ERROR, "撤回审批失败：" + e.getMessage());
            }
        }
        o.setStatus(STATUS_REJECTED);
        o.setRemark("发起人撤销");
        offerMapper.updateById(o);
        revertCandidate(o.getCandidateId(), STAGE_INTERVIEW);
    }

    /** 手动转建档：审批已通过但自动建档失败（如部门被删）时的补偿入口，幂等 */
    @Transactional(rollbackFor = Exception.class)
    public OfferVO convertToEmployee(Long id) {
        Offer o = requireOffer(id);
        if (!STATUS_APPROVED.equals(o.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "只有审批通过的 Offer 才能转建档");
        }
        if (o.getEmployeeId() != null) {
            Candidate c = candidateMapper.selectById(o.getCandidateId());
            Employee emp = employeeMapper.selectById(o.getEmployeeId());
            return toOfferVO(o, c, emp, orgMap());
        }
        Long employeeId = createArchive(o);
        o.setEmployeeId(employeeId);
        offerMapper.updateById(o);
        Candidate c = candidateMapper.selectById(o.getCandidateId());
        return toOfferVO(o, c, employeeMapper.selectById(employeeId), orgMap());
    }

    /**
     * 录用审批终审回调。幂等：同一结果可能重复投递，只有 APPROVING → 终态的那一次生效。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleOfferApprovalResult(ApprovalCallbackRequest req) {
        Long offerId = parseLongOrNull(req.getBizId());
        if (offerId == null) {
            log.warn("录用审批回调缺少业务ID：{}", req.getBizId());
            return;
        }
        Offer o = offerMapper.selectById(offerId);
        if (o == null) {
            log.warn("录用审批回调的 Offer 不存在：{}", offerId);
            return;
        }
        boolean approved = Boolean.TRUE.equals(req.getApproved())
                || Integer.valueOf(1).equals(req.getResult());
        String finalStatus = approved ? STATUS_APPROVED : STATUS_REJECTED;

        // 条件更新抢终态：重复回调 / 已撤销的单据在这里被吃掉
        int updated = offerMapper.update(null, new LambdaUpdateWrapper<Offer>()
                .eq(Offer::getId, offerId)
                .eq(Offer::getStatus, STATUS_APPROVING)
                .set(Offer::getStatus, finalStatus));
        if (updated == 0) {
            log.info("录用审批回调重复或非在途单据，忽略：offer={} target={}", offerId, finalStatus);
            return;
        }
        o.setStatus(finalStatus);

        if (approved) {
            if (o.getEmployeeId() == null) {
                try {
                    Long employeeId = createArchive(o);
                    o.setEmployeeId(employeeId);
                    offerMapper.updateById(o);
                } catch (Exception e) {
                    // 建档失败不回滚审批结果，列表里可用「转建档」重试
                    log.error("录用审批通过后自动建档失败 offer={}", offerId, e);
                }
            }
            Candidate c = candidateMapper.selectById(o.getCandidateId());
            if (c != null) {
                c.setStage(STAGE_OFFER);
                candidateMapper.updateById(c);
            }
        } else {
            revertCandidate(o.getCandidateId(), STAGE_INTERVIEW);
        }
    }

    /* ---------------- 内部 ---------------- */

    private Long createArchive(Offer o) {
        Candidate c = candidateMapper.selectById(o.getCandidateId());
        if (c == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "候选人不存在，无法建档");
        }
        return employeeService.createPendingArchive(c.getName(), c.getMobile(), c.getEmail(),
                o.getOrgId(), o.getPosition(), o.getExpectedOnboardDate(),
                "招聘录用转入：" + nvl(o.getPosition()));
    }

    private ApprovalStartDTO buildOfferStart(Offer o, Candidate c, SecurityUser user) {
        String bizType = o.getSalary() != null && salaryThreshold != null
                && o.getSalary().compareTo(salaryThreshold) > 0 ? BIZ_OFFER_SENIOR : BIZ_OFFER;
        ApprovalStartDTO dto = new ApprovalStartDTO();
        dto.setBizType(bizType);
        dto.setBizId(String.valueOf(o.getId()));
        dto.setBizNo(String.valueOf(o.getId()));
        // 本单据的 bizId 就是 Offer ID；主体填 Offer ID，避免审批中心按 orgId 误当主体
        dto.setSubjectId(String.valueOf(o.getId()));
        dto.setBizIdKind("DOC");
        dto.setTitle("录用审批 · " + nvl(c.getName()) + " · " + nvl(o.getPosition()));
        dto.setApplicantId(user.getAccountId());
        dto.setApplicantName(user.getUsername());
        dto.setApplicantOrgId(o.getOrgId());
        dto.setOrgPath(orgPathOf(o.getOrgId()));
        dto.setCallbackUrl(selfBaseUrl + CALLBACK_PATH);

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("offerId", String.valueOf(o.getId()));
        form.put("candidateId", String.valueOf(c.getId()));
        form.put("name", nvl(c.getName()));
        form.put("mobile", nvl(c.getMobile()));
        form.put("position", nvl(o.getPosition()));
        form.put("salary", o.getSalary());
        form.put("expectedOnboardDate", o.getExpectedOnboardDate() == null ? null : o.getExpectedOnboardDate().toString());
        form.put("orgId", String.valueOf(o.getOrgId()));
        form.put("orgPath", orgPathOf(o.getOrgId()));
        form.put("remark", nvl(o.getRemark()));
        form.put("submitter", user.getUsername());
        dto.setFormData(form);
        return dto;
    }

    /** 远程发起被确认失败后的补偿：撤销本地 Offer，候选人回退到提交前阶段 */
    private void compensate(Long offerId, String previousStage) {
        offerMapper.update(null, new LambdaUpdateWrapper<Offer>()
                .eq(Offer::getId, offerId)
                .eq(Offer::getStatus, STATUS_APPROVING)
                .set(Offer::getStatus, STATUS_REJECTED)
                .set(Offer::getRemark, "发起审批失败，系统自动撤销"));
        Offer o = offerMapper.selectById(offerId);
        if (o != null) {
            revertCandidate(o.getCandidateId(), previousStage);
        }
    }

    private void revertCandidate(Long candidateId, String stage) {
        if (candidateId == null) {
            return;
        }
        String target = STAGES.contains(stage) ? stage : STAGE_INTERVIEW;
        candidateMapper.update(null, new LambdaUpdateWrapper<Candidate>()
                .eq(Candidate::getId, candidateId)
                .set(Candidate::getStage, target));
    }

    private void applyCandidate(Candidate c, CandidateRequest req, String stage) {
        c.setName(req.getName().trim());
        c.setMobile(nvl(req.getMobile()));
        c.setEmail(nvl(req.getEmail()));
        c.setSource(nvl(req.getSource()).isBlank() ? "OTHER" : req.getSource().trim());
        c.setPosition(req.getPosition().trim());
        c.setOrgId(req.getOrgId());
        c.setStage(stage);
        c.setRemark(nvl(req.getRemark()));
    }

    private String normalizedStage(String stage, String fallback) {
        if (stage == null || stage.isBlank()) {
            return fallback;
        }
        String s = stage.trim().toUpperCase();
        if (!STAGES.contains(s)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "未知的候选人阶段：" + stage);
        }
        return s;
    }

    private boolean hasActiveOffer(Long candidateId) {
        Long cnt = offerMapper.selectCount(new LambdaQueryWrapper<Offer>()
                .eq(Offer::getCandidateId, candidateId)
                .in(Offer::getStatus, STATUS_APPROVING, STATUS_APPROVED));
        return cnt != null && cnt > 0;
    }

    private Offer activeOffer(Long candidateId) {
        List<Offer> list = offerMapper.selectList(new LambdaQueryWrapper<Offer>()
                .eq(Offer::getCandidateId, candidateId)
                .in(Offer::getStatus, STATUS_APPROVING, STATUS_APPROVED)
                .orderByDesc(Offer::getCreatedAt)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private Map<Long, Offer> activeOffers(List<Long> candidateIds) {
        if (candidateIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Offer> map = new HashMap<>();
        for (Offer o : offerMapper.selectList(new LambdaQueryWrapper<Offer>()
                .in(Offer::getCandidateId, candidateIds)
                .in(Offer::getStatus, STATUS_APPROVING, STATUS_APPROVED)
                .orderByDesc(Offer::getCreatedAt))) {
            map.putIfAbsent(o.getCandidateId(), o);
        }
        return map;
    }

    private Candidate requireCandidate(Long id) {
        Candidate c = candidateMapper.selectById(id);
        if (c == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "候选人不存在");
        }
        return c;
    }

    private Offer requireOffer(Long id) {
        Offer o = offerMapper.selectById(id);
        if (o == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Offer 不存在");
        }
        return o;
    }

    private CandidateVO toCandidateVO(Candidate c, Offer offer, Map<Long, OrgNode> orgMap) {
        CandidateVO vo = new CandidateVO();
        vo.setId(String.valueOf(c.getId()));
        vo.setName(nvl(c.getName()));
        vo.setMobile(nvl(c.getMobile()));
        vo.setEmail(nvl(c.getEmail()));
        vo.setSource(nvl(c.getSource()));
        vo.setPosition(nvl(c.getPosition()));
        vo.setOrgId(c.getOrgId() == null ? null : String.valueOf(c.getOrgId()));
        vo.setOrgPath(orgPathOf(c.getOrgId(), orgMap));
        vo.setStage(nvl(c.getStage()));
        vo.setOwnerId(c.getOwnerId() == null ? null : String.valueOf(c.getOwnerId()));
        vo.setOwnerName(nvl(c.getOwnerName()));
        vo.setRemark(nvl(c.getRemark()));
        vo.setHasActiveOffer(offer != null);
        vo.setOfferId(offer == null ? null : String.valueOf(offer.getId()));
        vo.setOfferStatus(offer == null ? null : offer.getStatus());
        vo.setCreatedAt(c.getCreatedAt() == null ? null : c.getCreatedAt().toString());
        return vo;
    }

    private OfferVO toOfferVO(Offer o, Candidate c, Employee emp, Map<Long, OrgNode> orgMap) {
        OfferVO vo = new OfferVO();
        vo.setId(String.valueOf(o.getId()));
        vo.setCandidateId(String.valueOf(o.getCandidateId()));
        vo.setCandidateName(c == null ? "" : nvl(c.getName()));
        vo.setCandidateMobile(c == null ? "" : nvl(c.getMobile()));
        vo.setPosition(nvl(o.getPosition()));
        vo.setSalary(o.getSalary() == null ? "" : o.getSalary().toPlainString());
        vo.setExpectedOnboardDate(o.getExpectedOnboardDate() == null ? null : o.getExpectedOnboardDate().toString());
        vo.setOrgId(o.getOrgId() == null ? null : String.valueOf(o.getOrgId()));
        vo.setOrgPath(orgPathOf(o.getOrgId(), orgMap));
        vo.setStatus(nvl(o.getStatus()));
        vo.setRemark(nvl(o.getRemark()));
        vo.setEmployeeId(o.getEmployeeId() == null ? null : String.valueOf(o.getEmployeeId()));
        vo.setEmployeeName(emp == null ? null : nvl(emp.getName()));
        vo.setEmployeeStatus(emp == null || emp.getStatus() == null ? null : String.valueOf(emp.getStatus()));
        vo.setCreatedAt(o.getCreatedAt() == null ? null : o.getCreatedAt().toString());
        return vo;
    }

    private Map<Long, OrgNode> orgMap() {
        Map<Long, OrgNode> map = new HashMap<>();
        for (OrgNode n : orgMapper.selectList(new LambdaQueryWrapper<OrgNode>())) {
            map.put(n.getId(), n);
        }
        return map;
    }

    private String orgPathOf(Long orgId) {
        return orgPathOf(orgId, orgMap());
    }

    /** 组织全路径，如「集团 / 子公司 / 研发部」；节点不存在返回空串 */
    private String orgPathOf(Long orgId, Map<Long, OrgNode> orgs) {
        if (orgId == null) {
            return "";
        }
        OrgNode node = orgs.get(orgId);
        if (node == null) {
            return "";
        }
        List<String> names = new ArrayList<>();
        if (node.getPath() != null) {
            for (String part : node.getPath().split("/")) {
                if (part.isBlank()) {
                    continue;
                }
                try {
                    OrgNode p = orgs.get(Long.valueOf(part));
                    if (p != null) {
                        names.add(nvl(p.getName()));
                    }
                } catch (NumberFormatException ignored) {
                    // 脏 path 跳过
                }
            }
        }
        if (names.isEmpty()) {
            names.add(nvl(node.getName()));
        }
        return String.join(" / ", names);
    }

    private SecurityUser currentUser() {
        return SecurityUtils.currentUser().orElseThrow(() ->
                new BizException(ErrorCode.UNAUTHORIZED));
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "日期格式应为 yyyy-MM-dd");
        }
    }

    private static Long parseLongOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
