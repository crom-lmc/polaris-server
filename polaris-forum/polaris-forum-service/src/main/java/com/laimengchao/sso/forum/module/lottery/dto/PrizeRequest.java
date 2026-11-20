package com.laimengchao.sso.forum.module.lottery.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class PrizeRequest {

    private Object key;
    private Object prizeType;
    private Object giftId;
    private Object name;
    private Object points;
    private Object color;
    private Object weight;
    private Object sortNo;
    private Object enabled;

    @JsonIgnore
    private boolean keyPresent;
    @JsonIgnore
    private boolean prizeTypePresent;
    @JsonIgnore
    private boolean giftIdPresent;
    @JsonIgnore
    private boolean namePresent;
    @JsonIgnore
    private boolean pointsPresent;
    @JsonIgnore
    private boolean colorPresent;
    @JsonIgnore
    private boolean weightPresent;
    @JsonIgnore
    private boolean sortNoPresent;
    @JsonIgnore
    private boolean enabledPresent;

    public void setKey(Object key) { this.key = key; this.keyPresent = true; }
    public void setPrizeType(Object prizeType) { this.prizeType = prizeType; this.prizeTypePresent = true; }
    public void setGiftId(Object giftId) { this.giftId = giftId; this.giftIdPresent = true; }
    public void setName(Object name) { this.name = name; this.namePresent = true; }
    public void setPoints(Object points) { this.points = points; this.pointsPresent = true; }
    public void setColor(Object color) { this.color = color; this.colorPresent = true; }
    public void setWeight(Object weight) { this.weight = weight; this.weightPresent = true; }
    public void setSortNo(Object sortNo) { this.sortNo = sortNo; this.sortNoPresent = true; }
    public void setEnabled(Object enabled) { this.enabled = enabled; this.enabledPresent = true; }
}
