package com.laimengchao.sso.settings.client.api;

import com.laimengchao.sso.settings.client.config.ApprovalFeignConfig;
import com.laimengchao.sso.settings.client.dto.NoticeBizHandledDTO;
import com.laimengchao.sso.settings.client.dto.NoticeSendDTO;
import com.laimengchao.sso.settings.client.dto.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 待办/消息内部接口：其他业务服务按事件编码推送站内待办/消息（可选邮件）。
 * <p>
 * 走 {@code /internal/notice/**}（不经网关），凭 X-Internal-Token 准入。
 * 返回 true 表示至少落了一条站内消息；规则停用/未配置且无兜底内容时为 false。
 */
@FeignClient(
        name = "settings",
        contextId = "settings-notice",
        url = "${settings.client.base-url:http://127.0.0.1:9007}",
        configuration = ApprovalFeignConfig.class)
public interface NoticeApi {

    @PostMapping("/internal/notice/send")
    R<Boolean> send(@RequestBody NoticeSendDTO dto);

    /** 群发：同一条待办发给多个接收人，返回成功落库条数 */
    @PostMapping("/internal/notice/send-batch")
    R<Integer> sendBatch(@RequestBody NoticeSendDTO dto);

    /** 业务已处理：把该业务下所有人未读的待办统一置读（群发待办只需一个人处理完） */
    @PostMapping("/internal/notice/biz-handled")
    R<Integer> bizHandled(@RequestBody NoticeBizHandledDTO dto);
}
