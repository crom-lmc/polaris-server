package com.laimengchao.sso.settings.module.approval.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalNode;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalPage;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalStep;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalTemplate;
import com.laimengchao.sso.settings.module.approval.entity.BizScene;
import com.laimengchao.sso.settings.module.approval.mapper.ApprovalNodeMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprovalPageMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprovalStepMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprovalTemplateMapper;
import com.laimengchao.sso.settings.module.approval.mapper.BizSceneMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 业务点「被谁引用了」的反向扫描。
 * <p>
 * 业务点不是独立表，而是 {@code cfg_biz_scene.form_schema} 里的一个 JSON 元素；
 * 前端保存业务点是<b>整列覆盖</b>，所以「删除」不会有任何外键或关联表拦一下。
 * 但流程配置里到处都是它的编码：条件表达式写 SpEL 变量、组织负责人规则写
 * {@code FIELD:<code>:USER}、审批页面写 {@code source=FORM} 的字段 key。
 * 悄悄删掉只会让这些配置变成运行时取不到值的悬空引用——现象是「流程莫名不生效」，
 * 而且配置界面上根本看不出来。所以删除前必须按编码反查一遍。
 * <p>
 * <b>扫描口径：宁可多拦，不可漏拦</b>。条件表达式只做「编码作为独立单词出现」的匹配，
 * 不解析 SpEL 语法树：管理员手写的表达式形态太多，漏判一个就够线上排查半天。
 * 字符串字面量会先剔除，避免 {@code reason == 'name'} 把业务点 name 误判成引用。
 * <p>
 * 只覆盖配置期引用。在途单的 {@code appr_instance.snapshot} 是发起时的快照，
 * 表单值也在 {@code form_data} 里，删配置不影响已发起的单，故不计入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizPointReferenceService {

    private final BizSceneMapper sceneMapper;
    private final ApprovalTemplateMapper templateMapper;
    private final ApprovalStepMapper stepMapper;
    private final ApprovalNodeMapper nodeMapper;
    private final ApprovalPageMapper pageMapper;
    private final ObjectMapper objectMapper;

    /** 引用表 key，与前端业务点列表的 rowKey 保持同一格式 */
    public static String key(String bizType, String code) {
        return bizType + "::" + code;
    }

    /**
     * 全量引用表：{@code bizType::code -> [引用位置说明, ...]}。
     * 只返回有引用的业务点；没被引用的不出现在 map 里。
     */
    public Map<String, List<String>> all() {
        Map<String, List<String>> refs = new LinkedHashMap<>();
        List<BizScene> scenes = sceneMapper.selectList(null);
        if (scenes.isEmpty()) {
            return refs;
        }

        Map<String, Set<String>> codesByBiz = new LinkedHashMap<>();
        for (BizScene s : scenes) {
            codesByBiz.put(s.getBizType(), parsePointCodes(s.getFormSchema()));
        }

        Map<Long, ApprovalTemplate> tplById = new HashMap<>();
        for (ApprovalTemplate t : templateMapper.selectList(null)) {
            tplById.put(t.getId(), t);
        }

        scanFieldSchemas(scenes, codesByBiz, refs);
        scanSteps(tplById, codesByBiz, refs);
        scanNodes(tplById, codesByBiz, refs);
        scanPages(tplById, codesByBiz, refs);
        return refs;
    }

    /** 单个业务场景的引用表，供保存时校验用 */
    public Map<String, List<String>> of(String bizType) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        String prefix = bizType + "::";
        all().forEach((k, v) -> {
            if (k.startsWith(prefix)) {
                out.put(k, v);
            }
        });
        return out;
    }

    /** formSchema 里的业务点编码集合；脏 JSON 按「没有业务点」处理，不阻断配置保存 */
    public Set<String> parsePointCodes(String formSchema) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonNode n : readArray(formSchema)) {
            String code = n.path("code").asText("").trim();
            if (!code.isEmpty()) {
                out.add(code);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 各类引用

    /** 场景自带的展示字段池（fieldSchema），source=FORM 的字段直接取业务点编码 */
    private void scanFieldSchemas(List<BizScene> scenes, Map<String, Set<String>> codesByBiz,
                                  Map<String, List<String>> refs) {
        for (BizScene s : scenes) {
            Set<String> codes = codes(s.getBizType(), codesByBiz);
            if (codes.isEmpty()) {
                continue;
            }
            String where = "业务场景「" + label(s.getBizName(), s.getBizType()) + "」· 展示字段池";
            for (JsonNode f : readArray(s.getFieldSchema())) {
                if (!"FORM".equals(f.path("source").asText(""))) {
                    continue;
                }
                String code = f.path("code").asText("").trim();
                if (codes.contains(code)) {
                    add(refs, s.getBizType(), code, where + " · 字段「" + label(f.path("name").asText(""), code) + "」");
                }
            }
        }
    }

    /** 步骤级生效条件 */
    private void scanSteps(Map<Long, ApprovalTemplate> tplById, Map<String, Set<String>> codesByBiz,
                           Map<String, List<String>> refs) {
        for (ApprovalStep st : stepMapper.selectList(null)) {
            ApprovalTemplate t = tplById.get(st.getTemplateId());
            if (t == null || codes(t.getBizType(), codesByBiz).isEmpty()) {
                continue;
            }
            String where = "审批流程「" + label(t.getName(), t.getCode()) + "」· 步骤「"
                    + label(st.getName(), "第" + st.getSeq() + "步") + "」· 生效条件";
            scanExpr(st.getConditionExpr(), t.getBizType(), codes(t.getBizType(), codesByBiz), where, refs, true);
        }
    }

    /** 分支生效条件 + 审批人规则里的 FIELD: 表单来源 */
    private void scanNodes(Map<Long, ApprovalTemplate> tplById, Map<String, Set<String>> codesByBiz,
                           Map<String, List<String>> refs) {
        for (ApprovalNode n : nodeMapper.selectList(null)) {
            ApprovalTemplate t = tplById.get(n.getTemplateId());
            if (t == null) {
                continue;
            }
            Set<String> codes = codes(t.getBizType(), codesByBiz);
            if (codes.isEmpty()) {
                continue;
            }
            String flow = "审批流程「" + label(t.getName(), t.getCode()) + "」";
            String nodeName = label(n.getName(), "分支" + n.getSeq());

            scanExpr(n.getConditionExpr(), t.getBizType(), codes,
                    flow + " · 分支「" + nodeName + "」· 生效条件", refs, true);

            for (JsonNode rule : readArray(n.getApprovers())) {
                String source = rule.path("source").asText("");
                if (!source.startsWith("FIELD:")) {
                    continue;
                }
                // FIELD:<业务点code>:<USER|ORG>
                String[] parts = source.split(":", 3);
                if (parts.length >= 2 && codes.contains(parts[1])) {
                    add(refs, t.getBizType(), parts[1],
                            flow + " · 节点「" + nodeName + "」· 审批人来源（取该字段的组织）");
                }
            }
        }
    }

    /** 审批页面：SECTIONS/GRID 的 FORM 字段 key，以及 TEMPLATE 模式 htmlTpl 里的占位符 */
    private void scanPages(Map<Long, ApprovalTemplate> tplById, Map<String, Set<String>> codesByBiz,
                           Map<String, List<String>> refs) {
        for (ApprovalPage p : pageMapper.selectList(null)) {
            ApprovalTemplate t = tplById.get(p.getTemplateId());
            if (t == null) {
                continue;
            }
            Set<String> codes = codes(t.getBizType(), codesByBiz);
            if (codes.isEmpty()) {
                continue;
            }
            String where = "审批流程「" + label(t.getName(), t.getCode()) + "」· " + viewName(p.getView()) + "页面";
            JsonNode cfg = readObject(p.getConfig());
            for (JsonNode section : arrayOf(cfg.get("sections"))) {
                for (JsonNode f : arrayOf(section.get("fields"))) {
                    if (!"FORM".equals(f.path("source").asText(""))) {
                        continue;
                    }
                    String code = f.path("key").asText("").trim();
                    if (codes.contains(code)) {
                        add(refs, t.getBizType(), code,
                                where + " · 字段「" + label(f.path("label").asText(""), code) + "」");
                    }
                }
            }
            // htmlTpl 是 HTML 文本，带引号的属性很多，这里不做字面量剔除，按保守口径匹配
            scanExpr(p.getHtmlTpl(), t.getBizType(), codes, where + " · 自定义模板", refs, false);
        }
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 表达式里找业务点编码。
     * 用「前后都不是标识符字符」的词匹配，{@code leaveTypeName} 不会命中 {@code name}；
     * stripLiterals=true 时先去掉引号字符串，避免把字面量值当成变量引用。
     */
    private void scanExpr(String expr, String bizType, Set<String> codes, String where,
                          Map<String, List<String>> refs, boolean stripLiterals) {
        if (expr == null || expr.isBlank()) {
            return;
        }
        String text = stripLiterals
                ? expr.replaceAll("'(?:[^'\\\\]|\\\\.)*'", " ").replaceAll("\"(?:[^\"\\\\]|\\\\.)*\"", " ")
                : expr;
        for (String code : codes) {
            Pattern p = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(code) + "(?![A-Za-z0-9_])");
            if (p.matcher(text).find()) {
                add(refs, bizType, code, where);
            }
        }
    }

    private Set<String> codes(String bizType, Map<String, Set<String>> codesByBiz) {
        return codesByBiz.getOrDefault(bizType, Set.of());
    }

    private void add(Map<String, List<String>> refs, String bizType, String code, String desc) {
        List<String> hits = refs.computeIfAbsent(key(bizType, code), k -> new ArrayList<>());
        if (!hits.contains(desc)) {
            hits.add(desc);
        }
    }

    private String viewName(String view) {
        return switch (view == null ? "" : view) {
            case "APPROVAL" -> "审批人";
            case "DETAIL" -> "发起人回看";
            case "AUDIT" -> "审计";
            default -> "审批";
        };
    }

    private String label(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<JsonNode> readArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode n = objectMapper.readTree(json);
            return n != null && n.isArray() ? toList(n) : List.of();
        } catch (Exception e) {
            log.warn("业务点引用扫描：JSON 解析失败，按空处理：{}", e.getMessage());
            return List.of();
        }
    }

    private JsonNode readObject(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode n = objectMapper.readTree(json);
            return n != null && n.isObject() ? n : objectMapper.createObjectNode();
        } catch (Exception e) {
            log.warn("业务点引用扫描：页面配置 JSON 解析失败，按空处理：{}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private List<JsonNode> arrayOf(JsonNode n) {
        if (n == null || !n.isArray()) {
            return List.of();
        }
        return toList(n);
    }

    private List<JsonNode> toList(JsonNode arr) {
        List<JsonNode> out = new ArrayList<>();
        arr.forEach(out::add);
        return out;
    }
}
