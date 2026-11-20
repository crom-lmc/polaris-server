package com.laimengchao.sso.forum.module.forum.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.forum.module.admin.service.SensitiveWordService;
import com.laimengchao.sso.forum.module.attachment.service.AttachmentService;
import com.laimengchao.sso.forum.module.badge.service.BadgeService;
import com.laimengchao.sso.forum.module.experience.service.ExperienceService;
import com.laimengchao.sso.forum.module.forum.dto.CommentCreateRequest;
import com.laimengchao.sso.forum.module.forum.dto.LikeToggleRequest;
import com.laimengchao.sso.forum.module.forum.dto.ThreadPublishRequest;
import com.laimengchao.sso.forum.module.forum.entity.ForumComment;
import com.laimengchao.sso.forum.module.forum.entity.ForumMember;
import com.laimengchao.sso.forum.module.forum.entity.ForumPointLog;
import com.laimengchao.sso.forum.module.forum.entity.ForumThread;
import com.laimengchao.sso.forum.module.forum.mapper.ForumMapper;
import com.laimengchao.sso.forum.module.forum.vo.AuthorVO;
import com.laimengchao.sso.forum.module.forum.vo.CommentVO;
import com.laimengchao.sso.forum.module.forum.vo.ForumMeVO;
import com.laimengchao.sso.forum.module.forum.vo.ForumMemberVO;
import com.laimengchao.sso.forum.module.forum.vo.LikeToggleVO;
import com.laimengchao.sso.forum.module.forum.vo.ThreadDetailVO;
import com.laimengchao.sso.forum.module.forum.vo.ThreadPageVO;
import com.laimengchao.sso.forum.module.forum.vo.ThreadVO;
import com.laimengchao.sso.forum.module.level.dto.LevelProgress;
import com.laimengchao.sso.forum.module.level.service.LevelService;
import com.laimengchao.sso.forum.module.notification.service.NotificationService;
import com.laimengchao.sso.forum.module.poll.dto.PollConfigDTO;
import com.laimengchao.sso.forum.module.poll.service.PollService;
import com.laimengchao.sso.forum.module.taxonomy.service.TaxonomyService;
import com.laimengchao.sso.forum.module.taxonomy.vo.BoardVO;
import com.laimengchao.sso.forum.service.ForumContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ForumService {

    private static final Set<String> KINDS = Set.of("feed", "topic", "question", "article");
    private static final String[] COLORS = {"#5b67f1", "#13a8a8", "#fa8c16", "#eb4b8d", "#1677ff", "#a855f7"};

    private final ForumMapper mapper;
    private final ObjectMapper objectMapper;
    private final SensitiveWordService sensitiveWordService;
    private final ForumContentService forumContentService;
    private final AttachmentService attachmentService;
    private final PollService pollService;
    private final NotificationService notificationService;
    private final BadgeService badgeService;
    private final TaxonomyService taxonomyService;
    private final ExperienceService experienceService;
    private final LevelService levelService;

    public List<BoardVO> boards() {
        return taxonomyService.boards();
    }

    public List<ThreadVO> list(String kind, String board, String keyword, String sort) {
        ListQuery query = normalizedListQuery(kind, board, keyword, sort);
        List<ForumThread> rows = mapper.listThreads(query.kind, query.board, query.keyword, query.sort, query.accountId);
        Map<Long, ForumMember> members = memberCache(rows.stream().map(ForumThread::getAuthorAccountId).toList());
        return rows.stream().map(row -> threadView(row, new ThreadVO(), members)).toList();
    }

    /** 服务端分页列表：每页只查当前页且不查正文，避免一次返回全部内容导致接口慢、渲染卡。 */
    public ThreadPageVO listPage(String kind, String board, String keyword, String sort, int page, int size) {
        ListQuery query = normalizedListQuery(kind, board, keyword, sort);
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        size = Math.min(size, 50); // 防止超大 size 拖慢接口
        long total = mapper.countThreads(query.kind, query.board, query.keyword, query.sort, query.accountId);
        long offset = (long) (page - 1) * size;
        List<ForumThread> rows = mapper.listThreadsPaged(query.kind, query.board, query.keyword, query.sort,
                query.accountId, offset, size);
        Map<Long, ForumMember> members = memberCache(rows.stream().map(ForumThread::getAuthorAccountId).toList());
        List<ThreadVO> records = rows.stream().map(row -> threadView(row, new ThreadVO(), members)).toList();
        ThreadPageVO result = new ThreadPageVO();
        result.setRecords(records);
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        return result;
    }

    /** 列表接口参数校验与归一，list 与 listPage 共用，保证筛选口径一致。 */
    private ListQuery normalizedListQuery(String kind, String board, String keyword, String sort) {
        if (kind != null && !kind.isBlank() && !KINDS.contains(kind)) throw param("不支持的内容类型");
        if (board != null && !board.isBlank()) taxonomyService.requireBoard(board, false);
        String q = keyword == null ? "" : keyword.trim();
        if (q.length() > 80) throw param("搜索关键词不能超过 80 个字符");
        String order = sort == null || sort.isBlank() ? "latest" : sort.trim().toLowerCase();
        if (!Set.of("latest", "hot", "recommended", "following").contains(order)) throw param("不支持的排序方式");
        Long accountId = SecurityUtils.currentUserId();
        if ("following".equals(order) && accountId == null) throw new BizException(ErrorCode.UNAUTHORIZED);
        return new ListQuery(kind, board, q, order, accountId);
    }

    private record ListQuery(String kind, String board, String keyword, String sort, Long accountId) {}

    @Transactional
    public ThreadDetailVO detail(long id) {
        ForumThread row = requiredThread(id);
        mapper.increaseViews(id);
        Long accountId = SecurityUtils.currentUserId();
        ThreadDetailVO view = threadView(row, new ThreadDetailVO());
        view.setViews(intValue(row.getViews()) + 1);
        view.setLiked(accountId != null && mapper.likeExists(accountId, "thread", id) > 0);
        view.setFavorited(accountId != null && mapper.favoriteExists(accountId, id) > 0);
        view.setAttachments(attachmentService.threadAttachments(id));
        view.setPoll(pollService.detail(id, accountId));
        return view;
    }

    public List<CommentVO> comments(long threadId) {
        requiredThread(threadId);
        Long accountId = SecurityUtils.currentUserId();
        List<ForumComment> rows = mapper.listComments(threadId);
        Map<Long, ForumMember> members = memberCache(rows.stream().map(ForumComment::getAuthorAccountId).toList());
        Set<Long> likedIds = accountId == null ? Set.of()
                : Set.copyOf(mapper.likeExistsBatch(accountId, "comment",
                        rows.stream().map(ForumComment::getId).toList()));
        Map<Long, CommentVO> topLevel = new LinkedHashMap<>();
        for (ForumComment row : rows) {
            if (longValue(row.getParentCommentId()) == 0) {
                CommentVO view = commentView(row, members);
                view.setLiked(likedIds.contains(row.getId()));
                view.setReplies(new ArrayList<>());
                topLevel.put(row.getId(), view);
            }
        }
        for (ForumComment row : rows) {
            long parentId = longValue(row.getParentCommentId());
            CommentVO parent = topLevel.get(parentId);
            if (parentId > 0 && parent != null && intValue(row.getDeleted()) == 0) {
                CommentVO view = commentView(row, members);
                view.setLiked(likedIds.contains(row.getId()));
                parent.getReplies().add(view);
            }
        }
        return new ArrayList<>(topLevel.values());
    }

    /** 列表预加载作者，把逐条 findMember 的 N+1 收敛为一次 IN 查询。 */
    private Map<Long, ForumMember> memberCache(List<Long> accountIds) {
        List<Long> ids = accountIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, ForumMember> cache = new HashMap<>();
        for (ForumMember member : mapper.findMembers(ids)) {
            cache.put(member.getAccountId(), member);
        }
        return cache;
    }

    @Transactional
    public ThreadDetailVO createThread(ThreadPublishRequest body) {
        return publish(requireUser(), body, null);
    }

    @Transactional
    public ThreadDetailVO publishDraft(ThreadPublishRequest body, long draftId) {
        return publish(requireUser(), body, draftId);
    }

    private ThreadDetailVO publish(SecurityUser user, ThreadPublishRequest body, Long draftId) {
        String kind = text(body.getKind(), "topic");
        String board = text(body.getBoard(), "general");
        if (!KINDS.contains(kind)) throw param("不支持的内容类型");
        taxonomyService.requireBoard(board, true);
        String title = text(body.getTitle(), "").trim();
        String content = forumContentService.sanitize(text(body.getContent(), ""));
        String plainContent = forumContentService.plainText(content);
        if ("feed".equals(kind)) title = "";
        if (!"feed".equals(kind) && (title.isBlank() || title.length() > 80)) throw param("标题长度应为 1-80 个字符");
        int maxContent = "feed".equals(kind) ? 5000 : 50000;
        if (plainContent.isBlank() || plainContent.length() > maxContent) throw param("正文长度应为 1-" + maxContent + " 个字符");
        if (content.length() > maxContent + 10000) throw param("正文格式内容过多，请减少分段或格式");
        int reward = "question".equals(kind) ? intValue(body.getReward()) : 0;
        if (reward < 0 || reward > 10000) throw param("悬赏积分范围为 0-10000");
        String tags = tagsJson(body.getTags());
        List<String> tagList = parseTags(tags);
        taxonomyService.preparePublishTags(board, tagList);
        long coverAttachmentId = longValue(body.getCoverAttachmentId());
        List<Long> attachmentIds = attachmentService.attachmentIds(body.getAttachmentIds());
        PollConfigDTO pollConfig = pollService.validateForPublish(kind, body.getPoll());
        sensitiveWordService.check(title, plainContent, String.join(" ", tagList));

        mapper.ensureMember(user.getAccountId(), user.getUsername());
        mapper.memberForUpdate(user.getAccountId());
        if (reward > 0) changePoints(user.getAccountId(), -reward, "QUESTION_REWARD", "", "发布问题悬赏");

        ForumThread values = new ForumThread();
        values.setId(IdWorker.getId());
        values.setKind(kind);
        values.setBoard(board);
        values.setTitle(title);
        values.setContent(content);
        values.setExcerpt(plainContent.substring(0, Math.min(plainContent.length(), 180)));
        values.setTags(tags);
        values.setAuthorAccountId(user.getAccountId());
        values.setAuthorName(user.getUsername());
        values.setReward(reward);
        values.setCoverAttachmentId(coverAttachmentId > 0 ? coverAttachmentId : null);
        mapper.insertThread(values);
        long id = values.getId();
        attachmentService.bindThread(id, draftId, user.getAccountId(), kind, attachmentIds, coverAttachmentId);
        pollService.createPoll(id, pollConfig);
        mapper.increaseMemberThreads(user.getAccountId());
        changePoints(user.getAccountId(), 20, "PUBLISH", String.valueOf(id), "发布社区内容");
        experienceService.grant(user.getAccountId(), "POST", String.valueOf(id));
        badgeService.evaluate(user.getAccountId(), "THREAD_COUNT", "TOTAL_POINTS");
        return detail(id);
    }

    @Transactional
    public CommentVO createComment(long threadId, CommentCreateRequest body) {
        SecurityUser user = requireUser();
        ForumThread thread = requiredThread(threadId);
        long parentCommentId = longValue(body.getParentCommentId());
        if (body.getParentCommentId() != null && parentCommentId <= 0) throw param("父评论 ID 不正确");
        ForumComment parent = null;
        if (parentCommentId > 0) {
            parent = requiredComment(parentCommentId);
            if (longValue(parent.getThreadId()) != threadId || longValue(parent.getParentCommentId()) > 0) {
                throw param("只能回复同一帖子下的顶级评论");
            }
        }
        String content = text(body.getContent(), "").trim();
        if (content.isBlank() || content.length() > 2000) throw param("评论长度应为 1-2000 个字符");
        sensitiveWordService.check(content);
        mapper.ensureMember(user.getAccountId(), user.getUsername());
        mapper.memberForUpdate(user.getAccountId());
        ForumComment values = new ForumComment();
        values.setId(IdWorker.getId());
        values.setThreadId(threadId);
        values.setAuthorAccountId(user.getAccountId());
        values.setAuthorName(user.getUsername());
        values.setContent(content);
        values.setParentCommentId(parentCommentId > 0 ? parentCommentId : null);
        mapper.insertComment(values);
        mapper.increaseReplies(threadId);
        long id = values.getId();
        changePoints(user.getAccountId(), 5, "COMMENT", String.valueOf(id), "参与社区评论");
        experienceService.grant(user.getAccountId(), "COMMENT", String.valueOf(id));
        ForumComment comment = mapper.findComment(id);
        notificationService.onComment(thread, parent, comment);
        badgeService.evaluate(user.getAccountId(), "COMMENT_COUNT", "TOTAL_POINTS");
        return commentView(comment);
    }

    @Transactional
    public LikeToggleVO toggleLike(LikeToggleRequest body) {
        SecurityUser user = requireUser();
        String targetType = text(body.getTargetType(), "");
        long targetId = longValue(body.getTargetId());
        if (!Set.of("thread", "comment").contains(targetType) || targetId <= 0) throw param("点赞目标不正确");
        ForumThread thread = "thread".equals(targetType) ? requiredThread(targetId) : null;
        ForumComment comment = "comment".equals(targetType) ? requiredComment(targetId) : null;
        long likeId = IdWorker.getId();
        int inserted = mapper.insertLike(likeId, user.getAccountId(), targetType, targetId);
        boolean liked = inserted > 0;
        int delta = liked ? 1 : -1;
        if (!liked) mapper.deleteLike(user.getAccountId(), targetType, targetId);
        if (thread != null) {
            mapper.changeThreadLikes(targetId, delta);
            long authorId = longValue(thread.getAuthorAccountId());
            if (authorId > 0) {
                mapper.changeAuthorLikes(authorId, delta);
                if (delta > 0) badgeService.evaluate(authorId, "LIKES_RECEIVED");
            }
            if (liked) notificationService.onLike(user.getAccountId(), targetType, targetId, likeId, authorId, 0, targetId);
        } else {
            mapper.changeCommentLikes(targetId, delta);
            long commentAuthorId = comment == null ? 0 : longValue(comment.getAuthorAccountId());
            long commentThreadId = comment == null ? 0 : longValue(comment.getThreadId());
            if (liked) notificationService.onLike(user.getAccountId(), targetType, targetId, likeId, 0, commentAuthorId, commentThreadId);
        }
        int likes = thread != null ? intValue(mapper.findThread(targetId).getLikes())
                : intValue(mapper.findComment(targetId).getLikes());
        return new LikeToggleVO(liked, likes);
    }

    public ForumMeVO me() {
        SecurityUser user = requireUser();
        mapper.ensureMember(user.getAccountId(), user.getUsername());
        ForumMeVO result = new ForumMeVO();
        result.setAccountId(user.getAccountId());
        result.setUsername(user.getUsername());
        result.setSuperAdmin(user.isSuperAdmin());
        result.setAppCode(user.getAppCode());
        result.setPermissions(user.getPermissions());
        result.setMember(memberView(mapper.findMember(user.getAccountId())));
        return result;
    }
    public ThreadVO threadView(ForumThread row) {
        return threadView(row, new ThreadVO());
    }

    public <T extends ThreadVO> T threadView(ForumThread row, T view) {
        return threadView(row, view, null);
    }

    /** memberCache 为列表预加载的作者映射；为 null 时逐条查（单条详情场景保持原行为）。 */
    public <T extends ThreadVO> T threadView(ForumThread row, T view, Map<Long, ForumMember> memberCache) {
        view.setId(row.getId());
        view.setKind(row.getKind());
        view.setBoard(row.getBoard());
        view.setTitle(row.getTitle());
        view.setContent(row.getContent());
        view.setExcerpt(row.getExcerpt());
        view.setTags(parseTags(text(row.getTags(), "[]")));
        long authorAccountId = longValue(row.getAuthorAccountId());
        view.setAuthorAccountId(authorAccountId > 0 ? authorAccountId : null);
        view.setReward(row.getReward());
        view.setReplies(row.getReplies());
        view.setViews(row.getViews());
        view.setLikes(row.getLikes());
        view.setPinned(intValue(row.getPinned()) == 1);
        view.setSolved(intValue(row.getSolved()) == 1);
        view.setRecommended(intValue(row.getRecommended()) == 1);
        view.setRecommendedAt(row.getRecommendedAt());
        view.setCreatedAt(row.getCreatedAt());
        view.setAuthor(author(authorAccountId, text(row.getAuthorName(), "社区成员"), memberCache));
        long coverId = longValue(row.getCoverAttachmentId());
        view.setCoverAttachmentId(coverId > 0 ? coverId : null);
        view.setCoverUrl(coverId > 0 ? "/api/forum/public/attachments/" + coverId : null);
        return view;
    }

    public CommentVO commentView(ForumComment row) {
        return commentView(row, null);
    }

    public CommentVO commentView(ForumComment row, Map<Long, ForumMember> memberCache) {
        CommentVO view = new CommentVO();
        view.setId(row.getId());
        view.setThreadId(row.getThreadId());
        view.setAuthorAccountId(row.getAuthorAccountId());
        view.setContent(intValue(row.getDeleted()) == 1 ? "" : row.getContent());
        view.setParentCommentId(row.getParentCommentId());
        view.setLikes(row.getLikes());
        view.setDeleted(intValue(row.getDeleted()) == 1);
        view.setCreatedAt(row.getCreatedAt());
        view.setTime(row.getCreatedAt());
        view.setAuthor(author(longValue(row.getAuthorAccountId()), text(row.getAuthorName(), "社区成员"), memberCache));
        return view;
    }

    private ForumMemberVO memberView(ForumMember row) {
        ForumMemberVO view = new ForumMemberVO();
        view.setAccountId(row.getAccountId());
        view.setUsername(row.getUsername());
        view.setNickname(row.getNickname());
        view.setBio(row.getBio());
        view.setLocation(row.getLocation());
        view.setWebsite(row.getWebsite());
        view.setPoints(row.getPoints());
        long experience = row.getExperience() == null ? 0 : row.getExperience();
        view.setExperience(experience);
        LevelProgress progress = levelService.progress(experience);
        view.setLevel(progress.getLevel());
        view.setLevelName(progress.getLevelName());
        view.setLevelColor(progress.getLevelColor());
        view.setThreadsCount(row.getThreadsCount());
        view.setLikesReceived(row.getLikesReceived());
        view.setAvatarAttachmentId(row.getAvatarAttachmentId());
        view.setCoverAttachmentId(row.getCoverAttachmentId());
        return view;
    }

    private AuthorVO author(long accountId, String fallbackName) {
        return author(accountId, fallbackName, null);
    }

    private AuthorVO author(long accountId, String fallbackName, Map<Long, ForumMember> memberCache) {
        ForumMember member = accountId > 0
                ? (memberCache != null ? memberCache.get(accountId) : mapper.findMember(accountId))
                : null;
        String nickname = member == null ? "" : text(member.getNickname(), "").trim();
        String name = nickname.isBlank() && member != null ? text(member.getUsername(), fallbackName) : nickname;
        if (name.isBlank()) name = fallbackName;
        int index = accountId > 0 ? (int) Math.floorMod(accountId, COLORS.length) : Math.floorMod(name.hashCode(), COLORS.length);
        long avatarId = member == null ? 0 : longValue(member.getAvatarAttachmentId());
        AuthorVO author = new AuthorVO();
        author.setAccountId(accountId > 0 ? accountId : null);
        author.setName(name);
        author.setInitials(name.isBlank() ? "P" : name.substring(0, 1));
        author.setTitle("社区成员");
        author.setColor(COLORS[index]);
        author.setAvatarUrl(avatarId > 0 ? "/api/forum/public/attachments/" + avatarId : null);
        return author;
    }

    private List<String> parseTags(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String tagsJson(List<Object> raw) {
        List<String> tags = new ArrayList<>();
        if (raw != null) {
            for (Object item : raw) {
                String tag = text(item, "").trim().replace("#", "");
                if (!tag.isBlank() && tag.length() <= 20 && !tags.contains(tag) && tags.size() < 5) tags.add(tag);
            }
        }
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "标签处理失败");
        }
    }

    private void changePoints(long accountId, int delta, String bizType, String bizId, String remark) {
        if (mapper.changePoints(accountId, delta) != 1) throw new BizException(ErrorCode.CONFLICT, "可用积分不足");
        ForumPointLog log = new ForumPointLog();
        log.setId(IdWorker.getId());
        log.setAccountId(accountId);
        log.setChangePoints(delta);
        log.setBalance(intValue(mapper.findMember(accountId).getPoints()));
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setRemark(remark);
        mapper.insertPointLog(log);
    }

    private ForumThread requiredThread(long id) {
        ForumThread row = mapper.findThread(id);
        if (row == null) throw new BizException(ErrorCode.NOT_FOUND, "内容不存在或已删除");
        return row;
    }

    private ForumComment requiredComment(long id) {
        ForumComment row = mapper.findComment(id);
        if (row == null) throw new BizException(ErrorCode.NOT_FOUND, "评论不存在或已删除");
        return row;
    }

    private SecurityUser requireUser() {
        return SecurityUtils.currentUser().orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
    }

    private BizException param(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }

    private String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null || String.valueOf(value).isBlank() ? 0 : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException e) { return 0; }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? 0 : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException e) { return 0; }
    }
}
