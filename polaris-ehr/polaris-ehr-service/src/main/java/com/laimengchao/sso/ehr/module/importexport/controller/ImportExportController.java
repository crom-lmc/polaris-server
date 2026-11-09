package com.laimengchao.sso.ehr.module.importexport.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.module.importexport.service.ExcelExportService;
import com.laimengchao.sso.ehr.module.importexport.service.ExcelImportService;
import com.laimengchao.sso.ehr.module.importexport.vo.ImportVos;
import com.laimengchao.sso.ehr.security.Permit;
import com.laimengchao.sso.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/** 批量导入导出：员工 xlsx 两段式导入，员工/合同/考勤 xlsx 导出。 */
@RestController
@RequestMapping("/api/ehr/import-export")
@RequiredArgsConstructor
public class ImportExportController {

    private final ExcelImportService importService;
    private final ExcelExportService exportService;

    /** 下载员工导入模板（四 sheet） */
    @GetMapping("/import/template")
    @Permit(PermissionCodes.EMPLOYEE_IMPORT)
    public ResponseEntity<byte[]> template() {
        return xlsx(importService.template(), "employee-import-template.xlsx");
    }

    /** 上传并解析预览：不落员工数据，只返回校验结果 */
    @PostMapping("/import")
    @Permit(PermissionCodes.EMPLOYEE_IMPORT)
    public R<ImportVos.Preview> preview(@RequestPart("file") MultipartFile file) {
        return R.ok(importService.preview(file, SecurityUtils.currentUserId()));
    }

    /** 确认导入：失败行不写库，错误明细生成独立 xlsx */
    @PostMapping("/import/{taskId}/confirm")
    @Permit(PermissionCodes.EMPLOYEE_IMPORT)
    public R<ImportVos.Result> confirm(@PathVariable Long taskId) {
        return R.ok(importService.confirm(taskId, SecurityUtils.currentUserId()));
    }

    @GetMapping("/import/{taskId}")
    @Permit(PermissionCodes.EMPLOYEE_IMPORT)
    public R<ImportVos.TaskView> task(@PathVariable Long taskId) {
        return R.ok(importService.task(taskId, SecurityUtils.currentUserId()));
    }

    @GetMapping("/export/employees")
    @Permit(PermissionCodes.EMPLOYEE_EXPORT)
    public ResponseEntity<byte[]> exportEmployees(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) Integer status) {
        return xlsx(exportService.employees(keyword, orgId, status), "employees-" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/export/contracts")
    @Permit(PermissionCodes.CONTRACT_VIEW)
    public ResponseEntity<byte[]> exportContracts() {
        return xlsx(exportService.contracts(), "contracts-" + LocalDate.now() + ".xlsx");
    }

    @GetMapping("/export/attendance")
    @Permit(PermissionCodes.OVERTIME_VIEW)
    public ResponseEntity<byte[]> exportAttendance(@RequestParam(required = false) String month) {
        return xlsx(exportService.attendance(month), "attendance-" + LocalDate.now() + ".xlsx");
    }

    private ResponseEntity<byte[]> xlsx(byte[] bytes, String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("attachment", fileName);
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
