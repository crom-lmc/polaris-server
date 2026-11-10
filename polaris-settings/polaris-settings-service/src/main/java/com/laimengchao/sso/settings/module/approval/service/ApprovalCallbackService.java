package com.laimengchao.sso.settings.module.approval.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.settings.module.approval.constant.InstanceStatus;
import com.laimengchao.sso.settings.module.approval.entity.ApprCallback;
import com.laimengchao.sso.settings.module.approval.entity.ApprInstance;
import com.laimengchao.sso.settings.module.approval.mapper.ApprCallbackMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 终审回调：把审批结果送回业务方。
 * <p>
 * <b>为什么是回调而不是让业务方轮询</b>：审批通过后业务要做实事（开户、置状态、停号），
 * 这些动作必须在审批结束的当下触发，轮询会有分钟级延迟，用户点了「通过」却看不到结果。
 * <p>
 * <b>为什么允许失败</b>：这是跨服务调用，回调失败时<b>审批结果依然是有效的</b>（单子确实过了），
 * 不能因为业务方临时不可用就把审批回滚，那会让审批人反复重试。
 * 所以失败落 appr_callback 表，由定时任务退避重试，同时在审批单上标记「业务处理失败」。
 * <p>
 * 代价是<b>最终一致性</b>，因此要求业务方的回调接口必须幂等（按 instanceId 去重），
 * 重试会重复投递。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalCallbackService {

    /** 最多重试 8 次，之后人工处理 */
    private static final int MAX_RETRY = 8;

    private final ApprCallbackMapper callbackMapper;
    private final ApprInstanceMapper instanceMapper;
    private final InternalHttpClient http;
    private final ObjectMapper objectMapper;

    /**
     * 落一条待回调记录，并在事务提交后立即投递一次。
     * <p>
     * 必须在 afterCommit 里发：业务方收到回调会去查审批单状态，
     * 如果这时事务还没提交，它查到的是「进行中」，会误判。
     */
    public void enqueue(ApprInstance inst) {
        ApprCallback cb = new ApprCallback();
        cb.setInstanceId(inst.getId());
        cb.setBizType(inst.getBizType());
        cb.setBizId(inst.getBizId());
        cb.setResult(inst.getStatus());
        cb.setCallbackUrl(inst.getCallbackUrl());
        cb.setPayload(buildPayload(inst));
        cb.setStatus(0);
        cb.setRetryCount(0);
        cb.setNextRetryAt(LocalDateTime.now());
        cb.setLastError("");
        callbackMapper.insert(cb);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(cb.getId());
                }
            });
        } else {
            dispatch(cb.getId());
        }
    }

    /** 立即投递一次 */
    public void dispatch(Long callbackId) {
        ApprCallback cb = callbackMapper.selectById(callbackId);
        if (cb == null || Integer.valueOf(1).equals(cb.getStatus())) {
            return;
        }
        boolean ok;
        try {
            ok = http.postOk(cb.getCallbackUrl(), cb.getPayload());
        } catch (Exception e) {
            ok = false;
            cb.setLastError(trim(e.getMessage()));
        }
        if (ok) {
            cb.setStatus(1);
            cb.setLastError("");
            markInstance(cb.getInstanceId(), 2);
        } else {
            cb.setRetryCount((cb.getRetryCount() == null ? 0 : cb.getRetryCount()) + 1);
            cb.setStatus(2);
            cb.setNextRetryAt(LocalDateTime.now().plusMinutes(backoffMinutes(cb.getRetryCount())));
            if (cb.getLastError() == null || cb.getLastError().isBlank()) {
                cb.setLastError("业务方未返回成功");
            }
            markInstance(cb.getInstanceId(), 3);
            log.warn("审批回调失败，{} 分钟后重试：instance={} url={}",
                    backoffMinutes(cb.getRetryCount()), cb.getInstanceId(), cb.getCallbackUrl());
        }
        callbackMapper.updateById(cb);
    }

    /** 扫描待重试的回调，退避间隔 1/2/4/8/16/30/30… 分钟（调度入口见 schedule 包的 ApprovalCallbackRetryJob） */
    public void retryFailed() {
        List<ApprCallback> list = callbackMapper.selectList(Wrappers.<ApprCallback>lambdaQuery()
                .eq(ApprCallback::getStatus, 2)
                .lt(ApprCallback::getRetryCount, MAX_RETRY)
                .le(ApprCallback::getNextRetryAt, LocalDateTime.now())
                .orderByAsc(ApprCallback::getNextRetryAt)
                .last("limit 20"));
        if (list.isEmpty()) {
            return;
        }
        log.info("重试审批回调 {} 条", list.size());
        for (ApprCallback cb : list) {
            dispatch(cb.getId());
        }
    }

    /** 人工重试入口（审批中心「重新通知业务方」按钮） */
    public void retryNow(Long instanceId) {
        ApprCallback cb = callbackMapper.selectOne(Wrappers.<ApprCallback>lambdaQuery()
                .eq(ApprCallback::getInstanceId, instanceId)
                .orderByDesc(ApprCallback::getId)
                .last("limit 1"));
        if (cb == null) {
            return;
        }
        cb.setStatus(0);
        cb.setRetryCount(0);
        cb.setNextRetryAt(LocalDateTime.now());
        callbackMapper.updateById(cb);
        dispatch(cb.getId());
    }

    private void markInstance(Long instanceId, int callbackStatus) {
        ApprInstance inst = instanceMapper.selectById(instanceId);
        if (inst != null) {
            inst.setCallbackStatus(callbackStatus);
            instanceMapper.updateById(inst);
        }
    }

    private long backoffMinutes(int retry) {
        long[] steps = {1, 2, 4, 8, 16, 30, 30, 30};
        int i = Math.min(Math.max(retry - 1, 0), steps.length - 1);
        return steps[i];
    }

    private String buildPayload(ApprInstance inst) {
        Map<String, Object> m = new LinkedHashMap<>();
        // 雪花 ID 转字符串，避免前端/业务方 JS 解析丢精度
        m.put("instanceId", String.valueOf(inst.getId()));
        m.put("bizType", inst.getBizType());
        m.put("bizId", inst.getBizId());
        // 业务主体与 bizId 语义显式下发：回调方不再靠 selectById(bizId) 查不到就猜「老单」
        m.put("subjectId", inst.getSubjectId());
        m.put("bizIdKind", inst.getBizIdKind());
        m.put("bizNo", inst.getBizNo());
        m.put("result", inst.getStatus());
        InstanceStatus st = InstanceStatus.of(inst.getStatus());
        m.put("resultName", st == null ? "" : st.getLabel());
        m.put("approved", InstanceStatus.APPROVED.getCode() == (inst.getStatus() == null ? -1 : inst.getStatus()));
        m.put("applicantId", inst.getApplicantId());
        m.put("applicantName", inst.getApplicantName());
        m.put("templateCode", inst.getTemplateCode());
        m.put("formData", readFormData(inst));
        m.put("finishedAt", inst.getFinishedAt() == null ? null : inst.getFinishedAt().toString());
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Object readFormData(ApprInstance inst) {
        if (inst.getFormData() == null || inst.getFormData().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(inst.getFormData(), Object.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String trim(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 480 ? s.substring(0, 480) : s;
    }
}
