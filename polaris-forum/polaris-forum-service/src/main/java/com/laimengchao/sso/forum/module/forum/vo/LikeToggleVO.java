package com.laimengchao.sso.forum.module.forum.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LikeToggleVO {

    private boolean liked;
    private int likes;
}
