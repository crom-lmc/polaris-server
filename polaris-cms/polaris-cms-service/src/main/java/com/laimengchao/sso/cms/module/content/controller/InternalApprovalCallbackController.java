package com.laimengchao.sso.cms.module.content.controller;

import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.cms.module.content.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.cms.module.content.service.ArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批中心终审结果的接收端（CMS 侧）。
 * <p>
 * settings 在审批结束、事务提交后回传，走 {@code /internal/**}，凭 X-Internal-Token 准入。
 * <p>
 * <b>成功/失败必须用 HTTP 状态码区分</b>：settings 的投递器只认 2xx，真异常要给 5xx 才会重试；
 * 「文章已删除」「重复投递」这类属于正常完成，给 200。
 */
@Slf4j
@RestController
@RequestMapping("/internal/cms")
@RequiredArgsConstructor
public class InternalApprovalCallbackController {

    /** 与 settings 的 cfg_biz_scene.biz_type 对齐 */
    private static final String BIZ_ARTICLE_PUBLISH = "cms.article_publish";

    private final ArticleService articleService;

    @PostMapping("/approval-callback")
    public ResponseEntity<R<Void>> approvalCallback(@RequestBody ApprovalCallbackRequest req) {
        log.info("收到内容审批终审回调 instance={} bizType={} bizId={} result={}",
                req.getInstanceId(), req.getBizType(), req.getBizId(), req.getResult());
        try {
            if (BIZ_ARTICLE_PUBLISH.equals(req.getBizType())) {
                articleService.handleApprovalResult(req);
            } else {
                log.info("审批回调的业务场景尚未接入，忽略：bizType={}", req.getBizType());
            }
            return ResponseEntity.ok(R.ok());
        } catch (Exception e) {
            log.error("处理内容审批回调失败 instance={} bizId={}",
                    req.getInstanceId(), req.getBizId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(R.fail(ErrorCode.SYSTEM_ERROR, "审批结果处理失败，请稍后重试"));
        }
    }
}
