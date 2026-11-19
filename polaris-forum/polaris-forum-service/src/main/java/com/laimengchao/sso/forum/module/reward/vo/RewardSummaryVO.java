package com.laimengchao.sso.forum.module.reward.vo;

import com.laimengchao.sso.forum.module.forum.entity.ForumMember;
import com.laimengchao.sso.forum.module.forum.entity.ForumPointLog;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RewardSummaryVO {

    private ForumMember member;
    private List<ForumPointLog> logs;
    private Boolean checkedIn;
    private Integer checkinDays;
    private Integer lotteryRemaining;
}
