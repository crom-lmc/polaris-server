package com.laimengchao.sso.ehr.module.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组织架构节点（自引用树）。depth/path 是冗余字段，用来把「祖先判定」和「整棵子树查询」
 * 变成一次索引扫描，避免 MySQL 递归 CTE（5.7 不支持）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_org_node")
public class OrgNode extends BaseEntity {

    /** 上级节点；集团为 null */
    private Long parentId;
    /** OrgType 名称：GROUP/COMPANY/DEPARTMENT/PROJECT/SUB_PROJECT */
    private String type;
    /** 组织编码：新增时按「父编码 + 4 位同级序号」自动生成（根 XC0001），创建后锁定不可改 */
    private String code;
    private String name;
    /** 组织简称：列表/报表里省地方用 */
    private String shortName;
    /** 部门邮箱（公用邮箱，不是负责人个人邮箱） */
    private String email;
    /** 办公地点 / 楼层工位 */
    private String address;
    /** 编制人数：与「直属在职人数」对照看超编/缺编 */
    private Integer headcount;
    /** 成本中心 / 费用归属编码 */
    private String costCenter;
    /** 成立（挂牌）日期 */
    private java.time.LocalDate establishDate;
    /** 负责人展示名（历史字段，手工填写） */
    private String leader;
    /** 负责人（关联 ehr_employee.id）：审批流按这个找人，比字符串靠谱 */
    private Long leaderEmployeeId;
    /** 负责人变更在途审批单 ID：组织列表据此展示「负责人变更审批中」并可点开详情 */
    private Long leaderApprovalInstanceId;
    private String phone;
    private Integer sort;
    /** 1 启用 / 2 停用 */
    private Integer status;
    /** 层级深度：集团=1 */
    private Integer depth;
    /** 祖先 ID 链（含自身），如 "1/2/3" */
    private String path;
    private String remark;

    public OrgType typeEnum() {
        return OrgType.of(type);
    }
}
