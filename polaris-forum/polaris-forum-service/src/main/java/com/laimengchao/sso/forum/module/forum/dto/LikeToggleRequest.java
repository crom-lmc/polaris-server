package com.laimengchao.sso.forum.module.forum.dto;

import lombok.Data;

@Data
public class LikeToggleRequest {

    private String targetType;
    private Object targetId;
}
