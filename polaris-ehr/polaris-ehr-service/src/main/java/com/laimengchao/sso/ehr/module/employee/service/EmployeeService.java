package com.laimengchao.sso.ehr.module.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.utils.ValidateUtil;
import com.laimengchao.sso.utils.SensitiveMasker;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.integration.AccountClient;
import com.laimengchao.sso.ehr.integration.AccountDtos;
import com.laimengchao.sso.ehr.module.audit.service.AuditLogService;
import com.laimengchao.sso.ehr.security.DataScopeService;
import com.laimengchao.sso.settings.client.api.ApprovalApi;
import com.laimengchao.sso.settings.client.api.NoticeApi;
import com.laimengchao.sso.settings.client.dto.ApprovalActionDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalInstanceDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import com.laimengchao.sso.settings.client.dto.NoticeBizHandledDTO;
import com.laimengchao.sso.settings.client.support.SettingsClient;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.ehr.module.employee.dto.EmployeeRequest;
import com.laimengchao.sso.ehr.module.employee.dto.PendingArchiveCommand;
import com.laimengchao.sso.ehr.module.employee.dto.ReinstateRequest;
import com.laimengchao.sso.ehr.module.employee.dto.ResignRequest;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeBank;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeEducation;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeFamily;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeWork;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeBankMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeEducationMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeFamilyMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeWorkMapper;
import com.laimengchao.sso.ehr.module.approval.support.ApprovalLauncher;
import com.laimengchao.sso.ehr.module.employee.constant.ApprovalRecord;
import com.laimengchao.sso.ehr.module.employee.constant.CloseAccountNotice;
import com.laimengchao.sso.ehr.module.employee.constant.EmployeeStatus;
import com.laimengchao.sso.ehr.module.employee.vo.EmployeeLookupDetailVO;
import com.laimengchao.sso.ehr.module.employee.vo.EmployeeLookupVO;
import com.laimengchao.sso.ehr.module.employee.vo.EmployeeVO;
import com.laimengchao.sso.ehr.module.employee.vo.HandoverCandidateVO;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.pinyin4j.PinyinHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 人员服务。核心是「建档」与「开户」两件事：建档案在本地事务里，开户走账号中心内部接口。
 * <p>
 * 两者顺序：先落档案再开户，开户失败直接抛异常回滚档案——否则会出现「人没进系统、账号却建出来了」
 * 的孤儿账户，比少建一条档案更难收拾。开户接口幂等，失败后重试不会重复建号。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeMapper employeeMapper;
    private final OrgNodeMapper orgMapper;
    private final EmployeeBankMapper bankMapper;
    private final EmployeeEducationMapper educationMapper;
    private final EmployeeWorkMapper workMapper;
    private final EmployeeFamilyMapper familyMapper;
    /** 人员数据范围（按权限码解析 ALL / ORG_TREE / ORG / SELF） */
    private final DataScopeService dataScope;
    /** 查看 / 明文 / 导出审计 */
    private final AuditLogService auditLogService;
    private final AccountClient accountClient;
    private final SettingsClient settingsClient;
    /** 配置中心待办接口：关闭账号后把群发的待办统一置读 */
    private final NoticeApi noticeApi;
    /** 审批中心（settings）内部接口：入职审批的发起与办理都转给它 */
    private final ApprovalApi approvalApi;
    /** 统一的三段式发起：本地先提交 → 远程发起 → 仅在途才回写实例号 */
    private final ApprovalLauncher approvalLauncher;
    /** 转正申请：申请单落库、发起审批、终审回调都归它 */
    private final com.laimengchao.sso.ehr.module.approval.service.RegularizeApplyService regularizeApplyService;
    /** 来源 Offer 校验：入职提交必须来自已确认 Offer，避免绕过招聘侧确认 */
    private final com.laimengchao.sso.ehr.module.recruit.mapper.OfferMapper offerMapper;

    /** 员工来源 */
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_RECRUIT = "RECRUIT";
    public static final String SOURCE_IMPORT = "IMPORT";

    /**
     * 本服务对内地址，用来拼终审回调地址。
     * <p>
     * 不能写成 127.0.0.1 的硬编码：settings 要是部署在另一台机器/容器里，
     * 回调打到它自己的 9006 就永远收不到结果。
     */
    @Value("${ehr.self-base-url:http://127.0.0.1:9006}")
    private String selfBaseUrl;

    /* ---------------- 查询 ---------------- */

    public R.Page<EmployeeVO> page(String keyword, Long orgId, Integer status, int page, int size) {
        LambdaQueryWrapper<Employee> w = listWrapper(keyword, orgId, status);
        w.orderByAsc(Employee::getOrgId).orderByAsc(Employee::getEmpNo);
        IPage<Employee> ip = employeeMapper.selectPage(new Page<>(page, size), w);
        List<EmployeeVO> rows = toVOs(ip.getRecords());
        return R.Page.of(rows, ip.getTotal());
    }

    /**
     * 列表/导出的公共查询条件：数据范围在前，业务筛选在后，两者条件叠加即自动取交集；
     * 选了范围外的组织时自然返回空，不会越权。
     */
    private LambdaQueryWrapper<Employee> listWrapper(String keyword, Long orgId, Integer status) {
        LambdaQueryWrapper<Employee> w = new LambdaQueryWrapper<>();
        dataScope.applyTo(w);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            w.and(q -> q.like(Employee::getName, kw)
                    .or().like(Employee::getEmpNo, kw)
                    .or().like(Employee::getMobile, kw)
                    .or().like(Employee::getEmail, kw)
                    .or().like(Employee::getPosition, kw));
        }
        if (orgId != null) {
            List<Long> scope = orgScope(orgId);
            w.in(Employee::getOrgId, scope);
        }
        if (status != null) {
            w.eq(Employee::getStatus, status);
        }
        return w;
    }

    /** 详情：先校验数据范围（越权 403），再按敏感权限决定明文/掩码，并记审计 */
    public EmployeeVO detail(Long id) {
        Employee emp = require(id);
        if (!dataScope.canAccess(emp)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该员工档案");
        }
        boolean reveal = dataScope.canRevealSensitive();
        EmployeeVO vo = toVOs(List.of(emp), !reveal).get(0);
        auditLogService.record(reveal ? AuditLogService.ACTION_PLAIN : AuditLogService.ACTION_VIEW,
                AuditLogService.TARGET_EMPLOYEE, id, reveal ? sensitiveFields(vo) : "");
        return vo;
    }

    /** 查看敏感明文：数据范围 + 敏感权限双校验，必留 PLAIN 审计 */
    public EmployeeVO sensitiveView(Long id) {
        Employee emp = require(id);
        if (!dataScope.canAccess(emp)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该员工档案");
        }
        if (!dataScope.canRevealSensitive()) {
            throw new BizException(ErrorCode.FORBIDDEN, "无查看敏感字段权限");
        }
        EmployeeVO vo = toVOs(List.of(emp), false).get(0);
        auditLogService.record(AuditLogService.ACTION_PLAIN,
                AuditLogService.TARGET_EMPLOYEE, id, sensitiveFields(vo));
        return vo;
    }

    /** 导出共用数据行：只包含当前数据范围内的人，敏感字段按权限掩码 */
    public List<EmployeeVO> exportRows(String keyword, Long orgId, Integer status) {
        LambdaQueryWrapper<Employee> w = listWrapper(keyword, orgId, status);
        w.orderByAsc(Employee::getOrgId).orderByAsc(Employee::getEmpNo);
        boolean reveal = dataScope.canRevealSensitive();
        return toVOs(employeeMapper.selectList(w), !reveal);
    }

    /** 导出 CSV：只包含当前数据范围内的人，敏感字段按权限掩码，并记 EXPORT 审计 */
    public String exportCsv(String keyword, Long orgId, Integer status) {
        boolean reveal = dataScope.canRevealSensitive();
        List<EmployeeVO> rows = exportRows(keyword, orgId, status);
        StringBuilder sb = new StringBuilder();
        sb.append("工号,姓名,组织,职位,状态,手机号,身份证号,紧急联系电话,邮箱,入职日期,预计转正日期\n");
        for (EmployeeVO vo : rows) {
            sb.append(csv(vo.getEmpNo())).append(',')
                    .append(csv(vo.getName())).append(',')
                    .append(csv(vo.getOrgPathName())).append(',')
                    .append(csv(vo.getPosition())).append(',')
                    .append(csv(vo.getStatusName())).append(',')
                    .append(csv(vo.getMobile())).append(',')
                    .append(csv(vo.getIdCard())).append(',')
                    .append(csv(vo.getEmergencyPhone())).append(',')
                    .append(csv(vo.getEmail())).append(',')
                    .append(csv(vo.getHireDate() == null ? "" : vo.getHireDate().toString())).append(',')
                    .append(csv(vo.getExpectedRegularizeDate() == null ? "" : vo.getExpectedRegularizeDate().toString()))
                    .append('\n');
        }
        auditLogService.record(AuditLogService.ACTION_EXPORT, AuditLogService.TARGET_EMPLOYEE, null,
                reveal ? "mobile,idCard,emergencyPhone" : "rows=" + rows.size());
        return sb.toString();
    }

    /** 当前登录人对应的员工档案（请假模块用；超管/HR 可能没有档案） */
    public EmployeeVO current(Long accountId) {
        Employee emp = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, accountId).last("limit 1"));
        if (emp == null) {
            return null;
        }
        // 本人档案返回明文，供「我的」相关页面回填编辑
        return toVOs(List.of(emp), false).get(0);
    }

    /**
     * 交接人候选：当前登录人所在「子公司」范围内在职且已开户的同事（没有子公司时退到整个集团）。
     * <p>
     * 单独开这个口子是因为普通员工没有 {@code employee:view}，拿不到人员列表，
     * 而他提交离职申请时必须选一个「能登录确认交接」的同事。只返回在职 + 已开户的人，
     * 与前端 HandoverEditor 的过滤口径一致，也避免把全公司电话邮箱暴露出去。
     */
    public List<HandoverCandidateVO> handoverCandidates(Long accountId) {
        Employee me = accountId == null ? null : employeeMapper.selectOne(
                new LambdaQueryWrapper<Employee>().eq(Employee::getAccountId, accountId).last("limit 1"));
        Map<Long, OrgNode> orgs = new HashMap<>();
        for (OrgNode n : orgMapper.selectList(new LambdaQueryWrapper<OrgNode>())) {
            orgs.put(n.getId(), n);
        }

        List<Long> scope = null;
        if (me != null && me.getOrgId() != null && orgs.containsKey(me.getOrgId())) {
            OrgNode top = companyOf(orgs.get(me.getOrgId()), orgs);
            if (top != null && top.getPath() != null && !top.getPath().isBlank()) {
                String prefix = top.getPath() + "/";
                scope = orgs.values().stream()
                        .filter(n -> n.getPath() != null
                                && (n.getPath().equals(top.getPath()) || n.getPath().startsWith(prefix)))
                        .map(OrgNode::getId)
                        .collect(Collectors.toList());
            }
        }

        LambdaQueryWrapper<Employee> w = new LambdaQueryWrapper<Employee>()
                .in(Employee::getStatus, EmployeeStatus.PROBATION.getCode(), EmployeeStatus.ACTIVE.getCode())
                .isNotNull(Employee::getAccountId);
        if (me != null) {
            w.ne(Employee::getId, me.getId());
        }
        if (scope != null && !scope.isEmpty()) {
            w.in(Employee::getOrgId, scope);
        }
        w.orderByAsc(Employee::getEmpNo).last("limit 500");

        return employeeMapper.selectList(w).stream().map(e -> {
            HandoverCandidateVO vo = new HandoverCandidateVO();
            vo.setId(e.getId());
            vo.setEmpNo(e.getEmpNo());
            vo.setName(e.getName());
            vo.setPosition(e.getPosition());
            vo.setOrgId(e.getOrgId());
            OrgNode org = e.getOrgId() == null ? null : orgs.get(e.getOrgId());
            if (org != null) {
                vo.setOrgName(org.getName());
                vo.setOrgPathName(pathName(org, orgs));
            }
            vo.setAccountId(e.getAccountId());
            vo.setAccountUsername(e.getAccountUsername());
            vo.setStatus(e.getStatus());
            return vo;
        }).collect(Collectors.toList());
    }

    /** 沿上级链找第一个「子公司」；没有子公司就用根节点（集团），保证任何组织形态都有候选范围 */
    private OrgNode companyOf(OrgNode node, Map<Long, OrgNode> orgs) {
        OrgNode cur = node;
        while (cur != null) {
            if ("COMPANY".equalsIgnoreCase(cur.getType())) {
                return cur;
            }
            cur = cur.getParentId() == null ? null : orgs.get(cur.getParentId());
        }
        OrgNode root = node;
        while (root != null && root.getParentId() != null && orgs.containsKey(root.getParentId())) {
            root = orgs.get(root.getParentId());
        }
        return root;
    }

    /* ---------------- 写入 ---------------- */

    /** 自动工号格式：大写字母前缀（A-Z）+ 4 位序号，如 A0001 */
    private static final java.util.regex.Pattern AUTO_EMP_NO = java.util.regex.Pattern.compile("^([A-Z])(\\d{4})$");

    /**
     * 取下一个自动工号：从 A0001 起，A9999 用尽换 B0001，以此类推到 Z9999。
     * 口径与 {@code EmployeeMapper.selectLatestGeneratedEmpNo} 一致——历史（含已删除）里
     * 该格式的最大工号 +1；同前缀序号满 9999 进位到下一个字母。全序列用尽抛业务异常。
     */
    private String nextEmpNo() {
        String latest = employeeMapper.selectLatestGeneratedEmpNo();
        if (latest == null || latest.isBlank()) {
            return "A0001";
        }
        java.util.regex.Matcher m = AUTO_EMP_NO.matcher(latest.trim().toUpperCase());
        if (!m.matches()) {
            return "A0001";
        }
        char prefix = m.group(1).charAt(0);
        int seq = Integer.parseInt(m.group(2)) + 1;
        if (seq > 9999) {
            if (prefix >= 'Z') {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "自动工号已用尽（A0001～Z9999 共 259,974 个），请联系管理员扩展编码规则");
            }
            prefix++;
            seq = 1;
        }
        return prefix + String.format("%04d", seq);
    }

    /* ---------------- 邮箱自动生成 ---------------- */

    /** 企业邮箱域名 */
    private static final String EMAIL_DOMAIN = "@xckj.com";

    /**
     * 邮箱解析：显式传入且非空就用传入值（保留接口兼容，前端已不再传）；
     * 否则按「姓名拼音 + @xckj.com」自动生成，重名依次追加 a..z、aa、ab…。
     * 查重范围是本表（ehr_employee）；极端情况与账号中心其他来源邮箱撞车时，
     * 开户接口会静默降级为不带邮箱建号，不会导致建档失败。
     */
    private String resolveEmail(String name, String provided) {
        if (provided != null && !provided.isBlank()) {
            return provided.trim();
        }
        String base = toPinyin(name);
        if (base.isEmpty()) {
            return "";
        }
        for (int i = 0; i < 1000; i++) {
            String candidate = base + emailSuffix(i) + EMAIL_DOMAIN;
            Long cnt = employeeMapper.selectCount(
                    new LambdaQueryWrapper<Employee>().eq(Employee::getEmail, candidate));
            if (cnt == null || cnt == 0) {
                return candidate;
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "邮箱自动生成失败：同拼音记录过多，请手动指定邮箱");
    }

    /** 第 n 个重复后缀：0→无，1→a，26→z，27→aa（26 进制字母序，无零） */
    private String emailSuffix(int n) {
        if (n == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            n--;
            sb.append((char) ('a' + n % 26));
            n /= 26;
        }
        return sb.reverse().toString();
    }

    /**
     * 姓名转拼音（小写、无声调）：汉字逐字取多音字第一个读音；
     * 已是英文/数字的原样保留，其余字符（空格、·、标点）跳过。
     */
    private String toPinyin(String name) {
        StringBuilder sb = new StringBuilder();
        for (char ch : name.trim().toCharArray()) {
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')) {
                sb.append(Character.toLowerCase(ch));
                continue;
            }
            String[] py = PinyinHelper.toHanyuPinyinStringArray(ch);
            if (py != null && py.length > 0) {
                // pinyin4j 无声调输出用 "u:" 表示 ü（如 吕→lu:3），统一转成惯用的 v
                sb.append(py[0].replaceAll("\\d", "").replace("u:", "v"));
            }
        }
        return sb.toString();
    }

    @Transactional(rollbackFor = Exception.class)
    public EmployeeVO create(EmployeeRequest req) {
        // 工号一律由系统自动生成（前端已禁用输入，这里再兜底忽略手动传入值）
        String name = requireText(req.getName(), "姓名必填");
        if (req.getOrgId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "所属组织必填");
        }
        OrgNode org = orgMapper.selectById(req.getOrgId());
        if (org == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "所属组织不存在");
        }
        validateCommon(null, req);

        Employee emp = new Employee();
        emp.setName(name);
        emp.setGender(req.getGender() == null ? 0 : req.getGender());
        emp.setMobile(nvl(req.getMobile()));
        emp.setEmail(resolveEmail(name, req.getEmail()));
        applyNativePlace(emp, req);
        emp.setIdCard(req.getIdCard() == null ? "" : req.getIdCard().trim().toUpperCase());
        emp.setBirthday(parseDate(req.getBirthday()));
        // 身份证合法且生日/性别未填时自动推导，减少重复录入
        if (emp.getBirthday() == null) {
            emp.setBirthday(ValidateUtil.birthdayFromIdCard(emp.getIdCard()));
        }
        if (emp.getGender() == 0) {
            Integer g = ValidateUtil.genderFromIdCard(emp.getIdCard());
            if (g != null) {
                emp.setGender(g);
            }
        }
        emp.setNation(nvl(req.getNation()));
        emp.setMaritalStatus(nvl(req.getMaritalStatus()));
        emp.setPoliticalStatus(nvl(req.getPoliticalStatus()));
        emp.setEducation(nvl(req.getEducation()));
        emp.setEmergencyContact(nvl(req.getEmergencyContact()));
        emp.setEmergencyPhone(nvl(req.getEmergencyPhone()));
        emp.setCurrentAddress(nvl(req.getCurrentAddress()));
        emp.setOrgId(req.getOrgId());
        emp.setPosition(nvl(req.getPosition()));
        emp.setHireDate(parseDate(req.getHireDate()));
        // 试用期：免试用期标记 + 时长（1-12 月），据此推导预计转正时间；提交入职前会再次校验
        emp.setExemptProbation(req.getExemptProbation() != null && req.getExemptProbation());
        emp.setProbationMonths(emp.getExemptProbation() ? null : req.getProbationMonths());
        recalcExpectedRegularizeDate(emp);
        // 新建档案一律「待入职」：人还没来、手续没办，先落草稿；账号留到入职终审通过时开
        emp.setStatus(EmployeeStatus.PENDING.getCode());
        emp.setOnboardStep(null);
        emp.setRegularizeStatus(0);
        emp.setRemark(nvl(req.getRemark()));
        // 自动取号：唯一索引是并发下的最终防线，撞号（并发建档/历史占号）就重算下一个，最多重试 5 次
        boolean inserted = false;
        for (int attempt = 0; attempt < 5 && !inserted; attempt++) {
            emp.setEmpNo(nextEmpNo());
            try {
                employeeMapper.insert(emp);
                inserted = true;
            } catch (DuplicateKeyException e) {
                // 并发抢号，循环里重取
            }
        }
        if (!inserted) {
            throw new BizException(EhrErrorCode.EMP_NO_DUPLICATED, "工号自动生成冲突，请重试");
        }

        // 待入职草稿不开户：统一账号在「提交 → 入职审批中」时才开，避免人没来先建号
        return toVOs(List.of(emp)).get(0);
    }

    /**
     * 招聘录用审批通过后自动建档：只落一条「待入职」草稿，不开户、不发起入职审批，
     * 剩余个人信息与入职材料由 HR 在人员档案里补齐后走现有入职流程。
     * 与 {@link #create} 共用自动工号逻辑，但不做完整性校验（候选人信息本来就不全）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createPendingArchive(String name, String mobile, String email, Long orgId,
                                     String position, LocalDate hireDate, String remark) {
        return createPendingArchive(PendingArchiveCommand.builder()
                .name(name).mobile(mobile).email(email).orgId(orgId)
                .position(position).hireDate(hireDate).remark(remark)
                .source(SOURCE_MANUAL)
                .build());
    }

    /** 招聘/门户转入建档：带出候选人字段，source_offer_id 唯一兜底重复建档 */
    @Transactional(rollbackFor = Exception.class)
    public Long createPendingArchive(PendingArchiveCommand cmd) {
        Employee emp = new Employee();
        emp.setName(requireText(cmd.getName(), "姓名必填"));
        emp.setGender(cmd.getGender() == null ? 0 : cmd.getGender());
        emp.setMobile(nvl(cmd.getMobile()));
        emp.setEmail(resolveEmail(cmd.getName(), cmd.getEmail()));
        emp.setIdCard(cmd.getIdCard() == null ? "" : cmd.getIdCard().trim());
        emp.setBirthday(cmd.getBirthday());
        if (emp.getBirthday() == null && !emp.getIdCard().isBlank()) {
            emp.setBirthday(ValidateUtil.birthdayFromIdCard(emp.getIdCard()));
        }
        emp.setNation("");
        emp.setMaritalStatus("");
        emp.setPoliticalStatus("");
        emp.setEducation(nvl(cmd.getEducation()));
        emp.setEmergencyContact("");
        emp.setEmergencyPhone("");
        emp.setCurrentAddress("");
        emp.setOrgId(cmd.getOrgId());
        emp.setPosition(nvl(cmd.getPosition()));
        emp.setHireDate(cmd.getHireDate());
        // P4-4：入职日先作为司龄起算日与首次参加工作日的默认值，后续由 HR 核定覆盖
        emp.setSeniorityStartDate(cmd.getHireDate());
        emp.setFirstWorkDate(cmd.getHireDate());
        emp.setSeniorityYears(java.math.BigDecimal.ZERO);
        emp.setExemptProbation(false);
        emp.setProbationMonths(null);
        recalcExpectedRegularizeDate(emp);
        emp.setStatus(EmployeeStatus.PENDING.getCode());
        emp.setOnboardStep(null);
        emp.setRegularizeStatus(0);
        emp.setRemark(nvl(cmd.getRemark()));
        emp.setSource(cmd.getSource() == null || cmd.getSource().isBlank() ? SOURCE_MANUAL : cmd.getSource());
        emp.setSourceOfferId(cmd.getSourceOfferId());
        boolean inserted = false;
        for (int attempt = 0; attempt < 5 && !inserted; attempt++) {
            emp.setEmpNo(nextEmpNo());
            try {
                employeeMapper.insert(emp);
                inserted = true;
            } catch (DuplicateKeyException e) {
                // 并发抢工号 / 同一 Offer 并发办理入职：先按来源 Offer 反查，命中直接复用
                if (cmd.getSourceOfferId() != null) {
                    Employee existing = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                            .eq(Employee::getSourceOfferId, cmd.getSourceOfferId())
                            .last("LIMIT 1"));
                    if (existing != null) {
                        return existing.getId();
                    }
                }
            }
        }
        if (!inserted) {
            throw new BizException(EhrErrorCode.EMP_NO_DUPLICATED, "工号自动生成冲突，请重试");
        }
        return emp.getId();
    }

    public EmployeeVO update(Long id, EmployeeRequest req) {
        Employee emp = require(id);
        EmployeeStatus st = EmployeeStatus.of(emp.getStatus());
        if (st != EmployeeStatus.PENDING) {
            // 试用期/在职的信息变更已升级为「变更单 + 审批对比页」，
            // 统一走 POST /{id}/change-apply（EmployeeChangeService），这里不再直改
            throw new BizException(EhrErrorCode.EMP_EDIT_NOT_ALLOWED,
                    "「" + (st == null ? "未知状态" : st.getLabel()) + "」员工的信息变更请走「变更」申请，审批通过后生效");
        }
        validateCommon(emp, req);
        // 待入职草稿没走过任何审批、也没有生效数据可保护，编辑仍直改（人事字段 + 个人信息全放行）
        applyPendingEdit(emp, req);
        approvalLauncher.inTx(() -> employeeMapper.updateById(emp));
        return toVOs(List.of(emp)).get(0);
    }

    /** 待入职草稿的完整编辑：姓名/组织/职位等人事字段 + 个人信息都可直接改 */
    private void applyPendingEdit(Employee emp, EmployeeRequest req) {
        // 刻意忽略 req.empNo：工号由系统生成，档案存续期内不允许修改
        if (req.getName() != null && !req.getName().isBlank()) {
            emp.setName(req.getName().trim());
        }
        if (req.getOrgId() != null) {
            if (orgMapper.selectById(req.getOrgId()) == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "所属组织不存在");
            }
            emp.setOrgId(req.getOrgId());
        }
        if (req.getGender() != null) {
            emp.setGender(req.getGender());
        }
        if (req.getEmail() != null) {
            emp.setEmail(req.getEmail().trim());
        }
        if (req.getNativePlace() != null) {
            emp.setNativePlace(req.getNativePlace().trim());
        }
        applyNativePlace(emp, req);
        if (req.getIdCard() != null) {
            emp.setIdCard(req.getIdCard().trim().toUpperCase());
        }
        if (req.getBirthday() != null) {
            emp.setBirthday(parseDate(req.getBirthday()));
        }
        if (req.getNation() != null) {
            emp.setNation(req.getNation().trim());
        }
        if (req.getPosition() != null) {
            emp.setPosition(req.getPosition().trim());
        }
        if (req.getHireDate() != null) {
            emp.setHireDate(parseDate(req.getHireDate()));
        }
        if (req.getSeniorityStartDate() != null) {
            emp.setSeniorityStartDate(parseDate(req.getSeniorityStartDate()));
            emp.setSeniorityYears(yearsSince(emp.getSeniorityStartDate()));
        }
        if (req.getFirstWorkDate() != null) {
            emp.setFirstWorkDate(parseDate(req.getFirstWorkDate()));
        }
        if (req.getRemark() != null) {
            emp.setRemark(req.getRemark().trim());
        }
        // 试用期字段：待录入阶段可调整（免试用期标记 + 时长），转为试用期/正式后锁定
        if (req.getExemptProbation() != null) {
            emp.setExemptProbation(req.getExemptProbation());
        }
        if (req.getProbationMonths() != null) {
            emp.setProbationMonths(emp.getExemptProbation() != null && emp.getExemptProbation()
                    ? null : req.getProbationMonths());
        }
        // 个人信息：待入职、试用期、在职三态都可维护
        if (req.getMobile() != null) {
            emp.setMobile(req.getMobile().trim());
        }
        if (req.getMaritalStatus() != null) {
            emp.setMaritalStatus(req.getMaritalStatus().trim());
        }
        if (req.getPoliticalStatus() != null) {
            emp.setPoliticalStatus(req.getPoliticalStatus().trim());
        }
        if (req.getEducation() != null) {
            emp.setEducation(req.getEducation().trim());
        }
        if (req.getEmergencyContact() != null) {
            emp.setEmergencyContact(req.getEmergencyContact().trim());
        }
        if (req.getEmergencyPhone() != null) {
            emp.setEmergencyPhone(req.getEmergencyPhone().trim());
        }
        if (req.getCurrentAddress() != null) {
            emp.setCurrentAddress(req.getCurrentAddress().trim());
        }
        // 刻意忽略 req.status：在职/离职必须走 resign / reinstate，
        // 否则「改个职位顺手把状态改了」会绕过离职日期与账号停用
        recalcExpectedRegularizeDate(emp);
    }

    /**
     * 删除档案：仅限「待入职」草稿。
     * <p>
     * 一旦提交入职（20）就意味着人已确定到岗、账号已开通、审批痕迹已生成，
     * 再删档会让审批链、账号、组织人数全部对不上；这类人员只能走减员（离职）流程，
     * 保证档案与账号只「停用」不「消失」。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Employee emp = require(id);
        if (!EmployeeStatus.PENDING.equals(EmployeeStatus.of(emp.getStatus()))) {
            throw new BizException(EhrErrorCode.EMP_DELETE_NOT_ALLOWED,
                    "只有「待入职」草稿可以删除；已提交入职的人员请走减员（离职）流程");
        }
        // 草稿被驳回回来时可能已开过户，删除前先停号，避免留下能登录的孤儿账号
        if (emp.getAccountId() != null) {
            accountClient.setEnabled(emp.getAccountId(), false);
        }
        // emp_no 只有 32 位，雪花 ID 有 19 位，直接拼会超长；截断原名并加短后缀保证唯一且可追溯
        String base = emp.getEmpNo() != null && emp.getEmpNo().length() > 20
                ? emp.getEmpNo().substring(0, 20) : emp.getEmpNo();
        emp.setEmpNo(base + "-del-" + (id % 1000000));
        employeeMapper.updateById(emp);
        employeeMapper.deleteById(id);
    }

    /**
     * 应急补开户：正常流程里账号由人事主管终审通过时自动开通，无需人工点。
     * 仅用于历史数据（流程改造前入职、账号为空）或极端情况下的人工兜底，接口保留但前端不放入口。
     */
    @Transactional(rollbackFor = Exception.class)
    public EmployeeVO openAccount(Long id) {
        Employee emp = require(id);
        if (isResigned(emp)) {
            throw new BizException(EhrErrorCode.EMP_STATUS_INVALID,
                    "该员工已离职，不能开户；如需返聘请先办理复职");
        }
        if (emp.getAccountId() != null) {
            throw new BizException(EhrErrorCode.EMP_ACCOUNT_EXISTS,
                    "该员工已绑定账号：" + emp.getAccountUsername());
        }
        AccountDtos.OpenResult result = accountClient.open(
                null, emp.getName(), emp.getEmail(), emp.getMobile(), null);
        bindAccount(emp, result);
        EmployeeVO vo = toVOs(List.of(emp)).get(0);
        vo.setAccount(toBrief(result));
        return vo;
    }

    /**
     * 办理离职：档案置离职 + 记录日期原因，并按需停用统一账号。
     * <p>
     * 为什么停用账号放在这里而不是让人去账号中心手动点：离职最常见的漏操作就是
     * 「档案标了离职、账号还能登」，把所有系统一起停掉才算办完。
     * 账号停用失败会抛异常回滚状态，避免出现「档案离职了、账号还活着」的半截状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public EmployeeVO resign(Long id, ResignRequest req) {
        Employee emp = require(id);
        if (isResigned(emp)) {
            throw new BizException(EhrErrorCode.EMP_STATUS_INVALID, "该员工已是离职状态");
        }
        LocalDate resignDate = req == null || req.getResignDate() == null
                || req.getResignDate().isBlank() ? LocalDate.now() : parseDate(req.getResignDate());
        if (emp.getHireDate() != null && resignDate.isBefore(emp.getHireDate())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "离职日期不能早于入职日期");
        }

        emp.setStatus(EmployeeStatus.LEFT.getCode());
        emp.setResignDate(resignDate);
        emp.setResignReason(req == null ? "" : nvl(req.getReason()));
        employeeMapper.updateById(emp);

        boolean disable = req == null || req.getDisableAccount() == null || req.getDisableAccount();
        boolean accountDisabled = false;
        if (disable && emp.getAccountId() != null) {
            accountClient.setEnabled(emp.getAccountId(), false);
            accountDisabled = true;
        }

        EmployeeVO vo = toVOs(List.of(emp)).get(0);
        vo.setAccountDisabled(accountDisabled);
        return vo;
    }

    /**
     * 关闭账号：离职 / 减员审批通过后的最后一步，档案从「待关闭账号」置为「已离职」。
     * <p>
     * 审批回调只进入「待关闭账号」，等 HR 在人员列表确认后才真正停用统一账号；
     * 停用失败直接抛异常回滚状态，不留下「档案已离职、账号还能登」的半截数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public EmployeeVO closeAccount(Long id) {
        Employee emp = require(id);
        if (EmployeeStatus.of(emp.getStatus()) != EmployeeStatus.PENDING_ACCOUNT_CLOSE) {
            throw new BizException(EhrErrorCode.EMP_STATUS_INVALID, "只有「待关闭账号」的员工可以关闭账号");
        }
        emp.setStatus(EmployeeStatus.LEFT.getCode());
        if (emp.getResignDate() == null) {
            emp.setResignDate(LocalDate.now());
        }
        employeeMapper.updateById(emp);

        boolean accountDisabled = false;
        if (emp.getAccountId() != null) {
            accountClient.setEnabled(emp.getAccountId(), false);
            accountDisabled = true;
        }
        markCloseAccountTodoDone(emp.getId());
        EmployeeVO vo = toVOs(List.of(emp)).get(0);
        vo.setAccountDisabled(accountDisabled);
        return vo;
    }

    /** 一个人关完账号，HR 群发的这条待办对所有人都没意义了，统一置读 */
    private void markCloseAccountTodoDone(Long employeeId) {
        NoticeBizHandledDTO dto = new NoticeBizHandledDTO();
        dto.setBizType(CloseAccountNotice.BIZ_TYPE);
        dto.setBizId(String.valueOf(employeeId));
        try {
            noticeApi.bizHandled(dto);
        } catch (Exception e) {
            // 置读失败不影响关闭账号的结果，下次可用消息中心手动置读
            log.error("关闭账号待办置读失败 employeeId={}", employeeId, e);
        }
    }

    /**
     * 复职：回到在职并清空离职信息；默认重新启用账号。
     * <p>
     * resignDate 需要显式 set null：MyBatis-Plus 的 updateById 默认跳过 null 字段，
     * 直接给实体赋值清不掉列，复职后会一直挂着上次的离职日期。
     * <p>
     * 直办接口统一回到 <b>20 入职审批中</b>（复职后重走一次入职审批，复用原账号）；
     * 走 {@code ehr.rehire} 审批通过的回调则用 {@link #reinstateByApproval}，直接回到试用期/在职。
     */
    @Transactional(rollbackFor = Exception.class)
    public EmployeeVO reinstate(Long id, ReinstateRequest req) {
        return doReinstate(id, req, EmployeeStatus.ONBOARDING);
    }

    /**
     * 复职审批通过后的落地：审批本身就是「能不能回来」的结论，
     * 不再让人重走一遍入职审批，直接回到试用期（免试用期则直接在职）。
     * 试用期起算与在职状态与 {@code /onboard} 保持一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public EmployeeVO reinstateByApproval(Long id, Boolean enableAccount, String remark) {
        Employee emp = require(id);
        EmployeeStatus target = Boolean.TRUE.equals(emp.getExemptProbation())
                ? EmployeeStatus.ACTIVE : EmployeeStatus.PROBATION;
        ReinstateRequest req = new ReinstateRequest();
        req.setEnableAccount(enableAccount);
        req.setRemark(remark);
        return doReinstate(id, req, target);
    }

    private EmployeeVO doReinstate(Long id, ReinstateRequest req, EmployeeStatus target) {
        Employee emp = require(id);
        if (!isResigned(emp)) {
            throw new BizException(EhrErrorCode.EMP_STATUS_INVALID, "该员工在职，无需复职");
        }
        String remark = emp.getRemark() == null ? "" : emp.getRemark();
        if (req != null && req.getRemark() != null && !req.getRemark().isBlank()) {
            String note = "复职：" + req.getRemark().trim();
            remark = remark.isBlank() ? note : remark + "；" + note;
            if (remark.length() > 255) {
                remark = remark.substring(remark.length() - 255);
            }
        }
        employeeMapper.update(null, new LambdaUpdateWrapper<Employee>()
                .eq(Employee::getId, id)
                .set(Employee::getStatus, target.getCode())
                .set(Employee::getResignDate, null)
                .set(Employee::getResignReason, "")
                .set(Employee::getRemark, remark));

        boolean enable = req == null || req.getEnableAccount() == null || req.getEnableAccount();
        boolean accountDisabled = false;
        if (enable && emp.getAccountId() != null) {
            accountClient.setEnabled(emp.getAccountId(), true);
            accountDisabled = false;
        } else {
            accountDisabled = emp.getAccountId() != null;
        }

        Employee fresh = require(id);
        EmployeeVO vo = toVOs(List.of(fresh)).get(0);
        vo.setAccountDisabled(accountDisabled);
        return vo;
    }

    /* ---------------- 入职 / 转正 状态流转 ----------------
     *
     * 入职审批已经搬到 settings 配置中心的「审批中心」：模板（几步、谁审、并行还是会签、条件分支）
     * 全在配置中心配，EHR 只做三件事 —— 发起、收终态回调、按结果改状态。
     *
     * 转正审批仍是本地的两步（部门主管 → 人事主管），等入职这条链路跑顺再按同样方式迁移。
     */

    private static final ObjectMapper APPROVAL_OM = new ObjectMapper();
    private static final String ROLE_DEPT = "DEPT";
    private static final String ROLE_HR = "HR";

    /** 入职审批场景编码，对应 settings 的 cfg_biz_scene.biz_type */
    private static final String BIZ_ONBOARD = "ehr.onboard";
    /** 转正审批场景编码（老链路已统一改为走审批中心） */
    private static final String BIZ_REGULARIZE = "ehr.regularize";
    /** 终审结果接收地址：settings 审批结束后回调它（必须是 settings 能直连的地址） */
    private static final String CALLBACK_PATH = "/internal/ehr/approval-callback";

    /**
     * 招聘转入的档案必须来自「已确认」的 Offer；存量草稿在这里被拦住，
     * 避免 HR 从人员列表绕过招聘侧的「确认 Offer」直接提交入职。
     */
    private void assertConfirmedOffer(Employee emp) {
        if (emp.getSourceOfferId() == null) {
            return;
        }
        com.laimengchao.sso.ehr.module.recruit.entity.Offer offer = offerMapper.selectById(emp.getSourceOfferId());
        if (offer == null || !"CONFIRMED".equals(offer.getStatus())) {
            throw new BizException(EhrErrorCode.EMP_STATUS_INVALID, "来源 Offer 尚未确认，请先在招聘页确认 Offer");
        }
    }

    /**
     * 待入职草稿提交：<b>三段式</b>——① 先本地置「入职审批中」并提交 ② 向审批中心发起实例
     * ③ 仅当实例仍在进行中才回写实例号。发起确认失败时把状态退回「待入职」，允许重新提交。
     * <p>
     * 这里<b>不开户</b>：账号留到终审通过时统一开通，避免「审批还没过就先把号建出来」。
     */
    public EmployeeVO submitDraft(Long id) {
        Employee emp = require(id);
        if (!EmployeeStatus.PENDING.equals(EmployeeStatus.of(emp.getStatus()))) {
            throw new BizException(EhrErrorCode.EMP_STATUS_INVALID, "只有「待入职」草稿可以提交入职");
        }
        assertConfirmedOffer(emp);
        assertOnboardComplete(emp);
        // ① 本地状态变更先提交：settings 的同步回调发生时，这行一定可见
        emp.setStatus(EmployeeStatus.ONBOARDING.getCode());
        emp.setOnboardStep(0);
        approvalLauncher.inTx(() -> employeeMapper.updateById(emp));

        // ②③ 远程发起 + 条件回写
        approvalLauncher.launch(
                () -> buildOnboardStart(emp),
                instanceId -> { /* 实例号已退役：在途判断统一查审批中心 */ },
                // 发起确认失败：退回「待入职」，用户可重新提交
                () -> employeeMapper.update(null, new LambdaUpdateWrapper<Employee>()
                        .eq(Employee::getId, emp.getId())
                        .eq(Employee::getStatus, EmployeeStatus.ONBOARDING.getCode())
                        .set(Employee::getStatus, EmployeeStatus.PENDING.getCode())));

        // 实例可能在 start() 内当场终态并同步回调，重新读一次拿真实状态
        Employee reloaded = employeeMapper.selectById(emp.getId());
        return toVOs(List.of(reloaded == null ? emp : reloaded)).get(0);
    }

    /**
     * 纯构造「入职审批」发起请求。审批走几步、谁审、能不能并行/会签/条件分支，
     * 全部由 settings 侧模板决定，EHR 不掺和；这里只负责把「审批人要看的表单」
     * 和「条件表达式要用的字段」打包过去。远程调用统一交给 ApprovalLauncher。
     */
    private ApprovalStartDTO buildOnboardStart(Employee emp) {
        SecurityUser user = SecurityUtils.currentUser().orElse(null);
        ApprovalStartDTO dto = new ApprovalStartDTO();
        dto.setBizType(BIZ_ONBOARD);
        dto.setBizId(String.valueOf(emp.getId()));
        dto.setBizNo(emp.getEmpNo());
        dto.setTitle("入职审批 · " + emp.getName());
        dto.setApplicantId(user == null ? null : user.getAccountId());
        dto.setApplicantName(user == null ? "系统" : approverName(user));
        dto.setApplicantOrgId(emp.getOrgId());
        dto.setOrgPath(orgPathOf(emp.getOrgId()));
        dto.setFormData(onboardFormData(emp));
        dto.setCallbackUrl(selfBaseUrl + CALLBACK_PATH);
        return dto;
    }

    /**
     * 审批表单快照：只放「审批人要看的」与「条件表达式要用的」。
     * <p>
     * 试用期汇总与档案完整度这类派生值<b>在这里算好再冻结</b>，而不是让审批中心回查 EHR：
     * 审批弹窗是「服务端算值、前端只画」，算值这一侧必须有人把结果放进快照里，
     * 否则 settings 要么留空、要么每渲染一次就远程调一次 EHR，把弹窗变成 N 次跨服务请求。
     * 冻结也符合审批的可复核性——驳回后重新提交才会刷新。
     */
    private Map<String, Object> onboardFormData(Employee emp) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeeId", String.valueOf(emp.getId()));
        m.put("empNo", nvl(emp.getEmpNo()));
        m.put("name", nvl(emp.getName()));
        m.put("orgId", emp.getOrgId() == null ? null : String.valueOf(emp.getOrgId()));
        m.put("orgPath", orgPathOf(emp.getOrgId()));
        m.put("position", nvl(emp.getPosition()));
        m.put("hireDate", emp.getHireDate() == null ? null : emp.getHireDate().toString());
        m.put("exemptProbation", Boolean.TRUE.equals(emp.getExemptProbation()));
        m.put("probationMonths", Boolean.TRUE.equals(emp.getExemptProbation()) ? 0 : emp.getProbationMonths());
        m.put("expectedRegularizeDate",
                emp.getExpectedRegularizeDate() == null ? null : emp.getExpectedRegularizeDate().toString());
        m.put("probationText", probationSummary(emp));
        m.put("profileStats", profileStats(emp.getId()));
        return m;
    }

    /** 「6 个月 · 预计 2027-05-10 转正」/「免试用期 · 入职直接转正」 */
    private String probationSummary(Employee emp) {
        if (Boolean.TRUE.equals(emp.getExemptProbation())) {
            return "免试用期 · 入职直接转正";
        }
        Integer months = emp.getProbationMonths();
        if (months == null) {
            return "";
        }
        String base = months + " 个月";
        return emp.getExpectedRegularizeDate() == null ? base
                : base + " · 预计 " + emp.getExpectedRegularizeDate() + " 转正";
    }

    /** 「银行卡 1 · 教育 2 · 工作 1 · 家庭 2」——审批人判断资料齐不齐的第一眼依据 */
    private String profileStats(Long empId) {
        if (empId == null) {
            return "";
        }
        long bank = countSub(bankMapper.selectCount(new LambdaQueryWrapper<EmployeeBank>()
                .eq(EmployeeBank::getEmployeeId, empId)));
        long edu = countSub(educationMapper.selectCount(new LambdaQueryWrapper<EmployeeEducation>()
                .eq(EmployeeEducation::getEmployeeId, empId)));
        long work = countSub(workMapper.selectCount(new LambdaQueryWrapper<EmployeeWork>()
                .eq(EmployeeWork::getEmployeeId, empId)));
        long family = countSub(familyMapper.selectCount(new LambdaQueryWrapper<EmployeeFamily>()
                .eq(EmployeeFamily::getEmployeeId, empId)));
        return "银行卡 " + bank + " · 教育 " + edu + " · 工作 " + work + " · 家庭 " + family;
    }

    /** 组织全路径，如「集团 / 子公司 / 研发部」；节点不存在返回空串 */
    private String orgPathOf(Long orgId) {
        if (orgId == null) {
            return "";
        }
        Map<Long, OrgNode> orgs = new HashMap<>();
        for (OrgNode n : orgMapper.selectList(new LambdaQueryWrapper<OrgNode>())) {
            orgs.put(n.getId(), n);
        }
        OrgNode node = orgs.get(orgId);
        return node == null ? "" : pathName(node, orgs);
    }

    /**
     * 员工快捷查询（控制台使用）：按工号精确 / 姓名模糊查，最多 10 条。
     * 只返回姓名、职位、邮件，任何隐私字段都不出库。
     */
    public List<EmployeeLookupVO> lookupEmployees(String keyword) {
        requireLogin();
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            // 空关键字返回前 10 位在职员工：约面试等场景打开下拉即可直接选人
            return employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                            .ne(Employee::getStatus, EmployeeStatus.LEFT.getCode())
                            .orderByAsc(Employee::getEmpNo)
                            .last("LIMIT 10"))
                    .stream().map(this::toLookupVO).toList();
        }
        if (kw.length() > 40) {
            kw = kw.substring(0, 40);
        }
        // 工号精确命中排前面：不足 10 条再按姓名模糊补齐，避免同名的人把工号结果挤掉
        List<Employee> hits = new ArrayList<>(employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .ne(Employee::getStatus, EmployeeStatus.LEFT.getCode())
                .eq(Employee::getEmpNo, kw)
                .last("LIMIT 10")));
        if (hits.size() < 10) {
            Set<Long> exists = new HashSet<>();
            for (Employee e : hits) {
                exists.add(e.getId());
            }
            LambdaQueryWrapper<Employee> byName = new LambdaQueryWrapper<Employee>()
                    .ne(Employee::getStatus, EmployeeStatus.LEFT.getCode())
                    .like(Employee::getName, kw)
                    .orderByAsc(Employee::getEmpNo)
                    .last("LIMIT " + (10 - hits.size()));
            if (!exists.isEmpty()) {
                byName.notIn(Employee::getId, exists);
            }
            hits.addAll(employeeMapper.selectList(byName));
        }
        List<EmployeeLookupVO> result = new ArrayList<>();
        for (Employee e : hits) {
            result.add(toLookupVO(e));
        }
        return result;
    }

    /** 员工快捷查询结果项：只给找人需要的非隐私字段 */
    private EmployeeLookupVO toLookupVO(Employee e) {
        EmployeeLookupVO vo = new EmployeeLookupVO();
        vo.setEmployeeId(e.getId());
        vo.setName(e.getName());
        vo.setPosition(e.getPosition());
        vo.setEmail(e.getEmail());
        vo.setDepartment(orgPathOf(e.getOrgId()));
        return vo;
    }

    /** 员工快捷查询详情：姓名 / 工号 / 账号 / 性别 / 职位 / 部门 / 邮件，同样不含隐私字段 */
    public EmployeeLookupDetailVO lookupEmployeeDetail(Long employeeId) {
        requireLogin();
        Employee e = employeeId == null ? null : employeeMapper.selectById(employeeId);
        if (e == null) {
            throw new BizException(EhrErrorCode.EMP_NOT_FOUND);
        }
        EmployeeLookupDetailVO vo = new EmployeeLookupDetailVO();
        vo.setName(e.getName());
        vo.setEmpNo(e.getEmpNo());
        vo.setAccount(e.getAccountUsername() == null || e.getAccountUsername().isBlank()
                ? "未开户" : e.getAccountUsername());
        vo.setGender(e.getGender() == null ? "未知" : (e.getGender() == 1 ? "男" : (e.getGender() == 2 ? "女" : "未知")));
        vo.setPosition(e.getPosition());
        vo.setDepartment(orgPathOf(e.getOrgId()));
        vo.setEmail(e.getEmail());
        return vo;
    }

    /** 查询接口面向所有已登录账号（控制台令牌作用域不是 ehr，不能挂 @Permit），这里兜底登录校验 */
    private void requireLogin() {
        if (SecurityUtils.currentUser().isEmpty()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    /**
     * 入职审批办理（兼容老前端）：把「通过/驳回」转给审批中心，由它推进流程。
     * <p>
     * <b>刻意不加 @Transactional</b>：settings 在审批到终态时会同步回调本服务，
     * 回调线程要 UPDATE 同一行 ehr_employee；若本方法持有该行的写锁去等 settings 响应，
     * 两边就会互相等待直到锁超时。这里只做「转发 + 回读」，全程不占锁。
     * <p>
     * 状态流转不再由这里决定 —— 终态由回调改，中间步骤的推进是审批中心的事。
     */
    public EmployeeVO approveOnboard(Long id, boolean pass, String note) {
        Employee emp = require(id);
        if (!EmployeeStatus.ONBOARDING.equals(EmployeeStatus.of(emp.getStatus()))) {
            throw new BizException(EhrErrorCode.EMP_STATUS_INVALID, "该员工不在「入职审批中」，无法审批");
        }
        Long instanceId = approvalLauncher.latestRunningId(BIZ_ONBOARD, String.valueOf(emp.getId()));
        if (instanceId == null) {
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID,
                    "这张入职审批单不在审批中心（历史数据），请到审批中心处理或重新提交入职");
        }
        SecurityUser user = SecurityUtils.currentUser()
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));

        // 审批权限由「任务分配」决定，不由权限点决定：查这张单上有没有我的待办
        ApprovalInstanceDTO inst = fetchInstance(instanceId, user.getAccountId());
        Long taskId = inst == null ? null : inst.getMyTaskId();
        if (taskId == null) {
            throw new BizException(EhrErrorCode.APPROVAL_NOT_APPROVER,
                    "当前步骤不由你审批，请到审批中心查看进度");
        }

        ApprovalActionDTO act = new ApprovalActionDTO();
        act.setOperatorId(user.getAccountId());
        act.setOperatorName(approverName(user));
        act.setPass(pass);
        act.setNote(note);
        com.laimengchao.sso.settings.client.dto.R<ApprovalInstanceDTO> resp;
        try {
            resp = approvalApi.approve(taskId, act);
        } catch (Exception e) {
            log.error("提交入职审批结果失败 employeeId={} taskId={}", id, taskId, e);
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID, "提交审批结果失败，请稍后重试");
        }
        if (resp == null || !resp.ok()) {
            String msg = resp == null ? "" : nvl(resp.getMessage());
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID,
                    msg.isBlank() ? "提交审批结果失败" : msg);
        }
        // 走到终态时 settings 已在事务提交后同步回调本服务，重新读库就能拿到改完的状态
        return toVOs(List.of(require(id))).get(0);
    }

    /** 查审批单（拿「我的待办任务」用）；查询失败返回 null，由调用方按「无权审批」处理 */
    private ApprovalInstanceDTO fetchInstance(Long instanceId, Long viewerId) {
        try {
            com.laimengchao.sso.settings.client.dto.R<ApprovalInstanceDTO> resp =
                    approvalApi.detail(instanceId, viewerId);
            return (resp != null && resp.ok()) ? resp.getData() : null;
        } catch (Exception e) {
            log.warn("查询审批单失败 instanceId={}", instanceId, e);
            return null;
        }
    }

    /**
     * 接收审批中心回传的入职审批终态。
     * <p>
     * <b>必须幂等</b>：审批中心回调失败会退避重试，同一结果可能投递多次。
     * 两道防重 —— ① 审批单号对不上（员工重提交过，旧单迟到的回调）直接忽略；
     * ② 状态已经是 25 及之后说明处理过了，不再重复开户。
     * <p>
     * 通过 → 「入职审批通过」（25）+ 开户；驳回 → 回「待入职」（10）。
     * 开户失败<b>不回滚状态</b>：审批确实过了，回滚会制造「单子过了人还在审批中」的死局，
     * 改为记日志 + 在审批备注里留一句，由 HR 在人员列表点「开户」补办。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleOnboardApprovalResult(ApprovalCallbackRequest req) {
        Long empId = parseLongOrNull(req.getBizId());
        if (empId == null) {
            log.warn("入职审批回调缺少业务ID：{}", req.getBizId());
            return;
        }
        Employee emp = employeeMapper.selectById(empId);
        if (emp == null) {
            log.warn("入职审批回调对应的员工不存在 bizId={}", req.getBizId());
            return;
        }
        EmployeeStatus cur = EmployeeStatus.of(emp.getStatus());

        if (Boolean.TRUE.equals(req.getApproved())) {
            if (isOnboarded(cur)) {
                log.info("入职审批回调重复投递，已处理过 employeeId={} status={}", empId, emp.getStatus());
                return;
            }
            if (!EmployeeStatus.ONBOARDING.equals(cur)) {
                log.warn("入职审批通过回调与员工状态不匹配，忽略 employeeId={} status={}", empId, emp.getStatus());
                return;
            }
            emp.setOnboardStep(2);
            emp.setStatus(EmployeeStatus.ONBOARD_APPROVED.getCode());
            emp.setOnboardApprovals(appendSystemRecord(emp.getOnboardApprovals(), true, "审批中心终审通过"));
            employeeMapper.updateById(emp);
            openAccountQuietly(emp);
        } else {
            if (!EmployeeStatus.ONBOARDING.equals(cur)) {
                log.info("入职审批驳回回调与状态不匹配，忽略 employeeId={} status={}", empId, emp.getStatus());
                return;
            }
            emp.setOnboardStep(-1);
            emp.setStatus(EmployeeStatus.PENDING.getCode());
            emp.setOnboardApprovals(appendSystemRecord(emp.getOnboardApprovals(), false,
                    req.getResultName() == null ? "审批未通过" : req.getResultName()));
            employeeMapper.updateById(emp);
        }
    }

    /** 是否已经过了入职审批这一关（含后续状态），用于回调幂等判断 */
    private boolean isOnboarded(EmployeeStatus st) {
        return EmployeeStatus.ONBOARD_APPROVED.equals(st)
                || EmployeeStatus.PROBATION.equals(st)
                || EmployeeStatus.ACTIVE.equals(st)
                || EmployeeStatus.OFFBOARDING.equals(st)
                || EmployeeStatus.PENDING_ACCOUNT_CLOSE.equals(st)
                || EmployeeStatus.LEFT.equals(st);
    }

    /** 回调里开户：失败只记日志与备注，绝不抛出（抛出会让审批中心以为回调没送到而反复重试） */
    private void openAccountQuietly(Employee emp) {
        if (emp.getAccountId() != null) {
            return;
        }
        try {
            AccountDtos.OpenResult r = openAccountFor(emp);
            if (r != null) {
                log.info("入职审批通过，已开通统一账号 employeeId={} username={}", emp.getId(), r.getUsername());
            }
        } catch (Exception e) {
            log.error("入职审批通过但开户失败，需人工补开户 employeeId={} name={}", emp.getId(), emp.getName(), e);
            try {
                emp.setOnboardApprovals(appendSystemRecord(emp.getOnboardApprovals(), true,
                        "统一账号开通失败，请在人员列表点「开户」补办"));
                employeeMapper.updateById(emp);
            } catch (Exception ignore) {
                // 备注写不进去不影响主流程
            }
        }
    }

    private Long parseLongOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 追一条系统生成的审批记录（回调触发时没有登录用户，不能复用 appendApproval） */
    private String appendSystemRecord(String existing, boolean pass, String note) {
        List<ApprovalRecord> list = readApprovals(existing);
        list.add(new ApprovalRecord("SYS", "审批中心", null, "审批中心", pass,
                nvl(note), LocalDateTime.now().toString().replace('T', ' ')));
        try {
            return APPROVAL_OM.writeValueAsString(list);
        } catch (Exception e) {
            return existing == null ? "[]" : existing;
        }
    }

    private List<ApprovalRecord> readApprovals(String existing) {
        if (existing == null || existing.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return APPROVAL_OM.readValue(existing, new TypeReference<List<ApprovalRecord>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 办理入职：仅「入职审批通过」（25）可调，由 HR 在人员列表点「入职」确认到岗。
     * <p>
     * 审批阶段已开通统一账号，这里只做状态流转：免试用期 → 直接正式（40）；
     * 否则进入试用期（30）。不重复开户，也不重新计算预计转正时间（以建档时为准）。
     */
    @Transactional(rollbackFor = Exception.class)
    public EmployeeVO onboard(Long id) {
        Employee emp = require(id);
        if (!EmployeeStatus.ONBOARD_APPROVED.equals(EmployeeStatus.of(emp.getStatus()))) {
            throw new BizException(EhrErrorCode.EMP_STATUS_INVALID, "该员工不在「入职审批通过」状态，无法办理入职");
        }
        if (Boolean.TRUE.equals(emp.getExemptProbation())) {
            emp.setStatus(EmployeeStatus.ACTIVE.getCode());
        } else {
            Integer months = emp.getProbationMonths();
            if (months == null || months < 1 || months > 12) {
                throw new BizException(ErrorCode.PARAM_ERROR, "试用期时长缺失或非法（应为 1-12 个月），无法办理入职");
            }
            emp.setStatus(EmployeeStatus.PROBATION.getCode());
        }
        employeeMapper.updateById(emp);
        return toVOs(List.of(emp)).get(0);
    }

    /**
     * 推导预计转正时间：免试用期 → null；入职日期与 1-12 月时长齐全 → 入职日期加月数；
     * 否则 null（信息不全，等提交入职时再由前端提示补全）。
     */
    private void recalcExpectedRegularizeDate(Employee emp) {
        if (Boolean.TRUE.equals(emp.getExemptProbation())) {
            emp.setExpectedRegularizeDate(null);
        } else if (emp.getHireDate() != null && emp.getProbationMonths() != null
                && emp.getProbationMonths() >= 1 && emp.getProbationMonths() <= 12) {
            emp.setExpectedRegularizeDate(emp.getHireDate().plusMonths(emp.getProbationMonths()));
        } else {
            emp.setExpectedRegularizeDate(null);
        }
    }

    /**
     * 试用期员工自助提交转正申请：不再本地模拟两步审批，改为向审批中心发起
     * {@code ehr.regularize} 实例，步骤 / 审批人 / 条件全部由模板决定。
     * 本人或 HR 均可提交。
     * <p>
     * 申请内容（自评、业绩、理由、后续计划）落在 {@code ehr_regularize_apply} 上，
     * 校验 / 发起 / 回调都由 {@link com.laimengchao.sso.ehr.module.approval.service.RegularizeApplyService} 负责，
     * 这里只做一层薄转发，保留老接口路径与返回类型不变。
     */
    public EmployeeVO applyRegularize(Long id) {
        return applyRegularize(id, null);
    }

    /** 带申请内容的转正申请（req 可为空，等价于只点一下按钮的老行为） */
    public EmployeeVO applyRegularize(Long id,
                                       com.laimengchao.sso.ehr.module.approval.dto.RegularizeApplyRequest req) {
        regularizeApplyService.submit(id, req);
        Employee reloaded = employeeMapper.selectById(id);
        EmployeeVO vo = toVOs(List.of(reloaded == null ? require(id) : reloaded)).get(0);
        // 实例号已退役：以审批中心的在途单为准（发起即终态时不标「审批中」）
        vo.setPendingApproval(reloaded != null
                && approvalLauncher.hasRunning(String.valueOf(id), BIZ_REGULARIZE));
        return vo;
    }

    /**
     * 转正审批终审回调。必须幂等：审批中心回调失败会退避重试，同一结果可能投递多次。
     * <p>
     * 通过 → 正式在职（40）+ regularizeStatus=3；驳回/撤回/终止 → 留在试用期、regularizeStatus=-1。
     * 两种情况都要清掉在途标记，否则人员列表会一直显示「转正审批中」。
     * <p>
     * 实际处理委托给 {@link com.laimengchao.sso.ehr.module.approval.service.RegularizeApplyService}
     * （那里有申请单，能按申请行定位并留档）；本方法保留是为了兼容既有调用方。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleRegularizeApprovalResult(ApprovalCallbackRequest req) {
        regularizeApplyService.handleRegularizeApprovalResult(req);
    }

    /**
     * 纯构造「转正审批」发起请求；表单快照里放审批人判断要用的试用期信息。
     * <p>
     * 已由 {@link com.laimengchao.sso.ehr.module.approval.service.RegularizeApplyService} 取代，
     * 保留仅供历史代码参考，不再调用。
     */
    @Deprecated
    private ApprovalStartDTO buildRegularizeStart(Employee emp) {
        SecurityUser user = SecurityUtils.currentUser().orElse(null);
        ApprovalStartDTO dto = new ApprovalStartDTO();
        dto.setBizType(BIZ_REGULARIZE);
        dto.setBizId(String.valueOf(emp.getId()));
        dto.setBizNo(nvl(emp.getEmpNo()));
        dto.setTitle("转正审批 · " + emp.getName());
        dto.setApplicantId(user == null ? null : user.getAccountId());
        dto.setApplicantName(user == null ? "系统" : approverName(user));
        dto.setApplicantOrgId(emp.getOrgId());
        dto.setOrgPath(orgPathOf(emp.getOrgId()));
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("employeeId", String.valueOf(emp.getId()));
        form.put("empNo", nvl(emp.getEmpNo()));
        form.put("name", nvl(emp.getName()));
        form.put("orgPath", orgPathOf(emp.getOrgId()));
        form.put("position", nvl(emp.getPosition()));
        form.put("hireDate", emp.getHireDate() == null ? null : emp.getHireDate().toString());
        form.put("probationMonths", Boolean.TRUE.equals(emp.getExemptProbation()) ? 0 : emp.getProbationMonths());
        form.put("expectedRegularizeDate",
                emp.getExpectedRegularizeDate() == null ? null : emp.getExpectedRegularizeDate().toString());
        form.put("probationText", probationSummary(emp));
        form.put("profileStats", profileStats(emp.getId()));
        dto.setFormData(form);
        dto.setCallbackUrl(selfBaseUrl + CALLBACK_PATH);
        return dto;
    }

    /**
     * 提交入职前的完整性校验：基本信息除「备注」外都要有值（工号/邮箱由系统生成，不校验），
     * 且银行卡、教育背景、工作经历、家庭成员四个档案卡片各至少 1 条。
     * <p>
     * 这些数据是审批人判断「人能不能入职」的依据，缺了就不该进审批流；
     * 一次性把缺的项全部列出来，避免让 HR 反复试错。
     */
    private void assertOnboardComplete(Employee emp) {
        List<String> missing = new ArrayList<>();
        if (blank(emp.getName())) missing.add("姓名");
        if (emp.getOrgId() == null) missing.add("所属组织");
        if (emp.getGender() == null || emp.getGender() == 0) missing.add("性别");
        if (blank(emp.getMobile())) missing.add("手机号");
        if (blank(emp.getIdCard())) missing.add("身份证号");
        if (emp.getBirthday() == null) missing.add("出生日期");
        if (blank(emp.getNation())) missing.add("民族");
        if (blank(emp.getMaritalStatus())) missing.add("婚姻状况");
        if (blank(emp.getPoliticalStatus())) missing.add("政治面貌");
        if (blank(emp.getEducation())) missing.add("最高学历");
        if (blank(emp.getPosition())) missing.add("职位");
        if (emp.getHireDate() == null) missing.add("入职日期");
        // 司龄核定必须在入职审批前完成：建档时只回填默认值，需 HR 实际核定一次（产出核定记录）
        if (emp.getSeniorityConfirmedAt() == null) {
            throw new BizException(EhrErrorCode.EMP_ONBOARD_INCOMPLETE,
                    "提交入职前请先完成司龄核定（核定司龄起算日与首次参加工作日期）");
        }
        if (blank(emp.getNativeDetail())) missing.add("籍贯-详细地址");
        if (blank(emp.getEmergencyContact())) missing.add("紧急联系人");
        if (blank(emp.getEmergencyPhone())) missing.add("紧急联系电话");
        if (blank(emp.getCurrentAddress())) missing.add("现居住址");
        // 试用期时长：免试用期时不要求；否则必须在 1-12 个月，作为转正预期的依据
        if (!Boolean.TRUE.equals(emp.getExemptProbation())) {
            Integer pm = emp.getProbationMonths();
            if (pm == null || pm < 1 || pm > 12) {
                missing.add("试用期时长(1-12个月)");
            }
        }

        Long id = emp.getId();
        List<String> emptyCards = new ArrayList<>();
        if (countSub(bankMapper.selectCount(new LambdaQueryWrapper<EmployeeBank>()
                .eq(EmployeeBank::getEmployeeId, id))) == 0) {
            emptyCards.add("银行卡");
        }
        if (countSub(educationMapper.selectCount(new LambdaQueryWrapper<EmployeeEducation>()
                .eq(EmployeeEducation::getEmployeeId, id))) == 0) {
            emptyCards.add("教育背景");
        }
        if (countSub(workMapper.selectCount(new LambdaQueryWrapper<EmployeeWork>()
                .eq(EmployeeWork::getEmployeeId, id))) == 0) {
            emptyCards.add("工作经历");
        }
        if (countSub(familyMapper.selectCount(new LambdaQueryWrapper<EmployeeFamily>()
                .eq(EmployeeFamily::getEmployeeId, id))) == 0) {
            emptyCards.add("家庭成员");
        }

        if (missing.isEmpty() && emptyCards.isEmpty()) {
            return;
        }
        StringBuilder msg = new StringBuilder("入职资料未填完整：");
        if (!missing.isEmpty()) {
            msg.append("基本信息缺少「").append(String.join("、", missing)).append("」");
        }
        if (!emptyCards.isEmpty()) {
            if (!missing.isEmpty()) {
                msg.append("；");
            }
            msg.append("以下档案至少填 1 条：").append(String.join("、", emptyCards));
        }
        throw new BizException(EhrErrorCode.EMP_ONBOARD_INCOMPLETE, msg.toString());
    }

    private long countSub(Long count) {
        return count == null ? 0 : count;
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 开户：入职终审通过时调用；已开户则直接返回 null（幂等，便于审批重试）。
     * <p>
     * 放在终审而不是提交时：提交只是「发起审批」，人还没被批准入职就先建号，
     * 一旦审批被驳回就会留下能登录但未入职的账号。
     */
    private AccountDtos.OpenResult openAccountFor(Employee emp) {
        if (emp.getAccountId() != null) {
            return null;
        }
        AccountDtos.OpenResult result = accountClient.open(
                null, emp.getName(), emp.getEmail(), emp.getMobile(), null);
        bindAccount(emp, result);
        return result;
    }

    /** 追加一条审批记录到 JSON 列，返回新的 JSON 字符串 */
    private String appendApproval(String existing, String role, String roleName, boolean pass, String note) {
        List<ApprovalRecord> list = readApprovals(existing);
        SecurityUser user = SecurityUtils.currentUser().orElse(null);
        Long aid = user == null ? null : user.getAccountId();
        String name = user == null ? "系统" : approverName(user);
        list.add(new ApprovalRecord(role, roleName, aid, name, pass,
                note == null ? "" : note.trim(), LocalDateTime.now().toString().replace('T', ' ')));
        try {
            return APPROVAL_OM.writeValueAsString(list);
        } catch (Exception e) {
            return existing == null ? "[]" : existing;
        }
    }

    private String approverName(SecurityUser user) {
        Employee me = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, user.getAccountId()).last("limit 1"));
        return me == null ? user.getUsername() : me.getName();
    }

    /* ---------------- 内部工具 ---------------- */

    /**
     * 新增/编辑共用的格式与查重校验。
     * update 是「字段级补丁」语义：只在字段真的传了（!= null）时才校验，避免 Partial 更新把别的字段误伤。
     */
    private void validateCommon(Employee existing, EmployeeRequest req) {
        boolean create = existing == null;
        if (create || req.getMobile() != null) {
            String mobile = req.getMobile();
            if (mobile != null && !mobile.isBlank()) {
                if (!ValidateUtil.isMobile(mobile)) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "手机号格式不正确（11 位大陆手机号）");
                }
                Long dup = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                        .eq(Employee::getMobile, mobile.trim())
                        .ne(Employee::getId, create ? 0L : existing.getId()));
                if (dup != null && dup > 0) {
                    throw new BizException(EhrErrorCode.EMP_MOBILE_DUPLICATED, "该手机号已存在于其他员工档案");
                }
            }
        }
        if (create || req.getEmail() != null) {
            String email = req.getEmail();
            if (email != null && !email.isBlank() && !ValidateUtil.isEmail(email)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "邮箱格式不正确");
            }
        }
        if (create || req.getEmergencyPhone() != null) {
            String phone = req.getEmergencyPhone();
            if (phone != null && !phone.isBlank() && !ValidateUtil.isMobile(phone)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "紧急联系电话格式不正确（11 位大陆手机号）");
            }
        }
        if (create || req.getIdCard() != null) {
            String idCard = req.getIdCard();
            if (idCard != null && !idCard.isBlank()) {
                if (!ValidateUtil.isIdCard(idCard)) {
                    throw new BizException(EhrErrorCode.EMP_ID_CARD_INVALID,
                            "身份证号不合法（需 18 位且校验码正确）");
                }
                Long dup = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                        .eq(Employee::getIdCard, idCard.trim().toUpperCase())
                        .ne(Employee::getId, create ? 0L : existing.getId()));
                if (dup != null && dup > 0) {
                    throw new BizException(EhrErrorCode.EMP_ID_CARD_DUPLICATED, "该身份证号已存在于其他员工档案");
                }
            }
        }
        if (create || req.getBirthday() != null) {
            LocalDate birthday = parseDate(req.getBirthday());
            if (birthday != null && birthday.isAfter(LocalDate.now())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "出生日期不能晚于今天");
            }
        }
    }

    private boolean isResigned(Employee emp) {
        return EmployeeStatus.LEFT.equals(EmployeeStatus.of(emp.getStatus()));
    }

    private void bindAccount(Employee emp, AccountDtos.OpenResult result) {
        emp.setAccountId(result.getAccountId());
        emp.setAccountUsername(result.getUsername());
        // 只回写开户字段，绝不能用 updateById(emp)：回调过程中状态、审批记录已被更新，
        // 整行回写会用内存里的旧快照覆盖掉刚写入的结果。
        employeeMapper.update(null, new LambdaUpdateWrapper<Employee>()
                .eq(Employee::getId, emp.getId())
                .set(Employee::getAccountId, result.getAccountId())
                .set(Employee::getAccountUsername, result.getUsername()));
    }

    private EmployeeVO.AccountBrief toBrief(AccountDtos.OpenResult r) {
        EmployeeVO.AccountBrief b = new EmployeeVO.AccountBrief();
        b.setUsername(r.getUsername());
        b.setInitialPassword(r.getInitialPassword());
        b.setCreated(r.isCreated());
        b.setApps(r.getApps());
        b.setRoles(r.getRoles());
        return b;
    }

    private Employee require(Long id) {
        Employee emp = employeeMapper.selectById(id);
        if (emp == null) {
            throw new BizException(EhrErrorCode.EMP_NOT_FOUND);
        }
        return emp;
    }

    /** 组织节点自身 + 整棵子树的 id，用于「按部门筛选时把下级的人也算上」 */
    private List<Long> orgScope(Long orgId) {
        OrgNode node = orgMapper.selectById(orgId);
        if (node == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织节点不存在");
        }
        List<OrgNode> all = orgMapper.selectList(new LambdaQueryWrapper<OrgNode>()
                .likeRight(OrgNode::getPath, node.getPath()));
        List<Long> ids = all.stream().map(OrgNode::getId).collect(Collectors.toList());
        if (ids.isEmpty()) {
            ids = new ArrayList<>(List.of(orgId));
        }
        return ids;
    }

    private List<EmployeeVO> toVOs(List<Employee> rows) {
        return toVOs(rows, true);
    }

    private List<EmployeeVO> toVOs(List<Employee> rows, boolean maskSensitive) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, OrgNode> orgs = new HashMap<>();
        for (OrgNode n : orgMapper.selectList(new LambdaQueryWrapper<OrgNode>())) {
            orgs.put(n.getId(), n);
        }
        return rows.stream().map(e -> toVO(e, orgs, maskSensitive)).collect(Collectors.toList());
    }

    private EmployeeVO toVO(Employee e, Map<Long, OrgNode> orgs, boolean maskSensitive) {
        EmployeeVO vo = new EmployeeVO();
        vo.setId(e.getId());
        vo.setEmpNo(e.getEmpNo());
        vo.setName(e.getName());
        vo.setGender(e.getGender());
        // 手机号 / 身份证 / 紧急联系电话统一走掩码，只有敏感权限接口才返回明文
        vo.setMobile(maskSensitive ? SensitiveMasker.maskMobile(e.getMobile()) : e.getMobile());
        vo.setEmail(e.getEmail());
        vo.setNativePlace(e.getNativePlace());
        vo.setNativeProvince(e.getNativeProvince());
        vo.setNativeCity(e.getNativeCity());
        vo.setNativeDistrict(e.getNativeDistrict());
        vo.setNativeDetail(e.getNativeDetail());
        vo.setIdCard(maskSensitive ? SensitiveMasker.maskIdCard(e.getIdCard()) : e.getIdCard());
        vo.setBirthday(e.getBirthday());
        vo.setAge(ValidateUtil.age(e.getBirthday()));
        vo.setNation(e.getNation());
        vo.setNationName(dictLabel("general.nation", e.getNation()));
        vo.setMaritalStatus(e.getMaritalStatus());
        vo.setMaritalStatusName(dictLabel("ehr.marital_status", e.getMaritalStatus()));
        vo.setPoliticalStatus(e.getPoliticalStatus());
        vo.setPoliticalStatusName(dictLabel("ehr.political_status", e.getPoliticalStatus()));
        vo.setEducation(e.getEducation());
        vo.setEducationName(bizLabel("education", e.getEducation()));
        vo.setEmergencyContact(e.getEmergencyContact());
        vo.setEmergencyPhone(maskSensitive ? SensitiveMasker.maskMobile(e.getEmergencyPhone()) : e.getEmergencyPhone());
        vo.setCurrentAddress(e.getCurrentAddress());
        vo.setOrgId(e.getOrgId());
        vo.setPosition(e.getPosition());
        vo.setHireDate(e.getHireDate());
        vo.setSeniorityStartDate(e.getSeniorityStartDate());
        vo.setFirstWorkDate(e.getFirstWorkDate());
        vo.setSeniorityYears(e.getSeniorityYears() == null ? null : e.getSeniorityYears().toPlainString());
        vo.setSeniorityConfirmedAt(e.getSeniorityConfirmedAt());
        vo.setProbationMonths(e.getProbationMonths());
        vo.setExemptProbation(e.getExemptProbation());
        vo.setExpectedRegularizeDate(e.getExpectedRegularizeDate());
        vo.setStatus(e.getStatus());
        EmployeeStatus st = EmployeeStatus.of(e.getStatus());
        vo.setStatusName(st == null ? "未知" : st.getLabel());
        vo.setOnboardStep(e.getOnboardStep());
        vo.setRegularizeStatus(e.getRegularizeStatus());
        vo.setOnboardApprovals(e.getOnboardApprovals());
        vo.setRegularizeApprovals(e.getRegularizeApprovals());
        vo.setResignDate(e.getResignDate());
        vo.setResignReason(e.getResignReason());
        vo.setAccountId(e.getAccountId());
        vo.setAccountUsername(e.getAccountUsername());
        vo.setRemark(e.getRemark());
        vo.setCreatedAt(e.getCreatedAt() == null ? null : e.getCreatedAt().toString().replace('T', ' '));
        vo.setSensitiveMasked(maskSensitive && (!isBlank(e.getMobile())
                || !isBlank(e.getIdCard())
                || !isBlank(e.getEmergencyPhone())));
        OrgNode org = e.getOrgId() == null ? null : orgs.get(e.getOrgId());
        if (org != null) {
            vo.setOrgName(org.getName());
            vo.setOrgPathName(pathName(org, orgs));
        }
        return vo;
    }

    /** 审计用：本次明文涉及哪些敏感字段 */
    private String sensitiveFields(EmployeeVO vo) {
        List<String> fields = new ArrayList<>();
        if (!isBlank(vo.getMobile())) {
            fields.add("mobile");
        }
        if (!isBlank(vo.getIdCard())) {
            fields.add("idCard");
        }
        if (!isBlank(vo.getEmergencyPhone())) {
            fields.add("emergencyPhone");
        }
        return String.join(",", fields);
    }

    /** CSV 单元格转义：含逗号/引号/换行时加引号，内部引号翻倍 */
    private String csv(String value) {
        String v = value == null ? "" : value;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String pathName(OrgNode node, Map<Long, OrgNode> orgs) {
        List<String> names = new ArrayList<>();
        if (node.getPath() != null) {
            for (String part : node.getPath().split("/")) {
                if (part.isBlank()) {
                    continue;
                }
                try {
                    OrgNode n = orgs.get(Long.valueOf(part));
                    if (n != null) {
                        names.add(n.getName());
                    }
                } catch (NumberFormatException ignored) {
                    // 脏 path 跳过
                }
            }
        }
        if (names.isEmpty()) {
            names.add(node.getName());
        }
        return String.join(" / ", names);
    }

    private String dictLabel(String typeCode, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String label = settingsClient.dict(typeCode).get(value);
        return label == null ? value : label;
    }

    /** 业务主数据翻译（银行/学历/职位等 biz_* 真表），字典类仍走 {@link #dictLabel} */
    private String bizLabel(String type, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String name = settingsClient.bizMap(type).get(value);
        return name == null ? value : name;
    }

    private String requireText(String raw, String message) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, message);
        }
        return s;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "日期格式应为 yyyy-MM-dd");
        }
    }

    private String nvl(String s) {
        return s == null ? "" : s.trim();
    }

    /** 司龄（年）：按 365.25 天/年折算，向下保留 1 位 */
    private java.math.BigDecimal yearsSince(LocalDate start) {
        if (start == null) {
            return java.math.BigDecimal.ZERO;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, LocalDate.now());
        if (days <= 0) {
            return java.math.BigDecimal.ZERO;
        }
        return java.math.BigDecimal.valueOf(days)
                .divide(java.math.BigDecimal.valueOf(365.25), 1, java.math.RoundingMode.DOWN);
    }

    /**
     * 籍贯结构化字段（省/市/区县/详细地址）落库，并拼接 nativePlace 兼容旧展示
     * （列表/查看页直接读 nativePlace）。传了结构化字段就用它们重拼；
     * 只传了老字段 nativePlace（老客户端）则原样保留，结构化字段单独存。
     */
    private void applyNativePlace(Employee emp, EmployeeRequest req) {
        String province = nvl(req.getNativeProvince());
        String city = nvl(req.getNativeCity());
        String district = nvl(req.getNativeDistrict());
        String detail = nvl(req.getNativeDetail());
        if (!province.isEmpty() || !city.isEmpty() || !district.isEmpty() || !detail.isEmpty()) {
            emp.setNativeProvince(province);
            emp.setNativeCity(city);
            emp.setNativeDistrict(district);
            emp.setNativeDetail(detail);
            // 拼「省市区 + 详细地址」作为展示值，如 陕西省西安市雁塔区XX路XX号
            emp.setNativePlace(province + city + district + detail);
        } else if (req.getNativePlace() != null) {
            emp.setNativePlace(nvl(req.getNativePlace()));
        }
    }
}
