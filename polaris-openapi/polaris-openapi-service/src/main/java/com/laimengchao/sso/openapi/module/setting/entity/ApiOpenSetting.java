package com.laimengchao.sso.openapi.module.setting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 开放平台设置（KV）实体。 */
@Data
@TableName("api_open_setting")
public class ApiOpenSetting {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String settingKey;
    private String settingValue;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
