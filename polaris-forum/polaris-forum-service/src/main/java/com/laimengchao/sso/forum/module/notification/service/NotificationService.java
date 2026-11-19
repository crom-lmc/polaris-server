package com.laimengchao.sso.forum.module.notification.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.forum.module.badge.entity.Badge;
import com.laimengchao.sso.forum.module.forum.entity.ForumComment;
import com.laimengchao.sso.forum.module.forum.entity.ForumThread;
import com.laimengchao.sso.forum.module.notification.dto.NotificationListDTO;
import com.laimengchao.sso.forum.module.notification.entity.Notification;
import com.laimengchao.sso.forum.module.notification.mapper.NotificationMapper;
import com.laimengchao.sso.forum.module.notification.vo.NotificationCountVO;
import com.laimengchao.sso.forum.module.notification.vo.NotificationReadAllVO;
import com.laimengchao.sso.forum.module.notification.vo.NotificationVO;
import com.laimengchao.sso.forum.module.report.entity.Report;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Set<String> TYPES = Set.of("THREAD_COMMENT", "COMMENT_REPLY", "BADGE_AWARDED", "REPORT_RESULT");

    private final NotificationMapper mapper;

    public void onComment(ForumThread thread, ForumComment parent, ForumComment comment) {
        long threadId = thread.getId();
        long actorId = longValue(comment.getAuthorAccountId());
        long threadAuthorId = longValue(thread.getAuthorAccountId());
        long commentId = comment.getId();
        String summary = abbreviate(text(comment.getContent()));
        if (parent != null) {
            long parentAuthorId = longValue(parent.getAuthorAccountId());
            if (parentAuthorId > 0 && parentAuthorId != actorId) {
                insert(parentAuthorId, actorId, "COMMENT_REPLY", commentId, threadId, "收到新的回复", summary);
            }
        }
        if (threadAuthorId > 0 && threadAuthorId != actorId
                && (parent == null || threadAuthorId != longValue(parent.getAuthorAccountId()))) {
            insert(threadAuthorId, actorId, "THREAD_COMMENT", commentId, threadId, "帖子收到新的评论", summary);
        }
    }

    public void onBadgeAwarded(long accountId, long memberBadgeId, Badge badge) {
        insert(accountId, null, "BADGE_AWARDED", memberBadgeId, null,
                "获得新勋章", "恭喜你获得「" + text(badge.getName()) + "」勋章");
    }

    public void onReportResult(Report report) {
        long reportId = report.getId();
        long threadId = longValue(report.getThreadId());
        long handlerId = longValue(report.getHandlerAccountId());
        String result = text(report.getHandleResult()).trim();
        String status = "RESOLVED".equals(report.getStatus()) ? "已处理" : "已驳回";
        insert(report.getReporterAccountId(), handlerId > 0 ? handlerId : null, "REPORT_RESULT",
                reportId, threadId > 0 ? threadId : null, "举报处理结果",
                "你的举报" + status + (result.isBlank() ? "" : "：" + abbreviate(result)));
    }

    public List<NotificationVO> list(String type, Boolean unread) {
        SecurityUser user = requireUser();
        String value = type == null ? "" : type.trim().toUpperCase();
        if (!value.isBlank() && !TYPES.contains(value)) throw param("消息类型不正确");
        return mapper.list(user.getAccountId(), value, unread).stream().map(this::view).toList();
    }

    public NotificationCountVO unreadCount() {
        return new NotificationCountVO(mapper.unreadCount(requireUser().getAccountId()));
    }

    public void markRead(long id) {
        if (mapper.markRead(id, requireUser().getAccountId()) != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "消息不存在");
        }
    }

    public NotificationReadAllVO markAllRead() {
        return new NotificationReadAllVO(mapper.markAllRead(requireUser().getAccountId()));
    }

    private void insert(long recipientId, Long actorId, String type, long bizId, Long threadId,
                        String title, String summary) {
        Notification values = new Notification();
        values.setId(IdWorker.getId());
        values.setRecipientAccountId(recipientId);
        values.setActorAccountId(actorId);
        values.setType(type);
        values.setBizId(bizId);
        values.setThreadId(threadId);
        values.setTitle(title);
        values.setSummary(summary);
        mapper.insert(values);
    }

    private NotificationVO view(NotificationListDTO row) {
        NotificationVO view = new NotificationVO();
        view.setId(row.getId());
        view.setRecipientAccountId(row.getRecipientAccountId());
        view.setActorAccountId(row.getActorAccountId());
        view.setActorName(row.getActorName());
        view.setActorAvatarAttachmentId(row.getActorAvatarAttachmentId());
        view.setType(row.getType());
        view.setBizId(row.getBizId());
        view.setThreadId(row.getThreadId());
        view.setThreadTitle(row.getThreadTitle());
        view.setTitle(row.getTitle());
        view.setSummary(row.getSummary());
        view.setReadAt(row.getReadAt());
        view.setCreatedAt(row.getCreatedAt());
        view.setRead(row.getReadAt() != null);
        long avatarId = longValue(row.getActorAvatarAttachmentId());
        view.setActorAvatarUrl(avatarId > 0 ? "/api/forum/public/attachments/" + avatarId : null);
        return view;
    }

    private String abbreviate(String value) {
        return value.length() <= 120 ? value : value.substring(0, 120);
    }

    private SecurityUser requireUser() {
        return SecurityUtils.currentUser().orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
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
