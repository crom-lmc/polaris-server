package com.laimengchao.sso.ehr.module.attachment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 业务附件元数据；真实文件按 file_key 落在本地目录，不对外暴露路径。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_attachment")
public class Attachment extends BaseEntity {

    /** LEAVE / EMPLOYEE / CONTRACT ... */
    private String bizType;
    /** 绑定后的业务单据ID；上传时为 0，提交成功后回写 */
    private Long bizId;
    private String fileName;
    private String fileKey;
    private Long size;
    private String mime;
    private Long uploadedBy;
}
