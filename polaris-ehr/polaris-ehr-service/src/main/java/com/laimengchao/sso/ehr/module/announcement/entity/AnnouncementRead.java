package com.laimengchao.sso.ehr.module.announcement.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 通知阅读记录：(通知, 账号) 唯一，重复打开只保留首次时间、不重复计数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_announcement_read")
public class AnnouncementRead extends BaseEntity {

    private Long announcementId;
    private Long accountId;
    private LocalDateTime readAt;
}
