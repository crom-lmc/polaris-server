package com.laimengchao.sso.settings.client.dto;

import lombok.Data;

/**
 * 业务已处理：把同一业务（bizType + bizId）下所有接收人的未读待办统一置为已读。
 * 用于群发待办——只要有一个人处理完，其他人的这条待办也应消失。
 */
@Data
public class NoticeBizHandledDTO {

    private String bizType;
    private String bizId;
}
