package com.laimengchao.sso.ehr.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.module.approval.support.ApprovalLauncher;
import com.laimengchao.sso.ehr.module.approval.dto.HandoverConfirmRequest;
import com.laimengchao.sso.ehr.module.approval.dto.ResignApplyRequest;
import com.laimengchao.sso.ehr.module.approval.entity.ResignApply;
import com.laimengchao.sso.ehr.module.approval.mapper.ResignApplyMapper;
import com.laimengchao.sso.ehr.module.approval.vo.ResignApplyVO;
import com.laimengchao.sso.ehr.module.employee.constant.CloseAccountNotice;
import com.laimengchao.sso.ehr.module.employee.constant.EmployeeStatus;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.settings.client.api.ApprovalApi;
import com.laimengchao.sso.settings.client.api.CodeRuleApi;
import com.laimengchao.sso.settings.client.api.NoticeApi;
import com.laimengchao.sso.settings.client.dto.ApprovalActionDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalInstanceDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import com.laimengchao.sso.settings.client.dto.NoticeSendDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 离职 / 减员申请服务。
 * <p>
 * <b>审批已统一走审批中心</b>：交接仍由本服务逐项确认（审批中心的通用审批弹窗不支持
 * 逐项勾选），但「部门负责人 → 集团人力资源中心」两级审批改为 settings 的
 * {@code ehr.resign} 实例，谁审、几步、条件全部由模板决定。
 * <p>
 * 完整链路：提交 →（交接人逐项确认交接）→ 发起审批中心实例 → 审批中心终审回调 → 生效。
 * <b>交接没全部确认前不发起审批</b>，从源头保证「交接未完成不准通过」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResignApplyService {

    public static final String SOURCE_SELF = "SELF";
    public static final String SOURCE_HR = "HR";

    /** 离职审批场景编码，对应 settings 的 cfg_biz_scene.biz_type（员工自助申请走这条） */
    private static final String BIZ_RESIGN = "ehr.resign";
    /**
     * 减员审批场景编码。HR 发起的减员与员工自助离职<b>拆成两条流程</b>：
     * 减员是公司行为，审批链和材料要求都不同，不能被员工离职那套模板套住。
     * 两者共用本服务的申请单与交接逻辑，只有 bizType 不同。
     */
    private static final String BIZ_REDUCE = "ehr.reduce";
    /** 终审结果接收地址（settings 直连 EHR，不经网关） */
    private static final String CALLBACK_PATH = "/internal/ehr/approval-callback";
    /** 单号规则对应的业务表名：配置中心「单号规则」按表名识别 */
    private static final String TABLE_RESIGN = "ehr_resign_apply";

    /** 待人事中心审批（老链路中间态，保留兼容历史数据；新流程不再产生） */
    public static final int STATUS_PENDING_HR = 4;

    private static final Map<Integer, String> STATUS_NAMES = Map.of(
            0, "待交接",
            1, "已通过",
            2, "已驳回",
            3, "已撤销",
            STATUS_PENDING_HR, "待人事中心审批");

    private static final Map<Integer, String> HANDOVER_STATUS_NAMES = Map.of(
            0, "待交接",
            1, "交接已完成",
            2, "无需交接");

    private static final Map<String, String> SOURCE_NAMES = Map.of(
            SOURCE_SELF, "员工申请",
            SOURCE_HR, "HR 减员");

    private final ResignApplyMapper mapper;
    private final EmployeeMapper employeeMapper;
    private final OrgNodeMapper orgMapper;
    private final HandoverService handoverService;
    private final ApprovalApi approvalApi;
    /** 统一的三段式发起：本地先提交 → 远程发起 → 仅在途才回写实例号 */
    private final ApprovalLauncher approvalLauncher;
    /** 配置中心取号接口：生成申请单号 */
    private final CodeRuleApi codeRuleApi;
    /** 配置中心待办/消息接口：审批通过后发「关闭账号」待办 */
    private final NoticeApi noticeApi;

    @Value("${ehr.self-base-url:http://127.0.0.1:9006}")
    private String selfBaseUrl;
    /** EHR 前端地址：待办点击后跳人员列表并打开该员工档案 */
    @Value("${ehr.web-base-url:http://127.0.0.1:5179}")
    private String webBaseUrl;
    /** HR 部门名称（按名称模糊匹配，含子部门），离职通过后群发待办给该部门全员 */
    @Value("${ehr.notice.hr-org-name:人力资源}")
    private String hrOrgName;

    /* ---------------- 提交 ---------------- */

    /**
     * 员工自助提交离职申请：被办理人就是当前登录人自己的档案。
     * <p>刻意不加 {@code @Transactional}：本地写入由 ApprovalLauncher.inTx 独立提交，
     * 远程发起必须发生在本地事务之外，否则 settings 的同步回调读不到未提交的业务行。
     */
    public ResignApplyVO submitSelf(ResignApplyRequest req) {
        SecurityUser user = currentUser();
        Employee emp = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, user.getAccountId()).last("limit 1"));
        if (emp == null) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "当前账号未关联员工档案，无法提交离职申请；请联系 HR 在人员管理里建档");
        }
        return doSubmit(emp, SOURCE_SELF, req, user);
    }

    /** HR 在人员列表上提交减员：被办理人是选中的那个人（同样不加事务，见 submitSelf） */
    public ResignApplyVO submitReduce(Long employeeId, ResignApplyRequest req) {
        SecurityUser user = currentUser();
        Employee emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BizException(EhrErrorCode.EMP_NOT_FOUND);
        }
        return doSubmit(emp, SOURCE_HR, req, user);
    }

    private ResignApplyVO doSubmit(Employee emp, String source, ResignApplyRequest req, SecurityUser user) {
        if (emp.getStatus() != null && emp.getStatus() == EmployeeStatus.LEFT.getCode()) {
            throw new BizException(EhrErrorCode.EMP_STATUS_INVALID, "该员工已是离职状态，无需再提交");
        }
        // 减员/离职只针对在岗人员：待入职(10)/审批中(20/25) 应走删除或撤回入职，不占减员通道
        EmployeeStatus cur = EmployeeStatus.of(emp.getStatus());
        if (cur == null || !cur.isWorking()) {
            throw new BizException(EhrErrorCode.EMP_STATUS_INVALID,
                    "只有试用期或在职员工才能办理离职/减员");
        }
        if (approvalLauncher.hasRunning(String.valueOf(emp.getId()), null)) {
            throw new BizException(EhrErrorCode.APPROVAL_DUPLICATED, "该员工已有在审批中的单据，请先处理完再提交离职/减员");
        }
        Long pending = mapper.selectCount(new LambdaQueryWrapper<ResignApply>()
                .eq(ResignApply::getEmployeeId, emp.getId())
                .in(ResignApply::getStatus, 0, STATUS_PENDING_HR));
        if (pending != null && pending > 0) {
            throw new BizException(EhrErrorCode.APPROVAL_DUPLICATED,
                    "该员工已有一张在审批中的离职/减员申请，请先处理完再提交");
        }
        LocalDate resignDate = parseDate(req == null ? null : req.getResignDate());
        if (resignDate == null) {
            resignDate = LocalDate.now();
        }
        if (emp.getHireDate() != null && resignDate.isBefore(emp.getHireDate())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "离职日期不能早于入职日期");
        }
        if (SOURCE_SELF.equals(source) && resignDate.isBefore(LocalDate.now())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "离职日期不能早于今天");
        }

        OrgNode org = emp.getOrgId() == null ? null : orgMapper.selectById(emp.getOrgId());
        ResignApply apply = new ResignApply();
        apply.setEmployeeId(emp.getId());
        apply.setEmpNo(emp.getEmpNo());
        // 申请单号：提交前调用配置中心「单号规则」取号，未配置时留空不阻断
        apply.setApplyNo(nextApplyNo(TABLE_RESIGN));
        apply.setEmployeeName(emp.getName());
        apply.setOrgId(emp.getOrgId());
        apply.setOrgName(orgName(org));
        apply.setSource(source);
        apply.setApplicantAccountId(user.getAccountId());
        apply.setApplicantName(user.getUsername());
        apply.setResignDate(resignDate);
        apply.setReason(req == null || req.getReason() == null ? "" : req.getReason().trim());
        apply.setHandover(req == null || req.getHandover() == null ? "" : req.getHandover().trim());
        apply.setStatus(0);
        // 记下提交前的状态：驳回/撤销要按这个还原，避免人卡在「离职中」
        apply.setPrevStatus(emp.getStatus());
        // 交接：选交接人 + 至少一条交接项；确实没有在办事项时显式 noHandover=true 才能跳过
        List<String> items = bindHandover(apply, emp, req);
        // ① 本地写入（申请单 + 交接项 + 档案置离职中）先独立提交：
        //    settings 的同步回调发生时，这条申请单一定可见
        approvalLauncher.inTx(() -> {
            mapper.insert(apply);
            handoverService.replaceItems(apply.getId(), apply.getEmployeeId(), items);
            // 提交即进入「离职中」：审批 + 交接期间档案不再算在职
            employeeMapper.update(null, new LambdaUpdateWrapper<Employee>()
                    .eq(Employee::getId, emp.getId())
                    .set(Employee::getStatus, EmployeeStatus.OFFBOARDING.getCode()));
        });

        // ② 无需交接：立刻发起审批；否则等交接人全部确认后再发起（confirmHandover 里触发）
        if (apply.getHandoverStatus() != null && apply.getHandoverStatus() == 2) {
            launchResignApproval(apply);
        }
        // 实例可能在 start() 内当场终态并同步回调，重新读一次拿真实状态
        ResignApply fresh = mapper.selectById(apply.getId());
        log.info("离职申请已提交：id={} 员工={} source={} 交接人={} 交接项={} 状态={}",
                apply.getId(), emp.getName(), source, apply.getHandoverName(), items.size(),
                fresh == null ? null : fresh.getStatus());
        return toVO(fresh == null ? apply : fresh, user);
    }

    /**
     * 解析并校验交接人 / 交接项目，写到申请单上。
     *
     * @return 校验、去重后的交接项目内容（按顺序）
     */
    private List<String> bindHandover(ResignApply apply, Employee emp, ResignApplyRequest req) {
        if (req != null && Boolean.TRUE.equals(req.getNoHandover())) {
            apply.setHandoverStatus(2);
            return List.of();
        }
        Long confirmerId = parseId(req == null ? null : req.getHandoverEmployeeId());
        if (confirmerId == null) {
            throw new BizException(EhrErrorCode.HANDOVER_CONFIRMER_INVALID,
                    "请选择交接人：离职/减员必须指定由谁接手工作");
        }
        Employee confirmer = employeeMapper.selectById(confirmerId);
        if (confirmer == null) {
            throw new BizException(EhrErrorCode.HANDOVER_CONFIRMER_INVALID, "交接人不存在");
        }
        if (confirmer.getId().equals(emp.getId())) {
            throw new BizException(EhrErrorCode.HANDOVER_CONFIRMER_INVALID, "交接人不能是离职/减员的人本人");
        }
        if (confirmer.getStatus() != null && confirmer.getStatus() == EmployeeStatus.LEFT.getCode()) {
            throw new BizException(EhrErrorCode.HANDOVER_CONFIRMER_INVALID,
                    "交接人「" + confirmer.getName() + "」已是离职状态");
        }
        if (confirmer.getAccountId() == null) {
            throw new BizException(EhrErrorCode.HANDOVER_CONFIRMER_INVALID,
                    "交接人「" + confirmer.getName() + "」还没有统一账号，无法登录确认交接；"
                            + "请先为其开户，或换一位交接人");
        }
        List<String> items = handoverService.normalize(req.getItems());
        if (items.isEmpty()) {
            throw new BizException(EhrErrorCode.HANDOVER_ITEM_INVALID,
                    "请至少填写一项交接内容（确实没有在办事项，可勾选「无需交接」）");
        }
        apply.setHandoverEmployeeId(confirmer.getId());
        apply.setHandoverAccountId(confirmer.getAccountId());
        apply.setHandoverName(confirmer.getName());
        apply.setHandoverStatus(0);
        return items;
    }

    /**
     * 发起离职/减员审批，并把实例号条件回写到申请单 + 员工档案。
     * <p>三段式由 ApprovalLauncher 托管：仅在实例仍在进行中时才回写；发起即终态时
     * 回调已经把业务行写成终态，不能再把实例号挂回去。
     */
    private void launchResignApproval(ResignApply apply) {
        String bizType = bizTypeOf(apply);
        if (approvalLauncher.hasRunning(String.valueOf(apply.getEmployeeId()), bizType)) {
            return;
        }
        ApprovalInstanceDTO inst = approvalLauncher.launch(
                () -> buildResignStart(apply),
                instanceId -> { /* 实例号已退役：在途判断统一查审批中心 */ },
                // 发起确认失败：退回「待交接」，交接人再确认一次即可重新发起
                () -> mapper.update(null, new LambdaUpdateWrapper<ResignApply>()
                        .eq(ResignApply::getId, apply.getId())
                        .eq(ResignApply::getStatus, 0)
                        .set(ResignApply::getHandoverStatus, 0)));
        log.info("离职/减员审批已发起 applyId={} instanceId={} status={}",
                apply.getId(), inst.getId(), inst.getStatus());
    }

    /**
     * 取申请单号：跨服务调用失败或未配置规则时直接抛出，不吞异常。
     * 单号是单据的必备标识，宁可提交失败也不能落一条没有编号的申请单。
     */
    private String nextApplyNo(String tableName) {
        // 用 var：settings-client 的 R 与 common.R 是两个类型，显式声明会编译报错
        var res = codeRuleApi.next(tableName);
        String no = res == null || res.getData() == null ? "" : res.getData().trim();
        if (no.isEmpty()) {
            throw new IllegalStateException("取申请单号失败：未配置单号规则或返回为空，tableName=" + tableName);
        }
        return no;
    }

    /**
     * 纯构造「离职/减员」发起请求。applicant 用<b>提交时快照的账号</b>而不是当前登录人：
     * 交接全部确认时按按钮的是交接人，但单据的发起人始终是原提交人
     * （审批中心据此限制「不能审自己提交的单」）。
     */
    private ApprovalStartDTO buildResignStart(ResignApply apply) {
        String bizType = bizTypeOf(apply);
        ApprovalStartDTO dto = new ApprovalStartDTO();
        dto.setBizType(bizType);
        dto.setBizId(String.valueOf(apply.getId()));
        // 审批中心「业务编码」用申请单号；未配置规则时回退工号，保证仍有标识
        dto.setBizNo(nvl(apply.getApplyNo()).isEmpty() ? nvl(apply.getEmpNo()) : nvl(apply.getApplyNo()));
        dto.setTitle((SOURCE_HR.equals(apply.getSource()) ? "减员审批 · " : "离职审批 · ")
                + nvl(apply.getEmployeeName()));
        dto.setApplicantId(apply.getApplicantAccountId());
        dto.setApplicantName(nvl(apply.getApplicantName()));
        dto.setApplicantOrgId(apply.getOrgId());
        dto.setOrgPath(nvl(apply.getOrgName()));

        HandoverService.Progress p =
                HandoverService.progressOf(handoverService.listByApply(apply.getId()));
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("employeeId", String.valueOf(apply.getEmployeeId()));
        form.put("empNo", nvl(apply.getEmpNo()));
        form.put("applyNo", nvl(apply.getApplyNo()));
        form.put("employeeName", nvl(apply.getEmployeeName()));
        form.put("orgName", nvl(apply.getOrgName()));
        form.put("resignDate", apply.getResignDate() == null ? null : apply.getResignDate().toString());
        // 场景 form_schema 里叫 lastWorkDate，两个键都放，页面配置改了也不会丢
        form.put("lastWorkDate", apply.getResignDate() == null ? null : apply.getResignDate().toString());
        form.put("reason", nvl(apply.getReason()));
        form.put("source", nvl(apply.getSource()));
        form.put("sourceName", SOURCE_NAMES.getOrDefault(apply.getSource(), nvl(apply.getSource())));
        form.put("handover", nvl(apply.getHandover()));
        form.put("handoverName", nvl(apply.getHandoverName()));
        form.put("handoverProgress", p.text());
        form.put("submitter", nvl(apply.getApplicantName()));
        dto.setFormData(form);
        dto.setCallbackUrl(selfBaseUrl + CALLBACK_PATH);
        return dto;
    }

    /* ---------------- 交接确认 ---------------- */

    /**
     * 交接人确认交接项。全部确认后申请单 handoverStatus 才置 1，
     * 并<b>在这一刻发起审批中心实例</b> —— 交接没做完就不进审批流。
     * <p>
     * 只认 {@code handover_account_id} 本人，不提供超管代确认的口子。
     */
    public ResignApplyVO confirmHandover(Long id, HandoverConfirmRequest req) {
        SecurityUser user = currentUser();
        ResignApply apply = require(id);
        if (apply.getStatus() == null || apply.getStatus() != 0) {
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID, "只有待审批的申请需要确认交接");
        }
        if (apply.getHandoverStatus() != null && apply.getHandoverStatus() == 2) {
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID, "本单标记为无需交接");
        }
        if (apply.getHandoverStatus() != null && apply.getHandoverStatus() == 1) {
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID, "交接已确认完毕并进入审批，无需重复确认");
        }
        if (apply.getHandoverAccountId() == null
                || !apply.getHandoverAccountId().equals(user.getAccountId())) {
            throw new BizException(EhrErrorCode.HANDOVER_NOT_CONFIRMER,
                    "本单的交接人是「" + apply.getHandoverName() + "」，只有本人可以确认");
        }
        List<Long> ids = parseIds(req == null ? null : req.getItemIds());
        HandoverService.Progress p = handoverService.confirm(id, ids,
                req == null ? null : req.getNote(), user.getAccountId());
        if (p.allDone()) {
            // ① 交接完成状态先独立提交：settings 的同步回调发生时，申请单一定可见
            approvalLauncher.inTx(() -> mapper.update(null, new LambdaUpdateWrapper<ResignApply>()
                    .eq(ResignApply::getId, id)
                    .set(ResignApply::getHandoverStatus, 1)
                    .set(ResignApply::getHandoverConfirmedAt, LocalDateTime.now())));
            log.info("交接已全部确认：applyId={} 交接人={} 共 {} 项", id, apply.getHandoverName(), p.total());
            // ②③ 交接全部确认后才发起审批中心实例
            launchResignApproval(apply);
            ResignApply fresh = mapper.selectById(id);
            return toVO(fresh == null ? apply : fresh, user);
        }
        return toVO(apply, user);
    }

    /* ---------------- 终审回调 ---------------- */

    /**
     * 接收审批中心回传的离职/减员终态。必须幂等：回调失败会退避重试。
     * <p>
     * 通过 → 档案置离职 + 停用账号、单据 status=1；驳回/撤回/终止 → 还原档案状态、单据 status=2/3。
     * 两种情况都清掉申请单与员工档案上的在途标记。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleResignApprovalResult(ApprovalCallbackRequest req) {
        Long applyId = parseLongOrNull(req.getBizId());
        Long instId = parseLongOrNull(req.getInstanceId());
        if (applyId == null) {
            log.warn("离职审批回调缺少业务ID：{}", req.getBizId());
            return;
        }
        ResignApply apply = mapper.selectById(applyId);
        if (apply == null) {
            log.warn("离职审批回调对应的申请不存在 bizId={}", req.getBizId());
            return;
        }
        if (apply.getApprovalInstanceId() != null && !apply.getApprovalInstanceId().equals(instId)) {
            log.info("忽略过期的离职审批回调 applyId={} 当前单={} 回调单={}",
                    applyId, apply.getApprovalInstanceId(), instId);
            return;
        }
        int st = apply.getStatus() == null ? -1 : apply.getStatus();
        if (st != 0 && st != STATUS_PENDING_HR) {
            log.info("离职审批回调重复投递或状态不匹配，忽略 applyId={} status={}", applyId, st);
            return;
        }
        if (Boolean.TRUE.equals(req.getApproved())) {
            Employee emp = employeeMapper.selectById(apply.getEmployeeId());
            if (emp == null || (emp.getStatus() != null
                    && (emp.getStatus() == EmployeeStatus.LEFT.getCode()
                    || emp.getStatus() == EmployeeStatus.PENDING_ACCOUNT_CLOSE.getCode()))) {
                // 已经离职 / 已在待关闭账号：视为处理过，正常返回 200，别让 settings 无意义地重试
                log.info("离职审批通过但档案已是离职或待关闭账号，按幂等处理 applyId={}", applyId);
                apply.setStatus(1);
            } else {
                markPendingAccountClose(apply);
                apply.setStatus(1);
            }
        } else {
            apply.setStatus(req.getResult() != null && (req.getResult() == 3 || req.getResult() == 4) ? 3 : 2);
            restoreEmployeeStatus(apply);
        }
        mapper.updateById(apply);
        clearApprovalMarks(apply);
        log.info("离职审批终审已处理 applyId={} status={} result={}", applyId,
                apply.getStatus(), req.getResultName());
    }

    /** 清掉申请单上的在途审批标记（updateById 跳过 null，必须用 wrapper） */
    private void clearApprovalMarks(ResignApply apply) {
        mapper.update(null, new LambdaUpdateWrapper<ResignApply>()
                .eq(ResignApply::getId, apply.getId())
                .set(ResignApply::getApprovalInstanceId, null));
    }

    /** 驳回 / 撤销后把档案从「离职中」还原回提交前的状态 */
    private void restoreEmployeeStatus(ResignApply apply) {
        Employee emp = employeeMapper.selectById(apply.getEmployeeId());
        if (emp == null || emp.getStatus() == null
                || emp.getStatus() != EmployeeStatus.OFFBOARDING.getCode()) {
            return;
        }
        Integer prev = apply.getPrevStatus();
        if (prev == null || EmployeeStatus.of(prev) == null || !EmployeeStatus.of(prev).isWorking()) {
            LocalDate regular = emp.getExpectedRegularizeDate();
            prev = regular != null && regular.isAfter(LocalDate.now())
                    ? EmployeeStatus.PROBATION.getCode()
                    : EmployeeStatus.ACTIVE.getCode();
        }
        employeeMapper.update(null, new LambdaUpdateWrapper<Employee>()
                .eq(Employee::getId, emp.getId())
                .eq(Employee::getStatus, EmployeeStatus.OFFBOARDING.getCode())
                .set(Employee::getStatus, prev));
        log.info("离职/减员单未通过，档案状态已还原：applyId={} 员工={} → {}",
                apply.getId(), emp.getName(), prev);
    }

    /**
     * 审批通过后进入「待关闭账号」，而不是直接置离职：
     * 账号关闭是独立的一步，由 HR 在人员列表点「关闭账号」触发（见 EmployeeService.closeAccount）。
     * 离职日期 / 原因先落到档案上，列表能直接看到计划离岗时间。
     */
    private void markPendingAccountClose(ResignApply apply) {
        Employee emp = employeeMapper.selectById(apply.getEmployeeId());
        if (emp == null) {
            throw new BizException(EhrErrorCode.EMP_NOT_FOUND, "员工档案已不存在，无法办理离职");
        }
        employeeMapper.update(null, new LambdaUpdateWrapper<Employee>()
                .eq(Employee::getId, emp.getId())
                .set(Employee::getStatus, EmployeeStatus.PENDING_ACCOUNT_CLOSE.getCode())
                .set(Employee::getResignDate, apply.getResignDate())
                .set(Employee::getResignReason, nvl(apply.getReason())));
        log.info("离职/减员审批通过，进入待关闭账号 employeeId={} applyId={}",
                emp.getId(), apply.getId());
        sendCloseAccountTodo(apply);
    }

    /** 关闭账号待办：减员只通知发起人，离职群发 HR 部门（含子部门）全员 */
    private void sendCloseAccountTodo(ResignApply apply) {
        boolean reduce = SOURCE_HR.equals(apply.getSource());
        List<Long> accountIds = reduce
                ? (apply.getApplicantAccountId() == null ? List.of() : List.of(apply.getApplicantAccountId()))
                : hrAccountIds();
        if (accountIds.isEmpty()) {
            log.warn("关闭账号待办没有可用接收人，已跳过 applyId={} source={}", apply.getId(), apply.getSource());
            return;
        }
        String employeeName = nvl(apply.getEmployeeName());
        NoticeSendDTO dto = new NoticeSendDTO();
        dto.setRuleCode(reduce ? "ehr.reduce.close" : "ehr.resign.close");
        // 强制站内方式为「待办」，否则规则缺失时会退化成普通消息
        dto.setChannel(1);
        dto.setAccountIds(accountIds);
        dto.setTitle(reduce ? "待办：关闭减员员工账号" : "待办：关闭离职员工账号");
        dto.setContent(reduce
                ? "你发起的" + employeeName + "的减员审批已经通过，请及时关闭" + employeeName + "的账号！"
                : employeeName + "的离职审批已经通过，请及时关闭" + employeeName + "的账号！");
        dto.getVars().put("employeeName", employeeName);
        dto.setBizType(CloseAccountNotice.BIZ_TYPE);
        dto.setBizId(String.valueOf(apply.getEmployeeId()));
        dto.setBizNo(apply.getApplyNo());
        dto.setApplicantName(apply.getApplicantName());
        dto.setLink(webBaseUrl + "/employees?employeeId=" + apply.getEmployeeId());
        try {
            noticeApi.sendBatch(dto);
            log.info("关闭账号待办已发送 employeeId={} 接收人 {} 个", apply.getEmployeeId(), accountIds.size());
        } catch (Exception e) {
            // 通知失败不回滚审批结果：账号该关还得关，别让消息中心抖动卡住流程
            log.error("关闭账号待办发送失败 applyId={} employeeId={}", apply.getId(), apply.getEmployeeId(), e);
        }
    }

    /** HR 部门（名称模糊匹配）+ 所有子部门内、仍有账号且未离职的人员账号ID */
    private List<Long> hrAccountIds() {
        List<OrgNode> all = orgMapper.selectList(null);
        OrgNode hrOrg = all.stream()
                .filter(o -> o.getName() != null && o.getName().contains(hrOrgName))
                .findFirst()
                .orElse(null);
        if (hrOrg == null) {
            log.warn("未找到 HR 部门「{}」，关闭账号待办不群发", hrOrgName);
            return List.of();
        }
        Set<Long> orgIds = new HashSet<>();
        orgIds.add(hrOrg.getId());
        boolean grown = true;
        while (grown) {
            grown = false;
            for (OrgNode o : all) {
                if (o.getParentId() != null && orgIds.contains(o.getParentId()) && orgIds.add(o.getId())) {
                    grown = true;
                }
            }
        }
        return employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                        .in(Employee::getOrgId, orgIds)
                        .isNotNull(Employee::getAccountId)
                        .ne(Employee::getStatus, EmployeeStatus.LEFT.getCode()))
                .stream().map(Employee::getAccountId).distinct().toList();
    }

    /* ---------------- 撤销 ---------------- */

    /**
     * 撤销申请：先把审批中心的在途单撤回，再改本地状态。
     * <p>
     * <b>刻意不加 @Transactional</b>：settings 处理撤回时会同步回调本服务，回调线程要
     * UPDATE 同一行；若本方法持有写锁等 settings 响应，两边会互相等待直到锁超时。
     */
    public ResignApplyVO cancel(Long id) {
        SecurityUser user = currentUser();
        ResignApply apply = require(id);
        if (!user.isSuperAdmin() && !user.getAccountId().equals(apply.getApplicantAccountId())) {
            throw new BizException(EhrErrorCode.APPROVAL_NOT_APPLICANT, "只能撤销自己提交的申请");
        }
        if (apply.getStatus() == null
                || (apply.getStatus() != 0 && apply.getStatus() != STATUS_PENDING_HR)) {
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID,
                    "只有还在审批中的申请可以撤销（已通过请走复职）");
        }
        Long runningId = approvalLauncher.latestRunningId(bizTypeOf(apply), String.valueOf(apply.getId()));
        if (runningId != null) {
            withdrawInstance(runningId, user);
        }
        apply.setStatus(3);
        restoreEmployeeStatus(apply);
        mapper.updateById(apply);
        clearApprovalMarks(apply);
        return toVO(apply, user);
    }

    private void withdrawInstance(Long instanceId, SecurityUser user) {
        ApprovalActionDTO act = new ApprovalActionDTO();
        act.setOperatorId(user.getAccountId());
        act.setOperatorName(user.getUsername());
        act.setNote("发起人撤销离职/减员申请");
        com.laimengchao.sso.settings.client.dto.R<ApprovalInstanceDTO> resp;
        try {
            resp = approvalApi.withdraw(instanceId, act);
        } catch (Exception e) {
            log.error("撤回离职审批单失败 instanceId={}", instanceId, e);
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID, "撤回审批单失败，请稍后重试");
        }
        if (resp == null || !resp.ok()) {
            String msg = resp == null ? "" : nvl(resp.getMessage());
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID,
                    msg.isBlank() ? "撤回审批单失败" : msg);
        }
    }

    /* ---------------- 查询 ---------------- */

    /** 单据详情：审批弹窗要展开交接清单，单独取一次比列表里塞全量更省 */
    public ResignApplyVO detail(Long id) {
        SecurityUser user = currentUser();
        return toVO(require(id), user);
    }

    /** 待我确认交接的单子（交接人视角）；includeFinished=true 时连已确认完的一起返回 */
    public List<ResignApplyVO> myHandovers(boolean includeFinished) {
        SecurityUser user = currentUser();
        LambdaQueryWrapper<ResignApply> w = new LambdaQueryWrapper<ResignApply>()
                .eq(ResignApply::getHandoverAccountId, user.getAccountId())
                .ne(ResignApply::getHandoverStatus, 2);
        if (!includeFinished) {
            // 只显示「还没确认完」的：交接一旦完成就发起审批，不该继续挂在待交接列表里
            w.eq(ResignApply::getStatus, 0).eq(ResignApply::getHandoverStatus, 0);
        } else {
            w.eq(ResignApply::getStatus, 0);
        }
        w.orderByDesc(ResignApply::getCreatedAt).last("limit 100");
        return mapper.selectList(w).stream().map(a -> toVO(a, user)).collect(Collectors.toList());
    }

    /** scope=mine 我提交的（含 HR 为我提交的减员）；scope=all 全部（需审批权限）；status 可过滤 */
    public R.Page<ResignApplyVO> page(String scope, Integer status, int page, int size) {
        SecurityUser user = currentUser();
        LambdaQueryWrapper<ResignApply> w = new LambdaQueryWrapper<>();
        boolean all = "all".equalsIgnoreCase(scope);
        if (all) {
            if (!user.isSuperAdmin()
                    && !user.getPermissions().contains(com.laimengchao.sso.ehr.constant.PermissionCodes.APPROVAL_APPROVE)
                    && !user.getPermissions().contains(com.laimengchao.sso.ehr.constant.PermissionCodes.EMPLOYEE_RESIGN)) {
                throw new BizException(ErrorCode.FORBIDDEN, "没有审批权限，只能查看自己提交的申请");
            }
        } else {
            Long myEmployeeId = employeeIdOf(user.getAccountId());
            if (myEmployeeId != null) {
                w.eq(ResignApply::getEmployeeId, myEmployeeId);
            } else {
                w.eq(ResignApply::getApplicantAccountId, user.getAccountId());
            }
        }
        if (status != null) {
            w.eq(ResignApply::getStatus, status);
        } else {
            // 已撤销只留档，不在列表展示
            w.ne(ResignApply::getStatus, 3);
        }
        w.orderByDesc(ResignApply::getCreatedAt);
        IPage<ResignApply> ip = mapper.selectPage(new Page<>(page, size), w);
        List<ResignApplyVO> rows = ip.getRecords().stream()
                .map(a -> toVO(a, user))
                .collect(Collectors.toList());
        return R.Page.of(rows, ip.getTotal());
    }

    /* ---------------- 内部工具 ---------------- */

    private SecurityUser currentUser() {
        return SecurityUtils.currentUser().orElseThrow(() ->
                new BizException(ErrorCode.UNAUTHORIZED));
    }

    private ResignApply require(Long id) {
        ResignApply apply = mapper.selectById(id);
        if (apply == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "申请不存在");
        }
        return apply;
    }

    /** 当前账号对应的员工档案 ID；超管/没建档的账号为 null */
    private Long employeeIdOf(Long accountId) {
        if (accountId == null) {
            return null;
        }
        Employee emp = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, accountId).last("limit 1"));
        return emp == null ? null : emp.getId();
    }

    private String orgName(OrgNode org) {
        if (org == null) {
            return "";
        }
        String path = org.getPath();
        if (path == null || path.isBlank()) {
            return org.getName();
        }
        OrgNode node = org;
        for (String part : path.split("/")) {
            try {
                OrgNode n = orgMapper.selectById(Long.valueOf(part));
                if (n != null) {
                    node = n;
                }
            } catch (NumberFormatException ignored) {
                // 脏 path 跳过
            }
        }
        return node.getName();
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private Long parseLongOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "ID 格式不合法：" + raw);
        }
    }

    private List<Long> parseIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream().map(this::parseId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "日期格式应为 yyyy-MM-dd");
        }
    }

    private ResignApplyVO toVO(ResignApply a, SecurityUser user) {
        ResignApplyVO vo = new ResignApplyVO();
        vo.setId(a.getId());
        vo.setEmployeeId(a.getEmployeeId());
        vo.setEmpNo(a.getEmpNo());
        vo.setApplyNo(a.getApplyNo());
        vo.setEmployeeName(a.getEmployeeName());
        vo.setOrgName(a.getOrgName());
        vo.setSource(a.getSource());
        vo.setSourceName(SOURCE_NAMES.getOrDefault(a.getSource(), a.getSource()));
        vo.setApplicantName(a.getApplicantName());
        vo.setResignDate(a.getResignDate() == null ? null : a.getResignDate().toString());
        vo.setReason(a.getReason());
        vo.setHandover(a.getHandover());
        // 交接：清单 + 进度 + 「是不是轮到我确认」
        List<com.laimengchao.sso.ehr.module.approval.entity.HandoverItem> items =
                handoverService.listByApply(a.getId());
        HandoverService.Progress p = HandoverService.progressOf(items);
        vo.setHandoverItems(HandoverService.toVOList(items));
        vo.setHandoverEmployeeId(a.getHandoverEmployeeId());
        vo.setHandoverAccountId(a.getHandoverAccountId());
        vo.setHandoverName(a.getHandoverName());
        vo.setHandoverStatus(a.getHandoverStatus());
        vo.setHandoverStatusName(HANDOVER_STATUS_NAMES.getOrDefault(a.getHandoverStatus(), "未知"));
        vo.setHandoverDone(p.done());
        vo.setHandoverTotal(p.total());
        vo.setHandoverProgress(p.text());
        vo.setHandoverCompleted(a.getHandoverStatus() != null && a.getHandoverStatus() != 0);
        vo.setHandoverConfirmedAt(a.getHandoverConfirmedAt() == null ? null
                : a.getHandoverConfirmedAt().toString().replace('T', ' '));
        vo.setStatus(a.getStatus());
        // 状态 0 分两段：还没发起审批（交接中）/ 已经在审批中心（离职审批中）
        if (a.getStatus() != null && a.getStatus() == 0) {
            // 实例号已退役，改按本地交接阶段判断：0=待交接人确认，1/2=交接完成已发起审批
            boolean awaitingHandover = a.getHandoverStatus() == null || a.getHandoverStatus() == 0;
            vo.setStatusName(awaitingHandover ? "待交接"
                    : (SOURCE_HR.equals(a.getSource()) ? "减员审批中" : "离职审批中"));
        } else {
            vo.setStatusName(STATUS_NAMES.getOrDefault(a.getStatus(), "未知"));
        }
        vo.setApplicantAccountId(a.getApplicantAccountId());
        vo.setApproverAccountId(a.getApproverAccountId());
        vo.setApproverName(a.getApproverName());
        vo.setApproverOrgName(a.getApproverOrgName());
        vo.setApproveNote(a.getApproveNote());
        vo.setApprovedAt(a.getApprovedAt() == null ? null : a.getApprovedAt().toString().replace('T', ' '));
        vo.setHrApproverAccountId(a.getHrApproverAccountId());
        vo.setHrApproverName(a.getHrApproverName());
        vo.setHrApproveNote(a.getHrApproveNote());
        vo.setHrApprovedAt(a.getHrApprovedAt() == null ? null : a.getHrApprovedAt().toString().replace('T', ' '));
        vo.setCreatedAt(a.getCreatedAt() == null ? null : a.getCreatedAt().toString().replace('T', ' '));
        if (user != null) {
            Long me = user.getAccountId();
            vo.setMine(me.equals(a.getApplicantAccountId()));
            vo.setHandoverMine(me.equals(a.getHandoverAccountId()));
        }
        // 审批动作统一走审批中心，本地不再判断「轮到我审」
        vo.setActionable(false);
        vo.setApprovalInstanceId(a.getApprovalInstanceId());
        vo.setApprovalBizType(a.getApprovalInstanceId() == null ? null : bizTypeOf(a));
        return vo;
    }

    /** 员工自助离职 → ehr.resign；HR 减员 → ehr.reduce（两条独立流程） */
    private String bizTypeOf(ResignApply a) {
        return SOURCE_HR.equals(a.getSource()) ? BIZ_REDUCE : BIZ_RESIGN;
    }
}
