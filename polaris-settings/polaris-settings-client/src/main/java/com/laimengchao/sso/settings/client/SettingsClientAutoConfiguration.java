package com.laimengchao.sso.settings.client;

import com.laimengchao.sso.settings.client.api.ApprovalApi;
import com.laimengchao.sso.settings.client.api.BankApi;
import com.laimengchao.sso.settings.client.api.CodeRuleApi;
import com.laimengchao.sso.settings.client.api.DictApi;
import com.laimengchao.sso.settings.client.api.EducationApi;
import com.laimengchao.sso.settings.client.api.EmbedPointApi;
import com.laimengchao.sso.settings.client.api.FamilyRelationApi;
import com.laimengchao.sso.settings.client.api.LeaveTypeApi;
import com.laimengchao.sso.settings.client.api.NoticeApi;
import com.laimengchao.sso.settings.client.api.PositionApi;
import com.laimengchao.sso.settings.client.api.ResignReasonApi;
import com.laimengchao.sso.settings.client.support.SettingsClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

/**
 * 引入 settings-client 即自动装配：
 * 1. 注册分类型的 Feign 查询接口——字典 {@link DictApi} 与六个主数据 Api
 *    （bank/education/position/leave_type/resign_reason/family_relation），
 *    全部直连 {@code ${settings.client.base-url}}（默认本机 9007）；
 * 2. 提供 60s 缓存门面 {@link SettingsClient}，业务服务直接注入使用。
 * 使用方已有同类型 Bean 时让位（@ConditionalOnMissingBean）。
 */
@AutoConfiguration
@EnableFeignClients(clients = {
        ApprovalApi.class,
        NoticeApi.class,
        EmbedPointApi.class,
        CodeRuleApi.class,
        DictApi.class,
        BankApi.class,
        EducationApi.class,
        PositionApi.class,
        LeaveTypeApi.class,
        ResignReasonApi.class,
        FamilyRelationApi.class
})
public class SettingsClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SettingsClient settingsClient(DictApi dictApi, BankApi bankApi, EducationApi educationApi,
                                         PositionApi positionApi, LeaveTypeApi leaveTypeApi,
                                         ResignReasonApi resignReasonApi, FamilyRelationApi familyRelationApi,
                                         EmbedPointApi embedPointApi) {
        return new SettingsClient(dictApi, bankApi, educationApi, positionApi,
                leaveTypeApi, resignReasonApi, familyRelationApi, embedPointApi);
    }
}
