package com.laimengchao.sso.ehr.module.org.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 组织树节点视图：带上中文类型名、直属人数与「允许新增哪些子类型」。
 * <p>
 * id/parentId 必须序列化成字符串：19 位雪花 ID 超过 JS 的 Number.MAX_SAFE_INTEGER，
 * 直接给数字会在前端丢精度，回传后端就变成「节点不存在」。
 */
@Data
public class OrgNodeVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;
    private String type;
    private String typeName;
    private String code;
    private String name;
    /** 组织简称 */
    private String shortName;
    /** 部门公用邮箱 */
    private String email;
    /** 办公地点 */
    private String address;
    /** 编制人数 */
    private Integer headcount;
    /** 成本中心 / 费用归属 */
    private String costCenter;
    /** 成立日期 */
    private java.time.LocalDate establishDate;
    private String leader;

    /** 上级节点名称（详情展示用，避免前端再查一次） */
    private String parentName;
    /** 下级节点数 */
    private Long childCount = 0L;
    /** 含下级在内的总人数 */
    private Long totalEmployeeCount = 0L;
    /**
     * 直属「未归档」员工数（含待入职/审批中/试用/在职/离职中，不含已离职）。
     * 删除与禁用的前置校验口径：树上的 employeeCount 只算试用期+在职，
     * 用它判断会把「只有待入职的部门」放过去，后端再拒一次，体验割裂。
     */
    private Long employeeCountAll = 0L;
    /** 创建时间 */
    private java.time.LocalDateTime createdAt;

    /** 负责人（员工档案ID）：审批流据此找人 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long leaderEmployeeId;

    /** 负责人姓名，从员工档案回填，纯展示 */
    private String leaderEmployeeName;

    private String phone;
    private Integer sort;
    private Integer status;
    private Integer depth;
    private String path;
    private String remark;
    /** 直属该节点的在职员工数（不含下级） */
    private Long employeeCount = 0L;
    /* ---------- 当前在途审批（负责人变更） ---------- */

    /** 负责人变更在途审批单 ID；有值即表示「负责人变更审批中」 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long approvalInstanceId;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String approvalBizType;

    /* ---------- 最近一次负责人变更（来自 ehr_leader_change，列表里直接给结论） ---------- */

    /** 0 审批中 / 1 已生效 / 2 已驳回 / 3 已撤销；无变更记录时为空 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Integer leaderChangeStatus;
    private String leaderChangeStatusName;
    /** 最近一次变更的审批单 ID：前端据此点开审批详情（含历史已结束的单） */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long leaderChangeInstanceId;
    /** 最近一次变更的提交时间 */
    private java.time.LocalDateTime leaderChangeAt;
    /** 最近一次变更的原因，列表里做 tooltip */
    private String leaderChangeReason;

    /** 前端新增子节点时可选的类型（code + label） */
    private List<ChildType> allowedChildTypes = new ArrayList<>();
    private List<OrgNodeVO> children = new ArrayList<>();

    @Data
    public static class ChildType {
        private String code;
        private String label;

        public ChildType(String code, String label) {
            this.code = code;
            this.label = label;
        }
    }
}
