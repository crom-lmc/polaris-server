package com.laimengchao.sso.forum.module.draft.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.forum.module.attachment.service.AttachmentService;
import com.laimengchao.sso.forum.module.draft.dto.DraftRequest;
import com.laimengchao.sso.forum.module.draft.entity.Draft;
import com.laimengchao.sso.forum.module.draft.mapper.DraftMapper;
import com.laimengchao.sso.forum.module.draft.vo.DraftVO;
import com.laimengchao.sso.forum.module.forum.dto.ThreadPublishRequest;
import com.laimengchao.sso.forum.module.forum.entity.ForumThread;
import com.laimengchao.sso.forum.module.forum.mapper.ForumMapper;
import com.laimengchao.sso.forum.module.forum.service.ForumService;
import com.laimengchao.sso.forum.module.forum.vo.ThreadDetailVO;
import com.laimengchao.sso.forum.module.poll.dto.PollConfigDTO;
import com.laimengchao.sso.forum.module.poll.dto.PollConfigRequest;
import com.laimengchao.sso.forum.module.poll.service.PollService;
import com.laimengchao.sso.forum.module.taxonomy.service.TaxonomyService;
import com.laimengchao.sso.forum.service.ForumContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DraftService {

    private static final Set<String> KINDS = Set.of("feed", "topic", "question", "article");

    private final DraftMapper mapper;
    private final ForumMapper forumMapper;
    private final ForumService forumService;
    private final ForumContentService forumContentService;
    private final AttachmentService attachmentService;
    private final PollService pollService;
    private final ObjectMapper objectMapper;
    private final TaxonomyService taxonomyService;

    public List<DraftVO> list() {
        SecurityUser user = requireUser();
        return mapper.list(user.getAccountId()).stream().map(this::view).toList();
    }

    public DraftVO get(long id) {
        return view(required(id, requireUser().getAccountId()));
    }

    @Transactional
    public DraftVO create(DraftRequest body) {
        SecurityUser user = requireUser();
        Draft values = values(body, user.getAccountId());
        values.setId(IdWorker.getId());
        mapper.insert(values);
        long id = values.getId();
        attachmentService.bindDraft(id, user.getAccountId(), text(values.getKind(), "topic"),
                body.getAttachmentIds(), longValue(values.getCoverAttachmentId()));
        return view(mapper.find(id, user.getAccountId()));
    }

    @Transactional
    public DraftVO update(long id, DraftRequest body) {
        SecurityUser user = requireUser();
        Draft existing = mapper.findForUpdate(id, user.getAccountId());
        if (existing == null) throw new BizException(ErrorCode.NOT_FOUND, "草稿不存在或已删除");
        if (longValue(existing.getPublishedThreadId()) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "已发布草稿不能继续编辑");
        }
        Draft values = values(body, user.getAccountId());
        values.setId(id);
        if (mapper.update(values) != 1) throw new BizException(ErrorCode.CONFLICT, "草稿状态已变化，请刷新后重试");
        attachmentService.bindDraft(id, user.getAccountId(), text(values.getKind(), "topic"),
                body.getAttachmentIds(), longValue(values.getCoverAttachmentId()));
        return view(mapper.find(id, user.getAccountId()));
    }

    @Transactional
    public void delete(long id) {
        if (mapper.delete(id, requireUser().getAccountId()) != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "草稿不存在或已删除");
        }
    }

    @Transactional
    public ThreadDetailVO publish(long id) {
        SecurityUser user = requireUser();
        Draft draft = mapper.findForUpdate(id, user.getAccountId());
        if (draft == null) throw new BizException(ErrorCode.NOT_FOUND, "草稿不存在或已删除");
        long publishedId = longValue(draft.getPublishedThreadId());
        if (publishedId > 0) {
            ForumThread target = forumMapper.findThreadAny(publishedId);
            if (target == null) throw new BizException(ErrorCode.NOT_FOUND, "草稿对应的已发布内容不存在");
            if (intValue(target.getDeleted()) == 1) {
                throw new BizException(ErrorCode.CONFLICT, "草稿对应内容已删除，不能重复发布");
            }
            return forumService.detail(publishedId);
        }
        ThreadPublishRequest body = new ThreadPublishRequest();
        body.setKind(draft.getKind());
        body.setBoard(draft.getBoard());
        body.setTitle(draft.getTitle());
        body.setContent(draft.getContent());
        body.setTags(new ArrayList<>(parseTags(text(draft.getTags(), "[]"))));
        body.setReward(draft.getReward());
        body.setCoverAttachmentId(draft.getCoverAttachmentId());
        body.setAttachmentIds(new ArrayList<>(attachmentService.draftAttachmentIds(id)));
        body.setPoll(publishPollConfig(parsePollConfig(text(draft.getPollConfig(), ""))));
        ThreadDetailVO result = forumService.publishDraft(body, id);
        long threadId = result.getId();
        if (mapper.markPublished(id, threadId) != 1) throw new BizException(ErrorCode.CONFLICT, "草稿发布状态更新失败");
        return result;
    }

    private Draft values(DraftRequest body, long accountId) {
        String kind = text(body.getKind(), "topic");
        String board = text(body.getBoard(), "general");
        if (!KINDS.contains(kind)) throw param("不支持的内容类型");
        taxonomyService.requireBoard(board, false);
        String title = text(body.getTitle(), "").trim();
        String content = forumContentService.sanitize(text(body.getContent(), ""));
        if ("feed".equals(kind)) title = "";
        if (title.length() > 160) throw param("草稿标题不能超过 160 个字符");
        if (content.length() > 50000) throw param("草稿正文不能超过 50000 个字符");
        int reward = "question".equals(kind) ? intValue(body.getReward()) : 0;
        if (reward < 0 || reward > 10000) throw param("悬赏积分范围为 0-10000");
        long coverId = longValue(body.getCoverAttachmentId());
        if (!"article".equals(kind) && coverId > 0) throw param("仅文章可以设置封面");
        PollConfigRequest rawPoll = body.isPollConfigPresent() ? body.getPollConfig() : body.getPoll();
        PollConfigDTO pollConfig = pollService.normalizeDraftConfig(rawPoll);
        String tags = tagsJson(body.getTags());
        taxonomyService.registerTags(board, parseTags(tags));
        Draft values = new Draft();
        values.setAccountId(accountId);
        values.setKind(kind);
        values.setBoard(board);
        values.setTitle(title);
        values.setContent(content);
        values.setTags(tags);
        values.setReward(reward);
        values.setCoverAttachmentId(coverId > 0 ? coverId : null);
        values.setPollConfig(pollConfig == null ? null : json(pollConfig));
        return values;
    }

    private DraftVO view(Draft row) {
        DraftVO view = new DraftVO();
        view.setId(row.getId());
        view.setKind(row.getKind());
        view.setBoard(row.getBoard());
        view.setTitle(row.getTitle());
        view.setContent(row.getContent());
        view.setTags(parseTags(text(row.getTags(), "[]")));
        view.setReward(intValue(row.getReward()));
        long coverId = longValue(row.getCoverAttachmentId());
        view.setCoverAttachmentId(coverId > 0 ? coverId : null);
        view.setPollConfig(parsePollConfig(text(row.getPollConfig(), "")));
        view.setAttachments(attachmentService.draftAttachments(row.getId()));
        long publishedId = longValue(row.getPublishedThreadId());
        view.setPublishedThreadId(publishedId > 0 ? publishedId : null);
        view.setCreatedAt(row.getCreatedAt());
        view.setUpdatedAt(row.getUpdatedAt());
        return view;
    }

    private PollConfigRequest publishPollConfig(PollConfigDTO config) {
        if (config == null) return null;
        PollConfigRequest request = new PollConfigRequest();
        request.setMode(config.getMode());
        request.setMaxChoices(config.getMaxChoices());
        request.setOptions(new ArrayList<>(config.getOptions().stream().map(TextNode::valueOf).toList()));
        return request;
    }

    private Draft required(long id, long accountId) {
        Draft row = mapper.find(id, accountId);
        if (row == null) throw new BizException(ErrorCode.NOT_FOUND, "草稿不存在或已删除");
        return row;
    }

    private PollConfigDTO parsePollConfig(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, PollConfigDTO.class);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "草稿投票配置读取失败");
        }
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
        return json(tags);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "草稿数据处理失败");
        }
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
