package com.laimengchao.sso.cms.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategorySaveRequest {

    private Long id;

    @NotBlank(message = "分类名不能为空")
    @Size(max = 64, message = "分类名最长 64 字")
    private String name;

    private Integer sort;
}
