package com.laimengchao.sso.forum.module.site.mapper;

import com.laimengchao.sso.forum.module.site.entity.FriendLink;
import com.laimengchao.sso.forum.module.site.entity.SiteConfig;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SiteMapper {

    SiteConfig findConfig();

    int updateConfig(SiteConfig values);

    List<FriendLink> listLinks(@Param("enabledOnly") boolean enabledOnly);

    FriendLink findLink(@Param("id") long id);

    int insertLink(FriendLink values);

    int updateLink(FriendLink values);
}
