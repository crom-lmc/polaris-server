package com.laimengchao.sso.job.client.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 执行器上报的 Handler 清单（应用启动时与中心重新建连时发送）。
 */
@Data
public class JobDefinitionsRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String appCode;
    /** 实例地址，形如 10.0.0.8:9006 */
    private String address;
    /** 应用版本号，便于排查灰度和不一致 */
    private String version;
    private List<HandlerDefinition> handlers;
}
