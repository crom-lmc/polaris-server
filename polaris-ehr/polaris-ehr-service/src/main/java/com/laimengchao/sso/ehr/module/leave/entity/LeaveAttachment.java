package com.laimengchao.sso.ehr.module.leave.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 请假证明材料：一张请假单可传多个附件。
 * 表只有 created_at（无 updated_at / deleted），删除时直接物理删除。
 */
@Data
@TableName("ehr_leave_attachment")
public class LeaveAttachment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long leaveId;
    /** ehr_attachment 附件ID（biz_type=LEAVE） */
    private Long attachmentId;
    /** 文件名快照，附件被后续清理也不影响列表展示 */
    private String fileName;
    private LocalDateTime createdAt;
}
