package com.laimengchao.sso.settings.module.approval.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批弹窗的渲染上下文：**页面配置 + 已经算好的字段值**。
 * <p>
 * 关键约定是「服务端算值，前端只画」：
 * <ul>
 *   <li>{@code page} 是配置器里画出来的版式（分组、列数、字段顺序、可用动作），
 *       前端照着它排版，不再自己写死「所属组织/职位/入职日期」这几行；</li>
 *   <li>{@code values} 是每个字段 <b>最终要显示的字符串</b>，
 *       取值口径（表单快照 / 实例属性 / 派生值）全在本服务定完，
 *       前端拿到直接 {@code v-for} 铺开，不参与任何计算。</li>
 * </ul>
 * 这样做的好处是权限与数据口径只有一处：审批人能不能看到某个字段、值该不该随业务变化，
 * 都在服务端判断，前端改版式不会改出「看到了不该看的数据」。
 */
@Data
public class ApprovalContextVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long instanceId;

    private String bizType;
    private String bizName;
    private String title;
    private String applicantName;
    private Integer status;
    private String statusName;

    /** 字段 key → 展示值；缺失的字段不出现在这里，前端显示占位符 */
    private Map<String, Object> values = new LinkedHashMap<>();

    /**
     * 表名 → 定位模式（MAIN_ROW / BIZ_ROW / FK_LIST）。
     * 前端据此判断「表名.列名」缺值时能否降级取 values[表名] 的首行：
     * 只有 FK_LIST（档案子表）允许首行降级，BIZ_ROW/MAIN_ROW 一律用扁平键。
     * 老单没有该字段时前端保持原有兼容行为。
     */
    private Map<String, String> tableModes = new LinkedHashMap<>();

    /** 解析后的页面配置（Map 结构）；未配置时为 null，前端走内置默认版式 */
    private Map<String, Object> page;

    /** 版式类型，便于前端快速分支：SECTIONS / GRID / TEMPLATE / COMPONENT */
    private String renderMode;

    /** 配置里声明的可执行动作，前端据此显隐按钮 */
    private List<String> actions = new ArrayList<>();

    /** 当前查看人在这张单上的待办任务；没有则 null（按钮就该置灰） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long myTaskId;
}
