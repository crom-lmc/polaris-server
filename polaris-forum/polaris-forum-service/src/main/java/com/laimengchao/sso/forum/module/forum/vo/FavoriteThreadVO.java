package com.laimengchao.sso.forum.module.forum.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class FavoriteThreadVO extends ThreadVO {

    @JsonIgnore
    @Override
    public String getRecommendedAt() {
        return super.getRecommendedAt();
    }
}
