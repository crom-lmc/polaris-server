package com.laimengchao.sso.settings.module.dict.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.settings.constant.PermissionCodes;
import com.laimengchao.sso.settings.module.dict.service.DictService;
import com.laimengchao.sso.settings.module.dict.vo.DictItemVO;
import com.laimengchao.sso.settings.module.dict.vo.DictTypeVO;
import com.laimengchao.sso.settings.security.Permit;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端字典接口（网关后，sso-web / settings-web 使用）。
 * 细粒度权限走 @Permit：查看登录即可 + dict:view，写操作分类型/项两级。
 */
@Slf4j
@RestController
@RequestMapping("/api/settings/dicts")
public class AdminDictController {

    private final DictService dictService;

    public AdminDictController(DictService dictService) {
        this.dictService = dictService;
    }

    /* ---------------- 字典类型 ---------------- */

    @GetMapping("/types")
    @Permit(PermissionCodes.DICT_VIEW)
    public R<List<DictTypeVO>> types(@RequestParam(required = false) String keyword) {
        return R.ok(dictService.listTypes(keyword));
    }

    @PostMapping("/types")
    @Permit(PermissionCodes.DICT_TYPE_MANAGE)
    public R<DictTypeVO> createType(@RequestBody TypeRequest body) {
        return R.ok(dictService.createType(body.code, body.name, body.appCode, body.remark));
    }

    @PatchMapping("/types/{id}")
    @Permit(PermissionCodes.DICT_TYPE_MANAGE)
    public R<DictTypeVO> updateType(@PathVariable Long id, @RequestBody TypeRequest body) {
        return R.ok(dictService.updateType(id, body.name, body.appCode, body.remark, body.status));
    }

    @DeleteMapping("/types/{id}")
    @Permit(PermissionCodes.DICT_TYPE_MANAGE)
    public R<Void> deleteType(@PathVariable Long id) {
        dictService.deleteType(id);
        return R.ok();
    }

    /* ---------------- 字典项 ---------------- */

    @GetMapping("/items")
    public R<List<DictItemVO>> items(@RequestParam String typeCode,
                                     @RequestParam(required = false) Boolean includeDisabled) {
        return R.ok(dictService.listItems(typeCode, includeDisabled));
    }

    @PostMapping("/items")
    @Permit(PermissionCodes.DICT_ITEM_MANAGE)
    public R<DictItemVO> createItem(@RequestBody ItemRequest body) {
        return R.ok(dictService.createItem(body.typeCode, body.value, body.label, body.sort, body.remark));
    }

    @PatchMapping("/items/{id}")
    @Permit(PermissionCodes.DICT_ITEM_MANAGE)
    public R<DictItemVO> updateItem(@PathVariable Long id, @RequestBody ItemRequest body) {
        return R.ok(dictService.updateItem(id, body.label, body.sort, body.status, body.remark));
    }

    @DeleteMapping("/items/{id}")
    @Permit(PermissionCodes.DICT_ITEM_MANAGE)
    public R<Void> deleteItem(@PathVariable Long id) {
        dictService.deleteItem(id);
        return R.ok();
    }

    @Data
    public static class TypeRequest {
        private String code;
        private String name;
        private String appCode;
        private String remark;
        private Integer status;
    }

    @Data
    public static class ItemRequest {
        private String typeCode;
        private String value;
        private String label;
        private Integer sort;
        private Integer status;
        private String remark;
    }
}
