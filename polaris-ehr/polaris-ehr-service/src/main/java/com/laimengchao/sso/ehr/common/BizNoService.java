package com.laimengchao.sso.ehr.common;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.settings.client.api.CodeRuleApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 业务单号统一生成：全部走配置中心「单号规则」（settings.cfg_code_rule），
 * 业务侧不再自行拼接编号；未配置规则时直接报错，避免出现无编号数据。
 */
@Service
@RequiredArgsConstructor
public class BizNoService {

    private final CodeRuleApi codeRuleApi;

    public String next(String tableName) {
        var res = codeRuleApi.next(tableName);
        String no = res == null || res.getData() == null ? "" : res.getData().trim();
        if (no.isEmpty()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "取业务单号失败：未配置单号规则或返回为空，tableName=" + tableName);
        }
        return no;
    }
}
