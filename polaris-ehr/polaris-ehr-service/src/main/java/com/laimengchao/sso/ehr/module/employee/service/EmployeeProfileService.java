package com.laimengchao.sso.ehr.module.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.utils.SensitiveMasker;
import com.laimengchao.sso.ehr.module.employee.constant.EmployeeStatus;
import com.laimengchao.sso.settings.client.support.SettingsClient;
import com.laimengchao.sso.ehr.module.employee.dto.EmployeeProfileDtos.BankRequest;
import com.laimengchao.sso.ehr.module.employee.dto.EmployeeProfileDtos.EducationRequest;
import com.laimengchao.sso.ehr.module.employee.dto.EmployeeProfileDtos.FamilyRequest;
import com.laimengchao.sso.ehr.module.employee.dto.EmployeeProfileDtos.WorkRequest;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeBank;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeEducation;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeFamily;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeWork;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeBankMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeEducationMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeFamilyMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeWorkMapper;
import com.laimengchao.sso.ehr.module.employee.vo.EmployeeProfileVO;
import com.laimengchao.sso.ehr.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 人员子档案：银行卡 / 教育背景 / 工作经历 / 家庭成员。
 * <p>
 * 四张小表统一放一个服务（各自开服务太散）。规则只有三条：
 * 1. 主档案必须存在；2. 字典型字段必须在配置中心字典内（字典拉不到时放行，配置中心故障不卡死录入）；
 * 3. 起止日期区间合法；工资卡每张档案至多一张（新工资卡自动顶掉旧的）。
 */
@Service
@RequiredArgsConstructor
public class EmployeeProfileService {

    private final EmployeeMapper employeeMapper;
    private final EmployeeBankMapper bankMapper;
    private final EmployeeEducationMapper educationMapper;
    private final EmployeeWorkMapper workMapper;
    private final EmployeeFamilyMapper familyMapper;
    private final SettingsClient settingsClient;
    /** 主档案 VO 组装（组织路径/字典名翻译）复用 EmployeeService，避免两处各写一份 */
    private final EmployeeService employeeService;
    /** 银行卡号按敏感权限掩码 */
    private final DataScopeService dataScope;

    /* ---------------- 聚合查询 ---------------- */

    public EmployeeProfileVO profile(Long employeeId) {
        requireEmployeeExists(employeeId);
        EmployeeProfileVO vo = new EmployeeProfileVO();
        vo.setEmployee(employeeService.detail(employeeId));
        vo.setBanks(bankMapper.selectList(bankW(employeeId)).stream()
                .map(this::toVO).collect(Collectors.toList()));
        vo.setEducations(educationMapper.selectList(educationW(employeeId)).stream()
                .map(this::toVO).collect(Collectors.toList()));
        vo.setWorks(workMapper.selectList(workW(employeeId)).stream()
                .map(this::toVO).collect(Collectors.toList()));
        vo.setFamilies(familyMapper.selectList(familyW(employeeId)).stream()
                .map(this::toVO).collect(Collectors.toList()));
        return vo;
    }

    /* ---------------- 银行卡 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    public EmployeeProfileVO.BankVO addBank(Long employeeId, BankRequest req) {
        requireWritable(employeeId);
        requireBizValue("bank", req.getBank(), "银行");
        EmployeeBank row = new EmployeeBank();
        row.setEmployeeId(employeeId);
        applyBank(row, req);
        if (Integer.valueOf(1).equals(row.getIsPrimary())) {
            clearPrimaryBank(employeeId, null);
        } else {
            row.setIsPrimary(0);
        }
        bankMapper.insert(row);
        return toVO(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public EmployeeProfileVO.BankVO updateBank(Long employeeId, Long id, BankRequest req) {
        EmployeeBank row = requireBank(employeeId, id);
        requireBizValue("bank", req.getBank(), "银行");
        applyBank(row, req);
        if (Integer.valueOf(1).equals(row.getIsPrimary())) {
            clearPrimaryBank(employeeId, id);
        } else {
            row.setIsPrimary(0);
        }
        bankMapper.updateById(row);
        return toVO(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteBank(Long employeeId, Long id) {
        requireBank(employeeId, id);
        bankMapper.deleteById(id);
    }

    private void applyBank(EmployeeBank row, BankRequest req) {
        row.setBank(req.getBank().trim());
        row.setAccountNo(req.getAccountNo().trim());
        row.setHolderName(req.getHolderName().trim());
        row.setBranch(nvl(req.getBranch()));
        row.setIsPrimary(Integer.valueOf(1).equals(req.getIsPrimary()) ? 1 : 0);
        row.setRemark(nvl(req.getRemark()));
    }

    /** 同一档案下只有一张工资卡：新工资卡落库/更新前先把其它卡降级 */
    private void clearPrimaryBank(Long employeeId, Long excludeId) {
        bankMapper.update(null, new LambdaUpdateWrapper<EmployeeBank>()
                .eq(EmployeeBank::getEmployeeId, employeeId)
                .ne(EmployeeBank::getId, excludeId == null ? -1L : excludeId)
                .set(EmployeeBank::getIsPrimary, 0));
    }

    private EmployeeBank requireBank(Long employeeId, Long id) {
        EmployeeBank row = bankMapper.selectById(id);
        if (row == null || !row.getEmployeeId().equals(employeeId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "银行卡不存在");
        }
        requireAccessible(employeeId);
        return row;
    }

    /* ---------------- 教育背景 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    public EmployeeProfileVO.EducationVO addEducation(Long employeeId, EducationRequest req) {
        requireWritable(employeeId);
        requireBizValue("education", req.getDegree(), "学历");
        EmployeeEducation row = new EmployeeEducation();
        row.setEmployeeId(employeeId);
        applyEducation(row, req);
        educationMapper.insert(row);
        return toVO(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public EmployeeProfileVO.EducationVO updateEducation(Long employeeId, Long id, EducationRequest req) {
        EmployeeEducation row = requireRow(employeeId, educationMapper.selectById(id), "教育经历不存在");
        requireBizValue("education", req.getDegree(), "学历");
        applyEducation(row, req);
        educationMapper.updateById(row);
        return toVO(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteEducation(Long employeeId, Long id) {
        requireRow(employeeId, educationMapper.selectById(id), "教育经历不存在");
        educationMapper.deleteById(id);
    }

    private void applyEducation(EmployeeEducation row, EducationRequest req) {
        row.setSchool(req.getSchool().trim());
        row.setMajor(nvl(req.getMajor()));
        row.setDegree(req.getDegree().trim());
        LocalDate start = parseDate(req.getStartDate());
        LocalDate end = parseDate(req.getEndDate());
        checkRange(start, end, "在校时间");
        row.setStartDate(start);
        row.setEndDate(end);
        row.setRemark(nvl(req.getRemark()));
    }

    /* ---------------- 工作经历 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    public EmployeeProfileVO.WorkVO addWork(Long employeeId, WorkRequest req) {
        requireWritable(employeeId);
        EmployeeWork row = new EmployeeWork();
        row.setEmployeeId(employeeId);
        applyWork(row, req);
        workMapper.insert(row);
        return toVO(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public EmployeeProfileVO.WorkVO updateWork(Long employeeId, Long id, WorkRequest req) {
        EmployeeWork row = requireRow(employeeId, workMapper.selectById(id), "工作经历不存在");
        applyWork(row, req);
        workMapper.updateById(row);
        return toVO(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteWork(Long employeeId, Long id) {
        requireRow(employeeId, workMapper.selectById(id), "工作经历不存在");
        workMapper.deleteById(id);
    }

    private void applyWork(EmployeeWork row, WorkRequest req) {
        row.setCompany(req.getCompany().trim());
        row.setPosition(nvl(req.getPosition()));
        LocalDate start = parseDate(req.getStartDate());
        LocalDate end = parseDate(req.getEndDate());
        checkRange(start, end, "工作时间");
        row.setStartDate(start);
        row.setEndDate(end);
        row.setLeaveReason(nvl(req.getLeaveReason()));
        row.setRemark(nvl(req.getRemark()));
    }

    /* ---------------- 家庭成员 ---------------- */

    @Transactional(rollbackFor = Exception.class)
    public EmployeeProfileVO.FamilyVO addFamily(Long employeeId, FamilyRequest req) {
        requireWritable(employeeId);
        requireBizValue("family_relation", req.getRelation(), "关系");
        EmployeeFamily row = new EmployeeFamily();
        row.setEmployeeId(employeeId);
        applyFamily(row, req);
        familyMapper.insert(row);
        return toVO(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public EmployeeProfileVO.FamilyVO updateFamily(Long employeeId, Long id, FamilyRequest req) {
        EmployeeFamily row = requireRow(employeeId, familyMapper.selectById(id), "家庭成员不存在");
        requireBizValue("family_relation", req.getRelation(), "关系");
        applyFamily(row, req);
        familyMapper.updateById(row);
        return toVO(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteFamily(Long employeeId, Long id) {
        requireRow(employeeId, familyMapper.selectById(id), "家庭成员不存在");
        familyMapper.deleteById(id);
    }

    private void applyFamily(EmployeeFamily row, FamilyRequest req) {
        row.setRelation(req.getRelation().trim());
        row.setName(req.getName().trim());
        row.setCompany(nvl(req.getCompany()));
        row.setPhone(nvl(req.getPhone()));
        row.setRemark(nvl(req.getRemark()));
    }

    /* ---------------- 内部工具 ---------------- */

    private Employee requireEmployeeExists(Long employeeId) {
        Employee emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BizException(EhrErrorCode.EMP_NOT_FOUND);
        }
        return emp;
    }

    private Employee requireAccessible(Long employeeId) {
        Employee emp = requireEmployeeExists(employeeId);
        if (!dataScope.canAccess(emp)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权维护该员工子档案");
        }
        return emp;
    }

    /**
     * 写子档案前的状态闸门：只有 待入职(10) / 试用期(30) / 在职(40) 允许维护档案信息。
     * <p>
     * 20 入职审批中的人还在审批依据上，50 离职中正在交接、60 已离职已归档，
     * 这三态改档案会让审批结论与档案对不上，统一拒绝。
     */
    private void requireWritable(Long employeeId) {
        Employee emp = requireAccessible(employeeId);
        EmployeeStatus st = EmployeeStatus.of(emp.getStatus());
        boolean writable = st == EmployeeStatus.PENDING
                || st == EmployeeStatus.PROBATION
                || st == EmployeeStatus.ACTIVE;
        if (!writable) {
            throw new BizException(EhrErrorCode.EMP_EDIT_NOT_ALLOWED,
                    "「" + (st == null ? "未知状态" : st.getLabel()) + "」不允许变更员工信息");
        }
    }

    /** 子记录必须存在且属于该员工（防止拿别人的子记录 ID 跨档案改删） */
    private <T> T requireRow(Long employeeId, T row, String message) {
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, message);
        }
        Long ownId;
        try {
            ownId = (Long) row.getClass().getMethod("getEmployeeId").invoke(row);
        } catch (ReflectiveOperationException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        if (!ownId.equals(employeeId)) {
            throw new BizException(ErrorCode.NOT_FOUND, message);
        }
        requireAccessible(employeeId);
        return row;
    }

    private LambdaQueryWrapper<EmployeeBank> bankW(Long employeeId) {
        return new LambdaQueryWrapper<EmployeeBank>()
                .eq(EmployeeBank::getEmployeeId, employeeId)
                .orderByDesc(EmployeeBank::getIsPrimary)
                .orderByAsc(EmployeeBank::getId);
    }

    private LambdaQueryWrapper<EmployeeEducation> educationW(Long employeeId) {
        return new LambdaQueryWrapper<EmployeeEducation>()
                .eq(EmployeeEducation::getEmployeeId, employeeId)
                .orderByDesc(EmployeeEducation::getStartDate);
    }

    private LambdaQueryWrapper<EmployeeWork> workW(Long employeeId) {
        return new LambdaQueryWrapper<EmployeeWork>()
                .eq(EmployeeWork::getEmployeeId, employeeId)
                .orderByDesc(EmployeeWork::getStartDate);
    }

    private LambdaQueryWrapper<EmployeeFamily> familyW(Long employeeId) {
        return new LambdaQueryWrapper<EmployeeFamily>()
                .eq(EmployeeFamily::getEmployeeId, employeeId)
                .orderByAsc(EmployeeFamily::getId);
    }

    private void requireBizValue(String type, String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BizException(EhrErrorCode.EMP_PROFILE_INVALID, name + "必选");
        }
        Map<String, String> biz = settingsClient.bizMap(type);
        // 主数据拉不到（配置中心故障）时放行，不能让配置中心故障卡死人事录入
        if (!biz.isEmpty() && !biz.containsKey(value.trim())) {
            throw new BizException(EhrErrorCode.EMP_PROFILE_INVALID,
                    name + "「" + value + "」不在配置中心的主数据里");
        }
    }

    private void checkRange(LocalDate start, LocalDate end, String name) {
        if (end != null && end.isBefore(start)) {
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

    private String nvl(String s) {
        return s == null ? "" : s.trim();
    }

    /* ---------------- VO 映射 ---------------- */

    private EmployeeProfileVO.BankVO toVO(EmployeeBank r) {
        EmployeeProfileVO.BankVO vo = new EmployeeProfileVO.BankVO();
        vo.setId(r.getId());
        vo.setEmployeeId(r.getEmployeeId());
        vo.setBank(r.getBank());
        vo.setBankName(bizLabel("bank", r.getBank()));
        boolean reveal = dataScope.canRevealSensitive();
        vo.setAccountNo(reveal ? r.getAccountNo() : SensitiveMasker.maskBankAccount(r.getAccountNo()));
        vo.setSensitiveMasked(!reveal && r.getAccountNo() != null && !r.getAccountNo().isBlank());
        vo.setHolderName(r.getHolderName());
        vo.setBranch(r.getBranch());
        vo.setIsPrimary(r.getIsPrimary());
        vo.setRemark(r.getRemark());
        return vo;
    }

    private EmployeeProfileVO.EducationVO toVO(EmployeeEducation r) {
        EmployeeProfileVO.EducationVO vo = new EmployeeProfileVO.EducationVO();
        vo.setId(r.getId());
        vo.setEmployeeId(r.getEmployeeId());
        vo.setSchool(r.getSchool());
        vo.setMajor(r.getMajor());
        vo.setDegree(r.getDegree());
        vo.setDegreeName(bizLabel("education", r.getDegree()));
        vo.setStartDate(r.getStartDate());
        vo.setEndDate(r.getEndDate());
        vo.setRemark(r.getRemark());
        return vo;
    }

    private EmployeeProfileVO.WorkVO toVO(EmployeeWork r) {
        EmployeeProfileVO.WorkVO vo = new EmployeeProfileVO.WorkVO();
        vo.setId(r.getId());
        vo.setEmployeeId(r.getEmployeeId());
        vo.setCompany(r.getCompany());
        vo.setPosition(r.getPosition());
        vo.setStartDate(r.getStartDate());
        vo.setEndDate(r.getEndDate());
        vo.setLeaveReason(r.getLeaveReason());
        vo.setRemark(r.getRemark());
        return vo;
    }

    private EmployeeProfileVO.FamilyVO toVO(EmployeeFamily r) {
        EmployeeProfileVO.FamilyVO vo = new EmployeeProfileVO.FamilyVO();
        vo.setId(r.getId());
        vo.setEmployeeId(r.getEmployeeId());
        vo.setRelation(r.getRelation());
        vo.setRelationName(bizLabel("family_relation", r.getRelation()));
        vo.setName(r.getName());
        vo.setCompany(r.getCompany());
        vo.setPhone(r.getPhone());
        vo.setRemark(r.getRemark());
        return vo;
    }

    private String bizLabel(String type, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String name = settingsClient.bizMap(type).get(value);
        return name == null ? value : name;
    }
}
