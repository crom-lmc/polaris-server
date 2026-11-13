package com.laimengchao.sso.ehr.module.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.attendance.dto.AttendanceRuleRequest;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceDaily;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceRule;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceRuleScope;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceRuleSegment;
import com.laimengchao.sso.ehr.module.attendance.mapper.AttendanceDailyMapper;
import com.laimengchao.sso.ehr.module.attendance.mapper.AttendanceRuleMapper;
import com.laimengchao.sso.ehr.module.attendance.mapper.AttendanceRuleScopeMapper;
import com.laimengchao.sso.ehr.module.attendance.mapper.AttendanceRuleSegmentMapper;
import com.laimengchao.sso.ehr.module.attendance.vo.AttendanceRuleVO;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 考勤规则中心：规则 CRUD 与按人解析。
 * <p>
 * 优先级固定为「指定员工 &gt; 最近祖先部门 &gt; 全员 &gt; DEFAULT 兜底」，
 * 只认 status=1 的规则；规则改版不影响历史日结果（生成时已写快照）。
 */
@Service
@RequiredArgsConstructor
public class AttendanceRuleService {

    private final AttendanceRuleMapper ruleMapper;
    private final AttendanceRuleScopeMapper scopeMapper;
    private final AttendanceRuleSegmentMapper segmentMapper;
    private final AttendanceDailyMapper dailyMapper;
    private final OrgNodeMapper orgMapper;

    /** 规则/作用域/时间段本地缓存（30s TTL，规则不常变）。 */
    private static final long CACHE_TTL_MS = 30_000L;
    private volatile List<AttendanceRule> cachedRules;
    private volatile long cachedRulesAt;
    private volatile List<AttendanceRuleScope> cachedScopes;
    private volatile long cachedScopesAt;
    private final Map<Long, List<AttendanceRuleSegment>> segmentCache = new ConcurrentHashMap<>();
    private final Map<Long, List<Long>> orgChainCache = new ConcurrentHashMap<>();

    private List<AttendanceRule> getEnabledRules() {
        long now = System.currentTimeMillis();
        List<AttendanceRule> r = cachedRules;
        if (r != null && now - cachedRulesAt < CACHE_TTL_MS) return r;
        synchronized (this) {
            now = System.currentTimeMillis();
            if (cachedRules != null && now - cachedRulesAt < CACHE_TTL_MS) return cachedRules;
            cachedRules = ruleMapper.selectList(new LambdaQueryWrapper<AttendanceRule>()
                    .eq(AttendanceRule::getStatus, 1));
            cachedRulesAt = now;
            return cachedRules;
        }
    }

    private List<AttendanceRuleScope> getAllScopes() {
        long now = System.currentTimeMillis();
        List<AttendanceRuleScope> s = cachedScopes;
        if (s != null && now - cachedScopesAt < CACHE_TTL_MS) return s;
        synchronized (this) {
            now = System.currentTimeMillis();
            if (cachedScopes != null && now - cachedScopesAt < CACHE_TTL_MS) return cachedScopes;
            cachedScopes = scopeMapper.selectList(null);
            cachedScopesAt = now;
            return cachedScopes;
        }
    }

    private List<AttendanceRuleSegment> segmentsCached(Long ruleId) {
        long now = System.currentTimeMillis();
        List<AttendanceRuleSegment> cached = segmentCache.get(ruleId);
        if (cached != null && !cached.isEmpty()) return cached;
        List<AttendanceRuleSegment> segs = segmentsOf(ruleId);
        segmentCache.put(ruleId, segs);
        return segs;
    }

    /** 命中的规则与时间段 */
    public record ResolvedRule(AttendanceRule rule, List<AttendanceRuleSegment> segments) {
    }

    public R.Page<AttendanceRuleVO> page(String keyword, int page, int size) {
        LambdaQueryWrapper<AttendanceRule> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(AttendanceRule::getCode, keyword.trim())
                    .or().like(AttendanceRule::getName, keyword.trim()));
        }
        w.orderByDesc(AttendanceRule::getStatus).orderByAsc(AttendanceRule::getCode);
        IPage<AttendanceRule> ip = ruleMapper.selectPage(new Page<>(page, size), w);
        List<AttendanceRuleVO> rows = ip.getRecords().stream().map(this::toVO).toList();
        return R.Page.of(rows, ip.getTotal());
    }

    public AttendanceRuleVO detail(Long id) {
        AttendanceRule rule = require(id);
        return toVO(rule);
    }

    @Transactional(rollbackFor = Exception.class)
    public AttendanceRuleVO save(Long id, AttendanceRuleRequest req) {
        if (req == null || req.getCode() == null || req.getCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "规则编码不能为空");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "规则名称不能为空");
        }
        if (req.getWorkStart() == null || req.getWorkEnd() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "上下班时间不能为空");
        }
        String code = req.getCode().trim();
        AttendanceRule duplicated = ruleMapper.selectOne(new LambdaQueryWrapper<AttendanceRule>()
                .eq(AttendanceRule::getCode, code).last("limit 1"));
        if (duplicated != null && !duplicated.getId().equals(id)) {
            throw new BizException(ErrorCode.CONFLICT, "规则编码已存在");
        }

        AttendanceRule rule = id == null ? new AttendanceRule() : require(id);
        rule.setCode(code);
        rule.setName(req.getName().trim());
        rule.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        rule.setWorkStart(req.getWorkStart());
        rule.setWorkEnd(req.getWorkEnd());
        rule.setRestStart(req.getRestStart());
        rule.setRestEnd(req.getRestEnd());
        rule.setWorkHours(req.getWorkHours() == null ? new BigDecimal("8.00") : req.getWorkHours());
        rule.setLateGrace(req.getLateGrace() == null ? 0 : req.getLateGrace());
        rule.setEarlyGrace(req.getEarlyGrace() == null ? 0 : req.getEarlyGrace());
        rule.setAbsentMinMinutes(req.getAbsentMinMinutes() == null ? 0 : req.getAbsentMinMinutes());
        rule.setFlexible(req.getFlexible() == null ? 0 : req.getFlexible());
        rule.setCrossDay(req.getCrossDay() == null ? 0 : req.getCrossDay());
        rule.setRequirePunch(req.getRequirePunch() == null ? 1 : req.getRequirePunch());
        rule.setColor(req.getColor() == null || req.getColor().isBlank() ? "#1677ff" : req.getColor());
        rule.setRemark(req.getRemark() == null ? "" : req.getRemark());
        if (id == null) {
            ruleMapper.insert(rule);
        } else {
            ruleMapper.updateById(rule);
        }

        overwriteScopes(rule.getId(), req.getScopes());
        overwriteSegments(rule.getId(), req.getSegments());
        return toVO(ruleMapper.selectById(rule.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        Long refs = dailyMapper.selectCount(new LambdaQueryWrapper<AttendanceDaily>()
                .eq(AttendanceDaily::getRuleId, id));
        if (refs != null && refs > 0) {
            throw new BizException(ErrorCode.CONFLICT, "该规则已有考勤日结果引用，不能删除");
        }
        scopeMapper.delete(new LambdaQueryWrapper<AttendanceRuleScope>().eq(AttendanceRuleScope::getRuleId, id));
        segmentMapper.delete(new LambdaQueryWrapper<AttendanceRuleSegment>().eq(AttendanceRuleSegment::getRuleId, id));
        ruleMapper.deleteById(id);
    }

    public AttendanceRuleVO toggle(Long id) {
        AttendanceRule rule = require(id);
        rule.setStatus(Integer.valueOf(1).equals(rule.getStatus()) ? 0 : 1);
        ruleMapper.updateById(rule);
        return toVO(rule);
    }

    /** 按员工解析规则：员工 &gt; 最近祖先部门 &gt; 全员 &gt; DEFAULT。 */
    public ResolvedRule resolveRule(Employee emp) {
        List<AttendanceRule> enabled = getEnabledRules();
        if (enabled.isEmpty()) {
            return null;
        }
        Map<Long, AttendanceRule> ruleById = new HashMap<>();
        for (AttendanceRule r : enabled) {
            ruleById.put(r.getId(), r);
        }

        AttendanceRule bestRule = null;
        int bestRank = -1;
        int bestDistance = Integer.MAX_VALUE;
        int bestPriority = Integer.MIN_VALUE;
        if (emp != null) {
            List<Long> chain = orgChainCached(emp.getOrgId());
            List<AttendanceRuleScope> scopes = getAllScopes();
            for (AttendanceRuleScope s : scopes) {
                AttendanceRule r = ruleById.get(s.getRuleId());
                if (r == null) {
                    continue;
                }
                String type = s.getScopeType() == null ? "" : s.getScopeType().toUpperCase();
                int rank;
                int distance = 0;
                if ("EMPLOYEE".equals(type) && emp.getId().equals(s.getScopeId())) {
                    rank = 3;
                } else if ("ORG".equals(type) && s.getScopeId() != null && chain.contains(s.getScopeId())) {
                    rank = 2;
                    distance = chain.indexOf(s.getScopeId());
                } else if ("ALL".equals(type)) {
                    rank = 1;
                } else {
                    continue;
                }
                int priority = s.getPriority() == null ? 0 : s.getPriority();
                boolean better = rank > bestRank
                        || (rank == bestRank && distance < bestDistance)
                        || (rank == bestRank && distance == bestDistance && priority > bestPriority);
                if (better) {
                    bestRule = r;
                    bestRank = rank;
                    bestDistance = distance;
                    bestPriority = priority;
                }
            }
        }

        if (bestRule == null) {
            bestRule = ruleMapper.selectOne(new LambdaQueryWrapper<AttendanceRule>()
                    .eq(AttendanceRule::getCode, "DEFAULT")
                    .eq(AttendanceRule::getStatus, 1)
                    .last("limit 1"));
        }
        if (bestRule == null) {
            return null;
        }
        return new ResolvedRule(bestRule, segmentsCached(bestRule.getId()));
    }

    private List<Long> orgChainCached(Long orgId) {
        if (orgId == null) return List.of();
        return orgChainCache.computeIfAbsent(orgId, this::orgChain);
    }

    public List<AttendanceRuleSegment> segmentsOf(Long ruleId) {
        return segmentMapper.selectList(new LambdaQueryWrapper<AttendanceRuleSegment>()
                .eq(AttendanceRuleSegment::getRuleId, ruleId)
                .orderByAsc(AttendanceRuleSegment::getSeq));
    }

    /* ---------- 内部 ---------- */

    private AttendanceRule require(Long id) {
        AttendanceRule rule = id == null ? null : ruleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "考勤规则不存在");
        }
        return rule;
    }

    private void overwriteScopes(Long ruleId, List<AttendanceRuleRequest.ScopeItem> items) {
        scopeMapper.delete(new LambdaQueryWrapper<AttendanceRuleScope>()
                .eq(AttendanceRuleScope::getRuleId, ruleId));
        if (items == null || items.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (AttendanceRuleRequest.ScopeItem item : items) {
            String type = item.getScopeType() == null ? "" : item.getScopeType().trim().toUpperCase();
            if (!"ALL".equals(type) && !"ORG".equals(type) && !"EMPLOYEE".equals(type)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "适用范围类型只能是 ALL/ORG/EMPLOYEE");
            }
            Long scopeId = "ALL".equals(type) ? 0L : item.getScopeId();
            if (scopeId == null || ("ORG".equals(type) || "EMPLOYEE".equals(type)) && scopeId <= 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "请选择规则适用的组织或员工");
            }
            if (!seen.add(type + ":" + scopeId)) {
                continue;
            }
            AttendanceRuleScope scope = new AttendanceRuleScope();
            scope.setId(IdWorker.getId());
            scope.setRuleId(ruleId);
            scope.setScopeType(type);
            scope.setScopeId(scopeId);
            scope.setPriority(item.getPriority() == null ? 0 : item.getPriority());
            scopeMapper.insert(scope);
        }
    }

    private void overwriteSegments(Long ruleId, List<AttendanceRuleRequest.SegmentItem> items) {
        segmentMapper.delete(new LambdaQueryWrapper<AttendanceRuleSegment>()
                .eq(AttendanceRuleSegment::getRuleId, ruleId));
        if (items == null || items.isEmpty()) {
            return;
        }
        int index = 0;
        for (AttendanceRuleRequest.SegmentItem item : items) {
            index++;
            if (item.getStartTime() == null || item.getEndTime() == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "时间段起止时间不能为空");
            }
            AttendanceRuleSegment seg = new AttendanceRuleSegment();
            seg.setId(IdWorker.getId());
            seg.setRuleId(ruleId);
            seg.setSeq(item.getSeq() == null ? index : item.getSeq());
            seg.setName(item.getName() == null ? "班次" + index : item.getName());
            seg.setStartTime(item.getStartTime());
            seg.setEndTime(item.getEndTime());
            seg.setWorkMinutes(item.getWorkMinutes() == null
                    ? defaultWorkMinutes(item.getStartTime(), item.getEndTime())
                    : item.getWorkMinutes());
            seg.setNeedPunch(item.getNeedPunch() == null ? 1 : item.getNeedPunch());
            seg.setCrossDay(item.getCrossDay() == null ? 0 : item.getCrossDay());
            segmentMapper.insert(seg);
        }
    }

    private int defaultWorkMinutes(LocalTime start, LocalTime end) {
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < 0) {
            minutes += 24 * 60;
        }
        return (int) Math.max(0, minutes);
    }

    /** 祖先链（含自身），从近到远。org.path 形如 "1/2/3"。 */
    private List<Long> orgChain(Long orgId) {
        if (orgId == null) {
            return List.of();
        }
        OrgNode node = orgMapper.selectById(orgId);
        List<Long> chain = new ArrayList<>();
        if (node == null || node.getPath() == null || node.getPath().isBlank()) {
            chain.add(orgId);
            return chain;
        }
        String[] parts = node.getPath().split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            try {
                chain.add(Long.valueOf(parts[i].trim()));
            } catch (NumberFormatException ignored) {
                // 脏数据跳过，保持其余祖先顺序
            }
        }
        if (chain.isEmpty()) {
            chain.add(orgId);
        }
        return chain;
    }

    private AttendanceRuleVO toVO(AttendanceRule rule) {
        AttendanceRuleVO vo = new AttendanceRuleVO();
        vo.setId(rule.getId());
        vo.setCode(rule.getCode());
        vo.setName(rule.getName());
        vo.setStatus(rule.getStatus());
        vo.setWorkStart(rule.getWorkStart());
        vo.setWorkEnd(rule.getWorkEnd());
        vo.setRestStart(rule.getRestStart());
        vo.setRestEnd(rule.getRestEnd());
        vo.setWorkHours(rule.getWorkHours());
        vo.setLateGrace(rule.getLateGrace());
        vo.setEarlyGrace(rule.getEarlyGrace());
        vo.setAbsentMinMinutes(rule.getAbsentMinMinutes());
        vo.setFlexible(rule.getFlexible());
        vo.setCrossDay(rule.getCrossDay());
        vo.setRequirePunch(rule.getRequirePunch());
        vo.setColor(rule.getColor());
        vo.setRemark(rule.getRemark());
        vo.setScopes(scopeMapper.selectList(new LambdaQueryWrapper<AttendanceRuleScope>()
                .eq(AttendanceRuleScope::getRuleId, rule.getId())
                .orderByDesc(AttendanceRuleScope::getPriority)));
        vo.setSegments(segmentsOf(rule.getId()));
        return vo;
    }
}
