package com.laimengchao.sso.job.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 应用上报的单个 Handler 定义。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HandlerDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 与 job_info.handler 一致 */
    private String name;

    /** 可选描述，便于后台排查 */
    private String description;
}
