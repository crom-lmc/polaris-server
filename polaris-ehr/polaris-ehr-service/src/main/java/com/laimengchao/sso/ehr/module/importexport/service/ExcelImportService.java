package com.laimengchao.sso.ehr.module.importexport.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.excel.ExcelReader;
import com.laimengchao.sso.common.excel.ExcelWriter;
import com.laimengchao.sso.ehr.module.attachment.service.AttachmentService;
import com.laimengchao.sso.ehr.module.attachment.vo.AttachmentVO;
import com.laimengchao.sso.ehr.module.audit.service.AuditLogService;
import com.laimengchao.sso.ehr.module.employee.dto.EmployeeRequest;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeEducation;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeFamily;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeWork;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeEducationMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeFamilyMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeWorkMapper;
import com.laimengchao.sso.ehr.module.employee.service.EmployeeService;
import com.laimengchao.sso.ehr.module.importexport.entity.ImportTask;
import com.laimengchao.sso.ehr.module.importexport.mapper.ImportTaskMapper;
import com.laimengchao.sso.ehr.module.importexport.vo.ImportVos;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 员工批量导入：xlsx 四张 sheet（基础信息 / 教育 / 工作 / 家庭），
 * 上传即解析并返回错误预览，确认后逐行独立事务入库，失败行不产生半成品数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelImportService {

    public static final String SHEET_BASE = "员工基础信息";
    public static final String SHEET_EDU = "教育经历";
    public static final String SHEET_WORK = "工作经历";
    public static final String SHEET_FAMILY = "家庭成员";
    public static final String SHEET_ERROR = "错误明细";

    private static final String[] BASE_HEADERS = {
            "姓名", "性别", "手机号", "邮箱", "身份证号", "出生日期", "民族", "婚姻状况",
            "政治面貌", "最高学历", "紧急联系人", "紧急联系电话", "现住址",
            "组织编码", "职位", "入职日期", "试用期月数", "免试用期", "备注"
    };
    private static final String[] EDU_HEADERS = {"姓名", "身份证号", "学校", "专业", "学历", "开始日期", "结束日期", "备注"};
    private static final String[] WORK_HEADERS = {"姓名", "身份证号", "公司", "职位", "开始日期", "结束日期", "离职原因", "备注"};
    private static final String[] FAMILY_HEADERS = {"姓名", "身份证号", "关系", "家庭成员姓名", "联系电话", "工作单位", "备注"};

    private final ImportTaskMapper importTaskMapper;
    private final AttachmentService attachmentService;
    private final EmployeeService employeeService;
    private final EmployeeMapper employeeMapper;
    private final EmployeeEducationMapper educationMapper;
    private final EmployeeWorkMapper workMapper;
    private final EmployeeFamilyMapper familyMapper;
    private final OrgNodeMapper orgMapper;
    private final AuditLogService auditLogService;
    private final PlatformTransactionManager transactionManager;

    /* ---------------- 对外入口 ---------------- */

    public byte[] template() {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            ExcelWriter.headerSheet(wb, SHEET_BASE, BASE_HEADERS, 18);
            ExcelWriter.headerSheet(wb, SHEET_EDU, EDU_HEADERS, 18);
            ExcelWriter.headerSheet(wb, SHEET_WORK, WORK_HEADERS, 18);
            ExcelWriter.headerSheet(wb, SHEET_FAMILY, FAMILY_HEADERS, 18);
            Sheet howto = wb.createSheet("填写说明");
            String[] lines = {
                    "1. 员工基础信息为必填 sheet，每行一位员工；教育/工作/家庭成员为选填，按「姓名+身份证号」匹配到基础信息行。",
                    "2. 组织编码请填写组织架构中的节点编码，可从「组织架构」页面查看。",
                    "3. 日期统一填 yyyy-MM-dd（如 2026-11-09），Excel 日期单元格也可直接识别。",
                    "4. 入职人员统一先落「待入职」档案，工号由系统自动生成；账号在入职审批通过后再开通。",
                    "5. 身份证号、手机号不可与系统已有员工重复，也不可在文件内重复。",
                    "6. 上传后先预览校验结果，确认后才真正入库；失败行不会写入任何数据。"
            };
            for (int i = 0; i < lines.length; i++) {
                Row row = howto.createRow(i);
                row.createCell(0).setCellValue(lines[i]);
            }
            howto.setColumnWidth(0, 120 * 256);
            return ExcelWriter.toBytes(wb, "导入模板生成失败");
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "导入模板生成失败");
        }
    }

    public ImportVos.Preview preview(MultipartFile file, Long accountId) {
        Parsed parsed = parseFile(file);
        ImportVos.Preview vo = new ImportVos.Preview();
        vo.setTotal(parsed.employees.size());
        vo.setValidCount(parsed.validCount());
        vo.setInvalidCount(parsed.invalidCount());
        vo.setErrors(parsed.allErrors(500));

        AttachmentVO attachment = attachmentService.upload(file, "IMPORT", accountId, null);
        ImportTask task = new ImportTask();
        task.setType("EMPLOYEE");
        task.setFileId(attachment.getId());
        task.setFileName(attachment.getFileName());
        task.setStatus("PREVIEW");
        task.setTotal(vo.getTotal());
        task.setValidCount(vo.getValidCount());
        task.setInvalidCount(vo.getInvalidCount());
        task.setSuccess(0);
        task.setFailed(0);
        task.setCreatedBy(accountId);
        importTaskMapper.insert(task);
        vo.setTaskId(task.getId());
        vo.setFileName(task.getFileName());
        return vo;
    }

    public ImportVos.Result confirm(Long taskId, Long accountId) {
        ImportTask task = importTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "导入任务不存在");
        }
        if (!SecurityUtils.isSuperAdmin() && accountId != null && !accountId.equals(task.getCreatedBy())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能确认本人上传的导入任务");
        }
        boolean locked = importTaskMapper.update(null, new LambdaUpdateWrapper<ImportTask>()
                .eq(ImportTask::getId, taskId)
                .eq(ImportTask::getStatus, "PREVIEW")
                .set(ImportTask::getStatus, "IMPORTING")) > 0;
        if (!locked) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该任务已确认过，请重新上传文件");
        }

        Parsed parsed;
        try {
            parsed = parseAttachment(task.getFileId());
        } catch (Exception e) {
            markFailed(taskId, 0, parsedCount(task), "文件解析失败：" + e.getMessage());
            throw new BizException(ErrorCode.PARAM_ERROR, "导入文件解析失败，请重新上传");
        }

        List<ImportVos.RowError> failures = new ArrayList<>();
        int success = 0;
        int failed = 0;
        for (EmpRow row : parsed.employees) {
            String rowError = validateForConfirm(row);
            if (rowError != null) {
                failures.add(error(SHEET_BASE, row.rowNum, row.name, rowError));
                failed++;
                continue;
            }
            try {
                Long employeeId = importOne(row);
                if (employeeId == null) {
                    failures.add(error(SHEET_BASE, row.rowNum, row.name, "建档失败"));
                    failed++;
                } else {
                    success++;
                }
            } catch (Exception e) {
                log.warn("导入行失败 row={} name={}", row.rowNum, row.name, e);
                failures.add(error(SHEET_BASE, row.rowNum, row.name, messageOf(e)));
                failed++;
            }
        }

        Long errorFileId = null;
        if (!failures.isEmpty()) {
            byte[] bytes = errorWorkbook(failures);
            AttachmentVO err = attachmentService.uploadBytes(bytes,
                    "员工导入错误明细-" + LocalDate.now() + ".xlsx", "IMPORT", accountId, taskId);
            errorFileId = err.getId();
        }

        task.setStatus("DONE");
        task.setSuccess(success);
        task.setFailed(failed);
        task.setErrorFileId(errorFileId);
        importTaskMapper.updateById(task);

        auditLogService.record(AuditLogService.ACTION_IMPORT, AuditLogService.TARGET_IMPORT, taskId,
                "success=" + success + ",failed=" + failed);
        ImportVos.Result result = new ImportVos.Result();
        result.setTaskId(taskId);
        result.setStatus("DONE");
        result.setTotal(parsed.employees.size());
        result.setSuccess(success);
        result.setFailed(failed);
        result.setErrorFileId(errorFileId);
        result.setErrors(failures.size() > 500 ? new ArrayList<>(failures.subList(0, 500)) : failures);
        return result;
    }

    public ImportVos.TaskView task(Long taskId, Long accountId) {
        ImportTask task = importTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "导入任务不存在");
        }
        if (!SecurityUtils.isSuperAdmin() && accountId != null && !accountId.equals(task.getCreatedBy())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该导入任务");
        }
        ImportVos.TaskView vo = new ImportVos.TaskView();
        vo.setId(task.getId());
        vo.setType(task.getType());
        vo.setFileName(task.getFileName());
        vo.setStatus(task.getStatus());
        vo.setTotal(task.getTotal());
        vo.setValidCount(task.getValidCount());
        vo.setInvalidCount(task.getInvalidCount());
        vo.setSuccess(task.getSuccess());
        vo.setFailed(task.getFailed());
        vo.setErrorFileId(task.getErrorFileId());
        vo.setCreatedAt(task.getCreatedAt() == null ? null : task.getCreatedAt().toString().replace('T', ' '));
        return vo;
    }

    /* ---------------- 入库 ---------------- */

    private String validateForConfirm(EmpRow row) {
        if (!row.errors.isEmpty()) {
            return String.join("；", row.errors);
        }
        if (!row.idCard.isBlank()) {
            Long cnt = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                    .eq(Employee::getIdCard, row.idCard));
            if (cnt != null && cnt > 0) {
                return "身份证号已存在";
            }
        }
        if (!row.mobile.isBlank()) {
            Long cnt = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                    .eq(Employee::getMobile, row.mobile));
            if (cnt != null && cnt > 0) {
                return "手机号已存在";
            }
        }
        return null;
    }

    private Long importOne(EmpRow row) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(status -> {
            EmployeeRequest req = new EmployeeRequest();
            req.setName(row.name);
            req.setGender(row.gender);
            req.setMobile(row.mobile);
            req.setEmail(row.email);
            req.setIdCard(row.idCard);
            req.setBirthday(row.birthday == null ? null : row.birthday.toString());
            req.setNation(row.nation);
            req.setMaritalStatus(row.maritalStatus);
            req.setPoliticalStatus(row.politicalStatus);
            req.setEducation(row.education);
            req.setEmergencyContact(row.emergencyContact);
            req.setEmergencyPhone(row.emergencyPhone);
            req.setCurrentAddress(row.currentAddress);
            req.setOrgId(row.orgId);
            req.setPosition(row.position);
            req.setHireDate(row.hireDate == null ? null : row.hireDate.toString());
            req.setProbationMonths(row.probationMonths);
            req.setExemptProbation(row.exemptProbation);
            req.setRemark(row.remark);
            req.setOpenAccount(false);
            Long employeeId = employeeService.create(req).getId();
            for (EmployeeEducation e : row.educations) {
                e.setEmployeeId(employeeId);
                educationMapper.insert(e);
            }
            for (EmployeeWork w : row.works) {
                w.setEmployeeId(employeeId);
                workMapper.insert(w);
            }
            for (EmployeeFamily f : row.families) {
                f.setEmployeeId(employeeId);
                familyMapper.insert(f);
            }
            return employeeId;
        });
    }

    /* ---------------- 解析与校验 ---------------- */

    private Parsed parseAttachment(Long fileId) throws IOException {
        Path path = attachmentService.localPath(fileId);
        try (Workbook wb = WorkbookFactory.create(path.toFile())) {
            return parse(wb);
        }
    }

    private Parsed parseFile(MultipartFile file) {
        String name = file == null || file.getOriginalFilename() == null
                ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "只支持 .xlsx / .xls 文件");
        }
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            return parse(wb);
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "Excel 文件读取失败，请确认文件未损坏");
        }
    }

    private Parsed parse(Workbook wb) {
        Sheet baseSheet = wb.getSheet(SHEET_BASE);
        if (baseSheet == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "模板缺少「" + SHEET_BASE + "」工作表");
        }
        Map<String, OrgNode> orgs = new HashMap<>();
        for (OrgNode n : orgMapper.selectList(new LambdaQueryWrapper<OrgNode>())) {
            if (n.getCode() != null && !n.getCode().isBlank()) {
                orgs.put(n.getCode().trim().toLowerCase(Locale.ROOT), n);
            }
        }

        Parsed parsed = new Parsed();
        Map<String, Integer> baseHeaders = ExcelReader.headers(baseSheet.getRow(0));
        ExcelReader.requireHeaders(baseHeaders, SHEET_BASE, "姓名", "组织编码");

        for (int i = 1; i <= baseSheet.getLastRowNum(); i++) {
            Row row = baseSheet.getRow(i);
            if (row == null || ExcelReader.blankRow(row, baseHeaders)) {
                continue;
            }
            EmpRow emp = parseBaseRow(row, baseHeaders, orgs);
            parsed.employees.add(emp);
            parsed.byKey.putIfAbsent(emp.key(), emp);
            parsed.byName.computeIfAbsent(norm(emp.name), k -> new ArrayList<>()).add(emp);
        }
        if (parsed.employees.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "「" + SHEET_BASE + "」没有任何数据行");
        }

        readEducation(wb.getSheet(SHEET_EDU), parsed);
        readWork(wb.getSheet(SHEET_WORK), parsed);
        readFamily(wb.getSheet(SHEET_FAMILY), parsed);
        checkDuplicates(parsed);
        return parsed;
    }

    private EmpRow parseBaseRow(Row row, Map<String, Integer> h, Map<String, OrgNode> orgs) {
        EmpRow emp = new EmpRow();
        emp.rowNum = row.getRowNum() + 1;
        emp.name = ExcelReader.text(row, h, "姓名");
        emp.mobile = ExcelReader.text(row, h, "手机号");
        emp.email = ExcelReader.text(row, h, "邮箱");
        emp.idCard = ExcelReader.text(row, h, "身份证号").toUpperCase(Locale.ROOT);
        emp.nation = ExcelReader.text(row, h, "民族");
        emp.maritalStatus = ExcelReader.text(row, h, "婚姻状况");
        emp.politicalStatus = ExcelReader.text(row, h, "政治面貌");
        emp.education = ExcelReader.text(row, h, "最高学历");
        emp.emergencyContact = ExcelReader.text(row, h, "紧急联系人");
        emp.emergencyPhone = ExcelReader.text(row, h, "紧急联系电话");
        emp.currentAddress = ExcelReader.text(row, h, "现住址");
        emp.position = ExcelReader.text(row, h, "职位");
        emp.remark = ExcelReader.text(row, h, "备注");
        emp.gender = parseGender(ExcelReader.text(row, h, "性别"));
        emp.exemptProbation = parseBool(ExcelReader.text(row, h, "免试用期"));
        String orgCode = ExcelReader.text(row, h, "组织编码");
        if (orgCode.isBlank()) {
            emp.errors.add("组织编码必填");
        } else {
            OrgNode org = orgs.get(orgCode.toLowerCase(Locale.ROOT));
            if (org == null) {
                emp.errors.add("组织编码不存在：" + orgCode);
            } else {
                emp.orgId = org.getId();
            }
        }
        if (emp.name.isBlank()) {
            emp.errors.add("姓名必填");
        }
        if (!emp.mobile.isBlank() && !emp.mobile.matches("^1\\d{10}$")) {
            emp.errors.add("手机号格式不正确");
        }
        if (!emp.idCard.isBlank() && !emp.idCard.matches("^\\d{17}[\\dX]$")) {
            emp.errors.add("身份证号格式不正确");
        }
        emp.birthday = ExcelReader.date(row, h, "出生日期", emp.errors);
        emp.hireDate = ExcelReader.date(row, h, "入职日期", emp.errors);
        String probation = ExcelReader.text(row, h, "试用期月数");
        if (!probation.isBlank() && !emp.exemptProbation) {
            try {
                int months = Integer.parseInt(probation);
                if (months < 1 || months > 12) {
                    emp.errors.add("试用期月数需在 1-12 之间");
                } else {
                    emp.probationMonths = months;
                }
            } catch (NumberFormatException e) {
                emp.errors.add("试用期月数需为整数");
            }
        }
        return emp;
    }

    private void readEducation(Sheet sheet, Parsed parsed) {
        if (sheet == null) {
            return;
        }
        Map<String, Integer> h = ExcelReader.headers(sheet.getRow(0));
        if (!h.containsKey(norm("姓名"))) {
            return;
        }
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || ExcelReader.blankRow(row, h)) {
                continue;
            }
            int rowNum = row.getRowNum() + 1;
            String name = ExcelReader.text(row, h, "姓名");
            String idCard = ExcelReader.text(row, h, "身份证号").toUpperCase(Locale.ROOT);
            EmpRow emp = match(parsed, name, idCard);
            if (emp == null) {
                parsed.errors.add(error(SHEET_EDU, rowNum, name, "未找到对应的员工基础信息行（姓名+身份证号）"));
                continue;
            }
            String school = ExcelReader.text(row, h, "学校");
            if (school.isBlank()) {
                parsed.errors.add(error(SHEET_EDU, rowNum, name, "学校必填"));
                continue;
            }
            EmployeeEducation e = new EmployeeEducation();
            e.setSchool(school);
            e.setMajor(ExcelReader.text(row, h, "专业"));
            e.setDegree(ExcelReader.text(row, h, "学历"));
            e.setRemark(ExcelReader.text(row, h, "备注"));
            List<String> errs = new ArrayList<>();
            e.setStartDate(ExcelReader.date(row, h, "开始日期", errs));
            e.setEndDate(ExcelReader.date(row, h, "结束日期", errs));
            if (!errs.isEmpty()) {
                parsed.errors.add(error(SHEET_EDU, rowNum, name, String.join("；", errs)));
                continue;
            }
            emp.educations.add(e);
        }
    }

    private void readWork(Sheet sheet, Parsed parsed) {
        if (sheet == null) {
            return;
        }
        Map<String, Integer> h = ExcelReader.headers(sheet.getRow(0));
        if (!h.containsKey(norm("姓名"))) {
            return;
        }
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || ExcelReader.blankRow(row, h)) {
                continue;
            }
            int rowNum = row.getRowNum() + 1;
            String name = ExcelReader.text(row, h, "姓名");
            String idCard = ExcelReader.text(row, h, "身份证号").toUpperCase(Locale.ROOT);
            EmpRow emp = match(parsed, name, idCard);
            if (emp == null) {
                parsed.errors.add(error(SHEET_WORK, rowNum, name, "未找到对应的员工基础信息行（姓名+身份证号）"));
                continue;
            }
            String company = ExcelReader.text(row, h, "公司");
            if (company.isBlank()) {
                parsed.errors.add(error(SHEET_WORK, rowNum, name, "公司必填"));
                continue;
            }
            EmployeeWork w = new EmployeeWork();
            w.setCompany(company);
            w.setPosition(ExcelReader.text(row, h, "职位"));
            w.setLeaveReason(ExcelReader.text(row, h, "离职原因"));
            w.setRemark(ExcelReader.text(row, h, "备注"));
            List<String> errs = new ArrayList<>();
            w.setStartDate(ExcelReader.date(row, h, "开始日期", errs));
            w.setEndDate(ExcelReader.date(row, h, "结束日期", errs));
            if (!errs.isEmpty()) {
                parsed.errors.add(error(SHEET_WORK, rowNum, name, String.join("；", errs)));
                continue;
            }
            emp.works.add(w);
        }
    }

    private void readFamily(Sheet sheet, Parsed parsed) {
        if (sheet == null) {
            return;
        }
        Map<String, Integer> h = ExcelReader.headers(sheet.getRow(0));
        if (!h.containsKey(norm("姓名"))) {
            return;
        }
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || ExcelReader.blankRow(row, h)) {
                continue;
            }
            int rowNum = row.getRowNum() + 1;
            String name = ExcelReader.text(row, h, "姓名");
            String idCard = ExcelReader.text(row, h, "身份证号").toUpperCase(Locale.ROOT);
            EmpRow emp = match(parsed, name, idCard);
            if (emp == null) {
                parsed.errors.add(error(SHEET_FAMILY, rowNum, name, "未找到对应的员工基础信息行（姓名+身份证号）"));
                continue;
            }
            String member = ExcelReader.text(row, h, "家庭成员姓名");
            if (member.isBlank()) {
                parsed.errors.add(error(SHEET_FAMILY, rowNum, name, "家庭成员姓名必填"));
                continue;
            }
            EmployeeFamily f = new EmployeeFamily();
            f.setName(member);
            f.setRelation(ExcelReader.text(row, h, "关系"));
            f.setPhone(ExcelReader.text(row, h, "联系电话"));
            f.setCompany(ExcelReader.text(row, h, "工作单位"));
            f.setRemark(ExcelReader.text(row, h, "备注"));
            emp.families.add(f);
        }
    }

    private EmpRow match(Parsed parsed, String name, String idCard) {
        if (name.isBlank()) {
            return null;
        }
        if (!idCard.isBlank()) {
            EmpRow emp = parsed.byKey.get(key(name, idCard));
            if (emp != null) {
                return emp;
            }
        }
        List<EmpRow> rows = parsed.byName.get(norm(name));
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    private void checkDuplicates(Parsed parsed) {
        Set<String> idCards = new HashSet<>();
        Set<String> mobiles = new HashSet<>();
        for (EmpRow emp : parsed.employees) {
            if (!emp.idCard.isBlank() && !idCards.add(emp.idCard)) {
                emp.errors.add("身份证号在文件中重复");
            }
            if (!emp.mobile.isBlank() && !mobiles.add(emp.mobile)) {
                emp.errors.add("手机号在文件中重复");
            }
        }
        if (!idCards.isEmpty()) {
            Set<String> existing = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                            .in(Employee::getIdCard, idCards))
                    .stream().map(Employee::getIdCard).collect(Collectors.toSet());
            for (EmpRow emp : parsed.employees) {
                if (!emp.idCard.isBlank() && existing.contains(emp.idCard)) {
                    emp.errors.add("身份证号已存在");
                }
            }
        }
        if (!mobiles.isEmpty()) {
            Set<String> existing = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                            .in(Employee::getMobile, mobiles))
                    .stream().map(Employee::getMobile).collect(Collectors.toSet());
            for (EmpRow emp : parsed.employees) {
                if (!emp.mobile.isBlank() && existing.contains(emp.mobile)) {
                    emp.errors.add("手机号已存在");
                }
            }
        }
    }

    /* ---------------- Excel 基础工具 ---------------- */

    private byte[] errorWorkbook(List<ImportVos.RowError> errors) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = ExcelWriter.headerSheet(wb, SHEET_ERROR,
                    new String[]{"工作表", "行号", "姓名", "错误原因"});
            for (int i = 0; i < errors.size(); i++) {
                ImportVos.RowError e = errors.get(i);
                Row row = sheet.createRow(i + 1);
                ExcelWriter.set(row, 0, e.getSheet());
                ExcelWriter.set(row, 1, e.getRow() == null ? "" : String.valueOf(e.getRow()));
                ExcelWriter.set(row, 2, e.getName());
                ExcelWriter.set(row, 3, e.getMessage());
            }
            sheet.setColumnWidth(3, 60 * 256);
            return ExcelWriter.toBytes(wb, "错误明细生成失败");
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "错误明细生成失败");
        }
    }

    private int parseGender(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String v = value.trim();
        if ("男".equals(v) || "1".equals(v) || "M".equalsIgnoreCase(v)) {
            return 1;
        }
        if ("女".equals(v) || "2".equals(v) || "F".equalsIgnoreCase(v)) {
            return 2;
        }
        return 0;
    }

    private boolean parseBool(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return "是".equals(v) || "true".equals(v) || "1".equals(v) || "y".equals(v) || "yes".equals(v);
    }

    private String norm(String value) {
        return ExcelReader.key(value);
    }

    private String key(String name, String idCard) {
        return norm(name) + "|" + (idCard == null ? "" : idCard.trim().toUpperCase(Locale.ROOT));
    }

    private ImportVos.RowError error(String sheet, int row, String name, String message) {
        ImportVos.RowError e = new ImportVos.RowError();
        e.setSheet(sheet);
        e.setRow(row);
        e.setName(name);
        e.setMessage(message);
        return e;
    }

    private void markFailed(Long taskId, int success, int failed, String reason) {
        importTaskMapper.update(null, new LambdaUpdateWrapper<ImportTask>()
                .eq(ImportTask::getId, taskId)
                .set(ImportTask::getStatus, "FAILED")
                .set(ImportTask::getSuccess, success)
                .set(ImportTask::getFailed, failed));
        log.error("导入任务失败 taskId={} reason={}", taskId, reason);
    }

    private int parsedCount(ImportTask task) {
        return task.getTotal() == null ? 0 : task.getTotal();
    }

    private String messageOf(Exception e) {
        if (e instanceof BizException) {
            return e.getMessage();
        }
        String msg = e.getMessage();
        return msg == null || msg.isBlank() ? "系统异常，请稍后重试" : "系统异常：" + msg;
    }

    /* ---------------- 数据结构 ---------------- */

    private static class Parsed {
        final List<EmpRow> employees = new ArrayList<>();
        final List<ImportVos.RowError> errors = new ArrayList<>();
        final Map<String, EmpRow> byKey = new HashMap<>();
        final Map<String, List<EmpRow>> byName = new HashMap<>();

        int validCount() {
            return (int) employees.stream().filter(e -> e.errors.isEmpty()).count();
        }

        int invalidCount() {
            return employees.size() - validCount();
        }

        List<ImportVos.RowError> allErrors(int limit) {
            List<ImportVos.RowError> all = new ArrayList<>(errors);
            for (EmpRow emp : employees) {
                if (!emp.errors.isEmpty()) {
                    ImportVos.RowError e = new ImportVos.RowError();
                    e.setSheet(SHEET_BASE);
                    e.setRow(emp.rowNum);
                    e.setName(emp.name);
                    e.setMessage(String.join("；", emp.errors));
                    all.add(e);
                }
            }
            return all.size() > limit ? new ArrayList<>(all.subList(0, limit)) : all;
        }
    }

    private static class EmpRow {
        int rowNum;
        String name = "";
        Integer gender = 0;
        String mobile = "";
        String email = "";
        String idCard = "";
        LocalDate birthday;
        String nation = "";
        String maritalStatus = "";
        String politicalStatus = "";
        String education = "";
        String emergencyContact = "";
        String emergencyPhone = "";
        String currentAddress = "";
        Long orgId;
        String position = "";
        LocalDate hireDate;
        Integer probationMonths;
        boolean exemptProbation;
        String remark = "";
        final List<String> errors = new ArrayList<>();
        final List<EmployeeEducation> educations = new ArrayList<>();
        final List<EmployeeWork> works = new ArrayList<>();
        final List<EmployeeFamily> families = new ArrayList<>();

        String key() {
            return normKey(name) + "|" + (idCard == null ? "" : idCard.trim().toUpperCase(Locale.ROOT));
        }

        private static String normKey(String value) {
            return value == null ? "" : value.replaceAll("[\\s*＊]", "");
        }
    }
}
