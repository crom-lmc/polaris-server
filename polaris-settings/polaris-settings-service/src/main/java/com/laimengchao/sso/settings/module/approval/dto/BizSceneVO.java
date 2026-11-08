package com.laimengchao.sso.settings.module.approval.dto;

import com.laimengchao.sso.settings.module.approval.entity.BizScene;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务场景列表项：场景字段 + 反向查出来的模板绑定信息。
 * <p>
 * <b>为什么不给 cfg_biz_scene 加 template_code 列</b>：场景与模板严格一对一，
 * 绑定关系只存在 {@code cfg_approval_template.biz_type} 一处（保存时校验同 bizType 唯一）。
 * 场景表再存一份编码就成了双写——改模板编码或换绑时必然对不上。
 * 列表要展示「绑了谁」，读的时候按 bizType 关联一次即可。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BizSceneVO extends BizScene {

    /** 绑定模板编码；未绑定为 null */
    private String templateCode;
    private String templateName;
    private Integer templateVersion;
    /** 绑定模板自身是否启用 */
    private Boolean templateEnabled;

    /**
     * 场景可用性：绑定了启用中的模板才算启用。
     * null 表示<b>未绑定模板</b>——前端要能区分「没绑」和「绑了但停了」，
     * 否则会显示成「停用」，让人以为配过又关掉了。
     */
    private Boolean enabled;
}
