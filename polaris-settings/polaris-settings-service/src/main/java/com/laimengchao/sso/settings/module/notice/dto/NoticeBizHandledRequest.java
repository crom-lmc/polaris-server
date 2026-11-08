package com.laimengchao.sso.settings.module.notice.dto;

import lombok.Data;

/**
 * 业务已处理：把同一业务（bizType + bizId）下所有接收人的未读待办统一置为已读。
 * <p>
 * 典型场景：群发给 HR 部门的「关闭账号」待办，只要有一个人完成了关闭动作，
 * 其他人的这条待办也不必再留着。
 */
@Data
public class NoticeBizHandledRequest {

    private String bizType;
    private String bizId;
}
