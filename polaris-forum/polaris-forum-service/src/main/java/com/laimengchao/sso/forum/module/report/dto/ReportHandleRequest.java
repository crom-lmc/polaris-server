package com.laimengchao.sso.forum.module.report.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class ReportHandleRequest {

    private Object status;
    private Object remark;

    @JsonIgnore
    private Set<String> unsupportedFields = new HashSet<>();

    @JsonAnySetter
    public void unsupportedField(String name, Object value) {
        unsupportedFields.add(name);
    }
}
