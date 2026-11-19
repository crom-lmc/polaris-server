package com.laimengchao.sso.forum.module.site.vo;

import lombok.Data;

import java.util.List;

@Data
public class PublicSiteConfigVO {

    private String aboutContent;
    private String footerText;
    private List<FriendLinkVO> friendLinks;
}
