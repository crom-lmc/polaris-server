package com.laimengchao.sso.account.service;

import com.laimengchao.sso.account.dto.request.CreatePermissionRequest;
import com.laimengchao.sso.account.dto.request.UpdatePermissionRequest;
import com.laimengchao.sso.account.dto.response.PermissionVO;

import java.util.List;

/**
 * 权限服务。权限数据集中在 sso_permission，是「角色 -> 权限 -> 账户」这条链的叶子。
 *
 * <p>权限点按 {@code appCode} 分域：{@code sso} 是平台自身，其余对应已注册的业务应用。
 * 不同应用可以有同名权限码（各自的 {@code article:list} 互不影响），
 * 因此所有查询与唯一性校验都必须带应用维度。
 */
public interface PermissionService {

    /**
     * 权限树（菜单 -> 按钮），用于前端动态菜单 + 按钮渲染。
     *
     * @param appCode 应用编码；为空表示全部应用（管理台「全部」视图），各应用自成一棵树
     */
    List<PermissionVO> tree(String appCode);

    /** 全部权限（扁平）；appCode 为空表示全部应用 */
    List<PermissionVO> listAll(String appCode);

    /** 新建权限（appCode 缺省为 sso） */
    PermissionVO create(CreatePermissionRequest req);

    /** 编辑权限（不允许改所属应用） */
    PermissionVO update(Long id, UpdatePermissionRequest req);

    /** 删除权限（内置码/带子节点/被角色引用时拒绝） */
    void delete(Long id);

    /**
     * 批量导入某应用的权限点（幂等 upsert，供业务应用接入时一次性灌入按钮码）。
     *
     * @return 新建数量（已存在的只更新名称/排序，不计数）
     */
    int importForApp(String appCode, List<CreatePermissionRequest> items);

    /** 删除某应用的全部权限点（应用下线时级联清理，同时解除角色关联） */
    int deleteByApp(String appCode);
}
