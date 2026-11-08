package com.laimengchao.sso.settings.module.dict.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 字典类型视图对象 */
@Data
public class DictTypeVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String code;
    private String name;
    private String appCode;
    private String remark;
    private Integer builtIn;
    private Integer status;
    /** 该类型下的字典项数量（列表页展示用） */
    private Long itemCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
