package com.laimengchao.sso.forum.module.lottery.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class LotteryConfigRequest {

    private Object mode;
    private Object lotteryCost;
    private Object dailyLimit;

    @JsonIgnore
    private boolean modePresent;
    @JsonIgnore
    private boolean lotteryCostPresent;
    @JsonIgnore
    private boolean dailyLimitPresent;

    public void setMode(Object mode) { this.mode = mode; this.modePresent = true; }
    public void setLotteryCost(Object lotteryCost) { this.lotteryCost = lotteryCost; this.lotteryCostPresent = true; }
    public void setDailyLimit(Object dailyLimit) { this.dailyLimit = dailyLimit; this.dailyLimitPresent = true; }
}
