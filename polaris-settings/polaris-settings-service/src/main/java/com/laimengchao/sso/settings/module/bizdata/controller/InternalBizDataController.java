package com.laimengchao.sso.settings.module.bizdata.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.settings.module.bizdata.service.BizItemService;
import com.laimengchao.sso.settings.module.bizdata.vo.BizItemVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨服务内部接口：其他服务凭 X-Internal-Token 拉取主数据（不经网关）。
 * <p>
 * 语义与字典内部接口一致：类别不存在/故障返回空列表而不是 404——
 * 调用方拿到空列表走自己的内置兜底，配置中心的故障不放大成业务服务的故障。
 */
@RestController
@RequestMapping("/internal/biz")
public class InternalBizDataController {

    private final BizItemService bizItemService;

    public InternalBizDataController(BizItemService bizItemService) {
        this.bizItemService = bizItemService;
    }

    /** 全部类别清单（固定六类，含展示名与备注），供调用方动态发现类别 */
    @GetMapping("/categories")
    public R<?> categories() {
        return R.ok(bizItemService.listCategories());
    }

    /** 单类别启用项列表（保持 sort 排序），类别不存在返回空列表 */
    @GetMapping("/{type}")
    public R<List<BizItemVO>> items(@PathVariable String type) {
        return R.ok(bizItemService.internalItems(type));
    }

    /**
     * 批量拉取：一次请求拿多个类别，返回 type → items（LinkedHashMap 按入参顺序）。
     * 未知类别对应空列表。
     */
    @PostMapping("/batch")
    public R<Map<String, List<BizItemVO>>> batch(@RequestBody BatchRequest req) {
        Map<String, List<BizItemVO>> out = new LinkedHashMap<>();
        if (req != null && req.getTypes() != null) {
            for (String type : req.getTypes()) {
                out.put(type, bizItemService.internalItems(type));
            }
        }
        return R.ok(out);
    }

    /** 批量拉取入参 */
    public static class BatchRequest {
        private List<String> types;

        public List<String> getTypes() {
            return types;
        }

        public void setTypes(List<String> types) {
            this.types = types;
        }
    }
}
