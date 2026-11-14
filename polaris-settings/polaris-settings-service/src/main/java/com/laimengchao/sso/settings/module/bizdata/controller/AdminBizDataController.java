package com.laimengchao.sso.settings.module.bizdata.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.settings.module.bizdata.service.BizItemService;
import com.laimengchao.sso.settings.module.bizdata.vo.BizCategoryVO;
import com.laimengchao.sso.settings.module.bizdata.vo.BizItemVO;
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

/**
 * 管理端主数据接口（网关后，settings-web 使用）。
 * 类别固定六类（bank/education/position/leave_type/resign_reason/family_relation），
 * 数据项 CRUD 走 @Permit 细粒度权限：查看 biz:view，写 biz:manage。
 */
@Slf4j
@RestController
@RequestMapping("/api/settings/biz")
public class AdminBizDataController {

    private final BizItemService bizItemService;

    public AdminBizDataController(BizItemService bizItemService) {
        this.bizItemService = bizItemService;
    }

    /** 类别清单（固定六类 + 各自条数），管理页 Tab 用 */
    @GetMapping("/categories")
    @Permit(PermissionCodes.BIZ_VIEW)
    public R<List<BizCategoryVO>> categories() {
        return R.ok(bizItemService.listCategories());
    }

    @GetMapping("/{type}/items")
    @Permit(PermissionCodes.BIZ_VIEW)
    public R<List<BizItemVO>> items(@PathVariable String type,
                                    @RequestParam(required = false) Boolean includeDisabled) {
        return R.ok(bizItemService.listItems(type, includeDisabled));
    }

    @PostMapping("/{type}/items")
    @Permit(PermissionCodes.BIZ_MANAGE)
    public R<BizItemVO> createItem(@PathVariable String type, @RequestBody ItemRequest body) {
        return R.ok(bizItemService.createItem(type, body.code, body.name, body.sort, body.remark, body.category));
    }

    @PatchMapping("/{type}/items/{id}")
    @Permit(PermissionCodes.BIZ_MANAGE)
    public R<BizItemVO> updateItem(@PathVariable String type, @PathVariable Long id,
                                   @RequestBody ItemRequest body) {
        return R.ok(bizItemService.updateItem(type, id, body.name, body.sort, body.status, body.remark, body.category));
    }

    @DeleteMapping("/{type}/items/{id}")
    @Permit(PermissionCodes.BIZ_MANAGE)
    public R<Void> deleteItem(@PathVariable String type, @PathVariable Long id) {
        bizItemService.deleteItem(type, id);
        return R.ok();
    }

    @Data
    public static class ItemRequest {
        private String code;
        private String name;
        private Integer sort;
        private Integer status;
        private String remark;
        /** 职位类别（管理类 / 技术类），仅 position 类型使用，其余类型忽略 */
        private String category;
    }
}
