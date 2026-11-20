package com.laimengchao.sso.account.service;

import com.laimengchao.sso.account.dto.response.OverviewVO;

/**
 * 账号中心概览统计。
 */
public interface OverviewService {

    /** 聚合概览数字与图表数据 */
    OverviewVO overview();
}
