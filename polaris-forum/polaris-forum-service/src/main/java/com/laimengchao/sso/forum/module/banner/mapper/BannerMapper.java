package com.laimengchao.sso.forum.module.banner.mapper;

import com.laimengchao.sso.forum.module.banner.entity.Banner;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BannerMapper {

    List<Banner> listBanners(@Param("enabledOnly") boolean enabledOnly);

    Banner findBanner(@Param("id") long id);

    int insertBanner(Banner values);

    int updateBanner(Banner values);
}
