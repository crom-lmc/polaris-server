package com.laimengchao.sso.openapi.module.app.vo;

import lombok.Data;

/** 第三方对接应用可调用的开放接口清单项（管理端展示用）。 */
@Data
public class AppEndpointVo {

    private String apiCode;
    private String apiName;

    /** 底层方向 1=INBOUND 2=OUTBOUND（保持不变） */
    private Integer direction;

    /** 业务语义：对外提供 / 调用第三方（与底层 INBOUND/OUTBOUND 展示相反） */
    private String directionLabel;

    /** 调用方向文案，如「XX中台 → 统一开放平台」 */
    private String directionFlow;

    private String operationType;
    private String method;
    private String path;
    private String appCode;
    private String requiredPerm;
    private Integer status;
}
