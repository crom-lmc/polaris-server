package com.laimengchao.sso.job.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 心跳 / 下线请求体。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobHeartbeatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String appCode;
    private String address;
}
