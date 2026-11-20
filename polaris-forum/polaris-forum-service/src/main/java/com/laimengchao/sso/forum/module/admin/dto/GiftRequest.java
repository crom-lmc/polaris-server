package com.laimengchao.sso.forum.module.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/** 礼品管理新增/编辑请求；Object 字段配合 present 标记区分「未传」与「传了 0/false」。 */
@Data
public class GiftRequest {

    private Object name;
    private Object category;
    private Object description;
    private Object points;
    private Object stock;
    private Object icon;
    private Object imageUrl;
    private Object minLevel;
    private Object status;

    @JsonIgnore private boolean namePresent;
    @JsonIgnore private boolean categoryPresent;
    @JsonIgnore private boolean descriptionPresent;
    @JsonIgnore private boolean pointsPresent;
    @JsonIgnore private boolean stockPresent;
    @JsonIgnore private boolean iconPresent;
    @JsonIgnore private boolean imageUrlPresent;
    @JsonIgnore private boolean minLevelPresent;
    @JsonIgnore private boolean statusPresent;

    public void setName(Object name) { this.name = name; this.namePresent = true; }
    public void setCategory(Object category) { this.category = category; this.categoryPresent = true; }
    public void setDescription(Object description) { this.description = description; this.descriptionPresent = true; }
    public void setPoints(Object points) { this.points = points; this.pointsPresent = true; }
    public void setStock(Object stock) { this.stock = stock; this.stockPresent = true; }
    public void setIcon(Object icon) { this.icon = icon; this.iconPresent = true; }
    public void setImageUrl(Object imageUrl) { this.imageUrl = imageUrl; this.imageUrlPresent = true; }
    public void setMinLevel(Object minLevel) { this.minLevel = minLevel; this.minLevelPresent = true; }
    public void setStatus(Object status) { this.status = status; this.statusPresent = true; }
}
