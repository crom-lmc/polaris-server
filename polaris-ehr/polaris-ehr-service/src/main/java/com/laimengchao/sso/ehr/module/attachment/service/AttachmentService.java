package com.laimengchao.sso.ehr.module.attachment.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.module.attachment.entity.Attachment;
import com.laimengchao.sso.ehr.module.attachment.mapper.AttachmentMapper;
import com.laimengchao.sso.ehr.module.attachment.vo.AttachmentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * 最小附件能力（P1-1 的 ehr_attachment 先落这一版）：上传后得到附件ID，
 * 业务提交时把附件ID带上，成功后由业务服务 bind。文件名只进元数据，落盘用雪花ID，避免路径穿越。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final long MAX_SIZE = 20L * 1024 * 1024;

    private final AttachmentMapper attachmentMapper;

    @Value("${ehr.file-root:./data/attachments}")
    private String fileRoot;

    public AttachmentVO upload(MultipartFile file, String bizType, Long accountId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择要上传的文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单个附件不能超过 20MB");
        }
        String type = bizType == null || bizType.isBlank() ? "TEMP" : bizType.trim().toUpperCase(Locale.ROOT);
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String suffix = suffixOf(original);
        long id = IdWorker.getId();
        String key = type + "/" + id + suffix;
        Path target = Paths.get(fileRoot).toAbsolutePath().normalize().resolve(key);
        try {
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException e) {
            log.error("附件落盘失败 key={}", key, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "附件保存失败，请稍后重试");
        }
        Attachment a = new Attachment();
        a.setId(id);
        a.setBizType(type);
        a.setBizId(0L);
        a.setFileName(original);
        a.setFileKey(key);
        a.setSize(file.getSize());
        a.setMime(file.getContentType() == null ? "" : file.getContentType());
        a.setUploadedBy(accountId);
        attachmentMapper.insert(a);
        AttachmentVO vo = new AttachmentVO();
        vo.setId(id);
        vo.setFileName(original);
        vo.setSize(file.getSize());
        vo.setMime(a.getMime());
        return vo;
    }

    /** 提交请假时校验附件归属，防止拿别人的附件ID */
    public Attachment requireOwned(Long attachmentId, Long accountId) {
        Attachment a = attachmentMapper.selectById(attachmentId);
        if (a == null || (accountId != null && !accountId.equals(a.getUploadedBy()))) {
            throw new BizException(EhrErrorCode.ATTACHMENT_NOT_FOUND, "附件不存在或无权使用");
        }
        return a;
    }

    /** 业务单据落库后绑定附件，避免未提交的附件被其他单据复用 */
    public void bind(Long attachmentId, String bizType, Long bizId) {
        attachmentMapper.update(null, new LambdaUpdateWrapper<Attachment>()
                .eq(Attachment::getId, attachmentId)
                .set(Attachment::getBizType, bizType)
                .set(Attachment::getBizId, bizId));
    }

    public String nameOf(Long attachmentId) {
        if (attachmentId == null) {
            return "";
        }
        Attachment a = attachmentMapper.selectById(attachmentId);
        return a == null ? "" : a.getFileName();
    }

    public Resource download(Long attachmentId, Long accountId, boolean canViewAll) {
        Attachment a = attachmentMapper.selectById(attachmentId);
        if (a == null) {
            throw new BizException(EhrErrorCode.ATTACHMENT_NOT_FOUND, "附件不存在");
        }
        if (!canViewAll && accountId != null && !accountId.equals(a.getUploadedBy())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权下载该附件");
        }
        Path p = Paths.get(fileRoot).toAbsolutePath().normalize().resolve(a.getFileKey());
        if (!Files.exists(p)) {
            throw new BizException(EhrErrorCode.ATTACHMENT_NOT_FOUND, "附件文件已丢失");
        }
        return new FileSystemResource(p);
    }

    public String fileName(Long attachmentId) {
        return nameOf(attachmentId);
    }

    private String suffixOf(String name) {
        int i = name.lastIndexOf('.');
        if (i < 0 || i > name.length() - 2) {
            return "";
        }
        String s = name.substring(i).toLowerCase(Locale.ROOT);
        return s.matches("\\.(pdf|png|jpg|jpeg|gif|webp|doc|docx|xls|xlsx|zip|rar)") ? s : "";
    }
}
