package com.laimengchao.sso.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 统一账号中心统一响应壳的客户端侧映射：{@code {code, message, data}}。
 * 与 settings-client 同样刻意在本模块内再声明一份而不是依赖 common——客户端 jar 保持零传递负担。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class R<T> {

    /** 0 = 成功 */
    private Integer code;

    private String message;

    private T data;

    public boolean ok() {
        return code != null && code == 0;
    }
}
