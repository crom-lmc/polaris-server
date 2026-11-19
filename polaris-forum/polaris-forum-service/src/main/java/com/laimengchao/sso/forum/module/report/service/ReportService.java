package com.laimengchao.sso.forum.module.report.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.forum.module.forum.entity.ForumComment;
import com.laimengchao.sso.forum.module.forum.mapper.ForumMapper;
import com.laimengchao.sso.forum.module.notification.service.NotificationService;
import com.laimengchao.sso.forum.module.report.dto.ReportCreateRequest;
import com.laimengchao.sso.forum.module.report.dto.ReportDetailDTO;
import com.laimengchao.sso.forum.module.report.dto.ReportHandleRequest;
import com.laimengchao.sso.forum.module.report.entity.Report;
import com.laimengchao.sso.forum.module.report.mapper.ReportMapper;
import com.laimengchao.sso.forum.module.report.vo.ReportVO;
import com.laimengchao.sso.forum.security.ForumAdminGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Set<String> STATUSES = Set.of("PENDING", "RESOLVED", "REJECTED");

    private final ReportMapper mapper;
    private final ForumMapper forumMapper;
    private final ForumAdminGuard guard;
    private final NotificationService notificationService;

    public ReportVO create(ReportCreateRequest body) {
        SecurityUser user = SecurityUtils.currentUser().orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        String targetType = text(body.getTargetType()).trim().toLowerCase();
        long targetId = longValue(body.getTargetId());
        if (!Set.of("thread", "comment").contains(targetType) || targetId <= 0) throw param("举报目标不正确");
        Long threadId;
        if ("thread".equals(targetType)) {
            if (forumMapper.findThread(targetId) == null) throw new BizException(ErrorCode.NOT_FOUND, "内容不存在或已删除");
            threadId = targetId;
        } else {
            ForumComment comment = forumMapper.findComment(targetId);
            if (comment == null) throw new BizException(ErrorCode.NOT_FOUND, "评论不存在或已删除");
            threadId = comment.getThreadId();
            if (forumMapper.findThread(threadId) == null) throw new BizException(ErrorCode.NOT_FOUND, "内容不存在或已删除");
        }
        String reason = text(body.getReason()).trim();
        String detail = text(body.getDetail()).trim();
        if (reason.isBlank() || reason.length() > 32) throw param("举报原因长度应为 1-32 个字符");
        if (detail.length() > 1000) throw param("举报说明不能超过 1000 个字符");
        Report values = new Report();
        values.setId(IdWorker.getId());
        values.setReporterAccountId(user.getAccountId());
        values.setTargetType(targetType);
        values.setTargetId(targetId);
        values.setThreadId(threadId);
        values.setReason(reason);
        values.setDetail(detail);
        try {
            mapper.insert(values);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.CONFLICT, "该内容已举报，请勿重复提交");
        }
        return view(mapper.findById(values.getId()));
    }

    public List<ReportVO> list(String status, String targetType) {
        guard.require("forum:report");
        String value = status == null ? "" : status.trim().toUpperCase();
        if (!value.isBlank() && !STATUSES.contains(value)) throw param("举报状态不正确");
        String type = targetType == null ? "" : targetType.trim().toLowerCase();
        if (!type.isBlank() && !Set.of("thread", "comment").contains(type)) throw param("举报目标类型仅支持 thread 或 comment");
        return mapper.list(value, type).stream().map(this::view).toList();
    }

    @Transactional
    public ReportVO handle(long id, ReportHandleRequest body) {
        SecurityUser user = guard.require("forum:report");
        if (!body.getUnsupportedFields().isEmpty()) throw param("举报处理仅支持 status 和 remark");
        String status = text(body.getStatus()).trim().toUpperCase();
        if (!Set.of("RESOLVED", "REJECTED").contains(status)) throw param("处理状态仅支持 RESOLVED 或 REJECTED");
        String remark = text(body.getRemark()).trim();
        if (remark.length() > 500) throw param("处理说明不能超过 500 个字符");
        Report report = mapper.forUpdate(id);
        if (report == null) throw new BizException(ErrorCode.NOT_FOUND, "举报记录不存在");
        if (!"PENDING".equals(report.getStatus())) throw new BizException(ErrorCode.CONFLICT, "该举报已处理，不能重复处理");
        if (mapper.handle(id, status, user.getAccountId(), user.getUsername(), remark) != 1) {
            throw new BizException(ErrorCode.CONFLICT, "该举报已处理，不能重复处理");
        }
        ReportDetailDTO handled = mapper.findById(id);
        notificationService.onReportResult(handled);
        return view(handled);
    }

    private ReportVO view(ReportDetailDTO row) {
        ReportVO view = new ReportVO();
        view.setId(row.getId());
        view.setReporterAccountId(row.getReporterAccountId());
        view.setReporterName(row.getReporterName());
        view.setTargetType(row.getTargetType());
        view.setTargetId(row.getTargetId());
        view.setThreadId(row.getThreadId());
        view.setReason(row.getReason());
        view.setDetail(row.getDetail());
        view.setStatus(row.getStatus());
        view.setHandlerAccountId(row.getHandlerAccountId());
        view.setHandlerName(row.getHandlerName());
        view.setHandleResult(row.getHandleResult());
        view.setHandledAt(row.getHandledAt());
        view.setCreatedAt(row.getCreatedAt());
        view.setThreadTitle(row.getThreadTitle());
        view.setTargetContent(row.getTargetContent());
        return view;
    }

    private BizException param(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null || String.valueOf(value).isBlank() ? 0 : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException e) { return 0; }
    }
}
