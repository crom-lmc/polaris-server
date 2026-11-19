package com.laimengchao.sso.forum.module.interaction.dto;

import com.laimengchao.sso.forum.module.forum.entity.ForumThread;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
public class FavoriteThreadDTO extends ForumThread {

    private Date favoritedAt;
}
