package com.laimengchao.sso.ehr.module.announcement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.announcement.dto.AnnouncementSaveRequest;
import com.laimengchao.sso.ehr.module.announcement.entity.Announcement;
import com.laimengchao.sso.ehr.module.announcement.entity.AnnouncementRead;
import com.laimengchao.sso.ehr.module.announcement.mapper.AnnouncementMapper;
import com.laimengchao.sso.ehr.module.announcement.mapper.AnnouncementReadMapper;
import com.laimengchao.sso.ehr.module.announcement.vo.AnnouncementVO;
import com.laimengchao.sso.ehr.module.employee.constant.EmployeeStatus;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.common.security.HtmlSanitizer;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.settings.client.api.CodeRuleApi;
import com.laimengchao.sso.settings.client.support.SettingsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 内部通知服务：人力中心维护（草稿 → 发布 → 下架），统一账号中心「通知中心」按目标范围展示。
 * <p>
 * 可见性口径：target_type=0（全员/集团）对所有登录人可见；target_type=1（按部门）时，
 * 只要本人档案所在组织的祖先链（path）里有任一被选中的部门即可见，天然支持「含下级部门」。
 * 阅读人数按 (通知, 账号) 唯一记录，重复打开不重复计数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    /** 通知类型字典：配置中心 ehr.announcement_category；取不到时用内置兜底，settings 故障不阻断业务 */
    private static final String DICT_CATEGORY = "ehr.announcement_category";
    private static final Map<String, String> CATEGORY_FALLBACK = Map.of(
            "HOLIDAY", "节假日通知",
            "COMMENDATION", "表彰通知",
            "DEPARTMENT", "部门通知",
            "POLICY", "制度通知",
            "APPOINTMENT", "部门任命",
            "OTHER", "其他");
    private static final String DEFAULT_CATEGORY = "OTHER";

    /** 取号业务标识：settings 的单号规则以业务库表名为 key */
    private static final String TABLE_ANNOUNCEMENT = "ehr_announcement";

    private static final Map<Integer, String> STATUS_NAMES = Map.of(
            0, "编辑中",
            1, "已发布",
            2, "已下架");

    private final AnnouncementMapper announcementMapper;
    private final AnnouncementReadMapper readMapper;
    private final EmployeeMapper employeeMapper;
    private final OrgNodeMapper orgMapper;
    private final CodeRuleApi codeRuleApi;
    private final SettingsClient settingsClient;

    /* ======================= 管理端 ======================= */

    public R.Page<AnnouncementVO> page(Integer status, String category, String keyword, int page, int size) {
        LambdaQueryWrapper<Announcement> w = new LambdaQueryWrapper<>();
        if (status != null) {
            w.eq(Announcement::getStatus, status);
        }
        String cat = category == null ? "" : category.trim().toUpperCase();
        if (!cat.isEmpty()) {
            w.eq(Announcement::getCategory, cat);
        }
        String kw = keyword == null ? "" : keyword.trim();
        if (!kw.isEmpty()) {
            w.and(q -> q.like(Announcement::getTitle, kw).or().like(Announcement::getSummary, kw));
        }
        w.orderByDesc(Announcement::getPinned).orderByDesc(Announcement::getCreatedAt);
        IPage<Announcement> ip = announcementMapper.selectPage(new Page<>(page, size), w);
        List<AnnouncementVO> rows = ip.getRecords().stream().map(a -> toVO(a, false)).toList();
        return R.Page.of(rows, ip.getTotal());
    }

    public AnnouncementVO detail(Long id) {
        return toVO(require(id), true);
    }

    public AnnouncementVO create(AnnouncementSaveRequest req) {
        SecurityUser user = requireLogin();
        Announcement a = new Announcement();
        a.setStatus(0);
        a.setPinned(0);
        a.setReadCount(0);
        a.setTargetCount(0);
        a.setPublisherAccountId(user.getAccountId());
        a.setPublisherName(user.getUsername());
        applySave(a, req);
        a.setAnnouncementNo(nextAnnouncementNo());
        announcementMapper.insert(a);
        return toVO(a, true);
    }

    /** 已发布的通知必须先下架才能编辑，避免线上内容被半截改写 */
    public AnnouncementVO update(Long id, AnnouncementSaveRequest req) {
        Announcement a = require(id);
        if (a.getStatus() != null && a.getStatus() == 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "已发布的通知请先下架再编辑");
        }
        applySave(a, req);
        announcementMapper.updateById(a);
        return toVO(a, true);
    }

    public AnnouncementVO publish(Long id) {
        Announcement a = require(id);
        if (a.getStatus() != null && a.getStatus() == 1) {
            return toVO(a, true);
        }
        int targetCount = computeTargetCount(a);
        announcementMapper.update(null, new LambdaUpdateWrapper<Announcement>()
                .eq(Announcement::getId, id)
                .set(Announcement::getStatus, 1)
                .set(Announcement::getPublishAt, LocalDateTime.now())
                .set(Announcement::getOfflineAt, null)
                .set(Announcement::getTargetCount, targetCount));
        return toVO(require(id), true);
    }

    public AnnouncementVO offline(Long id) {
        Announcement a = require(id);
        if (a.getStatus() == null || a.getStatus() != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "只有已发布的通知可以下架");
        }
        announcementMapper.update(null, new LambdaUpdateWrapper<Announcement>()
                .eq(Announcement::getId, id)
                .set(Announcement::getStatus, 2)
                .set(Announcement::getOfflineAt, LocalDateTime.now()));
        return toVO(require(id), true);
    }

    public void delete(Long id) {
        Announcement a = require(id);
        if (a.getStatus() != null && a.getStatus() == 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "已发布的通知请先下架再删除");
        }
        announcementMapper.deleteById(id);
    }

    /* ======================= 通知中心（账号中心调用） ======================= */

    public R.Page<AnnouncementVO> myPage(int page, int size, String category, String keyword, boolean unreadOnly) {
        SecurityUser user = requireLogin();
        Set<Long> scope = myOrgScope(user.getAccountId());

        List<Announcement> visible = announcementMapper.selectList(new LambdaQueryWrapper<Announcement>()
                        .eq(Announcement::getStatus, 1)
                        .orderByDesc(Announcement::getPinned)
                        .orderByDesc(Announcement::getPublishAt)
                        .orderByDesc(Announcement::getId))
                .stream()
                .filter(a -> visibleTo(a, scope))
                .collect(Collectors.toList());

        // 类型 / 关键字 / 未读筛选放在分页之前，否则「只看未读」会把每页筛成空
        String cat = category == null ? "" : category.trim();
        if (!cat.isEmpty()) {
            visible = visible.stream().filter(a -> cat.equalsIgnoreCase(a.getCategory())).toList();
        }
        String kw = keyword == null ? "" : keyword.trim();
        if (!kw.isEmpty()) {
            visible = visible.stream()
                    .filter(a -> (nvl(a.getTitle()).contains(kw)) || (nvl(a.getSummary()).contains(kw)))
                    .toList();
        }
        if (unreadOnly) {
            Set<Long> readIds = readIds(user.getAccountId(), visible);
            visible = visible.stream().filter(a -> !readIds.contains(a.getId())).toList();
        }

        int from = Math.max(0, Math.min((page - 1) * size, visible.size()));
        int to = Math.min(from + size, visible.size());
        List<Announcement> slice = visible.subList(from, to);
        Set<Long> readIds = readIds(user.getAccountId(), slice);
        List<AnnouncementVO> rows = slice.stream()
                .map(a -> {
                    AnnouncementVO vo = toVO(a, false);
                    vo.setRead(readIds.contains(a.getId()));
                    return vo;
                })
                .collect(Collectors.toList());
        return R.Page.of(rows, visible.size());
    }

    /** 打开详情即记读；只有首次打开才给冗余的阅读人数 +1 */
    @Transactional
    public AnnouncementVO myDetail(Long id) {
        SecurityUser user = requireLogin();
        Announcement a = require(id);
        if (a.getStatus() == null || a.getStatus() != 1 || !visibleTo(a, myOrgScope(user.getAccountId()))) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知不存在或已下架");
        }
        int inserted = readMapper.insertIgnore(IdWorker.getId(), id, user.getAccountId(), LocalDateTime.now());
        if (inserted > 0) {
            announcementMapper.update(null, new LambdaUpdateWrapper<Announcement>()
                    .eq(Announcement::getId, id)
                    .setSql("read_count = read_count + 1"));
        }
        AnnouncementVO vo = toVO(require(id), true);
        vo.setRead(true);
        return vo;
    }

    public long myUnreadCount() {
        SecurityUser user = requireLogin();
        Set<Long> scope = myOrgScope(user.getAccountId());
        List<Announcement> visible = announcementMapper.selectList(new LambdaQueryWrapper<Announcement>()
                        .eq(Announcement::getStatus, 1))
                .stream()
                .filter(a -> visibleTo(a, scope))
                .toList();
        if (visible.isEmpty()) {
            return 0;
        }
        Set<Long> readIds = readIds(user.getAccountId(), visible);
        return visible.stream().filter(a -> !readIds.contains(a.getId())).count();
    }

    /* ======================= 内部工具 ======================= */

    private void applySave(Announcement a, AnnouncementSaveRequest req) {
        String title = req.getTitle() == null ? "" : req.getTitle().trim();
        if (title.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "标题不能为空");
        }
        String content = HtmlSanitizer.cleanRichText(req.getContent());
        if (stripHtml(content).isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "通知内容不能为空");
        }
        Integer targetType = req.getTargetType();
        if (targetType == null || (targetType != 0 && targetType != 1)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "通知目标不合法");
        }

        a.setTitle(title);
        a.setSummary(nvl(req.getSummary()).trim());
        String category = req.getCategory() == null ? "" : req.getCategory().trim().toUpperCase();
        a.setCategory(categoryLabels().containsKey(category) ? category : DEFAULT_CATEGORY);
        a.setContent(content);
        a.setTargetType(targetType);
        a.setPinned(Boolean.TRUE.equals(req.getPinned()) ? 1 : 0);
        a.setRemark(nvl(req.getRemark()).trim());

        if (targetType == 0) {
            a.setTargetOrgIds("");
            a.setTargetOrgNames("全员");
            return;
        }
        List<Long> ids = req.getTargetOrgIds() == null ? List.of() : req.getTargetOrgIds().stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "按部门发送时必须选择至少一个部门");
        }
        Map<Long, OrgNode> orgs = allOrgs();
        for (Long id : ids) {
            if (!orgs.containsKey(id)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "通知目标里包含不存在的部门");
            }
        }
        a.setTargetOrgIds(ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
        a.setTargetOrgNames(ids.stream().map(id -> nvl(orgs.get(id).getName())).collect(Collectors.joining("、")));
    }

    private Announcement require(Long id) {
        Announcement a = id == null ? null : announcementMapper.selectById(id);
        if (a == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知不存在");
        }
        return a;
    }

    private SecurityUser requireLogin() {
        return SecurityUtils.currentUser()
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
    }

    /** 本人档案所在组织的祖先链（含自身）；没有档案（如纯管理账号）时不匹配任何部门通知 */
    private Set<Long> myOrgScope(Long accountId) {
        Employee emp = accountId == null ? null : employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, accountId).last("limit 1"));
        if (emp == null || emp.getOrgId() == null) {
            return Set.of();
        }
        OrgNode node = orgMapper.selectById(emp.getOrgId());
        if (node == null) {
            return Set.of(emp.getOrgId());
        }
        Set<Long> scope = new HashSet<>(parseIds(node.getPath()));
        scope.add(emp.getOrgId());
        return scope;
    }

    private boolean visibleTo(Announcement a, Set<Long> myOrgScope) {
        if (a.getTargetType() == null || a.getTargetType() == 0) {
            return true;
        }
        List<Long> targets = parseIds(a.getTargetOrgIds());
        return targets.stream().anyMatch(myOrgScope::contains);
    }

    private Set<Long> readIds(Long accountId, List<Announcement> list) {
        if (list.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = list.stream().map(Announcement::getId).toList();
        return readMapper.selectList(new LambdaQueryWrapper<AnnouncementRead>()
                        .eq(AnnouncementRead::getAccountId, accountId)
                        .in(AnnouncementRead::getAnnouncementId, ids))
                .stream()
                .map(AnnouncementRead::getAnnouncementId)
                .collect(Collectors.toSet());
    }

    /** 应读人数：目标范围内「未离职且已开户」的人（没有账号的人收不到通知，不应计入分母） */
    private int computeTargetCount(Announcement a) {
        LambdaQueryWrapper<Employee> w = new LambdaQueryWrapper<Employee>()
                .ne(Employee::getStatus, EmployeeStatus.LEFT.getCode())
                .isNotNull(Employee::getAccountId);
        if (a.getTargetType() != null && a.getTargetType() == 1) {
            Set<Long> subtree = subtreeIds(parseIds(a.getTargetOrgIds()));
            if (subtree.isEmpty()) {
                return 0;
            }
            w.in(Employee::getOrgId, subtree);
        }
        return employeeMapper.selectCount(w).intValue();
    }

    /** 选中部门 + 其全部下级（按组织 path 祖先链判定） */
    private Set<Long> subtreeIds(List<Long> roots) {
        Map<Long, OrgNode> orgs = allOrgs();
        Set<Long> out = new LinkedHashSet<>();
        for (OrgNode n : orgs.values()) {
            if (parseIds(n.getPath()).stream().anyMatch(roots::contains)) {
                out.add(n.getId());
            }
        }
        return out;
    }

    private Map<Long, OrgNode> allOrgs() {
        Map<Long, OrgNode> map = new LinkedHashMap<>();
        for (OrgNode n : orgMapper.selectList(new LambdaQueryWrapper<OrgNode>())) {
            map.put(n.getId(), n);
        }
        return map;
    }

    private List<Long> parseIds(String csv) {
        List<Long> ids = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return ids;
        }
        for (String part : csv.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.valueOf(p));
            } catch (NumberFormatException ignored) {
                // 脏数据跳过，不影响其余目标
            }
        }
        return ids;
    }

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .trim();
    }

    /** 取通知编号：跨服务失败或规则未配置时直接抛出，不吞异常（编号是通知的必备标识） */
    private String nextAnnouncementNo() {
        // 用 var：settings-client 的 R 与 common.R 是两个类型，只取 data 避免显式类型冲突
        var res = codeRuleApi.next(TABLE_ANNOUNCEMENT);
        String no = res == null || res.getData() == null ? "" : res.getData().trim();
        if (no.isEmpty()) {
            throw new IllegalStateException("取通知编号失败：未配置单号规则或返回为空，tableName=" + TABLE_ANNOUNCEMENT);
        }
        return no;
    }

    /** 类型字典（值→展示名）：优先取配置中心，取不到用内置兜底 */
    private Map<String, String> categoryLabels() {
        Map<String, String> dict = settingsClient.dict(DICT_CATEGORY);
        return dict.isEmpty() ? CATEGORY_FALLBACK : dict;
    }

    private AnnouncementVO toVO(Announcement a, boolean withContent) {
        AnnouncementVO vo = new AnnouncementVO();
        vo.setId(a.getId() == null ? null : String.valueOf(a.getId()));
        vo.setAnnouncementNo(nvl(a.getAnnouncementNo()));
        vo.setTitle(nvl(a.getTitle()));
        vo.setSummary(nvl(a.getSummary()));
        vo.setCategory(nvl(a.getCategory()));
        vo.setCategoryName(categoryLabels().getOrDefault(nvl(a.getCategory()), nvl(a.getCategory())));
        if (withContent) {
            vo.setContent(a.getContent());
        }
        vo.setTargetType(a.getTargetType());
        vo.setTargetOrgIds(parseIds(a.getTargetOrgIds()).stream().map(String::valueOf).toList());
        vo.setTargetOrgNames(nvl(a.getTargetOrgNames()));
        vo.setPinned(a.getPinned() == null ? 0 : a.getPinned());
        vo.setStatus(a.getStatus());
        vo.setStatusName(STATUS_NAMES.getOrDefault(a.getStatus(), ""));
        vo.setPublishAt(a.getPublishAt());
        vo.setOfflineAt(a.getOfflineAt());
        vo.setPublisherName(nvl(a.getPublisherName()));
        vo.setReadCount(a.getReadCount() == null ? 0 : a.getReadCount());
        vo.setTargetCount(a.getTargetCount() == null ? 0 : a.getTargetCount());
        vo.setRemark(nvl(a.getRemark()));
        vo.setCreatedAt(a.getCreatedAt());
        vo.setUpdatedAt(a.getUpdatedAt());
        return vo;
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
