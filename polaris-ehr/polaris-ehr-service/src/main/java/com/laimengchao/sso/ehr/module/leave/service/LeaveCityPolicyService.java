package com.laimengchao.sso.ehr.module.leave.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.laimengchao.sso.ehr.integration.EmbedPointReader;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 城市假期政策：婚假 / 产假 / 陪产假的天数与计算方式，按员工所属二级单位的城市
 * 从配置中心埋点 {@code ehr.leave.city-policy} 读取（每个地区政策不同）。
 * <p>
 * 城市取直属二级单位（depth=2）的 city，二级单位下的部门自动继承；
 * 埋点结构：{@code {"西安":{"MARRIAGE":{"days":13,"dayMode":"WORKDAY"},"MATERNITY":{"days":158},...}}}
 */
@Service
@RequiredArgsConstructor
public class LeaveCityPolicyService {

    public static final String TYPE_MARRIAGE = "MARRIAGE";
    public static final String TYPE_MATERNITY = "MATERNITY";
    public static final String TYPE_PATERNITY = "PATERNITY";

    /** 配置中心埋点：城市假期政策 */
    private static final String EMBED_CITY_POLICY = "ehr.leave.city-policy";
    /** 产假 / 陪产假固定按自然日（含周末与法定节假日） */
    private static final String MODE_NATURAL = "NATURAL";
    private static final String MODE_WORKDAY = "WORKDAY";
    private static final Set<String> POLICY_TYPES = Set.of(TYPE_MARRIAGE, TYPE_MATERNITY, TYPE_PATERNITY);

    private final EmbedPointReader embedPointReader;
    private final OrgNodeMapper orgMapper;

    public record CityPolicy(String city, String type, BigDecimal days, String dayMode) {
    }

    public boolean supports(String type) {
        return type != null && POLICY_TYPES.contains(type);
    }

    public Set<String> types() {
        return POLICY_TYPES;
    }

    /** 员工所属二级单位的城市；找不到返回 null */
    public String cityOf(Employee emp) {
        if (emp == null || emp.getOrgId() == null) {
            return null;
        }
        OrgNode self = orgMapper.selectById(emp.getOrgId());
        if (self == null) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        if (self.getPath() != null && !self.getPath().isBlank()) {
            for (String part : self.getPath().split("/")) {
                try {
                    ids.add(Long.parseLong(part.trim()));
                } catch (NumberFormatException ignored) {
                    // path 里的脏数据直接跳过
                }
            }
        }
        if (!ids.contains(self.getId())) {
            ids.add(self.getId());
        }
        List<OrgNode> nodes = orgMapper.selectBatchIds(ids);
        return nodes.stream()
                .filter(n -> Integer.valueOf(2).equals(n.getDepth()))
                .map(OrgNode::getCity)
                .filter(city -> city != null && !city.isBlank())
                .findFirst()
                .orElse(null);
    }

    /** 城市可用的三类政策；城市未配置或埋点缺失时返回空 map */
    public Map<String, CityPolicy> policies(String city) {
        Map<String, CityPolicy> out = new LinkedHashMap<>();
        if (city == null || city.isBlank()) {
            return out;
        }
        JsonNode root = embedPointReader.root(EMBED_CITY_POLICY);
        JsonNode cityNode = root == null ? null : root.get(city);
        if (cityNode == null) {
            return out;
        }
        for (String type : POLICY_TYPES) {
            JsonNode node = cityNode.get(type);
            if (node == null || !node.hasNonNull("days")) {
                continue;
            }
            String dayMode = TYPE_MARRIAGE.equals(type)
                    ? (MODE_WORKDAY.equalsIgnoreCase(node.path("dayMode").asText("")) ? MODE_WORKDAY : MODE_NATURAL)
                    : MODE_NATURAL;
            out.put(type, new CityPolicy(city, type, new BigDecimal(node.path("days").asText()), dayMode));
        }
        return out;
    }

    /** 员工某类假期的城市政策；未配置返回 null（由调用方给出可读错误） */
    public CityPolicy policy(Employee emp, String type) {
        return policies(cityOf(emp)).get(type);
    }
}
