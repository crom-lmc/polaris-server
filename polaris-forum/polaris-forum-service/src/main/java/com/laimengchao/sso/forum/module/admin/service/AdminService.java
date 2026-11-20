package com.laimengchao.sso.forum.module.admin.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.forum.module.admin.dto.CommentDeleteRequest;
import com.laimengchao.sso.forum.module.admin.dto.ExchangeFulfillmentRequest;
import com.laimengchao.sso.forum.module.admin.dto.GiftRequest;
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
import com.laimengchao.sso.forum.module.forum.entity.ForumGift;
import com.laimengchao.sso.forum.module.forum.entity.ForumThread;
import com.laimengchao.sso.forum.module.forum.mapper.ForumMapper;
import com.laimengchao.sso.forum.module.forum.service.ForumService;
import com.laimengchao.sso.forum.module.forum.vo.CommentVO;
import com.laimengchao.sso.forum.module.level.service.LevelService;
import com.laimengchao.sso.forum.module.taxonomy.service.TaxonomyService;
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
    private final ForumService forumService;
    private final TaxonomyService taxonomyService;
    private final LevelService levelService;

    public List<AdminThreadVO> threads(String kind, String board, String keyword, Boolean deleted) {
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
        requireFieldPermission(permission);
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
        String q = keyword == null ? "" : keyword.trim();
        if (q.length() > 80) throw param("搜索关键词不能超过 80 个字符");
        return mapper.listComments(threadId, q, deleted).stream().map(this::commentView).toList();
    }

    @Transactional
    public AdminCommentVO setCommentDeleted(long id, CommentDeleteRequest body) {
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
        return sensitiveWordMapper.listAll().stream().map(this::sensitiveWordView).toList();
    }

    public SensitiveWordVO addSensitiveWord(SensitiveWordRequest body) {
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
        if (sensitiveWordMapper.setEnabled(id, bool(body.getEnabled(), "enabled")) != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "敏感词不存在");
        }
    }

    public void deleteSensitiveWord(long id) {
        if (sensitiveWordMapper.delete(id) != 1) throw new BizException(ErrorCode.NOT_FOUND, "敏感词不存在");
    }

    /* ==================== 礼品管理 ==================== */

    public List<ForumGift> adminGifts() {
        return forumMapper.adminListGifts();
    }

    public ForumGift createGift(GiftRequest body) {
        ForumGift values = new ForumGift();
        values.setId(IdWorker.getId());
        applyGift(values, body, true);
        forumMapper.insertGift(values);
        return requireGift(values.getId());
    }

    public ForumGift updateGift(long id, GiftRequest body) {
        ForumGift current = requireGift(id);
        ForumGift values = new ForumGift();
        values.setId(id);
        values.setName(body.isNamePresent() ? giftName(body.getName()) : current.getName());
        values.setCategory(body.isCategoryPresent() ? giftCategory(body.getCategory()) : current.getCategory());
        values.setDescription(body.isDescriptionPresent() ? giftDescription(body.getDescription()) : current.getDescription());
        values.setPoints(body.isPointsPresent() ? giftNonNegative(body.getPoints(), "所需积分") : nvl(current.getPoints()));
        values.setStock(body.isStockPresent() ? giftNonNegative(body.getStock(), "库存") : nvl(current.getStock()));
        values.setIcon(body.isIconPresent() ? giftIcon(body.getIcon()) : current.getIcon());
        values.setImageUrl(body.isImageUrlPresent() ? giftImageUrl(body.getImageUrl()) : current.getImageUrl());
        values.setMinLevel(body.isMinLevelPresent() ? giftMinLevel(body.getMinLevel()) : nvl(current.getMinLevel()));
        values.setStatus(body.isStatusPresent() ? giftStatus(body.getStatus()) : nvl(current.getStatus()));
        forumMapper.updateGift(values);
        return requireGift(id);
    }

    /** 新增时必填字段校验；编辑时逐字段可选，故只有 create 传 required=true。 */
    private void applyGift(ForumGift values, GiftRequest body, boolean required) {
        if (required || body.isNamePresent()) values.setName(giftName(body.getName()));
        if (required || body.isCategoryPresent()) values.setCategory(giftCategory(body.getCategory()));
        if (required || body.isDescriptionPresent()) values.setDescription(giftDescription(body.getDescription()));
        if (required || body.isPointsPresent()) values.setPoints(giftNonNegative(body.getPoints(), "所需积分"));
        if (required || body.isStockPresent()) values.setStock(giftNonNegative(body.getStock(), "库存"));
        if (required || body.isIconPresent()) values.setIcon(giftIcon(body.getIcon()));
        if (required || body.isImageUrlPresent()) values.setImageUrl(giftImageUrl(body.getImageUrl()));
        if (required || body.isMinLevelPresent()) values.setMinLevel(giftMinLevel(body.getMinLevel()));
        if (required || body.isStatusPresent()) values.setStatus(giftStatus(body.getStatus()));
    }

    private ForumGift requireGift(long id) {
        ForumGift row = forumMapper.findGiftById(id);
        if (row == null) throw new BizException(ErrorCode.NOT_FOUND, "礼品不存在");
        return row;
    }

    private String giftName(Object value) {
        String name = text(value).trim();
        if (name.isBlank() || name.length() > 100) throw param("礼品名称应为 1-100 个字符");
        return name;
    }

    private String giftCategory(Object value) {
        String category = text(value).trim();
        if (category.isBlank() || category.length() > 32) throw param("礼品分类应为 1-32 个字符");
        return category;
    }

    private String giftDescription(Object value) {
        String description = text(value).trim();
        if (description.length() > 300) throw param("礼品描述不能超过 300 个字符");
        return description;
    }

    private String giftIcon(Object value) {
        String icon = text(value).trim();
        return icon.isBlank() ? "gift" : (icon.length() > 32 ? icon.substring(0, 32) : icon);
    }

    private String giftImageUrl(Object value) {
        String url = text(value).trim();
        if (url.length() > 500) throw param("礼品图片地址不能超过 500 个字符");
        return url;
    }

    private int giftStatus(Object value) {
        int status = intValue(value);
        if (status != 0 && status != 1) throw param("上架状态不正确");
        return status;
    }

    private int giftMinLevel(Object value) {
        int level = intValue(value);
        if (level < 1 || level > levelService.maxLevel()) throw param("兑换等级应为 1-" + levelService.maxLevel());
        return level;
    }

    private int giftNonNegative(Object value, String name) {
        int number = intValue(value);
        if (number < 0) throw param(name + "不能为负数");
        if (number > 100000000) throw param(name + "超出范围");
        return number;
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    public List<ForumExchange> exchanges(String status) {
        String value = status == null ? "" : status.trim().toUpperCase();
        if (!value.isBlank() && !Set.of("PENDING_ISSUE", "SHIPPING", "ISSUED").contains(value)) throw param("兑换状态不正确");
        return mapper.listExchanges(value);
    }

    @Transactional
    public ForumExchange shipExchange(long id, ExchangeFulfillmentRequest body) {
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
        SecurityUser user = currentUser();
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

    /** 入口鉴权已由 {@code @Permit} 切面完成，这里只取当前操作人。 */
    private SecurityUser currentUser() {
        return SecurityUtils.currentUser().orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
    }

    /**
     * 内容状态是一个端点承载置顶/加精/已解决/删除四种操作，注解只能声明「任一即可」，
     * 因此按请求里实际出现的字段再做一次精确判权，避免只有置顶权的账号能删除内容。
     */
    private void requireFieldPermission(String permission) {
        SecurityUser user = currentUser();
        if (user.isSuperAdmin() || (user.getPermissions() != null && user.getPermissions().contains(permission))) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN, "无权限：" + permission);
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
