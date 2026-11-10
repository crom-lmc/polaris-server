package com.laimengchao.sso.settings.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.settings.module.bizdata.service.BizItemService;
import com.laimengchao.sso.settings.module.bizdata.vo.BizItemVO;
import com.laimengchao.sso.settings.module.dict.service.DictService;
import com.laimengchao.sso.settings.module.dict.vo.DictItemVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 跨应用公开只读参考端点（网关后，供其他应用前端直连）。
 * <p>
 * 与 {@code /internal/**}（服务间、需 X-Internal-Token）不同，这里面向「已登录的其他应用前端」：
 * 网关已校验令牌并注入可信头，本控制器不再做应用作用域/权限码判定（无 {@code @Permit}），
 * 任何通过网关的有效令牌都能读。安全边界靠下面的类型白名单收口——只放行被引用做下拉的
 * 纯枚举字典与业务主数据，避免把配置中心的任意类型敞给外应用。这沿用本系统
 * blog/cms/portal 都有的 {@code xxx/public/**} 约定，把「什么数据允许跨应用暴露」的决策留在
 * settings 自己手里。
 * <p>
 * 数据走与内部接口同一套 {@code internalItems}（仅启用项 + 60s 缓存），类型不存在/停用返回空列表。
 */
@RestController
@RequestMapping("/api/settings/public")
@RequiredArgsConstructor
public class PublicReferenceController {

    /** 允许跨应用读取的纯枚举字典（民族/婚姻/政治面貌/性别/内部通知类型/招聘经验/职位标签/面试方式/沟通方式） */
    private static final Set<String> DICT_ALLOWED = Set.of(
            "general.nation",
            "ehr.marital_status",
            "ehr.political_status",
            "ehr.gender",
            "ehr.announcement_category",
            "ehr.job_experience",
            "ehr.job_tag",
            "ehr.job_city",
            "ehr.interview_method",
            "ehr.candidate_comm_type",
            "ehr.recruit_source"
    );

    /** 允许跨应用读取的业务主数据（银行/学历/职位/请假类型/离职原因/家庭成员关系） */
    private static final Set<String> BIZ_ALLOWED = Set.of(
            "bank",
            "education",
            "position",
            "leave_type",
            "resign_reason",
            "family_relation"
    );

    private final DictService dictService;
    private final BizItemService bizItemService;

    @GetMapping("/dicts/{typeCode}")
    public R<List<Option>> dictItems(@PathVariable String typeCode) {
        if (!DICT_ALLOWED.contains(typeCode)) {
            return R.ok(List.of());
        }
        return R.ok(dictService.internalItems(typeCode).stream()
                .map(i -> new Option(i.getValue(), i.getLabel(), null, null, null, null, null, null))
                .toList());
    }

    @GetMapping("/biz/{type}")
    public R<List<Option>> bizItems(@PathVariable String type) {
        if (!BIZ_ALLOWED.contains(type)) {
            return R.ok(List.of());
        }
        return R.ok(bizItemService.internalItems(type).stream()
                .map(i -> new Option(i.getCode(), i.getName(), i.getCategory(),
                        i.getJobFamily(), i.getJobFamilyName(), i.getSeries(), i.getLevelNo(), i.getLevelName()))
                .toList());
    }

    @Data
    public static class Option {
        private final String value;
        private final String label;
        /** 职位类别（管理类 / 技术类），仅 position 类型有值，其余为 null */
        private final String category;
        /** 职务族编码，前端用它分组（同族 = 一条晋升通道） */
        private final String jobFamily;
        /** 职务族展示名（开发工程师 / 会计 / 招聘专员 …） */
        private final String jobFamilyName;
        /** M 管理 / P 专业 */
        private final String series;
        /** 等级序号，同族内按它升序 = 晋升路线 */
        private final Integer levelNo;
        /** 等级展示名（助理 / 初级 / 中级 / 高级 / 资深） */
        private final String levelName;
    }
}
