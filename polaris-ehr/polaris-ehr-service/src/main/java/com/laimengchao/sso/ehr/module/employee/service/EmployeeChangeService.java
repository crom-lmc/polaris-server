package com.laimengchao.sso.ehr.module.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.module.approval.support.ApprovalLauncher;
import com.laimengchao.sso.ehr.module.employee.constant.EmployeeStatus;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.ehr.module.employee.dto.EmployeeChangeRequest;
import com.laimengchao.sso.ehr.module.employee.entity.EmpChange;
import com.laimengchao.sso.ehr.module.employee.entity.EmpChangeField;
import com.laimengchao.sso.ehr.module.employee.entity.EmpChangeRow;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeBank;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeEducation;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeFamily;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeWork;
import com.laimengchao.sso.ehr.module.employee.mapper.EmpChangeFieldMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmpChangeMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmpChangeRowMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeBankMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeEducationMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeFamilyMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeWorkMapper;
import com.laimengchao.sso.ehr.module.employee.vo.EmployeeChangeResultVO;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.ehr.security.SecurityUser;
import com.laimengchao.sso.ehr.security.SecurityUtils;
import com.laimengchao.sso.settings.client.dto.ApprovalInstanceDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import com.laimengchao.sso.settings.client.support.SettingsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 人员信息变更（ehr.emp_update）。
 * <p>
 * 试用期/在职员工在「变更」弹窗里改完基本信息和四类子档案后统一提交，这里做三件事：
 * <ol>
 *   <li>把提交内容与档案现状逐字段/逐行比对，算出基本信息字段差异与子表 ADD/DEL/MOD；</li>
 *   <li>落变更单三件套（主表 + 字段明细 + 行明细），并把同一份对比数据冻结进审批快照
 *       （formData.change），审批对比页只读快照不查业务库；</li>
 *   <li>发起审批；终审回调按变更单把档案与子表一次性落库（驳回/撤销则只终态变更单）。</li>
 * </ol>
 * 回调定位用快照里的 changeId（发起时冻结），天然抗「重复投递 / 换了一张新单」的竞态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeChangeService {

    /** 审批场景编码，对应 settings 的 cfg_biz_scene.biz_type */
    private static final String BIZ_EMP_UPDATE = "ehr.emp_update";
    /** 终审结果接收地址：settings 审批结束后回调它（必须是 settings 能直连的地址） */
    private static final String CALLBACK_PATH = "/internal/ehr/approval-callback";
    /** 实例状态：0 = 进行中（与 ApprovalLauncher.STATUS_RUNNING 对齐） */
    private static final int STATUS_RUNNING = 0;

    /** 可走审批的基本信息字段：key 与 ehr_employee 列对应；顺序即变更单与对比页的字段顺序 */
    private static final Map<String, String> BASE_LABELS = new LinkedHashMap<>() {{
        put("maritalStatus", "婚姻状况");
        put("politicalStatus", "政治面貌");
        put("education", "最高学历");
        put("currentAddress", "现居住址");
        put("mobile", "手机号");
        put("emergencyContact", "紧急联系人");
        put("emergencyPhone", "紧急联系电话");
    }};

    /** 子表列定义（对比页表头；key 与行快照里的键对应，range 由 startDate/endDate 拼出） */
    private static final List<Map<String, String>> BANK_COLUMNS = columns(
            new String[][]{{"bank", "银行"}, {"accountNo", "卡号"}, {"holderName", "持卡人"}, {"branch", "开户行"}, {"isPrimary", "工资卡"}});
    private static final List<Map<String, String>> EDUCATION_COLUMNS = columns(
            new String[][]{{"school", "学校"}, {"major", "专业"}, {"degree", "学历"}, {"range", "起止时间"}, {"remark", "备注"}});
    private static final List<Map<String, String>> WORK_COLUMNS = columns(
            new String[][]{{"company", "单位"}, {"position", "职位"}, {"range", "起止时间"}, {"leaveReason", "离职原因"}});
    private static final List<Map<String, String>> FAMILY_COLUMNS = columns(
            new String[][]{{"relation", "关系"}, {"name", "姓名"}, {"company", "工作单位"}, {"phone", "电话"}});

    private static final ObjectMapper OM = new ObjectMapper();

    private final EmployeeMapper employeeMapper;
    private final EmployeeBankMapper bankMapper;
    private final EmployeeEducationMapper educationMapper;
    private final EmployeeWorkMapper workMapper;
    private final EmployeeFamilyMapper familyMapper;
    private final EmpChangeMapper changeMapper;
    private final EmpChangeFieldMapper changeFieldMapper;
    private final EmpChangeRowMapper changeRowMapper;
    private final OrgNodeMapper orgMapper;
    private final SettingsClient settingsClient;
    /** 统一的三段式发起：本地先提交 → 远程发起 → 失败补偿 */
    private final ApprovalLauncher approvalLauncher;

    /** 本服务对内地址，用来拼终审回调地址（settings 必须能直连） */
    @Value("${ehr.self-base-url:http://127.0.0.1:9006}")
    private String selfBaseUrl;

    /* ---------------- 提交：比对差异 → 落变更单 → 发起审批 ---------------- */

    /**
     * 提交信息变更。基本信息只比 7 个可变字段；子表按「目标态整表」与现状逐行比对：
     * 带 id 的行比内容（MOD）、不带 id 的是新增（ADD）、库里有但没提交上来的是删除（DEL）。
     * 什么都没改直接报错，不浪费一张审批单。
     */
    public EmployeeChangeResultVO submit(Long id, EmployeeChangeRequest req) {
        Employee emp = require(id);
        EmployeeStatus st = EmployeeStatus.of(emp.getStatus());
        if (st == EmployeeStatus.PENDING) {
            throw new BizException(EhrErrorCode.EMP_EDIT_NOT_ALLOWED, "「待入职」草稿请直接编辑保存，无需变更审批");
        }
        if (st != EmployeeStatus.PROBATION && st != EmployeeStatus.ACTIVE) {
            throw new BizException(EhrErrorCode.EMP_EDIT_NOT_ALLOWED,
                    "「" + (st == null ? "未知状态" : st.getLabel()) + "」不允许变更员工信息，请先完成当前流程");
        }

        // ① 基本信息字段差异
        List<FieldDiff> baseDiffs = collectBaseDiffs(emp, req);
        // ② 子表行差异（null=本次不涉及该子表；空数组=全删）
        ListDiff banks = diffBanks(emp, req.getBanks());
        ListDiff educations = diffEducations(emp, req.getEducations());
        ListDiff works = diffWorks(emp, req.getWorks());
        ListDiff families = diffFamilies(emp, req.getFamilies());
        List<ListDiff> lists = List.of(banks, educations, works, families);
        List<RowDiff> rowDiffs = lists.stream()
                .flatMap(l -> l.changes.stream()).collect(Collectors.toList());
        if (baseDiffs.isEmpty() && rowDiffs.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "没有检测到变更内容，未提交");
        }

        // 格式与唯一性（审批有周期，回调落库前还会再查一次）
        for (FieldDiff d : baseDiffs) {
            if ("mobile".equals(d.key) || "emergencyPhone".equals(d.key)) {
                assertPhoneFormat(d.newValue, d.label);
            }
            if ("mobile".equals(d.key) && !d.newValue.isEmpty()) {
                assertMobileUnique(emp.getId(), d.newValue);
            }
        }
        assertNoRunningApproval(emp);

        // ③ 落变更单三件套（本地事务先行并提交，与远程发起解耦）
        SecurityUser user = SecurityUtils.currentUser().orElse(null);
        EmpChange master = new EmpChange();
        master.setEmployeeId(emp.getId());
        master.setEmpNo(nvl(emp.getEmpNo()));
        master.setEmployeeName(nvl(emp.getName()));
        master.setOrgId(emp.getOrgId());
        master.setOrgName(orgPathOf(emp.getOrgId()));
        master.setStatus(0);
        master.setApplicantAccountId(user == null ? null : user.getAccountId());
        master.setApplicantName(user == null ? "系统" : approverName(user));
        master.setBaseChanges(baseDiffs.size());
        master.setAddCount(countOp(rowDiffs, "ADD"));
        master.setModCount(countOp(rowDiffs, "MOD"));
        master.setDelCount(countOp(rowDiffs, "DEL"));
        master.setRemark("");
        approvalLauncher.inTx(() -> {
            changeMapper.insert(master);
            int seq = 0;
            for (FieldDiff d : baseDiffs) {
                EmpChangeField f = new EmpChangeField();
                f.setChangeId(master.getId());
                f.setFieldKey(d.key);
                f.setFieldLabel(d.label);
                f.setOldValue(d.oldValue);
                f.setNewValue(d.newValue);
                f.setSeq(seq++);
                changeFieldMapper.insert(f);
            }
            for (RowDiff d : rowDiffs) {
                EmpChangeRow r = new EmpChangeRow();
                r.setChangeId(master.getId());
                r.setListType(d.listType);
                r.setRowKey(d.rowKey);
                r.setOp(d.op);
                r.setOldJson(d.oldRow == null ? null : toJson(d.oldRow));
                r.setNewJson(d.newRow == null ? null : toJson(d.newRow));
                r.setSeq(d.seq);
                changeRowMapper.insert(r);
            }
        });

        // ④ 发起审批：快照里冻结完整对比载荷，ROUTE 对比页只读快照、不查业务库
        Map<String, Object> formData = buildFormData(emp, master, baseDiffs, lists);
        ApprovalInstanceDTO inst = approvalLauncher.launch(
                () -> buildStart(emp, master, formData),
                instanceId -> { /* 实例号回写见下方：RUNNING 时补记到变更单，仅为追溯 */ },
                () -> markLaunchFailed(master.getId()));
        if (Integer.valueOf(STATUS_RUNNING).equals(inst.getStatus())) {
            Long instId = inst.getId();
            approvalLauncher.inTx(() -> changeMapper.update(null, new LambdaUpdateWrapper<EmpChange>()
                    .eq(EmpChange::getId, master.getId())
                    .eq(EmpChange::getStatus, 0)
                    .set(EmpChange::getApprovalInstanceId, instId)));
        }

        EmployeeChangeResultVO vo = new EmployeeChangeResultVO();
        vo.setChangeId(String.valueOf(master.getId()));
        vo.setBaseChanges(master.getBaseChanges());
        vo.setAddCount(master.getAddCount());
        vo.setModCount(master.getModCount());
        vo.setDelCount(master.getDelCount());
        // 实例发起即终态时回调已处理完，不能再标「审批中」
        vo.setPendingApproval(Integer.valueOf(STATUS_RUNNING).equals(inst.getStatus()));
        vo.setChangeSummary(String.valueOf(formData.get("changeSummary")));
        return vo;
    }

    /** 发起确认失败：变更单置「已撤销」，允许重新提交（档案本来就没动，无需其它补偿） */
    private void markLaunchFailed(Long changeId) {
        changeMapper.update(null, new LambdaUpdateWrapper<EmpChange>()
                .eq(EmpChange::getId, changeId)
                .eq(EmpChange::getStatus, 0)
                .set(EmpChange::getStatus, 3)
                .set(EmpChange::getRemark, "发起审批失败"));
    }

    /* ---------------- 终审回调：通过 → 按变更单落库；驳回/撤销 → 只终态变更单 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    public void handleApprovalResult(ApprovalCallbackRequest req) {
        // 优先按快照里冻结的 changeId 定位：天然免疫「重复投递」与「驳回后又提了新单」的竞态
        Map<String, Object> form = req.getFormData() == null ? Map.of() : req.getFormData();
        Long changeId = parseLongOrNull(form.get("changeId"));
        EmpChange master = changeId == null ? null : changeMapper.selectById(changeId);
        if (master == null) {
            // 兼容：老单据快照没有 changeId，按「该员工唯一在途单」兜底（改造前的单没有变更单，会走到 warn 分支）
            Long empId = parseLongOrNull(req.getBizId());
            master = empId == null ? null : changeMapper.selectOne(new LambdaQueryWrapper<EmpChange>()
                    .eq(EmpChange::getEmployeeId, empId)
                    .eq(EmpChange::getStatus, 0)
                    .orderByDesc(EmpChange::getId).last("limit 1"));
        }
        if (master == null) {
            log.warn("信息变更回调找不到变更单 instance={} bizId={}，按已处理吃掉", req.getInstanceId(), req.getBizId());
            return;
        }
        if (!Integer.valueOf(0).equals(master.getStatus())) {
            log.info("信息变更回调重复投递或状态不匹配，忽略 changeId={} status={}", master.getId(), master.getStatus());
            return;
        }
        Employee emp = employeeMapper.selectById(master.getEmployeeId());
        if (emp == null) {
            log.warn("信息变更回调对应的员工不存在 changeId={} employeeId={}", master.getId(), master.getEmployeeId());
            return;
        }
        if (!Boolean.TRUE.equals(req.getApproved())) {
            changeMapper.update(null, new LambdaUpdateWrapper<EmpChange>()
                    .eq(EmpChange::getId, master.getId())
                    .eq(EmpChange::getStatus, 0)
                    .set(EmpChange::getStatus, isWithdrawn(req) ? 3 : 2)
                    .set(EmpChange::getRemark, nvl(req.getResultName())));
            clearApproval(emp.getId());
            log.info("人员信息变更审批未通过，档案保持原样 changeId={} result={}", master.getId(), req.getResultName());
            return;
        }
        applyMaster(emp, master);
        changeMapper.update(null, new LambdaUpdateWrapper<EmpChange>()
                .eq(EmpChange::getId, master.getId())
                .eq(EmpChange::getStatus, 0)
                .set(EmpChange::getStatus, 1));
        clearApproval(emp.getId());
        log.info("人员信息变更审批通过并生效 changeId={} employeeId={}", master.getId(), emp.getId());
    }

    /** 审批通过：基本信息逐字段覆盖；子表按 op 增/删/改 */
    private void applyMaster(Employee emp, EmpChange master) {
        List<EmpChangeField> fields = changeFieldMapper.selectList(new LambdaQueryWrapper<EmpChangeField>()
                .eq(EmpChangeField::getChangeId, master.getId()).orderByAsc(EmpChangeField::getSeq));
        boolean baseDirty = false;
        for (EmpChangeField f : fields) {
            String v = nvl(f.getNewValue());
            switch (f.getFieldKey()) {
                case "mobile" -> {
                    if (!v.isEmpty()) {
                        assertMobileUnique(emp.getId(), v);
                    }
                    emp.setMobile(v);
                    baseDirty = true;
                }
                case "maritalStatus" -> { emp.setMaritalStatus(v); baseDirty = true; }
                case "politicalStatus" -> { emp.setPoliticalStatus(v); baseDirty = true; }
                case "education" -> { emp.setEducation(v); baseDirty = true; }
                case "emergencyContact" -> { emp.setEmergencyContact(v); baseDirty = true; }
                case "emergencyPhone" -> { emp.setEmergencyPhone(v); baseDirty = true; }
                case "currentAddress" -> { emp.setCurrentAddress(v); baseDirty = true; }
                default -> log.warn("信息变更落库遇到未知字段，跳过 changeId={} field={}", master.getId(), f.getFieldKey());
            }
        }
        if (baseDirty) {
            employeeMapper.updateById(emp);
        }

        List<EmpChangeRow> rows = changeRowMapper.selectList(new LambdaQueryWrapper<EmpChangeRow>()
                .eq(EmpChangeRow::getChangeId, master.getId()).orderByAsc(EmpChangeRow::getSeq));
        for (EmpChangeRow r : rows) {
            Map<String, Object> neo = r.getNewJson() == null ? null : parseJson(r.getNewJson());
            switch (r.getListType()) {
                case "BANK" -> applyBank(emp.getId(), r, neo);
                case "EDUCATION" -> applyEducation(emp.getId(), r, neo);
                case "WORK" -> applyWork(emp.getId(), r, neo);
                case "FAMILY" -> applyFamily(emp.getId(), r, neo);
                default -> log.warn("信息变更落库遇到未知子表，跳过 changeId={} listType={}", master.getId(), r.getListType());
            }
        }
    }

    private void applyBank(Long empId, EmpChangeRow r, Map<String, Object> neo) {
        switch (r.getOp()) {
            case "ADD" -> {
                EmployeeBank e = new EmployeeBank();
                e.setEmployeeId(empId);
                fillBank(e, neo);
                bankMapper.insert(e);
                clearOtherPrimaryBank(empId, e);
            }
            case "MOD" -> {
                EmployeeBank e = findBank(r.getRowKey());
                if (e == null) {
                    log.warn("变更落库时银行卡不存在，跳过 rowKey={}", r.getRowKey());
                    return;
                }
                fillBank(e, neo);
                bankMapper.updateById(e);
                clearOtherPrimaryBank(empId, e);
            }
            case "DEL" -> {
                Long rowId = parseLongOrNull(r.getRowKey());
                if (rowId != null) {
                    bankMapper.deleteById(rowId);
                }
            }
            default -> log.warn("未知变更操作，跳过 rowKey={} op={}", r.getRowKey(), r.getOp());
        }
    }

    private void applyEducation(Long empId, EmpChangeRow r, Map<String, Object> neo) {
        switch (r.getOp()) {
            case "ADD" -> {
                EmployeeEducation e = new EmployeeEducation();
                e.setEmployeeId(empId);
                fillEducation(e, neo);
                educationMapper.insert(e);
            }
            case "MOD" -> {
                EmployeeEducation e = findEducation(r.getRowKey());
                if (e == null) {
                    log.warn("变更落库时教育背景不存在，跳过 rowKey={}", r.getRowKey());
                    return;
                }
                fillEducation(e, neo);
                educationMapper.updateById(e);
            }
            case "DEL" -> {
                Long rowId = parseLongOrNull(r.getRowKey());
                if (rowId != null) {
                    educationMapper.deleteById(rowId);
                }
            }
            default -> log.warn("未知变更操作，跳过 rowKey={} op={}", r.getRowKey(), r.getOp());
        }
    }

    private void applyWork(Long empId, EmpChangeRow r, Map<String, Object> neo) {
        switch (r.getOp()) {
            case "ADD" -> {
                EmployeeWork e = new EmployeeWork();
                e.setEmployeeId(empId);
                fillWork(e, neo);
                workMapper.insert(e);
            }
            case "MOD" -> {
                EmployeeWork e = findWork(r.getRowKey());
                if (e == null) {
                    log.warn("变更落库时工作经历不存在，跳过 rowKey={}", r.getRowKey());
                    return;
                }
                fillWork(e, neo);
                workMapper.updateById(e);
            }
            case "DEL" -> {
                Long rowId = parseLongOrNull(r.getRowKey());
                if (rowId != null) {
                    workMapper.deleteById(rowId);
                }
            }
            default -> log.warn("未知变更操作，跳过 rowKey={} op={}", r.getRowKey(), r.getOp());
        }
    }

    private void applyFamily(Long empId, EmpChangeRow r, Map<String, Object> neo) {
        switch (r.getOp()) {
            case "ADD" -> {
                EmployeeFamily e = new EmployeeFamily();
                e.setEmployeeId(empId);
                fillFamily(e, neo);
                familyMapper.insert(e);
            }
            case "MOD" -> {
                EmployeeFamily e = findFamily(r.getRowKey());
                if (e == null) {
                    log.warn("变更落库时家庭成员不存在，跳过 rowKey={}", r.getRowKey());
                    return;
                }
                fillFamily(e, neo);
                familyMapper.updateById(e);
            }
            case "DEL" -> {
                Long rowId = parseLongOrNull(r.getRowKey());
                if (rowId != null) {
                    familyMapper.deleteById(rowId);
                }
            }
            default -> log.warn("未知变更操作，跳过 rowKey={} op={}", r.getRowKey(), r.getOp());
        }
    }

    /** 工资卡唯一：本行被设为工资卡时，清掉该员工其它卡的工资卡标记 */
    private void clearOtherPrimaryBank(Long empId, EmployeeBank current) {
        if (!Integer.valueOf(1).equals(current.getIsPrimary())) {
            return;
        }
        bankMapper.update(null, new LambdaUpdateWrapper<EmployeeBank>()
                .eq(EmployeeBank::getEmployeeId, empId)
                .ne(EmployeeBank::getId, current.getId())
                .eq(EmployeeBank::getIsPrimary, 1)
                .set(EmployeeBank::getIsPrimary, 0));
    }

    private void fillBank(EmployeeBank e, Map<String, Object> m) {
        e.setBank(str(m, "bank"));
        e.setAccountNo(str(m, "accountNo"));
        e.setHolderName(str(m, "holderName"));
        e.setBranch(str(m, "branch"));
        e.setIsPrimary(num(m.get("isPrimary")) == 1 ? 1 : 0);
        e.setRemark(str(m, "remark"));
    }

    private void fillEducation(EmployeeEducation e, Map<String, Object> m) {
        e.setSchool(str(m, "school"));
        e.setMajor(str(m, "major"));
        e.setDegree(str(m, "degree"));
        e.setStartDate(parseDate(str(m, "startDate")));
        e.setEndDate(parseDate(str(m, "endDate")));
        e.setRemark(str(m, "remark"));
    }

    private void fillWork(EmployeeWork e, Map<String, Object> m) {
        e.setCompany(str(m, "company"));
        e.setPosition(str(m, "position"));
        e.setStartDate(parseDate(str(m, "startDate")));
        e.setEndDate(parseDate(str(m, "endDate")));
        e.setLeaveReason(str(m, "leaveReason"));
        e.setRemark(str(m, "remark"));
    }

    private void fillFamily(EmployeeFamily e, Map<String, Object> m) {
        e.setRelation(str(m, "relation"));
        e.setName(str(m, "name"));
        e.setCompany(str(m, "company"));
        e.setPhone(str(m, "phone"));
        e.setRemark(str(m, "remark"));
    }

    /* ---------------- 差异比对 ---------------- */

    /** 基本信息：7 个可变字段逐一与现状比对（trim 后 null 视为空串），顺序与 BASE_LABELS 一致 */
    private List<FieldDiff> collectBaseDiffs(Employee emp, EmployeeChangeRequest req) {
        List<FieldDiff> out = new ArrayList<>();
        addIfChanged(out, "maritalStatus", emp.getMaritalStatus(), req.getMaritalStatus());
        addIfChanged(out, "politicalStatus", emp.getPoliticalStatus(), req.getPoliticalStatus());
        addIfChanged(out, "education", emp.getEducation(), req.getEducation());
        addIfChanged(out, "currentAddress", emp.getCurrentAddress(), req.getCurrentAddress());
        addIfChanged(out, "mobile", emp.getMobile(), req.getMobile());
        addIfChanged(out, "emergencyContact", emp.getEmergencyContact(), req.getEmergencyContact());
        addIfChanged(out, "emergencyPhone", emp.getEmergencyPhone(), req.getEmergencyPhone());
        return out;
    }

    private void addIfChanged(List<FieldDiff> out, String key, String oldV, String newV) {
        String o = oldV == null ? "" : oldV.trim();
        String n = newV == null ? "" : newV.trim();
        if (!o.equals(n)) {
            out.add(new FieldDiff(key, BASE_LABELS.get(key), o, n));
        }
    }

    private ListDiff diffBanks(Employee emp, List<EmployeeChangeRequest.BankRow> submitted) {
        if (submitted == null) {
            return ListDiff.untouched("BANK", "银行卡", BANK_COLUMNS);
        }
        ListDiff diff = new ListDiff("BANK", "银行卡", BANK_COLUMNS);
        List<EmployeeBank> current = bankMapper.selectList(new LambdaQueryWrapper<EmployeeBank>()
                .eq(EmployeeBank::getEmployeeId, emp.getId())
                .orderByDesc(EmployeeBank::getIsPrimary).orderByAsc(EmployeeBank::getId));
        Map<Long, EmployeeBank> curById = current.stream()
                .collect(Collectors.toMap(EmployeeBank::getId, x -> x));
        Set<Long> submittedIds = new HashSet<>();
        int primaryCount = 0;

        // 已有行（MOD / 未变）与新增行（ADD）按提交顺序先过一遍
        List<Map<String, Object>> addRows = new ArrayList<>();
        Map<Long, RowDiff> modByKey = new HashMap<>();
        for (EmployeeChangeRequest.BankRow s : submitted) {
            requireBizValue("bank", s.getBank(), "银行");
            if (Integer.valueOf(1).equals(s.getIsPrimary())) {
                primaryCount++;
            }
            Map<String, Object> neo = bankDisplay(s);
            if (s.getId() == null) {
                addRows.add(neo);
                continue;
            }
            EmployeeBank cur = curById.get(s.getId());
            if (cur == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "银行卡信息已被他人变更，请刷新档案后重试");
            }
            submittedIds.add(s.getId());
            Map<String, Object> old = bankDisplay(cur);
            List<String> changed = changedKeys(old, neo, "bank", "accountNo", "holderName", "branch", "isPrimary", "remark");
            if (!changed.isEmpty()) {
                modByKey.put(cur.getId(), new RowDiff("BANK", String.valueOf(cur.getId()), "MOD", old, neo, changed));
            }
        }
        if (primaryCount > 1) {
            throw new BizException(EhrErrorCode.EMP_PROFILE_INVALID, "工资卡只能有一张");
        }

        // 页面行：按档案展示顺序摆（MOD 带新值与变化字段，DEL 整行删除线，未变的原样），新增行排最后
        int seq = 0;
        for (EmployeeBank cur : current) {
            Map<String, Object> old = bankDisplay(cur);
            if (!submittedIds.contains(cur.getId())) {
                diff.pageRows.add(pageRow("DEL", old, null, null));
                diff.changes.add(new RowDiff("BANK", String.valueOf(cur.getId()), "DEL", old, null, null, seq++));
            } else {
                RowDiff mod = modByKey.get(cur.getId());
                if (mod != null) {
                    mod.seq = seq++;
                    diff.pageRows.add(pageRow("MOD", old, mod.newRow, mod.changedKeys));
                    diff.changes.add(mod);
                } else {
                    diff.pageRows.add(pageRow("NONE", old, null, null));
                }
            }
        }
        for (Map<String, Object> neo : addRows) {
            diff.pageRows.add(pageRow("ADD", neo, null, null));
            diff.changes.add(new RowDiff("BANK", "", "ADD", null, neo, null, seq++));
        }
        return diff;
    }

    private ListDiff diffEducations(Employee emp, List<EmployeeChangeRequest.EducationRow> submitted) {
        if (submitted == null) {
            return ListDiff.untouched("EDUCATION", "教育背景", EDUCATION_COLUMNS);
        }
        ListDiff diff = new ListDiff("EDUCATION", "教育背景", EDUCATION_COLUMNS);
        List<EmployeeEducation> current = educationMapper.selectList(new LambdaQueryWrapper<EmployeeEducation>()
                .eq(EmployeeEducation::getEmployeeId, emp.getId())
                .orderByDesc(EmployeeEducation::getStartDate));
        Map<Long, EmployeeEducation> curById = current.stream()
                .collect(Collectors.toMap(EmployeeEducation::getId, x -> x));
        Set<Long> submittedIds = new HashSet<>();

        List<Map<String, Object>> addRows = new ArrayList<>();
        Map<Long, RowDiff> modByKey = new HashMap<>();
        for (EmployeeChangeRequest.EducationRow s : submitted) {
            requireBizValue("education", s.getDegree(), "学历");
            checkRange(parseDate(s.getStartDate()), parseDate(s.getEndDate()), "在校时间");
            Map<String, Object> neo = educationDisplay(s);
            if (s.getId() == null) {
                addRows.add(neo);
                continue;
            }
            EmployeeEducation cur = curById.get(s.getId());
            if (cur == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "教育背景已被他人变更，请刷新档案后重试");
            }
            submittedIds.add(s.getId());
            Map<String, Object> old = educationDisplay(cur);
            List<String> changed = changedKeys(old, neo, "school", "major", "degree", "startDate", "endDate", "remark");
            if (!changed.isEmpty()) {
                modByKey.put(cur.getId(), new RowDiff("EDUCATION", String.valueOf(cur.getId()), "MOD", old, neo, changed));
            }
        }

        int seq = 0;
        for (EmployeeEducation cur : current) {
            Map<String, Object> old = educationDisplay(cur);
            if (!submittedIds.contains(cur.getId())) {
                diff.pageRows.add(pageRow("DEL", old, null, null));
                diff.changes.add(new RowDiff("EDUCATION", String.valueOf(cur.getId()), "DEL", old, null, null, seq++));
            } else {
                RowDiff mod = modByKey.get(cur.getId());
                if (mod != null) {
                    mod.seq = seq++;
                    diff.pageRows.add(pageRow("MOD", old, mod.newRow, mod.changedKeys));
                    diff.changes.add(mod);
                } else {
                    diff.pageRows.add(pageRow("NONE", old, null, null));
                }
            }
        }
        for (Map<String, Object> neo : addRows) {
            diff.pageRows.add(pageRow("ADD", neo, null, null));
            diff.changes.add(new RowDiff("EDUCATION", "", "ADD", null, neo, null, seq++));
        }
        return diff;
    }

    private ListDiff diffWorks(Employee emp, List<EmployeeChangeRequest.WorkRow> submitted) {
        if (submitted == null) {
            return ListDiff.untouched("WORK", "工作经历", WORK_COLUMNS);
        }
        ListDiff diff = new ListDiff("WORK", "工作经历", WORK_COLUMNS);
        List<EmployeeWork> current = workMapper.selectList(new LambdaQueryWrapper<EmployeeWork>()
                .eq(EmployeeWork::getEmployeeId, emp.getId())
                .orderByDesc(EmployeeWork::getStartDate));
        Map<Long, EmployeeWork> curById = current.stream()
                .collect(Collectors.toMap(EmployeeWork::getId, x -> x));
        Set<Long> submittedIds = new HashSet<>();

        List<Map<String, Object>> addRows = new ArrayList<>();
        Map<Long, RowDiff> modByKey = new HashMap<>();
        for (EmployeeChangeRequest.WorkRow s : submitted) {
            checkRange(parseDate(s.getStartDate()), parseDate(s.getEndDate()), "在职时间");
            Map<String, Object> neo = workDisplay(s);
            if (s.getId() == null) {
                addRows.add(neo);
                continue;
            }
            EmployeeWork cur = curById.get(s.getId());
            if (cur == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "工作经历已被他人变更，请刷新档案后重试");
            }
            submittedIds.add(s.getId());
            Map<String, Object> old = workDisplay(cur);
            List<String> changed = changedKeys(old, neo, "company", "position", "startDate", "endDate", "leaveReason", "remark");
            if (!changed.isEmpty()) {
                modByKey.put(cur.getId(), new RowDiff("WORK", String.valueOf(cur.getId()), "MOD", old, neo, changed));
            }
        }

        int seq = 0;
        for (EmployeeWork cur : current) {
            Map<String, Object> old = workDisplay(cur);
            if (!submittedIds.contains(cur.getId())) {
                diff.pageRows.add(pageRow("DEL", old, null, null));
                diff.changes.add(new RowDiff("WORK", String.valueOf(cur.getId()), "DEL", old, null, null, seq++));
            } else {
                RowDiff mod = modByKey.get(cur.getId());
                if (mod != null) {
                    mod.seq = seq++;
                    diff.pageRows.add(pageRow("MOD", old, mod.newRow, mod.changedKeys));
                    diff.changes.add(mod);
                } else {
                    diff.pageRows.add(pageRow("NONE", old, null, null));
                }
            }
        }
        for (Map<String, Object> neo : addRows) {
            diff.pageRows.add(pageRow("ADD", neo, null, null));
            diff.changes.add(new RowDiff("WORK", "", "ADD", null, neo, null, seq++));
        }
        return diff;
    }

    private ListDiff diffFamilies(Employee emp, List<EmployeeChangeRequest.FamilyRow> submitted) {
        if (submitted == null) {
            return ListDiff.untouched("FAMILY", "家庭成员", FAMILY_COLUMNS);
        }
        ListDiff diff = new ListDiff("FAMILY", "家庭成员", FAMILY_COLUMNS);
        List<EmployeeFamily> current = familyMapper.selectList(new LambdaQueryWrapper<EmployeeFamily>()
                .eq(EmployeeFamily::getEmployeeId, emp.getId())
                .orderByAsc(EmployeeFamily::getId));
        Map<Long, EmployeeFamily> curById = current.stream()
                .collect(Collectors.toMap(EmployeeFamily::getId, x -> x));
        Set<Long> submittedIds = new HashSet<>();

        List<Map<String, Object>> addRows = new ArrayList<>();
        Map<Long, RowDiff> modByKey = new HashMap<>();
        for (EmployeeChangeRequest.FamilyRow s : submitted) {
            requireBizValue("family_relation", s.getRelation(), "关系");
            Map<String, Object> neo = familyDisplay(s);
            if (s.getId() == null) {
                addRows.add(neo);
                continue;
            }
            EmployeeFamily cur = curById.get(s.getId());
            if (cur == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "家庭成员信息已被他人变更，请刷新档案后重试");
            }
            submittedIds.add(s.getId());
            Map<String, Object> old = familyDisplay(cur);
            List<String> changed = changedKeys(old, neo, "relation", "name", "company", "phone", "remark");
            if (!changed.isEmpty()) {
                modByKey.put(cur.getId(), new RowDiff("FAMILY", String.valueOf(cur.getId()), "MOD", old, neo, changed));
            }
        }

        int seq = 0;
        for (EmployeeFamily cur : current) {
            Map<String, Object> old = familyDisplay(cur);
            if (!submittedIds.contains(cur.getId())) {
                diff.pageRows.add(pageRow("DEL", old, null, null));
                diff.changes.add(new RowDiff("FAMILY", String.valueOf(cur.getId()), "DEL", old, null, null, seq++));
            } else {
                RowDiff mod = modByKey.get(cur.getId());
                if (mod != null) {
                    mod.seq = seq++;
                    diff.pageRows.add(pageRow("MOD", old, mod.newRow, mod.changedKeys));
                    diff.changes.add(mod);
                } else {
                    diff.pageRows.add(pageRow("NONE", old, null, null));
                }
            }
        }
        for (Map<String, Object> neo : addRows) {
            diff.pageRows.add(pageRow("ADD", neo, null, null));
            diff.changes.add(new RowDiff("FAMILY", "", "ADD", null, neo, null, seq++));
        }
        return diff;
    }

    /** 逐键比对行快照（值统一按字符串比较；isPrimary 这类数字先归一化成字符串） */
    private List<String> changedKeys(Map<String, Object> oldRow, Map<String, Object> newRow, String... keys) {
        List<String> out = new ArrayList<>();
        for (String k : keys) {
            if (!strVal(oldRow.get(k)).equals(strVal(newRow.get(k)))) {
                out.add(k);
            }
        }
        return out;
    }

    /* ---------------- 行快照（含字典展示名，对比页直接渲染不用再查字典） ---------------- */

    private Map<String, Object> bankDisplay(EmployeeBank e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bank", nvl(e.getBank()));
        m.put("bankName", bizLabel("bank", e.getBank()));
        m.put("accountNo", nvl(e.getAccountNo()));
        m.put("holderName", nvl(e.getHolderName()));
        m.put("branch", nvl(e.getBranch()));
        m.put("isPrimary", e.getIsPrimary() == null ? 0 : e.getIsPrimary());
        m.put("remark", nvl(e.getRemark()));
        return m;
    }

    private Map<String, Object> bankDisplay(EmployeeChangeRequest.BankRow s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bank", nvl(s.getBank()));
        m.put("bankName", bizLabel("bank", s.getBank()));
        m.put("accountNo", nvl(s.getAccountNo()));
        m.put("holderName", nvl(s.getHolderName()));
        m.put("branch", nvl(s.getBranch()));
        m.put("isPrimary", Integer.valueOf(1).equals(s.getIsPrimary()) ? 1 : 0);
        m.put("remark", nvl(s.getRemark()));
        return m;
    }

    private Map<String, Object> educationDisplay(EmployeeEducation e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("school", nvl(e.getSchool()));
        m.put("major", nvl(e.getMajor()));
        m.put("degree", nvl(e.getDegree()));
        m.put("degreeName", bizLabel("education", e.getDegree()));
        m.put("startDate", dateStr(e.getStartDate()));
        m.put("endDate", dateStr(e.getEndDate()));
        m.put("remark", nvl(e.getRemark()));
        return m;
    }

    private Map<String, Object> educationDisplay(EmployeeChangeRequest.EducationRow s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("school", nvl(s.getSchool()));
        m.put("major", nvl(s.getMajor()));
        m.put("degree", nvl(s.getDegree()));
        m.put("degreeName", bizLabel("education", s.getDegree()));
        m.put("startDate", nvl(s.getStartDate()));
        m.put("endDate", nvl(s.getEndDate()));
        m.put("remark", nvl(s.getRemark()));
        return m;
    }

    private Map<String, Object> workDisplay(EmployeeWork e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("company", nvl(e.getCompany()));
        m.put("position", nvl(e.getPosition()));
        m.put("startDate", dateStr(e.getStartDate()));
        m.put("endDate", dateStr(e.getEndDate()));
        m.put("leaveReason", nvl(e.getLeaveReason()));
        m.put("remark", nvl(e.getRemark()));
        return m;
    }

    private Map<String, Object> workDisplay(EmployeeChangeRequest.WorkRow s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("company", nvl(s.getCompany()));
        m.put("position", nvl(s.getPosition()));
        m.put("startDate", nvl(s.getStartDate()));
        m.put("endDate", nvl(s.getEndDate()));
        m.put("leaveReason", nvl(s.getLeaveReason()));
        m.put("remark", nvl(s.getRemark()));
        return m;
    }

    private Map<String, Object> familyDisplay(EmployeeFamily e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("relation", nvl(e.getRelation()));
        m.put("relationName", bizLabel("family_relation", e.getRelation()));
        m.put("name", nvl(e.getName()));
        m.put("company", nvl(e.getCompany()));
        m.put("phone", nvl(e.getPhone()));
        m.put("remark", nvl(e.getRemark()));
        return m;
    }

    private Map<String, Object> familyDisplay(EmployeeChangeRequest.FamilyRow s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("relation", nvl(s.getRelation()));
        m.put("relationName", bizLabel("family_relation", s.getRelation()));
        m.put("name", nvl(s.getName()));
        m.put("company", nvl(s.getCompany()));
        m.put("phone", nvl(s.getPhone()));
        m.put("remark", nvl(s.getRemark()));
        return m;
    }

    /* ---------------- 审批快照：changeSummary 给列表看，change 给 ROUTE 对比页看 ---------------- */

    private Map<String, Object> buildFormData(Employee emp, EmpChange master,
                                              List<FieldDiff> baseDiffs, List<ListDiff> lists) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("employeeId", String.valueOf(emp.getId()));
        form.put("empNo", nvl(emp.getEmpNo()));
        form.put("empName", nvl(emp.getName()));
        form.put("orgName", nvl(master.getOrgName()));
        form.put("changeId", String.valueOf(master.getId()));
        form.put("changeSummary", buildSummary(baseDiffs, lists));

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("emp", empSnapshot(emp, master));
        change.put("base", baseSnapshot(emp, baseDiffs));
        List<Map<String, Object>> listPayloads = new ArrayList<>();
        for (ListDiff l : lists) {
            if (l.touched) {
                listPayloads.add(l.payload());
            }
        }
        change.put("lists", listPayloads);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("baseChanges", master.getBaseChanges());
        stats.put("addCount", master.getAddCount());
        stats.put("modCount", master.getModCount());
        stats.put("delCount", master.getDelCount());
        change.put("stats", stats);
        form.put("change", change);
        return form;
    }

    /** 头部信息：对比页页头（工号/姓名/组织/状态/账号）与审批表单区都用它 */
    private Map<String, Object> empSnapshot(Employee emp, EmpChange master) {
        Map<String, Object> m = new LinkedHashMap<>();
        EmployeeStatus st = EmployeeStatus.of(emp.getStatus());
        m.put("empNo", nvl(emp.getEmpNo()));
        m.put("name", nvl(emp.getName()));
        m.put("orgName", nvl(master.getOrgName()));
        m.put("statusName", st == null ? "" : st.getLabel());
        m.put("accountUsername", nvl(emp.getAccountUsername()));
        m.put("hasAccount", emp.getAccountId() != null);
        return m;
    }

    /**
     * 基本信息全量快照：key → {label, value, changed, newValue}。
     * 对比页按固定栅格（与变更表单一致的顺序）渲染：没变的画 value，变了的画「旧值删除线 + 新值红色」。
     */
    private Map<String, Object> baseSnapshot(Employee emp, List<FieldDiff> baseDiffs) {
        Map<String, String> newByKey = baseDiffs.stream()
                .collect(Collectors.toMap(d -> d.key, d -> d.newValue));
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("empNo", cell("工号", nvl(emp.getEmpNo()), null));
        base.put("name", cell("姓名", nvl(emp.getName()), null));
        base.put("gender", cell("性别", genderName(emp.getGender()), null));
        base.put("birthday", cellWithSuffix("出生日期", dateStr(emp.getBirthday()),
                emp.getBirthday() == null ? "" : Period.between(emp.getBirthday(), LocalDate.now()).getYears() + " 岁"));
        base.put("nation", cell("民族", nvl(emp.getNation()), null));
        base.put("nativePlace", cell("籍贯", nvl(emp.getNativePlace()), null));
        base.put("idCard", cell("身份证号", nvl(emp.getIdCard()), null));
        base.put("maritalStatus", cell("婚姻状况", nvl(emp.getMaritalStatus()), newByKey.get("maritalStatus")));
        base.put("politicalStatus", cell("政治面貌", nvl(emp.getPoliticalStatus()), newByKey.get("politicalStatus")));
        base.put("education", cell("最高学历", nvl(emp.getEducation()), newByKey.get("education")));
        base.put("position", cell("职位", nvl(emp.getPosition()), null));
        base.put("hireDate", cell("入职日期", dateStr(emp.getHireDate()), null));
        base.put("probation", cell("试用期", probationText(emp), null));
        base.put("orgName", cell("所属组织", orgPathOf(emp.getOrgId()), null));
        base.put("currentAddress", cell("现居住址", nvl(emp.getCurrentAddress()), newByKey.get("currentAddress")));
        base.put("mobile", cell("手机号", nvl(emp.getMobile()), newByKey.get("mobile")));
        base.put("email", cell("邮箱", nvl(emp.getEmail()), null));
        base.put("emergencyContact", cell("紧急联系人", nvl(emp.getEmergencyContact()), newByKey.get("emergencyContact")));
        base.put("emergencyPhone", cell("紧急联系电话", nvl(emp.getEmergencyPhone()), newByKey.get("emergencyPhone")));
        Map<String, Object> account = cell("登录账号", nvl(emp.getAccountUsername()), null);
        if (emp.getAccountId() != null) {
            account.put("tag", "已开户");
        }
        base.put("account", account);
        base.put("remark", cell("备注", nvl(emp.getRemark()), null));
        return base;
    }

    private Map<String, Object> cell(String label, String value, String newValue) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        m.put("changed", newValue != null);
        if (newValue != null) {
            m.put("newValue", newValue);
        }
        return m;
    }

    private Map<String, Object> cellWithSuffix(String label, String value, String suffix) {
        Map<String, Object> m = cell(label, value, null);
        m.put("suffix", suffix);
        return m;
    }

    private String buildSummary(List<FieldDiff> baseDiffs, List<ListDiff> lists) {
        List<String> parts = new ArrayList<>();
        if (!baseDiffs.isEmpty()) {
            parts.add("基本信息：" + baseDiffs.stream().map(d -> d.label).collect(Collectors.joining("、")));
        }
        for (ListDiff l : lists) {
            List<String> ops = new ArrayList<>();
            int mod = 0;
            int add = 0;
            int del = 0;
            for (RowDiff d : l.changes) {
                switch (d.op) {
                    case "MOD" -> mod++;
                    case "ADD" -> add++;
                    case "DEL" -> del++;
                    default -> { }
                }
            }
            if (mod > 0) {
                ops.add("修改 " + mod + " 行");
            }
            if (add > 0) {
                ops.add("新增 " + add + " 行");
            }
            if (del > 0) {
                ops.add("删除 " + del + " 行");
            }
            if (!ops.isEmpty()) {
                parts.add(l.label + "：" + String.join("、", ops));
            }
        }
        return String.join("；", parts);
    }

    /**
     * 纯构造「人员信息变更」发起请求。applicantOrgId 传<b>员工所在部门</b>而不是操作人的部门：
     * 模板第一步「所在部门负责人」按发起人组织解析，语义上要的是「这个员工所在部门的负责人」。
     */
    private ApprovalStartDTO buildStart(Employee emp, EmpChange master, Map<String, Object> formData) {
        SecurityUser user = SecurityUtils.currentUser().orElse(null);
        ApprovalStartDTO dto = new ApprovalStartDTO();
        dto.setBizType(BIZ_EMP_UPDATE);
        dto.setBizId(String.valueOf(emp.getId()));
        dto.setBizNo(nvl(emp.getEmpNo()));
        dto.setTitle("人员信息变更 · " + emp.getName());
        dto.setApplicantId(user == null ? null : user.getAccountId());
        dto.setApplicantName(user == null ? "系统" : approverName(user));
        dto.setApplicantOrgId(emp.getOrgId());
        dto.setOrgPath(master.getOrgName());
        dto.setFormData(formData);
        dto.setCallbackUrl(selfBaseUrl + CALLBACK_PATH);
        return dto;
    }

    /* ---------------- 内部工具 ---------------- */

    private Employee require(Long id) {
        Employee emp = employeeMapper.selectById(id);
        if (emp == null) {
            throw new BizException(EhrErrorCode.EMP_NOT_FOUND);
        }
        return emp;
    }

    /** 同一员工同时只允许一张在途审批单，避免通用指针被覆盖、列表显示错乱 */
    private void assertNoRunningApproval(Employee emp) {
        if (approvalLauncher.hasRunning(String.valueOf(emp.getId()), null)) {
            throw new BizException(EhrErrorCode.APPROVAL_DUPLICATED, "该员工已有在审批中的单据，请先到审批中心处理完再提交");
        }
    }

    /**
     * 终审回调处理完（通过/驳回/撤回）后清掉在途标记。
     * 必须用 UpdateWrapper 显式 set null：updateById 默认跳过 null 字段，清不掉列。
     */
    private void clearApproval(Long empId) {
        employeeMapper.update(null, new LambdaUpdateWrapper<Employee>()
                .eq(Employee::getId, empId)
                .set(Employee::getApprovalInstanceId, null)
                .set(Employee::getApprovalBizType, null));
    }

    private void assertMobileUnique(Long empId, String mobile) {
        Employee dup = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getMobile, mobile).ne(Employee::getId, empId).last("limit 1"));
        if (dup != null) {
            throw new BizException(EhrErrorCode.EMP_MOBILE_DUPLICATED,
                    "手机号已被「" + dup.getName() + "」占用，信息变更无法落库");
        }
    }

    private void assertPhoneFormat(String phone, String label) {
        if (!phone.isEmpty() && !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(ErrorCode.PARAM_ERROR, label + "格式不正确");
        }
    }

    /** 字典值校验：主数据拉不到（配置中心故障）时放行，不能让配置中心故障卡死人事录入 */
    private void requireBizValue(String type, String value, String name) {
        if (value == null || value.isBlank()) {
            return;
        }
        Map<String, String> biz = settingsClient.bizMap(type);
        if (!biz.isEmpty() && !biz.containsKey(value.trim())) {
            throw new BizException(EhrErrorCode.EMP_PROFILE_INVALID,
                    name + "「" + value + "」不在配置中心的主数据里");
        }
    }

    private String bizLabel(String type, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String name = settingsClient.bizMap(type).get(value.trim());
        return name == null ? value.trim() : name;
    }

    private void checkRange(LocalDate start, LocalDate end, String name) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BizException(EhrErrorCode.EMP_PROFILE_INVALID, name + "结束日期不能早于开始日期");
        }
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

    private String dateStr(LocalDate d) {
        return d == null ? "" : d.toString();
    }

    private String genderName(Integer gender) {
        if (gender == null) {
            return "";
        }
        return switch (gender) {
            case 1 -> "男";
            case 2 -> "女";
            default -> "未知";
        };
    }

    private String probationText(Employee emp) {
        if (Boolean.TRUE.equals(emp.getExemptProbation())) {
            return "免试用期";
        }
        if (emp.getProbationMonths() == null) {
            return "";
        }
        String text = emp.getProbationMonths() + " 个月";
        if (emp.getExpectedRegularizeDate() != null) {
            text += "，预计 " + dateStr(emp.getExpectedRegularizeDate()) + " 转正";
        }
        return text;
    }

    private String orgPathOf(Long orgId) {
        if (orgId == null) {
            return "";
        }
        Map<Long, OrgNode> orgs = new HashMap<>();
        for (OrgNode n : orgMapper.selectList(new LambdaQueryWrapper<OrgNode>())) {
            orgs.put(n.getId(), n);
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
                    OrgNode n = orgs.get(Long.valueOf(part));
                    if (n != null) {
                        names.add(n.getName());
                    }
                } catch (NumberFormatException ignored) {
                    // 脏 path 跳过
                }
            }
        }
        if (names.isEmpty()) {
            names.add(node.getName());
        }
        return String.join(" / ", names);
    }

    /** 发起人显示名：优先员工档案姓名（操作人多半是 HR），其次账号名 */
    private String approverName(SecurityUser user) {
        Employee me = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, user.getAccountId()).last("limit 1"));
        return me == null ? user.getUsername() : me.getName();
    }

    private EmployeeBank findBank(String rowKey) {
        Long id = parseLongOrNull(rowKey);
        return id == null ? null : bankMapper.selectById(id);
    }

    private EmployeeEducation findEducation(String rowKey) {
        Long id = parseLongOrNull(rowKey);
        return id == null ? null : educationMapper.selectById(id);
    }

    private EmployeeWork findWork(String rowKey) {
        Long id = parseLongOrNull(rowKey);
        return id == null ? null : workMapper.selectById(id);
    }

    private EmployeeFamily findFamily(String rowKey) {
        Long id = parseLongOrNull(rowKey);
        return id == null ? null : familyMapper.selectById(id);
    }

    /** result=3 撤销 / 4 终止 → 算「已撤销」，其余未通过都算「已驳回」 */
    private boolean isWithdrawn(ApprovalCallbackRequest req) {
        return req.getResult() != null && (req.getResult() == 3 || req.getResult() == 4);
    }

    private String toJson(Object value) {
        try {
            return OM.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "变更快照序列化失败");
        }
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return OM.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "变更快照解析失败");
        }
    }

    private static List<Map<String, String>> columns(String[][] defs) {
        List<Map<String, String>> out = new ArrayList<>();
        for (String[] d : defs) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("key", d[0]);
            m.put("label", d[1]);
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> pageRow(String op, Map<String, Object> row,
                                        Map<String, Object> newRow, List<String> changedKeys) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("op", op);
        m.put("row", row);
        if (newRow != null) {
            m.put("newRow", newRow);
        }
        if (changedKeys != null && !changedKeys.isEmpty()) {
            m.put("changedKeys", changedKeys);
        }
        return m;
    }

    private int countOp(List<RowDiff> diffs, String op) {
        int n = 0;
        for (RowDiff d : diffs) {
            if (op.equals(d.op)) {
                n++;
            }
        }
        return n;
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private int num(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    private String strVal(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private String nvl(String s) {
        return s == null ? "" : s.trim();
    }

    private Long parseLongOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 基本信息字段差异（key 与 ehr_employee 列对应） */
    private static final class FieldDiff {
        final String key;
        final String label;
        final String oldValue;
        final String newValue;

        FieldDiff(String key, String label, String oldValue, String newValue) {
            this.key = key;
            this.label = label;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }

    /** 子表行差异：ADD/DEL/MOD + 行快照（含字典展示名） */
    private static final class RowDiff {
        final String listType;
        final String rowKey;
        final String op;
        final Map<String, Object> oldRow;
        final Map<String, Object> newRow;
        final List<String> changedKeys;
        int seq;

        RowDiff(String listType, String rowKey, String op,
                Map<String, Object> oldRow, Map<String, Object> newRow, List<String> changedKeys) {
            this(listType, rowKey, op, oldRow, newRow, changedKeys, 0);
        }

        RowDiff(String listType, String rowKey, String op,
                Map<String, Object> oldRow, Map<String, Object> newRow, List<String> changedKeys, int seq) {
            this.listType = listType;
            this.rowKey = rowKey;
            this.op = op;
            this.oldRow = oldRow;
            this.newRow = newRow;
            this.changedKeys = changedKeys;
            this.seq = seq;
        }
    }

    /**
     * 一类子表的比对结果：changes 落变更单（只含变化行）；
     * pageRows 是给对比页的全量行（含未变的 NONE 行，新增行排最后）。
     */
    private static final class ListDiff {
        final String type;
        final String label;
        final List<Map<String, String>> columns;
        final List<RowDiff> changes = new ArrayList<>();
        final List<Map<String, Object>> pageRows = new ArrayList<>();
        /** false=提交方没传这个子表（本次不涉及），不进快照也不落变更单 */
        boolean touched = true;

        ListDiff(String type, String label, List<Map<String, String>> columns) {
            this.type = type;
            this.label = label;
            this.columns = columns;
        }

        static ListDiff untouched(String type, String label, List<Map<String, String>> columns) {
            ListDiff d = new ListDiff(type, label, columns);
            d.touched = false;
            return d;
        }

        Map<String, Object> payload() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", type);
            m.put("label", label);
            m.put("columns", columns);
            m.put("rows", pageRows);
            return m;
        }
    }
}
