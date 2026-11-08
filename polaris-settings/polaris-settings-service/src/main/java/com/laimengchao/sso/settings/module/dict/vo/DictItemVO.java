package com.laimengchao.sso.settings.module.dict.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 字典项视图对象 */
@Data
public class DictItemVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String typeCode;
    private String value;
    private String label;
    private Integer sort;
    private Integer status;
    private String remark;
    private Integer builtIn;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
