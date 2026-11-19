package com.laimengchao.sso.forum.module.attachment.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.forum.module.attachment.service.AttachmentService;
import com.laimengchao.sso.forum.module.attachment.vo.AttachmentVO;
import com.laimengchao.sso.forum.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/forum/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Permit(value = PermissionCodes.FORUM_ATTACHMENT_UPLOAD, app = PermissionCodes.APP_FORUM)
    public R<AttachmentVO> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(defaultValue = "ATTACHMENT") String usageType) {
        return R.ok(attachmentService.upload(file, usageType));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable long id) {
        return attachmentService.download(id, false);
    }
}
