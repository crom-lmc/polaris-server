package com.laimengchao.sso.ehr.module.attachment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.module.attachment.entity.Attachment;
import com.laimengchao.sso.ehr.module.attachment.mapper.AttachmentMapper;
import com.laimengchao.sso.ehr.module.attachment.vo.AttachmentVO;
import com.laimengchao.sso.ehr.module.contract.entity.Contract;
import com.laimengchao.sso.ehr.module.contract.mapper.ContractMapper;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.security.DataScopeService;
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
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

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
    private final EmployeeMapper employeeMapper;
    private final ContractMapper contractMapper;
    private final DataScopeService dataScopeService;

    @Value("${ehr.file-root:./data/attachments}")
    private String fileRoot;

    public AttachmentVO upload(MultipartFile file, String bizType, Long accountId, Long bizId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择要上传的文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单个附件不能超过 20MB");
        }
        String type = normalizeType(bizType);
        if (("EMPLOYEE".equals(type) || "CONTRACT".equals(type)) && bizId != null) {
            checkReadable(type, bizId);
        }
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
        a.setBizId(bizId == null ? 0L : bizId);
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
        vo.setBizType(type);
        vo.setBizId(a.getBizId());
        vo.setUploadedBy(accountId);
        vo.setCreatedAt(a.getCreatedAt());
        return vo;
    }

    /** 员工档案 / 合同附件列表：按数据范围校验归属 */
    public List<AttachmentVO> list(String bizType, Long bizId) {
        String type = normalizeType(bizType);
        checkReadable(type, bizId);
        return attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                        .eq(Attachment::getBizType, type)
                        .eq(Attachment::getBizId, bizId)
                        .orderByDesc(Attachment::getCreatedAt)
                        .orderByDesc(Attachment::getId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    /** 删除附件（逻辑删除，保留落盘文件便于追溯）；仅业务数据范围内可删 */
    public void delete(Long attachmentId, Long accountId) {
        Attachment a = attachmentMapper.selectById(attachmentId);
        if (a == null) {
            throw new BizException(EhrErrorCode.ATTACHMENT_NOT_FOUND, "附件不存在");
        }
        if ("EMPLOYEE".equals(a.getBizType()) || "CONTRACT".equals(a.getBizType())) {
            checkReadable(a.getBizType(), a.getBizId());
        } else if (accountId != null && !accountId.equals(a.getUploadedBy())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能删除自己上传的附件");
        }
        attachmentMapper.deleteById(attachmentId);
    }

    private AttachmentVO toVO(Attachment a) {
        AttachmentVO vo = new AttachmentVO();
        vo.setId(a.getId());
        vo.setFileName(a.getFileName());
        vo.setSize(a.getSize());
        vo.setMime(a.getMime());
        vo.setBizType(a.getBizType());
        vo.setBizId(a.getBizId());
        vo.setUploadedBy(a.getUploadedBy());
        vo.setCreatedAt(a.getCreatedAt());
        return vo;
    }

    private String normalizeType(String bizType) {
        return bizType == null || bizType.isBlank() ? "TEMP" : bizType.trim().toUpperCase(Locale.ROOT);
    }

    /** 档案 / 合同附件按员工数据范围；临时附件只允许本人 */
    private void checkReadable(String type, Long bizId) {
        if ("EMPLOYEE".equals(type)) {
            requireAccessibleEmployee(bizId);
        } else if ("CONTRACT".equals(type)) {
            Contract c = bizId == null ? null : contractMapper.selectById(bizId);
            if (c == null) {
                throw new BizException(EhrErrorCode.CONTRACT_NOT_FOUND, "合同不存在");
            }
            requireAccessibleEmployee(c.getEmployeeId());
        } else if ("TEMP".equals(type)) {
            var user = com.laimengchao.sso.ehr.security.SecurityUtils.currentUser().orElse(null);
            if (user == null || !user.isSuperAdmin()) {
                throw new BizException(ErrorCode.FORBIDDEN, "临时附件仅本人可操作");
            }
        }
    }

    private Employee requireAccessibleEmployee(Long employeeId) {
        Employee emp = employeeId == null ? null : employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BizException(EhrErrorCode.EMP_NOT_FOUND, "员工不存在");
        }
        if (!dataScopeService.canAccess(emp)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该员工的附件");
        }
        return emp;
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
        // 档案 / 合同附件按员工数据范围放行；请假附件沿用「本人或有审批/HR权限」
        if ("EMPLOYEE".equals(a.getBizType())) {
            requireAccessibleEmployee(a.getBizId());
        } else if ("CONTRACT".equals(a.getBizType())) {
            Contract c = contractMapper.selectById(a.getBizId());
            if (c == null) {
                throw new BizException(EhrErrorCode.CONTRACT_NOT_FOUND, "合同不存在");
            }
            requireAccessibleEmployee(c.getEmployeeId());
        } else if (!canViewAll && accountId != null && !accountId.equals(a.getUploadedBy())) {
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
