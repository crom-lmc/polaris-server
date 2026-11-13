package com.laimengchao.sso.cms.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.cms.common.CmsErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.cms.module.content.dto.ArticleQuery;
import com.laimengchao.sso.cms.module.content.dto.ArticleSaveRequest;
import com.laimengchao.sso.cms.module.content.dto.ArticleVO;
import com.laimengchao.sso.cms.module.content.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.cms.module.content.dto.VersionVO;
import com.laimengchao.sso.cms.module.content.entity.Article;
import com.laimengchao.sso.cms.module.content.entity.ArticleVersion;
import com.laimengchao.sso.cms.module.content.entity.Category;
import com.laimengchao.sso.cms.module.content.entity.Tag;
import com.laimengchao.sso.cms.module.content.mapper.ArticleMapper;
import com.laimengchao.sso.cms.module.content.mapper.ArticleVersionMapper;
import com.laimengchao.sso.cms.module.content.mapper.CategoryMapper;
import com.laimengchao.sso.cms.module.content.mapper.TagMapper;
import com.laimengchao.sso.common.security.HtmlSanitizer;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.settings.client.api.ApprovalApi;
import com.laimengchao.sso.settings.client.dto.ApprovalActionDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalInstanceDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 文章服务：状态机、版本快照与回滚、回收站、全文检索。
 *
 * <p>状态机（审核走审批中心）：
 * <pre>
 *   草稿0 ──提审──▶ 审批中6 ──审批通过──▶ 已发布3
 *      ▲                │驳回──▶ 驳回4 ──改后再提审──▶ 审批中6
 *      │                └撤回──▶ 草稿0
 *   已发布 ──下线──▶ 已下线5；定时发布=待发布2，由调度器到点置为已发布
 *   1 待审核是本地审核时代的历史状态，新单据不再产生
 * </pre>
 * 已发布文章的编辑<b>不回状态</b>：内容即时生效，但改前自动存一条快照，可随时回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleService {

    /** 内容发布审批场景编码，对应 settings 的 cfg_biz_scene.biz_type */
    public static final String BIZ_ARTICLE_PUBLISH = "cms.article_publish";
    private static final String CALLBACK_PATH = "/internal/cms/approval-callback";

    private final ArticleMapper articleMapper;
    private final ArticleVersionMapper versionMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ApprovalApi approvalApi;
    /** 终审回调地址：settings 直连 CMS，不经网关 */
    @Value("${cms.self-base-url:http://127.0.0.1:9004}")
    private String selfBaseUrl;

    // ---------------- 保存与查询 ----------------

    @Transactional(rollbackFor = Exception.class)
    public ArticleVO save(ArticleSaveRequest req) {
        Article article;
        if (req.getId() == null) {
            article = new Article();
            article.setStatus(Article.STATUS_DRAFT);
            article.setAuthorId(requireUserId());
            article.setAuthorName(SecurityUtils.currentUsername().orElse("unknown"));
            applyContent(article, req);
            applyTag(article, req.getTagId());
            decideSchedule(article, req);
            articleMapper.insert(article);
        } else {
            article = requireArticle(req.getId());
            // 已发布内容先快照：改坏了可以一键回滚
            if (article.published()) {
                snapshot(article, "编辑前自动快照", SecurityUtils.currentUsername().orElse(""));
            }
            applyContent(article, req);
            applyTag(article, req.getTagId());
            decideSchedule(article, req);
            articleMapper.updateById(article);
        }
        return detail(article.getId());
    }

    private void applyContent(Article article, ArticleSaveRequest req) {
        article.setTitle(req.getTitle().trim());
        article.setContent(clean(req.getContent()));
        article.setSummary(StringUtils.hasText(req.getSummary())
                ? req.getSummary().trim() : excerpt(clean(req.getContent()), 200));
        article.setCategoryId(req.getCategoryId());
        article.setCover(nvl(req.getCover()));
        article.setPinned(req.getPinned() != null && req.getPinned() == 1 ? 1 : 0);
        article.setRecommended(req.getRecommended() != null && req.getRecommended() == 1 ? 1 : 0);
        article.setSeoTitle(nvl(req.getSeoTitle()));
        article.setSeoKeywords(nvl(req.getSeoKeywords()));
        article.setSeoDescription(nvl(req.getSeoDescription()));
    }

    /** 定时发布：给了未来的 publishAt 就转「待发布」，否则保持/回落草稿语义 */
    private void decideSchedule(Article article, ArticleSaveRequest req) {
        if (req.getPublishAt() == null) {
            if (article.getStatus() != null && article.getStatus() == Article.STATUS_SCHEDULED) {
                article.setStatus(Article.STATUS_DRAFT);
                article.setPublishAt(null);
            }
            return;
        }
        if (req.getPublishAt().isAfter(LocalDateTime.now())) {
            article.setStatus(Article.STATUS_SCHEDULED);
            article.setPublishAt(req.getPublishAt());
        } else {
            throw new BizException(ErrorCode.PARAM_ERROR, "定时发布时间必须晚于当前时间");
        }
    }

    /**
     * 标签：现在一篇只挂一个（tag_id），同时把标签名快照进 {@code tag} 字段，
     * 前台展示不用再 join cms_tag。历史多标签的名字仍留在 {@code tag} 里（逗号分隔），
     * 下次编辑保存时自然收敛成单标签。
     */
    private void applyTag(Article article, Long tagId) {
        if (tagId == null) {
            article.setTagId(null);
            article.setTag("");
            return;
        }
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            throw new BizException(CmsErrorCode.DATA_NOT_FOUND, "标签不存在：" + tagId);
        }
        article.setTagId(tagId);
        article.setTag(tag.getName());
    }

    public ArticleVO detail(Long id) {
        Article article = requireArticle(id);
        return toVO(article, true);
    }

    /** 管理端分页：status=null 全部（不含回收站），-1=回收站 */
    public R.Page<ArticleVO> page(ArticleQuery q) {
        if (q.getStatus() != null && q.getStatus() == -1) {
            IPage<Article> p = articleMapper.pageRecycle(
                    Page.of(q.getPage(), q.getSize()), nvl(q.getKeyword()));
            return R.Page.of(p.getRecords().stream().map(a -> toVO(a, false)).toList(), p.getTotal());
        }
        LambdaQueryWrapper<Article> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(q.getKeyword())) {
            w.and(x -> x.like(Article::getTitle, q.getKeyword())
                    .or().like(Article::getSummary, q.getKeyword()));
        }
        if (q.getCategoryId() != null) {
            w.eq(Article::getCategoryId, q.getCategoryId());
        }
        if (q.getStatus() != null) {
            w.eq(Article::getStatus, q.getStatus());
        }
        if (q.getPinned() != null) {
            w.eq(Article::getPinned, q.getPinned());
        }
        if (q.getRecommended() != null) {
            w.eq(Article::getRecommended, q.getRecommended());
        }
        if (q.getTagId() != null) {
            w.eq(Article::getTagId, q.getTagId());
        }
        w.orderByDesc(Article::getPinned).orderByDesc(Article::getUpdatedAt);
        IPage<Article> p = articleMapper.selectPage(Page.of(q.getPage(), q.getSize()), w);
        return R.Page.of(p.getRecords().stream().map(a -> toVO(a, false)).toList(), p.getTotal());
    }

    // ---------------- 状态机动作 ----------------

    /**
     * 提审：草稿/驳回 → 审批中(6)，并向审批中心发起 {@code cms.article_publish} 实例。
     * <p>
     * <b>刻意不加 {@code @Transactional}</b>：settings 在模板没有审批人时会当场把实例推到终态
     * 并<b>同步</b>回调本服务；若本地状态还在未提交的事务里，回调线程读不到这条文章，
     * 会按「文章不存在」返回、审批单却已终结，文章就永远卡在「审批中」。
     * 所以这里先独立提交「审批中」，再远程发起。
     */
    public void submit(Long id, String note) {
        Article article = requireArticle(id);
        requireOwnerOrSuper(article);
        if (article.getStatus() != Article.STATUS_DRAFT
                && article.getStatus() != Article.STATUS_REJECTED) {
            throw new BizException(CmsErrorCode.ILLEGAL_STATE, "只有草稿或被驳回的稿件可以提审");
        }
        article.setStatus(Article.STATUS_APPROVING);
        article.setSubmitNote(nvl(note));
        article.setAuditNote("");
        articleMapper.updateById(article);

        ApprovalStartDTO dto = buildStart(article);
        com.laimengchao.sso.settings.client.dto.R<ApprovalInstanceDTO> resp;
        try {
            resp = approvalApi.start(dto);
        } catch (Exception e) {
            log.error("发起内容发布审批失败 articleId={}", id, e);
            revertToDraft(id, "审批中心不可用，提审失败，请稍后重试");
            throw new BizException(CmsErrorCode.APPROVAL_START_FAILED, "审批中心不可用，提审失败，请稍后重试");
        }
        if (resp == null || !resp.ok() || resp.getData() == null || resp.getData().getId() == null) {
            String msg = resp == null || resp.getMessage() == null || resp.getMessage().isBlank()
                    ? "发起审批失败，请稍后重试" : resp.getMessage();
            revertToDraft(id, msg);
            throw new BizException(CmsErrorCode.APPROVAL_START_FAILED, msg);
        }
        ApprovalInstanceDTO inst = resp.getData();
        // 实例发起即终态：回调已经把文章写成终态，绝不能再把实例号挂回去
        if (Integer.valueOf(0).equals(inst.getStatus())) {
            articleMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Article>()
                    .eq(Article::getId, id)
                    .isNull(Article::getApprovalInstanceId)
                    .set(Article::getApprovalInstanceId, inst.getId()));
        } else {
            log.info("内容发布审批发起即终态 articleId={} instance={} status={}",
                    id, inst.getId(), inst.getStatus());
        }
        log.info("内容提审已发起审批 articleId={} instance={} title={}", id, inst.getId(), article.getTitle());
    }

    /** 发起失败时退回草稿，别让稿件卡在「审批中」 */
    private void revertToDraft(Long articleId, String reason) {
        try {
            articleMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Article>()
                    .eq(Article::getId, articleId)
                    .eq(Article::getStatus, Article.STATUS_APPROVING)
                    .set(Article::getStatus, Article.STATUS_DRAFT)
                    .set(Article::getAuditNote, nvl(reason)));
        } catch (Exception e) {
            log.error("退回草稿失败 articleId={}", articleId, e);
        }
    }

    /** 纯构造审批发起请求（条件表达式用的业务点都放这里） */
    private ApprovalStartDTO buildStart(Article article) {
        ApprovalStartDTO dto = new ApprovalStartDTO();
        dto.setBizType(BIZ_ARTICLE_PUBLISH);
        dto.setBizId(String.valueOf(article.getId()));
        dto.setBizNo(String.valueOf(article.getId()));
        dto.setTitle("内容发布审批 · " + article.getTitle());
        dto.setApplicantId(article.getAuthorId());
        dto.setApplicantName(nvl(article.getAuthorName()));
        Category c = article.getCategoryId() == null ? null : categoryMapper.selectById(article.getCategoryId());

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("title", nvl(article.getTitle()));
        form.put("summary", nvl(article.getSummary()));
        form.put("categoryId", article.getCategoryId() == null ? "" : String.valueOf(article.getCategoryId()));
        form.put("categoryName", c == null ? "" : c.getName());
        form.put("authorName", nvl(article.getAuthorName()));
        form.put("pinned", Integer.valueOf(1).equals(article.getPinned()) ? "是" : "否");
        form.put("wordCount", plainText(article.getContent()).length());
        form.put("submitNote", nvl(article.getSubmitNote()));
        form.put("submitter", nvl(article.getAuthorName()));
        dto.setFormData(form);
        dto.setCallbackUrl(selfBaseUrl + CALLBACK_PATH);
        return dto;
    }

    /**
     * 接收审批中心回传的终审结果。<b>必须幂等</b>：回调失败会退避重试。
     * 通过 → 发布（有未来定时发布时间则转待发布）；驳回 → 4；撤回/终止 → 回草稿。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleApprovalResult(ApprovalCallbackRequest req) {
        Long articleId = parseLongOrNull(req.getBizId());
        Long instId = parseLongOrNull(req.getInstanceId());
        if (articleId == null) {
            log.warn("内容审批回调缺少业务ID：{}", req.getBizId());
            return;
        }
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            log.warn("内容审批回调对应的文章不存在或已删除 bizId={}", req.getBizId());
            return;
        }
        if (article.getApprovalInstanceId() != null
                && !article.getApprovalInstanceId().equals(instId)) {
            log.info("忽略过期的内容审批回调 articleId={} 当前单={} 回调单={}",
                    articleId, article.getApprovalInstanceId(), instId);
            return;
        }
        if (article.getStatus() == null || article.getStatus() != Article.STATUS_APPROVING) {
            log.info("内容审批回调重复投递或状态不匹配，忽略 articleId={} status={}",
                    articleId, article.getStatus());
            return;
        }
        if (Boolean.TRUE.equals(req.getApproved())) {
            // 审批通过 ≠ 对外发布：统一置「2 待发布」，由内容管理员在列表点「发布」后才
            // 变成「3 已发布」并对门户可见。设了未来 publishAt 的仍由调度器到点自动发布。
            // 这样「跳过审批直接发布」的通道被彻底关闭，发布动作始终可追溯。
            snapshot(article, "审批通过（待发布）", "审批中心");
            article.setStatus(Article.STATUS_SCHEDULED);
            boolean scheduled = article.getPublishAt() != null
                    && article.getPublishAt().isAfter(LocalDateTime.now());
            article.setAuditNote(scheduled
                    ? "审批通过，将于 " + article.getPublishAt() + " 定时发布（也可手动立即发布）"
                    : "审批通过，待发布");
            article.setAuditorName("审批中心");
            article.setAuditAt(LocalDateTime.now());
            articleMapper.updateById(article);
        } else if (req.getResult() != null && (req.getResult() == 3 || req.getResult() == 4)) {
            // 撤回 / 终止：回草稿，作者改完可以重新提审
            article.setStatus(Article.STATUS_DRAFT);
            article.setAuditNote("审批已撤回，稿件退回草稿");
        } else {
            article.setStatus(Article.STATUS_REJECTED);
            article.setAuditNote("审批驳回" + (req.getResultName() == null ? "" : "：" + req.getResultName()));
            article.setAuditorName("审批中心");
            article.setAuditAt(LocalDateTime.now());
        }
        articleMapper.updateById(article);
        clearApprovalMark(articleId);
        log.info("内容审批终审已处理 articleId={} status={} result={}",
                articleId, article.getStatus(), req.getResultName());
    }

    /**
     * 作者撤回审批中的稿件：先撤审批中心的在途单，再退回草稿。
     * <p>刻意不加事务：撤回时 settings 会同步回调本服务，回调线程要 UPDATE 同一行。
     */
    public void withdraw(Long id) {
        Article article = requireArticle(id);
        requireOwnerOrSuper(article);
        if (article.getStatus() == null || article.getStatus() != Article.STATUS_APPROVING) {
            throw new BizException(CmsErrorCode.ILLEGAL_STATE, "只有审批中的稿件可以撤回");
        }
        if (article.getApprovalInstanceId() != null) {
            ApprovalActionDTO act = new ApprovalActionDTO();
            act.setOperatorId(SecurityUtils.currentUserId());
            act.setOperatorName(SecurityUtils.currentUsername().orElse(""));
            act.setNote("作者撤回内容发布审批");
            try {
                approvalApi.withdraw(article.getApprovalInstanceId(), act);
            } catch (Exception e) {
                log.error("撤回内容审批失败 articleId={} instance={}", id, article.getApprovalInstanceId(), e);
                throw new BizException(CmsErrorCode.APPROVAL_WITHDRAW_FAILED, "撤回审批失败，请稍后重试");
            }
        }
        article.setStatus(Article.STATUS_DRAFT);
        article.setAuditNote("作者已撤回审批");
        articleMapper.updateById(article);
        clearApprovalMark(id);
    }

    /** updateById 跳过 null，清在途标记必须显式 set null */
    private void clearApprovalMark(Long articleId) {
        articleMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .set(Article::getApprovalInstanceId, null));
    }

    private String plainText(String html) {
        return Jsoup.parse(html == null ? "" : html).text();
    }

    private Long parseLongOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 审核通过 → 发布（写版本快照）。仅历史「1 待审核」单据可用；新单据在审批中心处理 */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, String note) {
        Article article = requireArticle(id);
        if (article.getStatus() == Article.STATUS_APPROVING) {
            throw new BizException(CmsErrorCode.ILLEGAL_STATE,
                    "该稿件正在审批中心走「内容发布审批」，请到审批中心处理");
        }
        if (article.getStatus() != Article.STATUS_PENDING) {
            throw new BizException(CmsErrorCode.ILLEGAL_STATE, "只有待审核的稿件可以审核");
        }
        publish(article, "审核通过发布", nvl(note));
    }

    /** 驳回：退回给作者改后再提审（历史本地审核单据） */
    public void reject(Long id, String note) {
        Article article = requireArticle(id);
        if (article.getStatus() == Article.STATUS_APPROVING) {
            throw new BizException(CmsErrorCode.ILLEGAL_STATE,
                    "该稿件正在审批中心走「内容发布审批」，请到审批中心驳回");
        }
        if (article.getStatus() != Article.STATUS_PENDING) {
            throw new BizException(CmsErrorCode.ILLEGAL_STATE, "只有待审核的稿件可以驳回");
        }
        article.setStatus(Article.STATUS_REJECTED);
        article.setAuditNote(nvl(note));
        article.setAuditorName(SecurityUtils.currentUsername().orElse(""));
        article.setAuditAt(LocalDateTime.now());
        articleMapper.updateById(article);
    }

    /**
     * 发布：只允许「审批通过后的待发布」稿件发布（含定时待发布可提前手动发）。
     * 草稿 / 驳回 / 审批中一律拒绝——后端也要堵死「跳过审核直接发布」，不能只靠隐藏按钮。
     */
    @Transactional(rollbackFor = Exception.class)
    public void publishNow(Long id) {
        Article article = requireArticle(id);
        if (article.getStatus() != Article.STATUS_SCHEDULED) {
            throw new BizException(CmsErrorCode.ILLEGAL_STATE,
                    "只有审批通过、处于待发布的文章可以发布；请先提交审批");
        }
        publish(article, "审批通过后发布", nvl(article.getAuditNote()));
    }

    /** 下线：从前台撤下，不进回收站 */
    public void offline(Long id) {
        Article article = requireArticle(id);
        if (article.getStatus() != Article.STATUS_PUBLISHED
                && article.getStatus() != Article.STATUS_SCHEDULED) {
            throw new BizException(CmsErrorCode.ILLEGAL_STATE, "只有已发布/待发布的文章可以下线");
        }
        article.setStatus(Article.STATUS_OFFLINE);
        articleMapper.updateById(article);
    }

    /** 置顶/取消置顶；推荐位同理 */
    public void setFlag(Long id, String flag, boolean on) {
        Article article = requireArticle(id);
        switch (flag) {
            case "pin" -> article.setPinned(on ? 1 : 0);
            case "recommend" -> article.setRecommended(on ? 1 : 0);
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "未知的标记类型");
        }
        articleMapper.updateById(article);
    }

    // ---------------- 回收站 ----------------

    public void toRecycle(Long id) {
        Article article = requireArticle(id);
        if (article.published()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "已发布的文章请先下线再删除");
        }
        if (article.getStatus() != null && article.getStatus() == Article.STATUS_APPROVING) {
            throw new BizException(CmsErrorCode.ILLEGAL_STATE, "审批中的稿件不能删除，请先撤回审批");
        }
        articleMapper.deleteById(id);
    }

    public void restore(Long id) {
        articleMapper.restore(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteForever(Long id) {
        Article article = articleMapper.selectDeleted(id);
        if (article == null) {
            throw new BizException(CmsErrorCode.DATA_NOT_FOUND);
        }
        versionMapper.delete(new LambdaQueryWrapper<ArticleVersion>()
                .eq(ArticleVersion::getArticleId, id));
        articleMapper.deleteForever(id);
    }

    // ---------------- 版本与回滚 ----------------

    public List<VersionVO> versions(Long articleId) {
        requireArticle(articleId);
        return versionMapper.selectList(new LambdaQueryWrapper<ArticleVersion>()
                        .eq(ArticleVersion::getArticleId, articleId)
                        .orderByDesc(ArticleVersion::getId))
                .stream().map(this::toVersionVO).toList();
    }

    public VersionVO versionDetail(Long articleId, Long versionId) {
        ArticleVersion v = versionMapper.selectById(versionId);
        if (v == null || !Objects.equals(v.getArticleId(), articleId)) {
            throw new BizException(CmsErrorCode.DATA_NOT_FOUND, "版本不存在");
        }
        return toVersionVO(v);
    }

    /** 一键回滚：当前内容先自动快照，再写入目标版本内容 */
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long articleId, Long versionId) {
        Article article = requireArticle(articleId);
        ArticleVersion v = versionMapper.selectById(versionId);
        if (v == null || !Objects.equals(v.getArticleId(), articleId)) {
            throw new BizException(CmsErrorCode.DATA_NOT_FOUND, "版本不存在");
        }
        snapshot(article, "回滚前自动快照", SecurityUtils.currentUsername().orElse(""));
        article.setTitle(v.getTitle());
        article.setSummary(v.getSummary());
        article.setContent(v.getContent());
        articleMapper.updateById(article);
        snapshot(article, "回滚自 v" + versionId, SecurityUtils.currentUsername().orElse(""));
    }

    /** 发布动作（含快照与审计字段），approve/publishNow/调度器共用 */
    private void publish(Article article, String remark, String auditNote) {
        snapshot(article, remark, SecurityUtils.currentUsername().orElse(""));
        article.setStatus(Article.STATUS_PUBLISHED);
        article.setPublishedAt(LocalDateTime.now());
        article.setAuditNote(auditNote);
        article.setAuditorName(SecurityUtils.currentUsername().orElse(""));
        article.setAuditAt(LocalDateTime.now());
        articleMapper.updateById(article);
    }

    private void snapshot(Article article, String remark, String operator) {
        ArticleVersion v = new ArticleVersion();
        v.setArticleId(article.getId());
        v.setTitle(article.getTitle());
        v.setSummary(article.getSummary());
        v.setContent(article.getContent());
        v.setRemark(remark);
        v.setOperator(operator);
        versionMapper.insert(v);
    }

    /** 定时发布调度：到点的待发布文章逐篇发布（调度入口见 schedule 包的 ScheduledPublishJob，每 30 秒） */
    @Transactional(rollbackFor = Exception.class)
    public int publishDueScheduled() {
        List<Article> due = articleMapper.listDueScheduled();
        for (Article a : due) {
            snapshot(a, "定时发布", "scheduler");
            a.setStatus(Article.STATUS_PUBLISHED);
            a.setPublishedAt(LocalDateTime.now());
            articleMapper.updateById(a);
        }
        if (!due.isEmpty()) {
            log.info("定时发布完成：{} 篇", due.size());
        }
        return due.size();
    }

    // ---------------- 前台公开接口 ----------------

    /** 前台文章列表：已发布，置顶优先 */
    public R.Page<ArticleVO> publicPage(String keyword, Long categoryId, Long tagId,
                                        int page, int size) {
        LambdaQueryWrapper<Article> w = new LambdaQueryWrapper<>();
        w.eq(Article::getStatus, Article.STATUS_PUBLISHED);
        if (StringUtils.hasText(keyword)) {
            w.and(x -> x.like(Article::getTitle, keyword).or().like(Article::getSummary, keyword));
        }
        if (categoryId != null) {
            w.eq(Article::getCategoryId, categoryId);
        }
        if (tagId != null) {
            w.eq(Article::getTagId, tagId);
        }
        w.orderByDesc(Article::getPinned).orderByDesc(Article::getPublishedAt);
        IPage<Article> p = articleMapper.selectPage(Page.of(page, size), w);
        return R.Page.of(p.getRecords().stream().map(a -> toVO(a, false)).toList(), p.getTotal());
    }

    /** 全文检索：ngram FULLTEXT，异常回退 LIKE */
    public List<ArticleVO> search(String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        List<Article> hits;
        try {
            hits = articleMapper.searchFulltext(keyword.trim(), limit);
        } catch (DataAccessException e) {
            log.warn("FULLTEXT 检索失败，回退 LIKE：{}", e.getMessage());
            hits = articleMapper.searchLike(keyword.trim(), limit);
        }
        return hits.stream().map(a -> toVO(a, false)).toList();
    }

    public ArticleVO publicDetail(Long id) {
        Article article = requireArticle(id);
        if (!article.published()) {
            throw new BizException(CmsErrorCode.DATA_NOT_FOUND);
        }
        return toVO(article, true);
    }

    // ---------------- 内部工具 ----------------

    private Article requireArticle(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BizException(CmsErrorCode.DATA_NOT_FOUND, "文章不存在");
        }
        return article;
    }

    private void requireOwnerOrSuper(Article article) {
        if (SecurityUtils.isSuperAdmin()) {
            return;
        }
        Long uid = SecurityUtils.currentUserId();
        if (uid == null || !uid.equals(article.getAuthorId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作自己的稿件");
        }
    }

    private Long requireUserId() {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return uid;
    }

    /** 正文入库前做 XSS 清洗（编辑器允许的标签白名单） */
    private String clean(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        return HtmlSanitizer.cleanRichText(html);
    }

    private String excerpt(String text, int max) {
        String plain = Jsoup.parse(text == null ? "" : text).text();
        return plain.length() <= max ? plain : plain.substring(0, max);
    }

    private String nvl(String s) {
        return s == null ? "" : s.trim();
    }

    private VersionVO toVersionVO(ArticleVersion v) {
        VersionVO vo = new VersionVO();
        vo.setId(v.getId());
        vo.setArticleId(v.getArticleId());
        vo.setTitle(v.getTitle());
        vo.setSummary(v.getSummary());
        vo.setContent(v.getContent());
        vo.setRemark(v.getRemark());
        vo.setOperator(v.getOperator());
        vo.setCreatedAt(v.getCreatedAt());
        return vo;
    }

    private ArticleVO toVO(Article a, boolean withContent) {
        ArticleVO vo = new ArticleVO();
        vo.setId(a.getId());
        vo.setTitle(a.getTitle());
        vo.setSummary(a.getSummary());
        if (withContent) {
            vo.setContent(a.getContent());
        }
        vo.setCategoryId(a.getCategoryId());
        Category c = a.getCategoryId() == null ? null : categoryMapper.selectById(a.getCategoryId());
        vo.setCategoryName(c == null ? "" : c.getName());
        vo.setCover(a.getCover());
        vo.setStatus(a.getStatus());
        vo.setPinned(a.getPinned());
        vo.setRecommended(a.getRecommended());
        vo.setSeoTitle(a.getSeoTitle());
        vo.setSeoKeywords(a.getSeoKeywords());
        vo.setSeoDescription(a.getSeoDescription());
        vo.setPublishAt(a.getPublishAt());
        vo.setPublishedAt(a.getPublishedAt());
        vo.setAuthorId(a.getAuthorId());
        vo.setAuthorName(a.getAuthorName());
        vo.setSubmitNote(a.getSubmitNote());
        vo.setAuditNote(a.getAuditNote());
        vo.setAuditorName(a.getAuditorName());
        vo.setAuditAt(a.getAuditAt());
        vo.setApprovalInstanceId(a.getApprovalInstanceId());
        vo.setCreatedAt(a.getCreatedAt());
        vo.setUpdatedAt(a.getUpdatedAt());

        vo.setTagId(a.getTagId());
        vo.setTag(nvl(a.getTag()));
        vo.setVersionCount(versionMapper.selectCount(new LambdaQueryWrapper<ArticleVersion>()
                .eq(ArticleVersion::getArticleId, a.getId())));
        return vo;
    }
}
