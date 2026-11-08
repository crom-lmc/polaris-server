package com.laimengchao.sso.settings.module.coderule.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务单号规则：按「业务库表名」定位，一条规则只负责一个业务单据的单号。
 * <p>
 * 业务方在生成数据前调用 {@code /internal/code-rules/next?tableName=xxx} 取号并写入自己单据；
 * 审批单固定用 {@code appr_instance}，由审批引擎在发起时自动取号。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cfg_code_rule")
public class CodeRule extends BaseEntity {

    /** 归属应用编码，仅用于界面分组展示 */
    private String appCode;
    /** 业务库表名，如 appr_instance；作为取号时的业务标识 */
    private String tableName;
    /** 单号前缀 */
    private String prefix;
    /** 各段之间的分隔符，如 -（列名避开 MySQL 保留字 separator） */
    private String separatorChar;
    /** 流水号位数 */
    private Integer seqLength;
    /** 1 不足位数补 0 / 0 不补 */
    private Integer padZero;
    /** 1 按月重置流水号 / 0 不重置 */
    private Integer resetMonthly;
    /** 起始流水号 */
    private Integer startNo;
    /** 日期格式，如 yyyyMMdd */
    private String dateFormat;
    /** 表达式格式，支持 {prefix} / {date} / {seq} / {period}，如 {prefix}-{date}-{seq} */
    private String exprFormat;
    /** 最近一次生成的流水号，0=尚未生成 */
    private Integer lastNo;
    /** 最近一次生成所属周期（yyyyMM），用于按月重置判断 */
    private String lastPeriod;
}
