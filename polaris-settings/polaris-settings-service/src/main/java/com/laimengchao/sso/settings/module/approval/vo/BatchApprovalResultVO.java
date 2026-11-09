package com.laimengchao.sso.settings.module.approval.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量审批结果：成功项已生效，失败项带原因，不做整体回滚。
 */
@Data
public class BatchApprovalResultVO {

    private List<Long> success = new ArrayList<>();
    private List<Failure> failed = new ArrayList<>();

    public void addSuccess(Long taskId) {
        success.add(taskId);
    }

    public void addFailure(Long taskId, String reason) {
        Failure f = new Failure();
        f.setTaskId(taskId);
        f.setReason(reason);
        failed.add(f);
    }

    @Data
    public static class Failure {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long taskId;
        private String reason;
    }
}
