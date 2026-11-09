package com.laimengchao.sso.ehr.module.reminder.service;

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
import com.laimengchao.sso.ehr.module.reminder.entity.Reminder;
import com.laimengchao.sso.ehr.module.reminder.mapper.JobLockMapper;
import com.laimengchao.sso.ehr.module.reminder.mapper.ReminderMapper;
import com.laimengchao.sso.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 试用期到期提醒。
 * <p>
 * 每天 09:00 由 {@link com.laimengchao.sso.ehr.module.reminder.job.ReminderJob} 触发，
 * 扫描「试用期 + 预计转正日 = 今天 + N 天」的在职员工（N 默认 30/7/1，可配），
 * 给本人、直属主管与 HR 各写一条提醒。唯一键保证同一个节点只提醒一次，
 * 转正/离职后状态不再命中，自然停止。
 * <p>
 * HR 在 EHR 库里没有角色表，接收人通过 {@code ehr.reminder.hr-account-ids}
 * 配置统一账号 ID（逗号分隔）；不配则只提醒员工与直属主管。
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
    private final EmployeeMapper employeeMapper;
    private final ContractMapper contractMapper;
    private final ApproverResolver approverResolver;

    /** 提醒节点：T-N 天，默认 30/7/1 */
    @Value("${ehr.reminder.probation-offsets:30,7,1}")
    private String probationOffsets;

    /** 合同到期提醒节点：T-N 天，默认 30/7/1 */
    @Value("${ehr.reminder.contract-offsets:30,7,1}")
    private String contractOffsets;

    /** HR 接收人统一账号 ID，逗号分隔 */
    @Value("${ehr.reminder.hr-account-ids:}")
    private String hrAccountIds;

    /** 每日生成提醒；返回本次新增条数（0 表示没抢到锁或没有命中） */
    public int generateDaily() {
        LocalDateTime now = LocalDateTime.now();
        int locked = jobLockMapper.tryLock(JOB_NAME, now.plusMinutes(30), INSTANCE_ID, now);
        if (locked <= 0) {
            log.info("试用期提醒任务已被其它实例抢占，本次跳过");
            return 0;
        }
        List<Integer> offsets = parseOffsets();
        List<Long> hrIds = parseHrIds();
        int created = 0;
        for (Integer offset : offsets) {
            LocalDate dueDate = LocalDate.now().plusDays(offset);
            List<Employee> rows = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                    .eq(Employee::getStatus, EmployeeStatus.PROBATION.getCode())
                    .eq(Employee::getExpectedRegularizeDate, dueDate));
            for (Employee emp : rows) {
                created += remindEmployee(emp, offset, dueDate, hrIds);
            }
        }
        log.info("试用期提醒生成完成：新增 {} 条，节点 {}，HR 接收人 {}", created, offsets, hrIds);
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
        List<Long> hrIds = parseHrIds();
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
                created += remindContract(c, emp, offset, dueDate, hrIds);
            }
        }
        log.info("合同到期提醒生成完成：新增 {} 条，节点 {}，HR 接收人 {}", created, offsets, hrIds);
        return created;
    }

    /** 续签 PENDING 或终止 TERMINATING 期间不再重复提醒 */
    private boolean hasRunningChange(Long employeeId) {
        return contractMapper.selectCount(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getEmployeeId, employeeId)
                .in(Contract::getStatus, "PENDING", "TERMINATING")) > 0;
    }

    private int remindContract(Contract c, Employee emp, int offset, LocalDate dueDate, List<Long> hrIds) {
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
        receivers.addAll(hrIds);
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

    private int remindEmployee(Employee emp, int offset, LocalDate dueDate, List<Long> hrIds) {
        String title = "试用期到期提醒（T-" + offset + "）";
        String content = String.format("%s 的试用期将于 %s 到期（剩余 %d 天），请及时跟进转正评估。",
                emp.getName(), dueDate, offset);
        Set<Long> receivers = new LinkedHashSet<>();
        if (emp.getAccountId() != null) {
            receivers.add(emp.getAccountId());
        }
        // 直属主管：从员工所在组织沿 path 上溯，跳过员工本人
        ApproverResolver.Approver leader = approverResolver.resolve(emp, emp.getAccountId());
        if (leader != null && leader.exists()) {
            receivers.add(leader.accountId());
        }
        receivers.addAll(hrIds);
        int created = 0;
        for (Long receiverId : receivers) {
            created += reminderMapper.insertIgnore(IdWorker.getId(), emp.getId(), receiverId,
                    TYPE_PROBATION_DUE, emp.getId(), dueDate, title, content);
        }
        return created;
    }

    /* ---------- 我的提醒 ---------- */

    public R.Page<Reminder> my(boolean unreadOnly, int page, int size) {
        Long accountId = SecurityUtils.currentUserId();
        LambdaQueryWrapper<Reminder> w = new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getReceiverId, accountId);
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

    public int unreadCount() {
        Long accountId = SecurityUtils.currentUserId();
        return Math.toIntExact(reminderMapper.selectCount(new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getReceiverId, accountId)
                .eq(Reminder::getReadFlag, 0)));
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

    private List<Long> parseHrIds() {
        if (hrAccountIds == null || hrAccountIds.isBlank()) {
            return List.of();
        }
        try {
            return Arrays.stream(hrAccountIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::valueOf)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            log.warn("ehr.reminder.hr-account-ids 配置不是合法的账号 ID 列表：{}", hrAccountIds);
            return List.of();
        }
    }
}
