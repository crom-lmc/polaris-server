package com.laimengchao.sso.settings.module.notice.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.settings.module.notice.dto.NoticeBizHandledRequest;
import com.laimengchao.sso.settings.module.notice.dto.NoticeSendRequest;
import com.laimengchao.sso.settings.module.notice.service.NoticeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 跨服务发送待办/消息（X-Internal-Token 准入，不经网关）。
 * <p>
 * 业务方只需按事件编码调用，例如：
 * <pre>
 * POST /internal/notice/send
 * {"ruleCode":"approval.approved","accountId":123,"vars":{"title":"请假申请"}}
 * </pre>
 * 标题/内容使用运营中心在配置页维护的模板渲染；站内方式与邮件开关也由配置决定。
 */
@RestController
@RequestMapping("/internal/notice")
public class InternalNoticeController {

    private final NoticeService noticeService;

    public InternalNoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /** 返回 true 表示至少落了一条站内消息；规则停用/未配置且无兜底内容时为 false */
    @PostMapping("/send")
    public R<Boolean> send(@RequestBody NoticeSendRequest req) {
        return R.ok(noticeService.send(req));
    }

    /** 群发：同一条待办发给多个接收人（如 HR 部门全员），返回成功落库条数 */
    @PostMapping("/send-batch")
    public R<Integer> sendBatch(@RequestBody NoticeSendRequest req) {
        return R.ok(noticeService.sendBatch(req));
    }

    /** 业务已处理：把该业务下所有人未读的待办统一置读（群发待办只需一个人处理完） */
    @PostMapping("/biz-handled")
    public R<Integer> bizHandled(@RequestBody NoticeBizHandledRequest req) {
        return R.ok(noticeService.markBizHandled(req.getBizType(), req.getBizId()));
    }

    /**
     * 取规则上配置的指定接收人：{accountIds: [...], orgIds: [...]}。
     * 两者都为空表示未配置，由业务方调用时自行决定接收人；orgIds 由业务方按自身组织树展开（含下级）。
     */
    @GetMapping("/rule-receivers")
    public R<Map<String, List<Long>>> ruleReceivers(@RequestParam String code) {
        return R.ok(noticeService.ruleReceivers(code));
    }
}
