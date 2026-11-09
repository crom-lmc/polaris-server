package com.laimengchao.sso.ehr.module.importexport.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 批量导入任务：上传预览与确认入库两段式，原始 xlsx 与错误明细都落附件表。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_import_task")
public class ImportTask extends BaseEntity {

    /** EMPLOYEE 员工批量导入 */
    private String type;
    private Long fileId;
    private String fileName;
    /** PREVIEW 待确认 / IMPORTING 导入中 / DONE 已完成 / FAILED 失败 */
    private String status;
    private Integer total;
    private Integer validCount;
    private Integer invalidCount;
    private Integer success;
    private Integer failed;
    private Long errorFileId;
    private Long createdBy;
}
