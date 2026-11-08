package com.laimengchao.sso.account.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.account.domain.entity.SsoAccountApp;
import com.laimengchao.sso.account.domain.entity.SsoApplication;
import com.laimengchao.sso.account.dto.request.ApplicationRequest;
import com.laimengchao.sso.account.dto.response.ApplicationVO;
import com.laimengchao.sso.account.mapper.SsoAccountAppMapper;
import com.laimengchao.sso.account.mapper.SsoApplicationMapper;
import com.laimengchao.sso.account.service.ApplicationService;
import com.laimengchao.sso.account.service.PermissionService;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    /** 1 启用 / 2 停用，与 sso_application.status 注释一致 */
    private static final int ENABLED = 1;
    private static final int DISABLED = 2;

    /** 去掉 0/O/1/l/I 等易混淆字符，便于人工抄写密钥 */
    private static final char[] SECRET_ALPHABET =
            "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SsoApplicationMapper applicationMapper;
    private final SsoAccountAppMapper accountAppMapper;
    private final PermissionService permissionService;

    @Override
    public List<ApplicationVO> list(String keyword, Integer status) {
        LambdaQueryWrapper<SsoApplication> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(SsoApplication::getCode, keyword)
                    .or().like(SsoApplication::getName, keyword));
        }
        if (status != null) {
            w.eq(SsoApplication::getStatus, status);
        }
        w.orderByAsc(SsoApplication::getSort).orderByAsc(SsoApplication::getId);
        return applicationMapper.selectList(w).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<ApplicationVO> listEnabled() {
        LambdaQueryWrapper<SsoApplication> w = new LambdaQueryWrapper<>();
        w.eq(SsoApplication::getStatus, ENABLED)
                .orderByAsc(SsoApplication::getSort).orderByAsc(SsoApplication::getId);
        return applicationMapper.selectList(w).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public ApplicationVO detail(Long id) {
        return toVO(require(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplicationVO create(ApplicationRequest req) {
        String code = normalizedCode(req.getCode());
        if (BUILTIN_CODE.equals(code)) {
            throw new BizException(ErrorCode.CONFLICT, "该应用编码为平台保留，请换一个");
        }
        if (applicationMapper.selectByCode(code) != null) {
            throw new BizException(ErrorCode.CONFLICT, "应用编码已存在");
        }
        SsoApplication app = new SsoApplication();
        app.setCode(code);
        app.setName(requireName(req.getName()));
        app.setBaseUrl(nvl(req.getBaseUrl()));
        app.setRedirectUri(nvl(req.getRedirectUri()));
        app.setIcon(nvl(req.getIcon()));
        app.setRemark(nvl(req.getRemark()));
        app.setStatus(req.getStatus() == null ? ENABLED : req.getStatus());
        app.setSort(req.getSort() == null ? 0 : req.getSort());
        app.setAppSecret(randomSecret(32));
        applicationMapper.insert(app);

        ApplicationVO vo = toVO(app);
        // 明文只在创建时返回一次，之后接口一律脱敏
        vo.setAppSecret(app.getAppSecret());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplicationVO update(Long id, ApplicationRequest req) {
        SsoApplication app = require(id);
        ensureMutable(app);
        // 编码是 ticket/secret 的关联键，创建后不允许改：改了等于换一个应用
        if (req.getCode() != null && !req.getCode().isBlank()
                && !app.getCode().equals(req.getCode().trim())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "应用编码不允许修改");
        }
        if (req.getName() != null) {
            app.setName(requireName(req.getName()));
        }
        if (req.getBaseUrl() != null) {
            app.setBaseUrl(req.getBaseUrl().trim());
        }
        if (req.getRedirectUri() != null) {
            app.setRedirectUri(req.getRedirectUri().trim());
        }
        if (req.getIcon() != null) {
            app.setIcon(req.getIcon().trim());
        }
        if (req.getRemark() != null) {
            app.setRemark(req.getRemark().trim());
        }
        if (req.getStatus() != null) {
            app.setStatus(req.getStatus());
        }
        if (req.getSort() != null) {
            app.setSort(req.getSort());
        }
        applicationMapper.updateById(app);
        return toVO(app);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SsoApplication app = require(id);
        ensureMutable(app);
        // 关联表是物理表（无逻辑删除列），必须显式清理，否则残留授权会在应用重建后"复活"
        accountAppMapper.delete(new LambdaQueryWrapper<SsoAccountApp>().eq(SsoAccountApp::getAppId, id));
        // 应用下线后它注册的按钮码已无人使用，留着只会在权限树里变成僵尸节点
        permissionService.deleteByApp(app.getCode());
        applicationMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetSecret(Long id) {
        SsoApplication app = require(id);
        ensureMutable(app);
        String secret = randomSecret(32);
        app.setAppSecret(secret);
        applicationMapper.updateById(app);
        return secret;
    }

    @Override
    public SsoApplication requireEnabled(String code) {
        SsoApplication app = applicationMapper.selectByCode(code);
        if (app == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "应用不存在：" + code);
        }
        if (app.getStatus() == null || app.getStatus() != ENABLED) {
            throw new BizException(ErrorCode.FORBIDDEN, "应用已停用：" + code);
        }
        return app;
    }

    @Override
    public boolean verifySecret(String code, String secret) {
        SsoApplication app = applicationMapper.selectByCode(code);
        if (app == null || secret == null || app.getAppSecret() == null) {
            return false;
        }
        // 定长比较，避免时序侧信道（密钥本身熵足够，这里只是稳妥起见）
        return java.security.MessageDigest.isEqual(
                app.getAppSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // ---------------- 内部工具 ----------------

    private SsoApplication require(Long id) {
        SsoApplication app = applicationMapper.selectById(id);
        if (app == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "应用不存在");
        }
        return app;
    }

    /** 内置应用（统一账号中心）只读：编辑/停用/删除/重置密钥一律拒绝 */
    private void ensureMutable(SsoApplication app) {
        if (BUILTIN_CODE.equals(app.getCode())) {
            throw new BizException(ErrorCode.FORBIDDEN, "平台内置应用不允许操作");
        }
    }

    private String normalizedCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "应用编码必填");
        }
        String code = raw.trim();
        if (!code.matches("^[a-z0-9][a-z0-9_-]{1,63}$")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "编码需 2-64 位小写字母/数字/下划线/中划线，且以字母或数字开头");
        }
        return code;
    }

    private String requireName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "应用名称必填");
        }
        return raw.trim();
    }

    private String nvl(String s) {
        return s == null ? "" : s.trim();
    }

    private String randomSecret(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(SECRET_ALPHABET[RANDOM.nextInt(SECRET_ALPHABET.length)]);
        }
        return sb.toString();
    }

    private ApplicationVO toVO(SsoApplication app) {
        ApplicationVO vo = new ApplicationVO();
        vo.setId(app.getId());
        vo.setCode(app.getCode());
        vo.setName(app.getName());
        vo.setBaseUrl(app.getBaseUrl());
        vo.setRedirectUri(app.getRedirectUri());
        vo.setIcon(app.getIcon());
        vo.setRemark(app.getRemark());
        vo.setStatus(app.getStatus());
        vo.setSort(app.getSort());
        vo.setCreatedAt(app.getCreatedAt());
        vo.setBuiltIn(BUILTIN_CODE.equals(app.getCode()));
        vo.setAppSecretMasked(maskSecret(app.getAppSecret()));
        return vo;
    }

    /** 只露出首尾各 4 位，足够人工核对是否换过密钥 */
    private String maskSecret(String secret) {
        if (secret == null || secret.length() <= 8) {
            return "****";
        }
        return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
    }
}
