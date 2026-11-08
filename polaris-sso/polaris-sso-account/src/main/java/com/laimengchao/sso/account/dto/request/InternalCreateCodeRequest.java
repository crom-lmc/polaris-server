package com.laimengchao.sso.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 服务间：生成并保存邮箱验证码。 */
@Data
public class InternalCreateCodeRequest {

    @NotBlank(message = "邮箱不能为空")
    private String email;

    private String scene;

    private String ip;
}
