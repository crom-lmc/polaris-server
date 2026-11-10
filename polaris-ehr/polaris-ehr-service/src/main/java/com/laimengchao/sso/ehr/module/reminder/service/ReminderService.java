package com.laimengchao.sso.ehr.module.reminder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.approval.service.ApproverResolver;
import com.laimengchao.sso.ehr.module.contract.entity.Contract;
import com.laimengchao.sso.ehr.module.contract.mapper.ContractMapper;
import com.laimengchao.sso.ehr.module.employee.constant.EmployeeStatus;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.ehr.module.reminder.entity.Reminder;
import com.laimengchao.sso.ehr.module.reminder.mapper.JobLockMapper;
import com.laimengchao.sso.ehr.module.reminder.mapper.ProbationNoticeLogMapper;
import com.laimengchao.sso.ehr.module.reminder.mapper.ReminderMapper;
import com.laimengchao.sso.settings.client.api.NoticeApi;
import com.laimengchao.sso.settings.client.dto.EmbedPointDTO;
import com.laimengchao.sso.settings.client.dto.NoticeSendDTO;
import com.laimengchao.sso.settings.client.support.SettingsClient;
import com.laimengchao.sso.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 试用期到期提醒。
 * <p>
 * 每天 09:00 由 {@link com.laimengchao.sso.ehr.schedule.ReminderJob} 触发，
 * 扫描「试用期 + 预计转正日 = 今天 + N 天」的在职员工（N 默认 30/7/1，可配），
 * 给本人、直属主管与 HR 各写一条提醒。唯一键保证同一个节点只提醒一次，
 * 转正/离职后状态不再命中，自然停止。
 * <p>
 * HR 接收人不再写死在 yml：统一读配置中心埋点
 * {@code ehr.probation.reminder.receivers}（部门多选 + 人员多选 + 是否含直属主管）；
 * 配置中心不可用则退化为「本人 + 直属主管」，提醒任务不中断。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    public static final String TYPE_PROBATION_DUE = "PROBATION_DUE";
    public static final String TYPE_CONTRACT_DUE = "CONTRACT_DUE";
    private static final String JOB_NAME = "probation-reminder-daily";
    private static final String CONTRACT_JOB_NAME = "contract-reminder-daily";
    private static final String INSTANCE_ID = UUID.randomUUID().toString().substring(0, 8);

    private final ReminderMapper reminderMapper;
    private final JobLockMapper jobLockMapper;
    private final ProbationNoticeLogMapper probationNoticeLogMapper;
    private final NoticeApi noticeApi;
    private final EmployeeMapper employeeMapper;
    private final ContractMapper contractMapper;
    private final ApproverResolver approverResolver;
    private final OrgNodeMapper orgNodeMapper;
    private final SettingsClient settingsClient;
    private final ObjectMapper objectMapper;

    /** 配置中心埋点：试用期/合同到期提醒的额外接收人 */
    private static final String EMBED_REMINDER_RECEIVERS = "ehr.probation.reminder.receivers";

    /** 提醒节点：T-N 天，默认 30/7/1 */
    @Value("${ehr.reminder.probation-offsets:30,7,1}")
    private String probationOffsets;

    /** 合同到期提醒节点：T-N 天，默认 30/7/1 */
    @Value("${ehr.reminder.contract-offsets:30,7,1}")
    private String contractOffsets;

    /** 每日生成提醒；返回本次新增条数（0 表示没抢到锁或没有命中） */
    public int generateDaily() {
        LocalDateTime now = LocalDateTime.now();
        int locked = jobLockMapper.tryLock(JOB_NAME, now.plusMinutes(30), INSTANCE_ID, now);
        if (locked <= 0) {
            log.info("试用期提醒任务已被其它实例抢占，本次跳过");
            return 0;
        }
        List<Integer> offsets = parseOffsets();
        ReceiverConfig receivers = receiverConfig();
        int created = 0;
        for (Integer offset : offsets) {
            LocalDate dueDate = LocalDate.now().plusDays(offset);
            List<Employee> rows = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                    .eq(Employee::getStatus, EmployeeStatus.PROBATION.getCode())
                    .eq(Employee::getExpectedRegularizeDate, dueDate));
            for (Employee emp : rows) {
                created += remindEmployee(emp, offset, dueDate, receivers);
            }
        }
        // 已超期（预计转正日已过但仍是试用期）：立即提醒，之后每天重复提醒直到转正/离职
        // trigger_date 用「今天」，配合唯一键同一天同一接收人只产生 1 条
        List<Employee> overdue = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getStatus, EmployeeStatus.PROBATION.getCode())
                .lt(Employee::getExpectedRegularizeDate, LocalDate.now()));
        for (Employee emp : overdue) {
            created += remindOverdue(emp, receivers);
        }
        // T-3 起（含已超期）每天给员工本人发一条站内「消息」（点击即已读），直到转正/离职
        int noticed = notifyProbationDueSoon();
        log.info("试用期提醒生成完成：新增 {} 条，节点 {}，额外接收人 {}，站内消息 {} 条",
                created, offsets, receivers.accountIds, noticed);
        return created;
    }

    /**
     * 每日生成合同到期提醒：扫描「生效中 + 结束日期 = 今天 + N 天」的合同，
     * 给员工本人、直属主管与 HR 各写一条。已有续签/终止在途审批的跳过，
     * 避免同一合同重复打扰。
     */
    public int generateContractDaily() {
        LocalDateTime now = LocalDateTime.now();
        int locked = jobLockMapper.tryLock(CONTRACT_JOB_NAME, now.plusMinutes(30), INSTANCE_ID, now);
        if (locked <= 0) {
            log.info("合同到期提醒任务已被其它实例抢占，本次跳过");
            return 0;
        }
        List<Integer> offsets = parseOffsets(contractOffsets);
        ReceiverConfig receivers = receiverConfig();
        int created = 0;
        for (Integer offset : offsets) {
            LocalDate dueDate = LocalDate.now().plusDays(offset);
            List<Contract> rows = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                    .eq(Contract::getStatus, "ACTIVE")
                    .eq(Contract::getEndDate, dueDate));
            for (Contract c : rows) {
                if (hasRunningChange(c.getEmployeeId())) {
                    continue;
                }
                Employee emp = employeeMapper.selectById(c.getEmployeeId());
                if (emp == null) {
                    continue;
                }
                created += remindContract(c, emp, offset, dueDate, receivers);
            }
        }
        log.info("合同到期提醒生成完成：新增 {} 条，节点 {}，额外接收人 {}", created, offsets, receivers.accountIds);
        return created;
    }

    /** 续签 PENDING 或终止 TERMINATING 期间不再重复提醒 */
    private boolean hasRunningChange(Long employeeId) {
        return contractMapper.selectCount(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getEmployeeId, employeeId)
                .in(Contract::getStatus, "PENDING", "TERMINATING")) > 0;
    }

    private int remindContract(Contract c, Employee emp, int offset, LocalDate dueDate, ReceiverConfig extra) {
        String title = "合同到期提醒（T-" + offset + "）";
        String content = String.format("%s 的%s合同将于 %s 到期（剩余 %d 天），请及时发起续签或终止审批。",
                emp.getName(), typeName(c.getContractType()), dueDate, offset);
        Set<Long> receivers = new LinkedHashSet<>();
        if (emp.getAccountId() != null) {
            receivers.add(emp.getAccountId());
        }
        ApproverResolver.Approver leader = approverResolver.resolve(emp, emp.getAccountId());
        if (leader != null && leader.exists()) {
            receivers.add(leader.accountId());
        }
        receivers.addAll(extra.accountIds);
        int created = 0;
        for (Long receiverId : receivers) {
            created += reminderMapper.insertIgnore(IdWorker.getId(), emp.getId(), receiverId,
                    TYPE_CONTRACT_DUE, c.getId(), dueDate, title, content);
        }
        return created;
    }

    private String typeName(String type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case "FIXED" -> "固定期限";
            case "UNFIXED" -> "无固定期限";
            case "INTERN" -> "实习";
            case "LABOR" -> "劳务";
            default -> type;
        };
    }

    private int remindEmployee(Employee emp, int offset, LocalDate dueDate, ReceiverConfig extra) {
        String title = "试用期到期提醒（T-" + offset + "）";
        String content = String.format("%s 的试用期将于 %s 到期（剩余 %d 天），请及时跟进转正评估。",
                emp.getName(), dueDate, offset);
        Set<Long> receivers = new LinkedHashSet<>();
        if (emp.getAccountId() != null) {
            receivers.add(emp.getAccountId());
        }
        // 直属主管：从员工所在组织沿 path 上溯，跳过员工本人；由埋点开关控制是否发送
        if (extra.directSupervisor) {
            ApproverResolver.Approver leader = approverResolver.resolve(emp, emp.getAccountId());
            if (leader != null && leader.exists()) {
                receivers.add(leader.accountId());
            }
        }
        receivers.addAll(extra.accountIds);
        int created = 0;
        for (Long receiverId : receivers) {
            created += reminderMapper.insertIgnore(IdWorker.getId(), emp.getId(), receiverId,
                    TYPE_PROBATION_DUE, emp.getId(), dueDate, title, content);
        }
        return created;
    }

    /** 超期提醒：标题不带 T-N，内容标注超期天数 */
    private int remindOverdue(Employee emp, ReceiverConfig extra) {
        LocalDate today = LocalDate.now();
        long days = ChronoUnit.DAYS.between(emp.getExpectedRegularizeDate(), today);
        String title = "试用期已到期提醒";
        String content = String.format("%s 的试用期已于 %s 到期（已超期 %d 天），请尽快办理转正或延期手续。",
                emp.getName(), emp.getExpectedRegularizeDate(), days);
        Set<Long> receivers = new LinkedHashSet<>();
        if (emp.getAccountId() != null) {
            receivers.add(emp.getAccountId());
        }
        if (extra.directSupervisor) {
            ApproverResolver.Approver leader = approverResolver.resolve(emp, emp.getAccountId());
            if (leader != null && leader.exists()) {
                receivers.add(leader.accountId());
            }
        }
        receivers.addAll(extra.accountIds);
        int created = 0;
        for (Long receiverId : receivers) {
            created += reminderMapper.insertIgnore(IdWorker.getId(), emp.getId(), receiverId,
                    TYPE_PROBATION_DUE, emp.getId(), today, title, content);
        }
        return created;
    }

    /**
     * 试用期到期前 3 天起（含当天与已超期），每天给员工本人发一条站内「消息」
     * （channel=2，点击即已读），直到转正/离职状态变化后自然停止。
     * 同一员工同一天只发一条（ehr_probation_notice_log 唯一键去重，任务重跑不重复）。
     */
    private int notifyProbationDueSoon() {
        LocalDate today = LocalDate.now();
        List<Employee> rows = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getStatus, EmployeeStatus.PROBATION.getCode())
                .isNotNull(Employee::getAccountId)
                .le(Employee::getExpectedRegularizeDate, today.plusDays(3)));
        int sent = 0;
        for (Employee emp : rows) {
            if (probationNoticeLogMapper.insertIgnore(IdWorker.getId(), emp.getId(), today) <= 0) {
                continue;
            }
            try {
                LocalDate due = emp.getExpectedRegularizeDate();
                long days = ChronoUnit.DAYS.between(today, due);
                boolean expired = days < 0;
                String title = expired ? "试用期已到期" : "试用期即将到期";
                String content = expired
                        ? String.format("你的试用期已于 %s 到期（已超期 %d 天），请联系主管或 HR 尽快办理转正或延期手续。", due, -days)
                        : String.format("你的试用期将于 %s 到期（剩余 %d 天），请提前与主管沟通转正评估事宜。", due, days);
                NoticeSendDTO dto = new NoticeSendDTO();
                dto.setRuleCode("ehr.probation.due");
                dto.setAccountId(emp.getAccountId());
                dto.setChannel(2);
                dto.setTitle(title);
                dto.setContent(content);
                dto.setBizType("PROBATION");
                dto.setBizId(String.valueOf(emp.getId()));
                dto.setBizNo(emp.getEmpNo());
                dto.setApplicantName("系统");
                noticeApi.send(dto);
                sent++;
            } catch (Exception e) {
                log.warn("发送试用期到期站内消息失败 employee={}", emp.getId(), e);
            }
        }
        return sent;
    }

    /* ---------- 我的提醒 ---------- */

    public R.Page<Reminder> my(boolean unreadOnly, String type, int page, int size) {
        Long accountId = SecurityUtils.currentUserId();
        List<Long> probationIds = probationEmployeeIds();
        LambdaQueryWrapper<Reminder> w = new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getReceiverId, accountId);
        if (type != null && !type.isBlank()) {
            w.eq(Reminder::getType, type.trim());
        }
        // 试用期提醒是「消息」不是待办：已读不消失，只有员工不再处于试用期（转正/离职）才消失
        w.and(x -> {
            x.ne(Reminder::getType, TYPE_PROBATION_DUE);
            if (!probationIds.isEmpty()) {
                x.or().in(Reminder::getEmployeeId, probationIds);
            }
        });
        if (unreadOnly) {
            w.eq(Reminder::getReadFlag, 0);
        }
        w.orderByAsc(Reminder::getReadFlag)
                .orderByAsc(Reminder::getTriggerDate)
                .orderByDesc(Reminder::getId);
        IPage<Reminder> ip = reminderMapper.selectPage(new Page<>(page, size), w);
        fillEmployeeNames(ip.getRecords());
        return R.Page.of(ip.getRecords(), ip.getTotal());
    }

    public int unreadCount(String type) {
        Long accountId = SecurityUtils.currentUserId();
        List<Long> probationIds = probationEmployeeIds();
        LambdaQueryWrapper<Reminder> w = new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getReceiverId, accountId)
                .eq(Reminder::getReadFlag, 0);
        if (type != null && !type.isBlank()) {
            w.eq(Reminder::getType, type.trim());
        }
        w.and(x -> {
            x.ne(Reminder::getType, TYPE_PROBATION_DUE);
            if (!probationIds.isEmpty()) {
                x.or().in(Reminder::getEmployeeId, probationIds);
            }
        });
        return Math.toIntExact(reminderMapper.selectCount(w));
    }

    /** 仍处于试用期的员工 ID：试用期提醒只对这些人展示 */
    private List<Long> probationEmployeeIds() {
        return employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                        .select(Employee::getId)
                        .eq(Employee::getStatus, EmployeeStatus.PROBATION.getCode()))
                .stream().map(Employee::getId).collect(Collectors.toList());
    }

    public void markRead(Long id) {
        Long accountId = SecurityUtils.currentUserId();
        reminderMapper.update(null, new LambdaUpdateWrapper<Reminder>()
                .eq(Reminder::getId, id)
                .eq(Reminder::getReceiverId, accountId)
                .set(Reminder::getReadFlag, 1));
    }

    public void markAllRead() {
        Long accountId = SecurityUtils.currentUserId();
        reminderMapper.update(null, new LambdaUpdateWrapper<Reminder>()
                .eq(Reminder::getReceiverId, accountId)
                .eq(Reminder::getReadFlag, 0)
                .set(Reminder::getReadFlag, 1));
    }

    private void fillEmployeeNames(List<Reminder> rows) {
        List<Long> ids = rows.stream().map(Reminder::getEmployeeId).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> names = employeeMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName, (a, b) -> a));
        for (Reminder row : rows) {
            row.setEmployeeName(names.get(row.getEmployeeId()));
        }
    }

    private List<Integer> parseOffsets() {
        return parseOffsets(probationOffsets);
    }

    private List<Integer> parseOffsets(String config) {
        List<Integer> out = new ArrayList<>();
        if (config == null || config.isBlank()) {
            return List.of(30, 7, 1);
        }
        for (String part : config.split(",")) {
            try {
                out.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // 配置里混入脏值：跳过，不影响其它节点
            }
        }
        return out.isEmpty() ? List.of(30, 7, 1) : out;
    }

    /**
     * 读取配置中心埋点里的额外接收人。
     * <p>
     * JSON：{@code {"orgIds":["组织ID"],"accountIds":["账号ID"],"directSupervisor":true}}；
     * 部门按整棵子树展开为「在职 + 已开户」员工的账号 ID。
     * 埋点缺失/JSON 非法/配置中心不可用 → 空接收人 + 直属主管照发（保持原有兜底语义）。
     */
    private ReceiverConfig receiverConfig() {
        EmbedPointDTO point = settingsClient.embedPoint(EMBED_REMINDER_RECEIVERS);
        if (point == null || point.getValue() == null || point.getValue().isBlank()) {
            return ReceiverConfig.EMPTY;
        }
        try {
            JsonNode root = objectMapper.readTree(point.getValue());
            Set<Long> accountIds = new LinkedHashSet<>(toLongList(root.get("accountIds")));
            accountIds.addAll(expandOrgAccountIds(toLongList(root.get("orgIds"))));
            boolean directSupervisor = root.path("directSupervisor").asBoolean(true);
            return new ReceiverConfig(new ArrayList<>(accountIds), directSupervisor);
        } catch (Exception e) {
            log.warn("埋点 {} 解析失败，收件人退化为本人+直属主管：{}", EMBED_REMINDER_RECEIVERS, e.getMessage());
            return ReceiverConfig.EMPTY;
        }
    }

    /** 组织（含所有下级）→ 在职且已开户员工 → 账号 ID；口径与组织树「账号ID」接口一致 */
    private List<Long> expandOrgAccountIds(List<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return List.of();
        }
        List<OrgNode> roots = orgNodeMapper.selectBatchIds(orgIds);
        if (roots.isEmpty()) {
            return List.of();
        }
        List<OrgNode> nodes = orgNodeMapper.selectList(new LambdaQueryWrapper<OrgNode>()
                .and(w -> {
                    for (OrgNode root : roots) {
                        String path = root.getPath();
                        if (path == null || path.isBlank()) {
                            w.or().eq(OrgNode::getId, root.getId());
                        } else {
                            w.or(x -> x.eq(OrgNode::getPath, path)
                                    .or().likeRight(OrgNode::getPath, path + "/"));
                        }
                    }
                }));
        if (nodes.isEmpty()) {
            return List.of();
        }
        List<Long> ids = nodes.stream().map(OrgNode::getId).collect(Collectors.toList());
        return employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                        .in(Employee::getOrgId, ids)
                        .isNotNull(Employee::getAccountId)
                        .ne(Employee::getStatus, 60))
                .stream().map(Employee::getAccountId).distinct().collect(Collectors.toList());
    }

    private List<Long> toLongList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (JsonNode item : node) {
            try {
                out.add(Long.valueOf(item.asText("").trim()));
            } catch (NumberFormatException ignored) {
                // 前端传了脏值：跳过，不影响其它收件人
            }
        }
        return out;
    }

    /** 埋点解析出的额外接收人；EMPTY = 无额外收件人但保留直属主管 */
    private record ReceiverConfig(List<Long> accountIds, boolean directSupervisor) {
        static final ReceiverConfig EMPTY = new ReceiverConfig(List.of(), true);
    }
}
