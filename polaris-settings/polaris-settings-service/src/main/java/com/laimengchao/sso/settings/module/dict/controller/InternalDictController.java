package com.laimengchao.sso.settings.module.dict.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.settings.module.dict.service.DictService;
import com.laimengchao.sso.settings.module.dict.vo.DictItemVO;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 跨服务内部接口：其他服务凭 X-Internal-Token 拉取字典（不经网关）。
 * <p>
 * 语义刻意宽松：类型不存在/停用返回空列表而不是 404——调用方拿到空列表走自己的内置兜底，
 * 配置中心的故障不应放大成业务服务的故障。
 */
@RestController
@RequestMapping("/internal/dicts")
public class InternalDictController {

    private final DictService dictService;

    public InternalDictController(DictService dictService) {
        this.dictService = dictService;
    }

    @GetMapping("/{typeCode}")
    public R<List<DictItemVO>> items(@PathVariable String typeCode) {
        return R.ok(dictService.internalItems(typeCode));
    }

    @PostMapping("/batch")
    public R<Map<String, List<DictItemVO>>> batch(@RequestBody BatchRequest body) {
        return R.ok(dictService.internalBatch(body == null ? null : body.typeCodes));
    }

    @Data
    public static class BatchRequest {
        private List<String> typeCodes;
    }
}
