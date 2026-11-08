package com.laimengchao.sso.settings.client.dto;

import lombok.Data;

import java.util.List;

/** 字典批量拉取入参：与 settings-service POST /internal/dicts/batch 的请求体一致 */
@Data
public class DictBatchRequest {

    private List<String> typeCodes;

    public DictBatchRequest() {
    }

    public DictBatchRequest(List<String> typeCodes) {
        this.typeCodes = typeCodes;
    }
}
