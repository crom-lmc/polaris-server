package com.laimengchao.sso.account.service;

import com.laimengchao.sso.account.domain.entity.SsoApplication;
import com.laimengchao.sso.account.dto.request.ApplicationRequest;
import com.laimengchao.sso.account.dto.response.ApplicationVO;

import java.util.List;

/**
 * 应用注册服务。应用是「账号能进哪些业务系统」的粒度，与按钮级权限码正交。
 */
public interface ApplicationService {

    /**
     * 平台内置应用编码：统一账号中心自身。
     * 启动时幂等播种到 sso_application，列表默认存在，
     * 且不允许编辑/停用/删除/重置密钥——删掉它等于把平台自己从接入列表里开除。
     */
    String BUILTIN_CODE = "sso-center";

    List<ApplicationVO> list(String keyword, Integer status);

    List<ApplicationVO> listEnabled();

    ApplicationVO detail(Long id);

    /** 创建：服务端生成 appSecret，返回体带一次明文 */
    ApplicationVO create(ApplicationRequest req);

    ApplicationVO update(Long id, ApplicationRequest req);

    void delete(Long id);

    /** 重置密钥，返回新的明文（旧密钥立即失效） */
    String resetSecret(Long id);

    /** 取启用状态的应用；不存在或已停用抛业务异常（ticket 签发前校验用） */
    SsoApplication requireEnabled(String code);

    /** 校验应用密钥，供 /api/auth/ticket/exchange 鉴权 */
    boolean verifySecret(String code, String secret);
}
