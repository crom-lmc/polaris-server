package com.laimengchao.sso.job.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * 中心统一响应壳的精简映射：{@code {code, message}}。
 * 客户端模块不依赖 common，故在此单独声明。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobAck implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 0 = 成功 */
    private Integer code;
    private String message;

    public boolean ok() {
        return code != null && code == 0;
    }
}
