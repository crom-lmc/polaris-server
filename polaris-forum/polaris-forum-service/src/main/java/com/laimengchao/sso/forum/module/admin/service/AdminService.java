package com.laimengchao.sso.forum.module.admin.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.forum.module.admin.dto.CommentDeleteRequest;
import com.laimengchao.sso.forum.module.admin.dto.ExchangeFulfillmentRequest;
import com.laimengchao.sso.forum.module.admin.dto.SensitiveWordEnabledRequest;
import com.laimengchao.sso.forum.module.admin.dto.SensitiveWordRequest;
import com.laimengchao.sso.forum.module.admin.dto.ThreadStatusUpdateRequest;
import com.laimengchao.sso.forum.module.admin.entity.SensitiveWord;
import com.laimengchao.sso.forum.module.admin.mapper.AdminMapper;
import com.laimengchao.sso.forum.module.admin.mapper.SensitiveWordMapper;
import com.laimengchao.sso.forum.module.admin.vo.AdminCommentVO;
import com.laimengchao.sso.forum.module.admin.vo.AdminThreadVO;
import com.laimengchao.sso.forum.module.admin.vo.SensitiveWordVO;
import com.laimengchao.sso.forum.module.forum.dto.ForumCommentDetailDTO;
import com.laimengchao.sso.forum.module.forum.entity.ForumComment;
import com.laimengchao.sso.forum.module.forum.entity.ForumExchange;
import com.laimengchao.sso.forum.module.forum.entity.ForumThread;
import com.laimengchao.sso.forum.module.forum.mapper.ForumMapper;
import com.laimengchao.sso.forum.module.forum.service.ForumService;
import com.laimengchao.sso.forum.module.forum.vo.CommentVO;
import com.laimengchao.sso.forum.module.taxonomy.service.TaxonomyService;
import com.laimengchao.sso.forum.security.ForumAdminGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminService {

    private static final Set<String> KINDS = Set.of("feed", "topic", "question", "article");

    private final AdminMapper mapper;
    private final ForumMapper forumMapper;
    private final SensitiveWordMapper sensitiveWordMapper;
    private final ForumAdminGuard guard;
    private final ForumService forumService;
    private final TaxonomyService taxonomyService;

    public List<AdminThreadVO> threads(String kind, String board, String keyword, Boolean deleted) {
        guard.require("forum:content:view");
        if (kind != null && !kind.isBlank() && !KINDS.contains(kind)) throw param("不支持的内容类型");
        if (board != null && !board.isBlank()) taxonomyService.requireBoard(board, false);
        String q = keyword == null ? "" : keyword.trim();
        if (q.length() > 80) throw param("搜索关键词不能超过 80 个字符");
        return mapper.listThreads(kind, board, q, deleted).stream()
                .map(this::threadView).toList();
    }

    public AdminThreadVO updateThread(long id, ThreadStatusUpdateRequest body) {
        if (body.fieldCount() != 1) throw param("每次只能更新一种内容状态");
        // 先按操作字段定权限码并校验，再读取内容，避免无权限请求探测内容是否存在
        String permission;
        if (body.isDeletedPresent()) {
            permission = "forum:content:delete";
        } else if (body.isPinnedPresent()) {
            permission = "forum:content:pin";
        } else if (body.isSolvedPresent()) {
            permission = "forum:content:solve";
        } else if (body.isRecommendedPresent()) {
            permission = "forum:content:recommend";
        } else {
            throw param("不支持的内容状态字段");
        }
        guard.require(permission);
        ForumThread thread = requireThread(id);
        if (body.isDeletedPresent()) {
            mapper.setThreadDeleted(id, bool(body.getDeleted(), "deleted"));
        } else if (body.isPinnedPresent()) {
            mapper.setThreadPinned(id, bool(body.getPinned(), "pinned"));
        } else if (body.isSolvedPresent()) {
            boolean solved = bool(body.getSolved(), "solved");
            if (solved && !"question".equals(thread.getKind())) throw param("仅问题可以标记为已解决");
            mapper.setThreadSolved(id, solved);
        } else {
            mapper.setThreadRecommended(id, bool(body.getRecommended(), "recommended"));
        }
        return threadView(forumMapper.findThreadAny(id));
    }

    public List<AdminCommentVO> comments(Long threadId, String keyword, Boolean deleted) {
        guard.require("forum:comment:view");
        String q = keyword == null ? "" : keyword.trim();
        if (q.length() > 80) throw param("搜索关键词不能超过 80 个字符");
        return mapper.listComments(threadId, q, deleted).stream().map(this::commentView).toList();
    }

    @Transactional
    public AdminCommentVO setCommentDeleted(long id, CommentDeleteRequest body) {
        guard.require("forum:comment:delete");
        boolean deleted = bool(body.getDeleted(), "deleted");
        ForumComment comment = mapper.commentForUpdate(id);
        if (comment == null) throw new BizException(ErrorCode.NOT_FOUND, "评论不存在");
        boolean current = intValue(comment.getDeleted()) == 1;
        if (current != deleted) {
            if (mapper.setCommentDeleted(id, deleted) != 1) throw new BizException(ErrorCode.CONFLICT, "评论状态已变化");
            mapper.changeReplies(comment.getThreadId(), deleted ? -1 : 1);
        }
        return commentView(mapper.findComment(id));
    }

    public List<SensitiveWordVO> sensitiveWords() {
        guard.require("forum:sensitive:view");
        return sensitiveWordMapper.listAll().stream().map(this::sensitiveWordView).toList();
    }

    public SensitiveWordVO addSensitiveWord(SensitiveWordRequest body) {
        guard.require("forum:sensitive:add");
        String word = text(body.getWord()).trim();
        if (word.isBlank() || word.length() > 100) throw param("敏感词长度应为 1-100 个字符");
        SensitiveWord values = new SensitiveWord();
        values.setId(IdWorker.getId());
        values.setWord(word);
        values.setEnabled(true);
        try {
            sensitiveWordMapper.insert(values);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.CONFLICT, "敏感词已存在");
        }
        return sensitiveWordView(values);
    }

    public void setSensitiveWordEnabled(long id, SensitiveWordEnabledRequest body) {
        guard.require("forum:sensitive:toggle");
        if (sensitiveWordMapper.setEnabled(id, bool(body.getEnabled(), "enabled")) != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "敏感词不存在");
        }
    }

    public void deleteSensitiveWord(long id) {
        guard.require("forum:sensitive:delete");
        if (sensitiveWordMapper.delete(id) != 1) throw new BizException(ErrorCode.NOT_FOUND, "敏感词不存在");
    }

    public List<ForumExchange> exchanges(String status) {
        guard.require("forum:exchange:view");
        String value = status == null ? "" : status.trim().toUpperCase();
        if (!value.isBlank() && !Set.of("PENDING_ISSUE", "SHIPPING", "ISSUED").contains(value)) throw param("兑换状态不正确");
        return mapper.listExchanges(value);
    }

    @Transactional
    public ForumExchange shipExchange(long id, ExchangeFulfillmentRequest body) {
        guard.require("forum:exchange:ship");
        String trackingNo = text(body.getTrackingNo()).trim();
        String remark = remark(body);
        if (trackingNo.isBlank()) throw param("快递单号不能为空");
        if (trackingNo.length() > 100) throw param("快递单号不能超过 100 个字符");
        if (remark.length() > 500) throw param("发放备注不能超过 500 个字符");
        ForumExchange exchange = mapper.exchangeForUpdate(id);
        if (exchange == null) throw new BizException(ErrorCode.NOT_FOUND, "兑换记录不存在");
        if (!"PENDING_ISSUE".equals(exchange.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "仅待发放记录可以登记快递单号");
        }
        if (mapper.ship(id, trackingNo, remark) != 1) {
            throw new BizException(ErrorCode.CONFLICT, "仅待发放记录可以登记快递单号");
        }
        return mapper.findExchange(id);
    }

    @Transactional
    public ForumExchange issueExchange(long id, ExchangeFulfillmentRequest body) {
        SecurityUser user = guard.require("forum:exchange:issue");
        String remark = remark(body);
        if (remark.length() > 500) throw param("发放备注不能超过 500 个字符");
        ForumExchange exchange = mapper.exchangeForUpdate(id);
        if (exchange == null) throw new BizException(ErrorCode.NOT_FOUND, "兑换记录不存在");
        if (!"SHIPPING".equals(exchange.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "仅物流运输中的记录可以确认线下签收");
        }
        if (mapper.issue(id, user.getAccountId(), user.getUsername(), remark) != 1) {
            throw new BizException(ErrorCode.CONFLICT, "仅物流运输中的记录可以确认线下签收");
        }
        return mapper.findExchange(id);
    }

    private String remark(ExchangeFulfillmentRequest body) {
        return text(body.isIssueRemarkPresent() ? body.getIssueRemark() : body.getRemark()).trim();
    }

    private SensitiveWordVO sensitiveWordView(SensitiveWord row) {
        SensitiveWordVO view = new SensitiveWordVO();
        view.setId(row.getId());
        view.setWord(row.getWord());
        view.setEnabled(Boolean.TRUE.equals(row.getEnabled()));
        view.setCreatedAt(row.getCreatedAt());
        view.setUpdatedAt(row.getUpdatedAt());
        return view;
    }

    private ForumThread requireThread(long id) {
        ForumThread row = forumMapper.findThreadAny(id);
        if (row == null) throw new BizException(ErrorCode.NOT_FOUND, "内容不存在");
        return row;
    }

    private AdminThreadVO threadView(ForumThread row) {
        AdminThreadVO view = forumService.threadView(row, new AdminThreadVO());
        view.setDeleted(intValue(row.getDeleted()) == 1);
        return view;
    }

    private AdminCommentVO commentView(ForumComment row) {
        CommentVO base = forumService.commentView(row);
        AdminCommentVO view = new AdminCommentVO();
        view.setId(base.getId());
        view.setThreadId(base.getThreadId());
        view.setAuthorAccountId(base.getAuthorAccountId());
        view.setContent(text(row.getContent()));
        view.setParentCommentId(base.getParentCommentId());
        view.setLikes(base.getLikes());
        view.setDeleted(intValue(row.getDeleted()) == 1);
        view.setCreatedAt(base.getCreatedAt());
        view.setTime(base.getTime());
        view.setAuthor(base.getAuthor());
        view.setLiked(base.getLiked());
        view.setReplies(base.getReplies());
        if (row instanceof ForumCommentDetailDTO detail) view.setThreadTitle(detail.getThreadTitle());
        return view;
    }

    private boolean bool(Object value, String name) {
        if (value instanceof Boolean bool) return bool;
        if ("true".equalsIgnoreCase(text(value)) || "false".equalsIgnoreCase(text(value))) {
            return Boolean.parseBoolean(text(value));
        }
        throw param(name + " 必须为布尔值");
    }

    private BizException param(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? 0 : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException e) { return 0; }
    }
}
