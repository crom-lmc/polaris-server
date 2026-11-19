package com.laimengchao.sso.forum.module.poll.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.forum.module.admin.service.SensitiveWordService;
import com.laimengchao.sso.forum.module.forum.mapper.ForumMapper;
import com.laimengchao.sso.forum.module.poll.dto.PollConfigDTO;
import com.laimengchao.sso.forum.module.poll.dto.PollConfigRequest;
import com.laimengchao.sso.forum.module.poll.dto.PollVoteRequest;
import com.laimengchao.sso.forum.module.poll.entity.Poll;
import com.laimengchao.sso.forum.module.poll.entity.PollOption;
import com.laimengchao.sso.forum.module.poll.mapper.PollMapper;
import com.laimengchao.sso.forum.module.poll.vo.PollOptionVO;
import com.laimengchao.sso.forum.module.poll.vo.PollVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PollService {

    private final PollMapper mapper;
    private final ForumMapper forumMapper;
    private final SensitiveWordService sensitiveWordService;

    public PollConfigDTO normalizeDraftConfig(PollConfigRequest source) {
        if (source == null) return null;
        if (Boolean.FALSE.equals(source.getEnabled())) return null;
        String mode = text(source.getMode(), "SINGLE").trim().toUpperCase();
        if (!Set.of("SINGLE", "MULTIPLE").contains(mode)) throw param("投票模式不正确");
        int maxChoices = intValue(source.getMaxChoices(), "SINGLE".equals(mode) ? 1 : 2);
        return new PollConfigDTO(mode, maxChoices, optionTexts(source.getOptions()));
    }

    public PollConfigDTO validateForPublish(String kind, PollConfigRequest raw) {
        PollConfigDTO config = normalizeDraftConfig(raw);
        if (config == null) return null;
        if (!Set.of("feed", "topic").contains(kind)) throw param("仅动态和话题支持投票");
        List<String> options = config.getOptions();
        if (options.size() < 2 || options.size() > 10) throw param("投票选项数量应为 2-10 个");
        int maxChoices = config.getMaxChoices();
        if ("SINGLE".equals(config.getMode())) {
            maxChoices = 1;
        } else if (maxChoices < 2 || maxChoices > options.size()) {
            throw param("多选投票上限应为 2 至选项总数");
        }
        config.setMaxChoices(maxChoices);
        sensitiveWordService.check(options.toArray(String[]::new));
        return config;
    }

    public void createPoll(long threadId, PollConfigDTO config) {
        if (config == null) return;
        Poll poll = new Poll();
        poll.setId(IdWorker.getId());
        poll.setThreadId(threadId);
        poll.setMode(config.getMode());
        poll.setMaxChoices(config.getMaxChoices());
        mapper.insertPoll(poll);
        long pollId = poll.getId();
        List<String> options = config.getOptions();
        for (int i = 0; i < options.size(); i++) {
            PollOption option = new PollOption();
            option.setId(IdWorker.getId());
            option.setPollId(pollId);
            option.setText(options.get(i));
            option.setSortNo(i);
            mapper.insertOption(option);
        }
    }

    public PollVO detail(long threadId, Long accountId) {
        Poll poll = mapper.findByThread(threadId);
        if (poll == null) return null;
        long pollId = poll.getId();
        PollVO view = new PollVO();
        view.setId(pollId);
        view.setMode(poll.getMode());
        view.setMaxChoices(intValue(poll.getMaxChoices(), 1));
        view.setTotalVotes(mapper.totalVotes(pollId));
        List<Long> selected = accountId == null ? List.of() : mapper.selectedOptionIds(pollId, accountId);
        view.setVoted(!selected.isEmpty());
        view.setSelectedOptionIds(selected);
        view.setOptions(mapper.options(pollId).stream().map(this::optionView).toList());
        return view;
    }

    @Transactional
    public long vote(long threadId, PollVoteRequest body) {
        SecurityUser user = SecurityUtils.currentUser().orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        Poll poll = mapper.findByThreadForUpdate(threadId);
        if (poll == null) throw new BizException(ErrorCode.NOT_FOUND, "投票不存在");
        if (forumMapper.findThread(threadId) == null) throw new BizException(ErrorCode.NOT_FOUND, "内容不存在或已删除");
        long pollId = poll.getId();
        List<Long> optionIds = voteOptionIds(body.getOptionIds());
        int maxChoices = intValue(poll.getMaxChoices(), 1);
        if ("SINGLE".equals(text(poll.getMode(), "SINGLE")) && optionIds.size() != 1) {
            throw param("单选投票只能选择一个选项");
        }
        if (optionIds.isEmpty() || optionIds.size() > maxChoices) throw param("选择数量超出本次投票限制");
        for (Long optionId : optionIds) {
            if (mapper.optionBelongs(pollId, optionId) != 1) throw param("投票选项不正确");
        }
        if (mapper.countAccountVotes(pollId, user.getAccountId()) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "您已经投过票，不能重复投票或改票");
        }
        for (Long optionId : optionIds) {
            mapper.insertVote(IdWorker.getId(), pollId, user.getAccountId(), optionId);
        }
        return threadId;
    }

    private PollOptionVO optionView(PollOption row) {
        PollOptionVO view = new PollOptionVO();
        view.setId(row.getId());
        view.setText(row.getText());
        view.setVotes(row.getVotes());
        return view;
    }

    private List<Long> voteOptionIds(List<Object> raw) {
        if (raw == null) throw param("optionIds 必须为数组");
        Set<Long> ids = new LinkedHashSet<>();
        for (Object value : raw) {
            long id = longValue(value);
            if (id <= 0) throw param("投票选项不正确");
            if (!ids.add(id)) throw param("投票选项不能重复");
        }
        return new ArrayList<>(ids);
    }

    private List<String> optionTexts(List<JsonNode> raw) {
        if (raw == null) return new ArrayList<>();
        List<String> options = new ArrayList<>();
        for (JsonNode item : raw) {
            JsonNode value = item != null && item.isObject() ? item.get("text") : item;
            String text = jsonText(value, "").trim();
            if (text.isEmpty()) continue;
            if (text.length() > 200) throw param("投票选项不能超过 200 个字符");
            options.add(text);
        }
        return options;
    }

    private String jsonText(JsonNode value, String fallback) {
        if (value == null || value.isNull()) return fallback;
        return value.isValueNode() ? value.asText() : value.toString();
    }

    private BizException param(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }

    private String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null || String.valueOf(value).isBlank() ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException e) { return fallback; }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null || String.valueOf(value).isBlank() ? 0 : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException e) { return 0; }
    }
}
