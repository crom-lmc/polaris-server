package com.laimengchao.sso.ehr.module.importexport.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.excel.ExcelWriter;
import com.laimengchao.sso.ehr.module.attendance.service.AttendanceService;
import com.laimengchao.sso.ehr.module.attendance.vo.AttendanceMonthVO;
import com.laimengchao.sso.ehr.module.audit.service.AuditLogService;
import com.laimengchao.sso.ehr.module.contract.entity.Contract;
import com.laimengchao.sso.ehr.module.contract.mapper.ContractMapper;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.employee.service.EmployeeService;
import com.laimengchao.sso.ehr.module.employee.vo.EmployeeVO;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.ehr.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 员工 / 合同 / 考勤导出（.xlsx）：统一走数据范围与敏感掩码，并记 EXPORT 审计。 */
@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private final EmployeeService employeeService;
    private final EmployeeMapper employeeMapper;
    private final ContractMapper contractMapper;
    private final OrgNodeMapper orgMapper;
    private final AttendanceService attendanceService;
    private final DataScopeService dataScopeService;
    private final AuditLogService auditLogService;

    public byte[] employees(String keyword, Long orgId, Integer status) {
        List<EmployeeVO> rows = employeeService.exportRows(keyword, orgId, status);
        String[] headers = {"工号", "姓名", "组织", "职位", "状态", "手机号", "身份证号",
                "紧急联系人", "紧急联系电话", "邮箱", "入职日期", "预计转正日期", "备注"};
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = ExcelWriter.headerSheet(wb, "员工", headers);
            for (int i = 0; i < rows.size(); i++) {
                EmployeeVO vo = rows.get(i);
                Row row = sheet.createRow(i + 1);
                ExcelWriter.set(row, 0, vo.getEmpNo());
                ExcelWriter.set(row, 1, vo.getName());
                ExcelWriter.set(row, 2, vo.getOrgPathName());
                ExcelWriter.set(row, 3, vo.getPosition());
                ExcelWriter.set(row, 4, vo.getStatusName());
                ExcelWriter.set(row, 5, vo.getMobile());
                ExcelWriter.set(row, 6, vo.getIdCard());
                ExcelWriter.set(row, 7, vo.getEmergencyContact());
                ExcelWriter.set(row, 8, vo.getEmergencyPhone());
                ExcelWriter.set(row, 9, vo.getEmail());
                ExcelWriter.set(row, 10, vo.getHireDate() == null ? "" : vo.getHireDate().toString());
                ExcelWriter.set(row, 11, vo.getExpectedRegularizeDate() == null ? "" : vo.getExpectedRegularizeDate().toString());
                ExcelWriter.set(row, 12, vo.getRemark());
            }
            auditLogService.record(AuditLogService.ACTION_EXPORT, AuditLogService.TARGET_EMPLOYEE, null,
                    "xlsx rows=" + rows.size());
            return ExcelWriter.toBytes(wb, "员工导出失败");
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "员工导出失败");
        }
    }

    public byte[] contracts() {
        Map<Long, OrgNode> orgs = new HashMap<>();
        for (OrgNode n : orgMapper.selectList(new LambdaQueryWrapper<OrgNode>())) {
            orgs.put(n.getId(), n);
        }
        List<Contract> contracts = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .orderByDesc(Contract::getStartDate).orderByDesc(Contract::getId));
        List<Long> employeeIds = contracts.stream().map(Contract::getEmployeeId)
                .filter(id -> id != null).distinct().collect(Collectors.toList());
        Map<Long, Employee> emps = employeeIds.isEmpty() ? Map.of()
                : employeeMapper.selectBatchIds(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e, (a, b) -> a));
        List<Contract> visible = contracts.stream()
                .filter(c -> {
                    Employee e = emps.get(c.getEmployeeId());
                    return e != null && dataScopeService.canAccess(e);
                })
                .collect(Collectors.toList());

        String[] headers = {"合同编号", "员工工号", "员工姓名", "组织", "合同类型", "开始日期",
                "结束日期", "签订日期", "状态", "备注"};
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = ExcelWriter.headerSheet(wb, "合同", headers);
            for (int i = 0; i < visible.size(); i++) {
                Contract c = visible.get(i);
                Employee e = emps.get(c.getEmployeeId());
                Row row = sheet.createRow(i + 1);
                ExcelWriter.set(row, 0, c.getContractNo());
                ExcelWriter.set(row, 1, e == null ? "" : e.getEmpNo());
                ExcelWriter.set(row, 2, e == null ? "" : e.getName());
                ExcelWriter.set(row, 3, e == null ? "" : pathName(orgs.get(e.getOrgId()), orgs));
                ExcelWriter.set(row, 4, contractType(c.getContractType()));
                ExcelWriter.set(row, 5, c.getStartDate() == null ? "" : c.getStartDate().toString());
                ExcelWriter.set(row, 6, c.getEndDate() == null ? "" : c.getEndDate().toString());
                ExcelWriter.set(row, 7, c.getSignDate() == null ? "" : c.getSignDate().toString());
                ExcelWriter.set(row, 8, contractStatus(c.getStatus()));
                ExcelWriter.set(row, 9, c.getRemark());
            }
            auditLogService.record(AuditLogService.ACTION_EXPORT, AuditLogService.TARGET_CONTRACT, null,
                    "xlsx rows=" + visible.size());
            return ExcelWriter.toBytes(wb, "合同导出失败");
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "合同导出失败");
        }
    }

    public byte[] attendance(String month) {
        List<AttendanceMonthVO> rows = attendanceService.monthly(month, null);
        String[] headers = {"月份", "员工ID", "员工姓名", "应出勤天数", "实际出勤天数",
                "迟到分钟", "早退分钟", "缺勤小时", "缺勤天数", "请假天数", "加班小时",
                "实际出勤小时", "确认状态"};
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = ExcelWriter.headerSheet(wb, "月度考勤", headers);
            for (int i = 0; i < rows.size(); i++) {
                AttendanceMonthVO vo = rows.get(i);
                Row row = sheet.createRow(i + 1);
                ExcelWriter.set(row, 0, vo.getMonth());
                ExcelWriter.set(row, 1, vo.getEmployeeId() == null ? "" : String.valueOf(vo.getEmployeeId()));
                ExcelWriter.set(row, 2, vo.getEmployeeName());
                ExcelWriter.set(row, 3, vo.getShouldDays());
                ExcelWriter.set(row, 4, vo.getActualDays());
                ExcelWriter.set(row, 5, vo.getLateMinutes() == null ? "" : String.valueOf(vo.getLateMinutes()));
                ExcelWriter.set(row, 6, vo.getEarlyMinutes() == null ? "" : String.valueOf(vo.getEarlyMinutes()));
                ExcelWriter.set(row, 7, vo.getAbsentHours());
                ExcelWriter.set(row, 8, vo.getAbsentDays());
                ExcelWriter.set(row, 9, vo.getLeaveDays());
                ExcelWriter.set(row, 10, vo.getOvertimeHours());
                ExcelWriter.set(row, 11, vo.getWorkHours());
                ExcelWriter.set(row, 12, Integer.valueOf(1).equals(vo.getConfirmed()) ? "已确认" : "未确认");
            }
            auditLogService.record(AuditLogService.ACTION_EXPORT, AuditLogService.TARGET_ATTENDANCE, null,
                    "month=" + (month == null ? LocalDate.now().toString().substring(0, 7) : month)
                            + ",rows=" + rows.size());
            return ExcelWriter.toBytes(wb, "考勤导出失败");
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "考勤导出失败");
        }
    }

    private String pathName(OrgNode node, Map<Long, OrgNode> orgs) {
        if (node == null) {
            return "";
        }
        if (node.getPath() != null && !node.getPath().isBlank()) {
            List<String> names = new ArrayList<>();
            for (String part : node.getPath().split("/")) {
                try {
                    OrgNode n = orgs.get(Long.parseLong(part));
                    if (n != null && n.getName() != null) {
                        names.add(n.getName());
                    }
                } catch (NumberFormatException ignored) {
                    // 跳过异常路径片段
                }
            }
            if (!names.isEmpty()) {
                return String.join(" / ", names);
            }
        }
        return node.getName() == null ? "" : node.getName();
    }

    private String contractType(String type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case "FIXED" -> "固定期限";
            case "UNFIXED" -> "无固定期限";
            case "INTERN" -> "实习";
            case "LABOR" -> "劳务";
            default -> type;
        };
    }

    private String contractStatus(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "ACTIVE" -> "生效";
            case "PENDING" -> "续签审批中";
            case "TERMINATING" -> "终止审批中";
            case "EXPIRED" -> "已到期";
            case "RENEWED" -> "已续签";
            case "TERMINATED" -> "已终止";
            default -> status;
        };
    }
}
