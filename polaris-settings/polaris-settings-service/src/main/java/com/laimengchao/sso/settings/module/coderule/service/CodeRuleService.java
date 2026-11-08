package com.laimengchao.sso.settings.module.coderule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.settings.module.coderule.entity.CodeRule;
import com.laimengchao.sso.settings.module.coderule.mapper.CodeRuleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 业务单号规则：配置维护 + 按规则生成单号。
 * <p>
 * 规则以「业务库表名」为业务标识：业务方生成数据前调用 {@link #nextCode(String)} 取号，
 * 审批单号即 {@code appr_instance}。
 * 生成时用「读当前流水号 → 条件更新」的乐观并发，避免并发取号时重号。
 */
@Slf4j
@Service
public class CodeRuleService {

    /** 审批单表名，审批引擎发起时取号使用 */
    public static final String APPROVAL_TABLE = "appr_instance";

    private static final String DEFAULT_DATE_FORMAT = "yyyyMMdd";

    private final CodeRuleMapper ruleMapper;

    public CodeRuleService(CodeRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    /* ---------------- 管理端配置 ---------------- */

    public List<CodeRule> list(String keyword) {
        LambdaQueryWrapper<CodeRule> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            qw.and(w -> w.like(CodeRule::getTableName, k)
                    .or().like(CodeRule::getAppCode, k)
                    .or().like(CodeRule::getPrefix, k));
        }
        qw.orderByAsc(CodeRule::getAppCode).orderByAsc(CodeRule::getTableName);
        return ruleMapper.selectList(qw);
    }

    @Transactional
    public CodeRule create(CodeRule req) {
        CodeRule rule = normalize(req);
        assertUnique(rule);
        rule.setLastNo(0);
        rule.setLastPeriod("");
        ruleMapper.insert(rule);
        return rule;
    }

    /**
     * 编辑规则：应用与数据库表名是取号的业务标识，创建后不允许修改，这里也不接受客户端传值。
     */
    @Transactional
    public CodeRule update(Long id, CodeRule req) {
        CodeRule rule = requireRule(id);
        rule.setPrefix(safe(req.getPrefix()));
        rule.setSeparatorChar(safe(req.getSeparatorChar()));
        rule.setSeqLength(normalizeSeqLength(req.getSeqLength()));
        rule.setPadZero(normalizeFlag(req.getPadZero(), 1));
        rule.setResetMonthly(normalizeFlag(req.getResetMonthly(), 0));
        rule.setStartNo(normalizeStartNo(req.getStartNo()));
        rule.setDateFormat(normalizeDateFormat(req.getDateFormat()));
        // 表达式由前缀/分隔符/分段拼出，前端只读展示，不接受客户端传值
        rule.setExprFormat(buildExpr(rule.getPrefix(), rule.getSeparatorChar()));
        ruleMapper.updateById(rule);
        return rule;
    }

    /**
     * 调整「下一个」流水号，只能往后调整，避免调到已用过的号导致重复。
     *
     * @param nextNo 下一次取号要返回的流水号
     */
    @Transactional
    public CodeRule adjustNextNo(Long id, Integer nextNo) {
        CodeRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<CodeRule>()
                .eq(CodeRule::getId, id)
                .last("FOR UPDATE"));
        if (rule == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "单号规则不存在");
        }
        if (nextNo == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "下一个流水号不能为空");
        }
        int current = rule.getLastNo() == null ? 0 : rule.getLastNo();
        int minNext = current > 0 ? current + 1 : startNoOf(rule);
        if (nextNo < minNext) {
            throw new BizException(ErrorCode.PARAM_ERROR, "只能往后调整，下一个流水号不能小于 " + minNext);
        }
        // last_no 存「已用到的号」，下一次取号时 +1 即得到 nextNo
        rule.setLastNo(nextNo - 1);
        // 对齐当前周期，避免按月重置规则在下一次取号时又被重置回起始号
        rule.setLastPeriod(periodOf());
        ruleMapper.updateById(rule);
        return rule;
    }

    /* ---------------- 单号生成 ---------------- */

    /**
     * 按业务表名生成下一个单号；未配置规则时返回空串，调用方自行决定是否继续。
     * 业务方在生成数据前调用，把返回值写进自己的单据字段即可。
     * <p>
     * 用 {@code SELECT ... FOR UPDATE} 锁住规则行，并发取号串行执行，保证不重号；
     * 放在事务里，锁持有到调用方事务结束（未在事务中调用时由本方法自己开事务）。
     */
    @Transactional
    public String nextCode(String tableName) {
        if (!StringUtils.hasText(tableName)) {
            return "";
        }
        CodeRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<CodeRule>()
                .eq(CodeRule::getTableName, tableName.trim())
                .last("FOR UPDATE"));
        if (rule == null) {
            return "";
        }
        String period = periodOf();
        Integer last = rule.getLastNo();
        boolean reset = Integer.valueOf(1).equals(rule.getResetMonthly())
                && !period.equals(safe(rule.getLastPeriod()));
        int seq = (last == null || last <= 0 || reset) ? startNoOf(rule) : last + 1;
        rule.setLastNo(seq);
        rule.setLastPeriod(period);
        ruleMapper.updateById(rule);
        return format(rule, seq);
    }

    /* ---------------- 内部 ---------------- */

    private CodeRule normalize(CodeRule req) {
        CodeRule rule = new CodeRule();
        rule.setAppCode(safe(req.getAppCode()));
        rule.setTableName(requireText(req.getTableName(), "数据库表名不能为空"));
        rule.setPrefix(safe(req.getPrefix()));
        rule.setSeparatorChar(safe(req.getSeparatorChar()));
        rule.setSeqLength(normalizeSeqLength(req.getSeqLength()));
        rule.setPadZero(normalizeFlag(req.getPadZero(), 1));
        rule.setResetMonthly(normalizeFlag(req.getResetMonthly(), 0));
        rule.setStartNo(normalizeStartNo(req.getStartNo()));
        rule.setDateFormat(normalizeDateFormat(req.getDateFormat()));
        rule.setExprFormat(buildExpr(rule.getPrefix(), rule.getSeparatorChar()));
        return rule;
    }

    /** 表达式固定为「前缀 + 分隔符 + 日期 + 分隔符 + 流水号」，不允许前端改 */
    private String buildExpr(String prefix, String separatorChar) {
        String sep = separatorChar == null ? "" : separatorChar;
        String expr = "";
        if (StringUtils.hasText(prefix)) {
            expr = "{prefix}" + sep;
        }
        return expr + "{date}" + sep + "{seq}";
    }

    /** 同一张业务表只允许一条规则，否则取号时无法确定用哪条 */
    private void assertUnique(CodeRule rule) {
        Long exists = ruleMapper.selectCount(new LambdaQueryWrapper<CodeRule>()
                .eq(CodeRule::getTableName, rule.getTableName()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.CONFLICT, "该数据库表已配置单号规则：" + rule.getTableName());
        }
    }

    private CodeRule requireRule(Long id) {
        CodeRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "单号规则不存在");
        }
        return rule;
    }

    private String requireText(String v, String msg) {
        if (!StringUtils.hasText(v)) {
            throw new BizException(ErrorCode.PARAM_ERROR, msg);
        }
        return v.trim();
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }

    private int normalizeSeqLength(Integer v) {
        if (v == null) {
            return 4;
        }
        if (v < 1 || v > 12) {
            throw new BizException(ErrorCode.PARAM_ERROR, "流水号长度需在 1~12 之间");
        }
        return v;
    }

    private int normalizeStartNo(Integer v) {
        if (v == null) {
            return 1;
        }
        if (v < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "起始流水号不能小于 1");
        }
        return v;
    }

    private int normalizeFlag(Integer v, int def) {
        return Integer.valueOf(1).equals(v) ? 1 : def;
    }

    /** 日期格式为空按 yyyyMMdd；只允许前台可选的 4 种，避免出现不一致的展示 */
    private String normalizeDateFormat(String v) {
        String fmt = StringUtils.hasText(v) ? v.trim() : DEFAULT_DATE_FORMAT;
        switch (fmt) {
            case "yyyyMMdd":
            case "yyMMdd":
            case "yyyy":
            case "yyyyMM":
                return fmt;
            default:
                throw new BizException(ErrorCode.PARAM_ERROR, "日期格式不合法：" + fmt);
        }
    }

    private int startNoOf(CodeRule rule) {
        return rule.getStartNo() == null || rule.getStartNo() < 1 ? 1 : rule.getStartNo();
    }

    private String periodOf() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    /** 按表达式拼单号：{prefix} / {date} / {seq}；老数据没有表达式时按字段临时拼 */
    private String format(CodeRule rule, int seq) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern(
                StringUtils.hasText(rule.getDateFormat()) ? rule.getDateFormat().trim() : DEFAULT_DATE_FORMAT));
        String seqStr = String.valueOf(seq);
        int len = rule.getSeqLength() == null ? 0 : rule.getSeqLength();
        if (Integer.valueOf(1).equals(rule.getPadZero()) && len > seqStr.length()) {
            seqStr = "0".repeat(len - seqStr.length()) + seqStr;
        }
        String expr = StringUtils.hasText(rule.getExprFormat())
                ? rule.getExprFormat()
                : buildExpr(rule.getPrefix(), rule.getSeparatorChar());
        return expr.replace("{prefix}", safe(rule.getPrefix()))
                .replace("{date}", date)
                .replace("{seq}", seqStr)
                .trim();
    }
}
