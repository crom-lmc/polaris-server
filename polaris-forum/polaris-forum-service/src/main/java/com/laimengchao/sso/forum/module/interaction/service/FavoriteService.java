package com.laimengchao.sso.forum.module.interaction.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.forum.module.forum.mapper.ForumMapper;
import com.laimengchao.sso.forum.module.forum.service.ForumService;
import com.laimengchao.sso.forum.module.forum.vo.FavoriteThreadVO;
import com.laimengchao.sso.forum.module.forum.vo.ThreadVO;
import com.laimengchao.sso.forum.module.interaction.mapper.FavoriteMapper;
import com.laimengchao.sso.forum.module.interaction.vo.FavoriteToggleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteMapper mapper;
    private final ForumMapper forumMapper;
    private final ForumService forumService;

    public FavoriteToggleVO add(long threadId) {
        long accountId = requireUser().getAccountId();
        requireThread(threadId);
        mapper.add(IdWorker.getId(), accountId, threadId);
        return new FavoriteToggleVO(true);
    }

    public FavoriteToggleVO remove(long threadId) {
        long accountId = requireUser().getAccountId();
        requireThread(threadId);
        mapper.remove(accountId, threadId);
        return new FavoriteToggleVO(false);
    }

    public List<ThreadVO> list() {
        return mapper.list(requireUser().getAccountId()).stream().map(row -> {
            ThreadVO view = forumService.threadView(row, new FavoriteThreadVO());
            view.setFavorited(true);
            view.setFavoritedAt(row.getFavoritedAt());
            return view;
        }).toList();
    }

    public int count(long accountId) {
        return mapper.count(accountId);
    }

    private void requireThread(long threadId) {
        if (forumMapper.findThread(threadId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "内容不存在或已删除");
        }
    }

    private SecurityUser requireUser() {
        return SecurityUtils.currentUser().orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
    }
}
