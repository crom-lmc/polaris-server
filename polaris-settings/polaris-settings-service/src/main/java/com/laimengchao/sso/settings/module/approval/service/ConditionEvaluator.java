package com.laimengchao.sso.settings.module.approval.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 条件表达式求值：SpEL + SimpleEvaluationContext。
 * <p>
 * <b>为什么必须是 SimpleEvaluationContext</b>：条件表达式是管理员在配置界面上手写的，
 * 不是开发提交的代码。它只要能对「表单快照里的字段」做比较，绝不该有执行任意代码的能力。
 * SimpleEvaluationContext 只提供只读数据绑定，禁类型引用 {@code T(...)}、禁构造器、禁赋值、
 * 禁 Bean 引用，连方法调用解析器都不装（所以「包含」用的是 SpEL 内置运算符 {@code matches}
 * 而不是 {@code x.contains('v')}，后者在这里会报 EL1004E）——比 StandardEvaluationContext
 * 安全一个量级。
 * <p>
 * 求值失败（语法错、字段缺失、类型不匹配）一律按<b>假</b>处理并记 warn：
 * 一个配错的条件不应该让整张单子卡死，跳过该分支比抛出异常更可接受。
 */
@Slf4j
@Component
public class ConditionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    /** 表达式编译结果缓存：条件在每次流程推进时都会求值，重复编译很浪费 */
    private final Map<String, Expression> cache = new ConcurrentHashMap<>();

    /**
     * @param expr 空字符串视为「无条件」，返回 true
     * @param vars 表单快照（键即字段 code）
     */
    public boolean eval(String expr, Map<String, Object> vars) {
        if (expr == null || expr.isBlank()) {
            return true;
        }
        try {
            return evalStrict(expr, vars);
        } catch (Exception ex) {
            // 业务侧把数字塞成字符串（"3000"）时，`wordCount > 3000` 在 SpEL 里会抛
            // 「Cannot compare String with Integer」。业务点是 NUMBER 类型却传了字符串属于
            // 常见脏数据，这里退一步：纯数字串按数值再算一次，只影响原本直接抛异常的表达式，
            // 不会改变 `== '13800138000'` 这类本来就合法的字符串比较。
            Map<String, Object> coerced = coerceNumericStrings(vars);
            if (coerced != null) {
                try {
                    return evalStrict(expr, coerced);
                } catch (Exception ignored) {
                    // 落到下面的 warn
                }
            }
            log.warn("条件求值失败，按「不满足」处理：expr={} err={}", expr, ex.getMessage());
            return false;
        }
    }

    private boolean evalStrict(String expr, Map<String, Object> vars) {
        Expression e = cache.computeIfAbsent(expr.trim(), parser::parseExpression);
        SimpleEvaluationContext ctx = SimpleEvaluationContext
                .forPropertyAccessors(new MapAccessor())
                .withRootObject(vars == null ? new HashMap<>() : vars)
                .build();
        Boolean v = e.getValue(ctx, Boolean.class);
        return Boolean.TRUE.equals(v);
    }

    /** 纯数字字符串 → BigDecimal；没有任何可转换的键就返回 null，省一次无意义的求值 */
    private Map<String, Object> coerceNumericStrings(Map<String, Object> vars) {
        if (vars == null || vars.isEmpty()) {
            return null;
        }
        Map<String, Object> out = new HashMap<>(vars);
        boolean changed = false;
        for (Map.Entry<String, Object> en : vars.entrySet()) {
            Object val = en.getValue();
            if (!(val instanceof String s)) {
                continue;
            }
            String t = s.trim();
            if (t.isEmpty() || t.length() > 18 || !t.matches("-?\\d+(\\.\\d+)?")) {
                continue;
            }
            try {
                out.put(en.getKey(), new java.math.BigDecimal(t));
                changed = true;
            } catch (NumberFormatException ignored) {
                // 转换失败就保留原值
            }
        }
        return changed ? out : null;
    }

    /**
     * 配置保存时的校验：只保证「语法能编译」，不保证「变量一定取得到值」。
     * <p>
     * 为什么不再拿空 Map 试算：MapAccessor 对不存在的键 canRead=false，
     * 于是 {@code days > 3} 这种完全合法的条件会抛 EL1008E（Property or field cannot be found），
     * 管理员就永远存不进带业务点变量的条件——历史上能存进去的都是种子 SQL 直插的。
     * 运行时变量池来自业务单据，配置期拿不到真实值，所以只校验语法。
     * 若调用方确实给了样例值，则顺带试算一次，但两类错误不算错：
     * <ul>
     *   <li>变量缺失（EL1008E）：样例池不全，运行时有值；</li>
     *   <li>类型不可比（EL1013E）：样例给的是字符串，业务单据里可能是数字，
     *       运行时 {@link #eval} 会做纯数字串兜底。拿样例把合法条件拦在保存门外是本末倒置。</li>
     * </ul>
     */
    public String validate(String expr, Map<String, Object> sampleVars) {
        if (expr == null || expr.isBlank()) {
            return null;
        }
        Expression e;
        try {
            e = parser.parseExpression(expr.trim());
        } catch (Exception ex) {
            return ex.getMessage();
        }
        if (sampleVars == null || sampleVars.isEmpty()) {
            return null;
        }
        try {
            SimpleEvaluationContext ctx = SimpleEvaluationContext
                    .forPropertyAccessors(new MapAccessor())
                    .withRootObject(sampleVars)
                    .build();
            e.getValue(ctx, Object.class);
            return null;
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            // 样例池里没有这个变量、或样例值的类型与字面量不可比，都是常态，交给运行时处理
            if (msg.contains("EL1008E") || msg.contains("cannot be found on object of type")
                    || msg.contains("EL1013E") || msg.contains("Cannot compare instances")) {
                return null;
            }
            return msg;
        }
    }
}
