package com.laimengchao.sso.account.dto.request;

import lombok.Data;

import java.util.List;

/** 覆盖式设置某账号可访问的应用（传空数组即收回全部）。 */
@Data
public class SetAccountAppsRequest {

    private List<Long> appIds;
}
