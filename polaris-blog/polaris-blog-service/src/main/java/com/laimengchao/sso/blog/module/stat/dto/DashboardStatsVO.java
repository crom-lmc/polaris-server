package com.laimengchao.sso.blog.module.stat.dto;

import com.laimengchao.sso.blog.module.article.dto.ArticleListVO;
import com.laimengchao.sso.blog.module.comment.dto.CommentVO;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 仪表盘统计数据。
 */
@Data
public class DashboardStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文章总数 */
    private long articleCount;

    /** 已发布 */
    private long publishedCount;

    /** 草稿 */
    private long draftCount;

    /** 全站阅读量 */
    private long viewCount;

    /** 评论总数 */
    private long commentCount;

    /** 待审评论 */
    private long pendingCommentCount;

    private long categoryCount;

    private long tagCount;

    /** 作者数（按 SSO 账户去重；原 t_user 总数） */
    private long userCount;

    private long mediaCount;

    /** 近 30 天文章发布趋势 */
    private List<DailyCountVO> articleTrend = new ArrayList<>();

    /** 近 30 天评论趋势 */
    private List<DailyCountVO> commentTrend = new ArrayList<>();

    /** 热门文章 TOP10 */
    private List<ArticleListVO> topArticles = new ArrayList<>();

    /** 待审评论列表 */
    private List<CommentVO> pendingComments = new ArrayList<>();
}
