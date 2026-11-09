package com.laimengchao.sso.ehr.module.attachment.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.module.attachment.service.AttachmentService;
import com.laimengchao.sso.ehr.module.attachment.vo.AttachmentVO;
import com.laimengchao.sso.ehr.security.Permit;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 附件上传/下载：上传给请假申请人用，下载本人或有审批/HR 权限的人可用。 */
@RestController
@RequestMapping("/api/ehr/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @GetMapping
    @Permit({PermissionCodes.CONTRACT_VIEW, PermissionCodes.EMPLOYEE_VIEW})
    public R<List<AttachmentVO>> list(@RequestParam String bizType, @RequestParam Long bizId) {
        return R.ok(attachmentService.list(bizType, bizId));
    }

    @PostMapping
    @Permit({PermissionCodes.LEAVE_APPLY, PermissionCodes.CONTRACT_MANAGE, PermissionCodes.EMPLOYEE_EDIT})
    public R<AttachmentVO> upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam(defaultValue = "TEMP") String bizType,
                                  @RequestParam(required = false) Long bizId) {
        SecurityUser user = SecurityUtils.currentUser().orElseThrow();
        return R.ok(attachmentService.upload(file, bizType, user.getAccountId(), bizId));
    }

    @DeleteMapping("/{id}")
    @Permit({PermissionCodes.CONTRACT_MANAGE, PermissionCodes.EMPLOYEE_EDIT})
    public R<Void> delete(@PathVariable Long id) {
        SecurityUser user = SecurityUtils.currentUser().orElseThrow();
        attachmentService.delete(id, user.getAccountId());
        return R.ok();
    }

    @GetMapping("/{id}/download")
    @Permit({PermissionCodes.LEAVE_VIEW, PermissionCodes.CONTRACT_VIEW, PermissionCodes.EMPLOYEE_VIEW})
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        SecurityUser user = SecurityUtils.currentUser().orElseThrow();
        boolean canViewAll = user.isSuperAdmin()
                || user.getPermissions().contains(PermissionCodes.LEAVE_APPROVE)
                || user.getPermissions().contains(PermissionCodes.LEAVE_QUOTA_ADJUST);
        Resource resource = attachmentService.download(id, user.getAccountId(), canViewAll);
        String name = URLEncoder.encode(attachmentService.fileName(id), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + name)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
