package com.laimengchao.sso.settings.module.notice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalTemplate;
import com.laimengchao.sso.settings.module.approval.mapper.ApprovalTemplateMapper;
import com.laimengchao.sso.settings.module.notice.dto.NoticeSendRequest;
import com.laimengchao.sso.settings.module.notice.entity.NoticeMessage;
import com.laimengchao.sso.settings.module.notice.entity.NoticeRule;
import com.laimengchao.sso.settings.module.notice.mapper.NoticeMessageMapper;
import com.laimengchao.sso.settings.module.notice.mapper.NoticeRuleMapper;
import com.laimengchao.sso.settings.module.notice.vo.NoticeMessageVO;
import com.laimengchao.sso.settings.module.notice.vo.NoticeRuleVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 待办/消息服务：
 * 1. 配置维护（模块/描述/模板/站内方式/邮件开关）；
 * 2. 跨服务发送（按事件编码渲染模板 → 落站内消息 → 可选邮件）；
 * 3. 我的消息（未读数、列表、已读）。
 * <p>
 * 站内待办与站内消息互斥由 channel 单值保证，邮件只是叠加通道。
 */
@Slf4j
@Service
public class NoticeService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9._-]{1,63}$");
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final NoticeRuleMapper ruleMapper;
    private final NoticeMessageMapper messageMapper;
    private final NoticeMailSender mailSender;
    private final ApprovalTemplateMapper templateMapper;

    public NoticeService(NoticeRuleMapper ruleMapper, NoticeMessageMapper messageMapper,
                         NoticeMailSender mailSender, ApprovalTemplateMapper templateMapper) {
        this.ruleMapper = ruleMapper;
        this.messageMapper = messageMapper;
        this.mailSender = mailSender;
        this.templateMapper = templateMapper;
    }

    /* ---------------- 发送 ---------------- */

    /**
     * 按事件编码发送。规则停用或不存在且调用方未给兜底内容时返回 false，不产生任何数据。
     * <p>
     * 不加 @Transactional：调用方（审批引擎）在自己事务里调用，这里若抛异常被外层 catch，
     * 事务已标记 rollback-only，会把审批主流程一起回滚。单条 insert + 已兜底的邮件足够安全。
     */
    public boolean send(NoticeSendRequest req) {
        if (req == null || req.getAccountId() == null) {
            return false;
        }
        NoticeRule rule = null;
        if (StringUtils.hasText(req.getRuleCode())) {
            rule = ruleMapper.selectOne(new LambdaQueryWrapper<NoticeRule>()
                    .eq(NoticeRule::getCode, req.getRuleCode().trim())
                    .last("LIMIT 1"));
            if (rule != null && !Integer.valueOf(1).equals(rule.getEnabled())) {
                return false;
            }
        }
        String title = rule != null && StringUtils.hasText(rule.getTitleTpl())
                ? render(rule.getTitleTpl(), req) : render(req.getTitle(), req);
        String content = rule != null && StringUtils.hasText(rule.getContentTpl())
                ? render(rule.getContentTpl(), req) : render(req.getContent(), req);
        if (!StringUtils.hasText(title) && !StringUtils.hasText(content)) {
            log.warn("待办/消息未配置且无兜底内容，跳过：ruleCode={} accountId={}",
                    req.getRuleCode(), req.getAccountId());
            return false;
        }

        NoticeMessage msg = new NoticeMessage();
        msg.setAccountId(req.getAccountId());
        msg.setRuleCode(rule == null ? safe(req.getRuleCode()) : rule.getCode());
        msg.setKind(req.getChannel() != null ? req.getChannel()
                : (rule == null || rule.getChannel() == null ? 2 : rule.getChannel()));
        msg.setModule(StringUtils.hasText(req.getModule()) ? safe(req.getModule())
                : (rule == null ? "" : safe(rule.getModule())));
        msg.setTitle(title);
        msg.setContent(content);
        msg.setBizType(safe(req.getBizType()));
        msg.setBizId(safe(req.getBizId()));
        msg.setBizNo(safe(req.getBizNo()));
        msg.setApplicantName(safe(req.getApplicantName()));
        msg.setLink(safe(req.getLink()));
        messageMapper.insert(msg);

        boolean sendMail = req.getEmail() != null
                ? Boolean.TRUE.equals(req.getEmail())
                : rule != null && Integer.valueOf(1).equals(rule.getEmail());
        if (sendMail) {
            mailSender.send(req.getAccountId(), title, content);
        }
        return true;
    }

    /**
     * 群发：同一条业务待办发给多个账号（如「HR 部门全员」）。
     * 逐条落库，返回成功条数；单条失败不影响其它人。
     */
    public int sendBatch(NoticeSendRequest req) {
        if (req == null || req.getAccountIds() == null || req.getAccountIds().isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (Long accountId : req.getAccountIds()) {
            if (accountId == null) {
                continue;
            }
            req.setAccountId(accountId);
            if (send(req)) {
                sent++;
            }
        }
        return sent;
    }

    /* ---------------- 管理端配置 ---------------- */

    public List<NoticeRuleVO> listRules(String keyword) {
        LambdaQueryWrapper<NoticeRule> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(NoticeRule::getCode, keyword.trim())
                    .or().like(NoticeRule::getName, keyword.trim())
                    .or().like(NoticeRule::getModule, keyword.trim()));
        }
        qw.orderByAsc(NoticeRule::getSort).orderByAsc(NoticeRule::getCode);
        return ruleMapper.selectList(qw).stream().map(this::toRuleVO).toList();
    }

    @Transactional
    public NoticeRuleVO createRule(NoticeRule req) {
        if (req.getCode() == null || !CODE_PATTERN.matcher(req.getCode().trim()).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "事件编码需小写字母开头，仅小写字母/数字/点/下划线/中划线");
        }
        String code = req.getCode().trim();
        Long exists = ruleMapper.selectCount(new LambdaQueryWrapper<NoticeRule>().eq(NoticeRule::getCode, code));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.CONFLICT, "事件编码已存在：" + code);
        }
        NoticeRule rule = new NoticeRule();
        rule.setCode(code);
        rule.setAppCode(safe(req.getAppCode()));
        rule.setModule(safe(req.getModule()));
        rule.setName(requireName(req.getName()));
        rule.setTitleTpl(safe(req.getTitleTpl()));
        rule.setContentTpl(safe(req.getContentTpl()));
        rule.setChannel(normalizeChannel(req.getChannel()));
        rule.setEmail(normalizeFlag(req.getEmail()));
        rule.setEnabled(req.getEnabled() == null ? 1 : normalizeFlag(req.getEnabled()));
        rule.setBuiltIn(0);
        rule.setSort(req.getSort() == null ? 0 : req.getSort());
        rule.setReceiverAccountIds(safe(req.getReceiverAccountIds()));
        rule.setReceiverOrgIds(safe(req.getReceiverOrgIds()));
        ruleMapper.insert(rule);
        return toRuleVO(rule);
    }

    @Transactional
    public NoticeRuleVO updateRule(Long id, NoticeRule req) {
        NoticeRule rule = requireRule(id);
        if (req.getName() != null) {
            rule.setName(requireName(req.getName()));
        }
        if (req.getAppCode() != null) {
            rule.setAppCode(safe(req.getAppCode()));
        }
        if (req.getModule() != null) {
            rule.setModule(safe(req.getModule()));
        }
        if (req.getTitleTpl() != null) {
            rule.setTitleTpl(req.getTitleTpl());
        }
        if (req.getContentTpl() != null) {
            rule.setContentTpl(req.getContentTpl());
        }
        if (req.getChannel() != null) {
            rule.setChannel(normalizeChannel(req.getChannel()));
        }
        if (req.getEmail() != null) {
            rule.setEmail(normalizeFlag(req.getEmail()));
        }
        if (req.getEnabled() != null) {
            rule.setEnabled(normalizeFlag(req.getEnabled()));
        }
        if (req.getSort() != null) {
            rule.setSort(req.getSort());
        }
        if (req.getReceiverAccountIds() != null) {
            rule.setReceiverAccountIds(safe(req.getReceiverAccountIds()));
        }
        if (req.getReceiverOrgIds() != null) {
            rule.setReceiverOrgIds(safe(req.getReceiverOrgIds()));
        }
        ruleMapper.updateById(rule);
        return toRuleVO(rule);
    }

    @Transactional
    public void deleteRule(Long id) {
        NoticeRule rule = requireRule(id);
        if (Integer.valueOf(1).equals(rule.getBuiltIn())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "内置配置不可删除，停用即可");
        }
        ruleMapper.deleteById(id);
    }

    /* ---------------- 我的消息 ---------------- */

    public List<NoticeMessageVO> myMessages(Long accountId, Integer kind, Boolean unreadOnly, Integer limit) {
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        LambdaQueryWrapper<NoticeMessage> qw = new LambdaQueryWrapper<NoticeMessage>()
                .eq(NoticeMessage::getAccountId, accountId);
        if (kind != null) {
            qw.eq(NoticeMessage::getKind, kind);
        }
        if (Boolean.TRUE.equals(unreadOnly)) {
            qw.isNull(NoticeMessage::getReadAt);
        }
        qw.orderByDesc(NoticeMessage::getCreatedAt).last("LIMIT " + size);
        List<NoticeMessage> list = messageMapper.selectList(qw);
        // 左侧树按「业务应用 → 业务类型」分组，名称从审批模板取；非审批消息退回模块名
        Map<String, ApprovalTemplate> tplMap = resolveTemplates(list);
        return list.stream()
                .map(m -> toMessageVO(m, m.getBizType() == null ? null : tplMap.get(m.getBizType())))
                .toList();
    }

    /** 审批单办结后，把该单下的待办消息一并置为已读，前端待办自然消失 */
    @Transactional
    public int markBizHandled(String bizType, String bizId) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizId)) {
            return 0;
        }
        return messageMapper.update(null, new LambdaUpdateWrapper<NoticeMessage>()
                .eq(NoticeMessage::getBizType, bizType)
                .eq(NoticeMessage::getBizId, bizId)
                .eq(NoticeMessage::getKind, 1)
                .isNull(NoticeMessage::getReadAt)
                .set(NoticeMessage::getReadAt, LocalDateTime.now()));
    }

    public long unreadCount(Long accountId, Integer kind) {
        Long n = messageMapper.selectCount(new LambdaQueryWrapper<NoticeMessage>()
                .eq(NoticeMessage::getAccountId, accountId)
                .eq(kind != null, NoticeMessage::getKind, kind)
                .isNull(NoticeMessage::getReadAt));
        return n == null ? 0L : n;
    }

    @Transactional
    public int markRead(Long accountId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return messageMapper.update(null, new LambdaUpdateWrapper<NoticeMessage>()
                .eq(NoticeMessage::getAccountId, accountId)
                .isNull(NoticeMessage::getReadAt)
                .in(NoticeMessage::getId, ids)
                .set(NoticeMessage::getReadAt, LocalDateTime.now()));
    }

    @Transactional
    public int markAllRead(Long accountId) {
        return messageMapper.update(null, new LambdaUpdateWrapper<NoticeMessage>()
                .eq(NoticeMessage::getAccountId, accountId)
                .isNull(NoticeMessage::getReadAt)
                .set(NoticeMessage::getReadAt, LocalDateTime.now()));
    }

    /* ---------------- 内部 ---------------- */

    private NoticeRule requireRule(Long id) {
        NoticeRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "消息配置不存在");
        }
        return rule;
    }

    private String requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "配置描述不能为空");
        }
        return name.trim();
    }

    /** 站内方式只允许 1待办 / 2消息，缺省按消息 */
    private int normalizeChannel(Integer channel) {
        return Integer.valueOf(1).equals(channel) ? 1 : 2;
    }

    private int normalizeFlag(Integer v) {
        return Integer.valueOf(1).equals(v) ? 1 : 0;
    }

    private String render(String tpl, NoticeSendRequest req) {
        if (!StringUtils.hasText(tpl)) {
            return "";
        }
        String out = tpl;
        if (req.getVars() != null) {
            for (var e : req.getVars().entrySet()) {
                out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        // 调用方没传的变量留空，避免把 {xxx} 原样展示给用户
        return out.replaceAll("\\{[A-Za-z0-9_]+}", "").trim();
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }

    private NoticeRuleVO toRuleVO(NoticeRule r) {
        NoticeRuleVO vo = new NoticeRuleVO();
        vo.setId(r.getId());
        vo.setCode(r.getCode());
        vo.setAppCode(r.getAppCode());
        vo.setModule(r.getModule());
        vo.setName(r.getName());
        vo.setTitleTpl(r.getTitleTpl());
        vo.setContentTpl(r.getContentTpl());
        vo.setChannel(r.getChannel());
        vo.setEmail(r.getEmail());
        vo.setEnabled(r.getEnabled());
        vo.setBuiltIn(r.getBuiltIn());
        vo.setSort(r.getSort());
        vo.setReceiverAccountIds(r.getReceiverAccountIds());
        vo.setReceiverOrgIds(r.getReceiverOrgIds());
        vo.setCreatedAt(r.getCreatedAt());
        return vo;
    }

    /** 取规则上配置的指定接收人（账号 ID 列表 + 组织节点 ID 列表），供业务方自行展开部门。 */
    public Map<String, List<Long>> ruleReceivers(String code) {
        NoticeRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<NoticeRule>()
                .eq(NoticeRule::getCode, code == null ? "" : code.trim())
                .last("LIMIT 1"));
        Map<String, List<Long>> out = new HashMap<>();
        out.put("accountIds", parseIdList(rule == null ? null : rule.getReceiverAccountIds()));
        out.put("orgIds", parseIdList(rule == null ? null : rule.getReceiverOrgIds()));
        return out;
    }

    private static List<Long> parseIdList(String csv) {
        List<Long> ids = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return ids;
        }
        for (String part : csv.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // 忽略无效项
            }
        }
        return ids;
    }

    /** 批量取审批模板，用于给消息补「业务应用 / 业务类型」分组名 */
    private Map<String, ApprovalTemplate> resolveTemplates(List<NoticeMessage> list) {
        List<String> bizTypes = list.stream()
                .map(NoticeMessage::getBizType)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (bizTypes.isEmpty()) {
            return Map.of();
        }
        return templateMapper.selectList(new LambdaQueryWrapper<ApprovalTemplate>()
                        .in(ApprovalTemplate::getBizType, bizTypes))
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        ApprovalTemplate::getBizType, t -> t, (a, b) -> a));
    }

    private NoticeMessageVO toMessageVO(NoticeMessage m, ApprovalTemplate tpl) {
        NoticeMessageVO vo = new NoticeMessageVO();
        vo.setId(m.getId());
        vo.setModule(m.getModule());
        vo.setKind(m.getKind());
        vo.setTitle(m.getTitle());
        vo.setContent(m.getContent());
        vo.setBizType(m.getBizType());
        vo.setBizId(m.getBizId());
        vo.setBizNo(m.getBizNo());
        vo.setApplicantName(m.getApplicantName());
        vo.setLink(m.getLink());
        vo.setReadAt(m.getReadAt());
        vo.setCreatedAt(m.getCreatedAt());
        if (tpl != null) {
            vo.setGroupApp(tpl.getAppCode());
            vo.setGroupName(tpl.getBizName());
        } else {
            vo.setGroupApp("");
            vo.setGroupName(m.getModule());
        }
        return vo;
    }
}
