package com.laimengchao.sso.account.controller;

import com.laimengchao.sso.account.domain.entity.SsoApplication;
import com.laimengchao.sso.account.dto.request.InternalAppVerifyRequest;
import com.laimengchao.sso.account.dto.response.ApplicationVO;
import com.laimengchao.sso.account.service.ApplicationService;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务间内部接口：应用密钥校验。**不经网关**，仅供 sso-auth 的 ticket exchange 调用。
 * <p>
 * 为什么放在 account：应用注册表归账户域管，auth 只做令牌签发，不重复维护应用数据。
 */
@RestController
@RequestMapping("/internal/apps")
@RequiredArgsConstructor
public class InternalAppController {

    private final ApplicationService applicationService;

    /**
     * 启用中的应用清单（精简字段）。供 settings 等服务的「归属应用」下拉读取，
     * 只暴露 id/code/name，不吐 baseUrl/secret 等敏感字段。
     */
    @GetMapping
    public R<List<AppBrief>> list() {
        List<AppBrief> apps = applicationService.listEnabled().stream()
                .map(a -> new AppBrief(a.getId(), a.getCode(), a.getName()))
                .toList();
        return R.ok(apps);
    }

    /** 校验应用编码 + 密钥；失败抛 401，成功返回应用基本信息 */
    @PostMapping("/verify")
    public R<ApplicationVO> verify(@RequestBody InternalAppVerifyRequest req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "应用编码必填");
        }
        if (!applicationService.verifySecret(req.getCode(), req.getSecret())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "应用密钥无效");
        }
        // 密钥正确后再确认应用处于启用状态（停用的应用不能换 token）
        SsoApplication app = applicationService.requireEnabled(req.getCode());
        ApplicationVO vo = new ApplicationVO();
        vo.setId(app.getId());
        vo.setCode(app.getCode());
        vo.setName(app.getName());
        vo.setBaseUrl(app.getBaseUrl());
        return R.ok(vo);
    }

    /** 应用精简信息（内部接口出参，防字段外泄） */
    @Data
    @AllArgsConstructor
    public static class AppBrief {
        private Long id;
        private String code;
        private String name;
    }
}
