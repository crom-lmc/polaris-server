package com.laimengchao.sso.ehr.module.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.ehr.common.BizNoService;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.recruit.dto.InterviewFeedbackRequest;
import com.laimengchao.sso.ehr.module.recruit.dto.InterviewRequest;
import com.laimengchao.sso.ehr.module.recruit.entity.Candidate;
import com.laimengchao.sso.ehr.module.recruit.entity.Interview;
import com.laimengchao.sso.ehr.module.recruit.mapper.CandidateMapper;
import com.laimengchao.sso.ehr.module.recruit.mapper.InterviewMapper;
import com.laimengchao.sso.ehr.module.recruit.vo.InterviewTodoVO;
import com.laimengchao.sso.ehr.module.recruit.vo.InterviewVO;
import com.laimengchao.sso.ehr.module.reminder.entity.Reminder;
import com.laimengchao.sso.ehr.module.reminder.mapper.ReminderMapper;
import com.laimengchao.sso.settings.client.api.NoticeApi;
import com.laimengchao.sso.settings.client.dto.NoticeBizHandledDTO;
import com.laimengchao.sso.settings.client.dto.NoticeSendDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 招聘多轮面试：HR 约面试 → 面试官在 EHR 填反馈 → HR 依据反馈判定通过/淘汰。
 * <p>
 * 轮数不固定，由 HR 与用人部门线下确定；每轮可约多位面试官（每人一行）。
 * 未通过原因为 Offer 前置校验与门户半年禁投提供依据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    public static final String TYPE_INTERVIEW = "INTERVIEW_FEEDBACK";

    /** 统一账号中心待办的业务类型（bizType），与 bizId=面试记录ID 一起定位一条待办 */
    private static final String NOTICE_BIZ_TYPE = "EHR_INTERVIEW";
    private static final String NOTICE_RULE_CODE = "ehr.interview.todo";

    private static final DateTimeFormatter FMT_SECOND = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FMT_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 面试记录表名，配置中心「单号规则」按此取号 */
    private static final String TABLE_INTERVIEW = "ehr_interview";

    private final InterviewMapper interviewMapper;
    private final CandidateMapper candidateMapper;
    private final EmployeeMapper employeeMapper;
    private final ReminderMapper reminderMapper;
    private final NoticeApi noticeApi;
    private final BizNoService bizNoService;

    /** 候选人全部面试记录，按轮次、时间升序，前端按 roundNo 分组展示 */
    public List<InterviewVO> listByCandidate(Long candidateId) {
        List<Interview> rows = interviewMapper.selectList(new LambdaQueryWrapper<Interview>()
                .eq(Interview::getCandidateId, candidateId)
                .orderByAsc(Interview::getRoundNo)
                .orderByAsc(Interview::getScheduledAt));
        return rows.stream().map(this::toVO).toList();
    }

    /** 约面试：轮次只能自增（当前最大轮次 + 1） */
    @Transactional(rollbackFor = Exception.class)
    public List<InterviewVO> schedule(Long candidateId, InterviewRequest req) {
        Candidate c = requireCandidate(candidateId);
        if (RecruitService.STAGE_REJECTED.equals(c.getStage())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "已淘汰的候选人不能约面试");
        }
        List<Long> interviewerIds = req.getInterviewerIds().stream()
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (interviewerIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择面试官");
        }
        List<Interview> existing = interviewMapper.selectList(new LambdaQueryWrapper<Interview>()
                .eq(Interview::getCandidateId, c.getId()));
        // 正在面试中（还有未反馈的安排）不允许再约，等本轮全部出结果后再约下一轮
        boolean pending = existing.stream()
                .anyMatch(r -> Interview.STATUS_SCHEDULED.equals(r.getStatus()));
        if (pending) {
            throw new BizException(ErrorCode.PARAM_ERROR, "当前面试尚未完成，请等待面试结果后再约下一轮");
        }
        // 轮次只能自增：第一次第 1 轮、第二次第 2 轮，是否继续下一轮由 HR 与面试官线下沟通
        int roundNo = maxRound(existing) + 1;
        if (roundNo > 1 && !roundPassed(existing, roundNo - 1)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "上一轮面试未通过，不能约下一轮");
        }
        LocalDateTime scheduledAt = parseDateTime(req.getScheduledAt());
        Map<Long, Employee> employees = employeeMapper.selectBatchIds(interviewerIds).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        for (Long id : interviewerIds) {
            if (!employees.containsKey(id)) {
                throw new BizException(ErrorCode.NOT_FOUND, "面试官不存在：" + id);
            }
        }

        String method = blankTo(req.getMethod(), "ONSITE").toUpperCase();
        List<Interview> created = new ArrayList<>();
        for (Long interviewerId : interviewerIds) {
            Employee emp = employees.get(interviewerId);
            Interview iv = new Interview();
            iv.setCandidateId(c.getId());
            iv.setJobId(c.getJobId());
            iv.setInterviewNo(bizNoService.next(TABLE_INTERVIEW));
            iv.setRoundNo(roundNo);
            iv.setInterviewerId(emp.getId());
            iv.setInterviewerAccountId(emp.getAccountId());
            iv.setInterviewerName(nvl(emp.getName()));
            iv.setScheduledAt(scheduledAt);
            iv.setMethod(method);
            iv.setLocation(nvl(req.getLocation()).trim());
            iv.setStatus(Interview.STATUS_SCHEDULED);
            iv.setRemark(nvl(req.getRemark()).trim());
            interviewMapper.insert(iv);
            created.add(iv);
            remind(emp, c, iv);
        }
        c.setStage(RecruitService.STAGE_INTERVIEW);
        c.setLastStageAt(LocalDateTime.now());
        candidateMapper.updateById(c);
        return created.stream().map(this::toVO).toList();
    }

    /** 面试官「我的面试」：待反馈在前按时间升序，已反馈的保留在列表中供查看结果 */
    public List<InterviewTodoVO> myTodos() {
        Long accountId = currentAccountId();
        List<Interview> rows = interviewMapper.selectList(new LambdaQueryWrapper<Interview>()
                .eq(Interview::getInterviewerAccountId, accountId)
                .in(Interview::getStatus, Interview.STATUS_SCHEDULED, Interview.STATUS_DONE)
                .orderByAsc(Interview::getScheduledAt));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Candidate> candidates = candidateMapper.selectBatchIds(
                        rows.stream().map(Interview::getCandidateId).distinct().toList())
                .stream().collect(Collectors.toMap(Candidate::getId, Function.identity()));
        List<InterviewTodoVO> pending = new ArrayList<>();
        List<InterviewTodoVO> done = new ArrayList<>();
        for (Interview r : rows) {
            Candidate c = candidates.get(r.getCandidateId());
            InterviewTodoVO vo = new InterviewTodoVO();
            vo.setId(String.valueOf(r.getId()));
            vo.setCandidateId(String.valueOf(r.getCandidateId()));
            vo.setCandidateName(c == null ? "" : nvl(c.getName()));
            vo.setPosition(c == null ? "" : nvl(c.getPosition()));
            vo.setRoundNo(r.getRoundNo());
            vo.setScheduledAt(timeOf(r.getScheduledAt()));
            vo.setMethod(r.getMethod());
            vo.setLocation(nvl(r.getLocation()));
            vo.setStatus(r.getStatus());
            vo.setScore(r.getScore());
            vo.setFeedback(nvl(r.getFeedback()));
            vo.setResult(r.getRoundResult());
            vo.setInterviewNo(nvl(r.getInterviewNo()));
            vo.setFeedbackAt(r.getFeedbackAt() == null ? "" : r.getFeedbackAt().format(FMT_MINUTE));
            if (Interview.STATUS_SCHEDULED.equals(r.getStatus())) {
                pending.add(vo);
            } else {
                done.add(vo);
            }
        }
        done.sort(Comparator.comparing(InterviewTodoVO::getFeedbackAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        pending.addAll(done);
        return pending;
    }

    /** 面试官提交反馈：必须给出本轮结论；不通过即淘汰，通过则等本轮全部通过后由 HR 决定发 Offer 或下一轮 */
    @Transactional(rollbackFor = Exception.class)
    public void submitFeedback(Long interviewId, InterviewFeedbackRequest req) {
        Interview iv = requireInterview(interviewId);
        Long accountId = currentAccountId();
        if (iv.getInterviewerAccountId() == null || !iv.getInterviewerAccountId().equals(accountId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有该场面试的面试官可以填写反馈");
        }
        if (Interview.STATUS_CANCELED.equals(iv.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该场面试已取消");
        }
        if (iv.getRoundResult() != null && Interview.STATUS_DONE.equals(iv.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该轮面试已结束，不能再修改反馈");
        }
        String result = nvl(req.getResult()).trim().toUpperCase();
        if (!Interview.RESULT_PASS.equals(result) && !Interview.RESULT_FAIL.equals(result)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "本轮结果只能是 PASS 或 FAIL");
        }
        LocalDateTime now = LocalDateTime.now();
        iv.setScore(req.getScore());
        iv.setFeedback(req.getFeedback().trim());
        iv.setFeedbackAt(now);
        iv.setStatus(Interview.STATUS_DONE);
        iv.setRoundResult(result);
        iv.setDecidedAt(now);
        iv.setDecidedBy(accountId);
        interviewMapper.updateById(iv);
        reminderMapper.update(null, new LambdaUpdateWrapper<Reminder>()
                .eq(Reminder::getType, TYPE_INTERVIEW)
                .eq(Reminder::getBizId, iv.getId())
                .eq(Reminder::getReceiverId, accountId)
                .set(Reminder::getReadFlag, 1));
        closeNotice(interviewId);

        List<Interview> valid = interviewMapper.selectList(new LambdaQueryWrapper<Interview>()
                        .eq(Interview::getCandidateId, iv.getCandidateId())
                        .eq(Interview::getRoundNo, iv.getRoundNo()))
                .stream()
                .filter(r -> !Interview.STATUS_CANCELED.equals(r.getStatus()))
                .toList();

        if (Interview.RESULT_FAIL.equals(result)) {
            // 任一面试官给出不通过：整轮判为不通过，直接淘汰候选人
            interviewMapper.update(null, new LambdaUpdateWrapper<Interview>()
                    .eq(Interview::getCandidateId, iv.getCandidateId())
                    .eq(Interview::getRoundNo, iv.getRoundNo())
                    .ne(Interview::getStatus, Interview.STATUS_CANCELED)
                    .set(Interview::getRoundResult, Interview.RESULT_FAIL)
                    .set(Interview::getDecidedAt, now)
                    .set(Interview::getDecidedBy, accountId));
            for (Interview other : valid) {
                if (other.getId().equals(iv.getId())
                        || !Interview.STATUS_SCHEDULED.equals(other.getStatus())) {
                    continue;
                }
                other.setStatus(Interview.STATUS_CANCELED);
                other.setRemark(appendRemark(other.getRemark(), "本轮面试未通过，安排取消", null));
                interviewMapper.updateById(other);
                closeInterviewTodo(other);
            }
            Candidate c = requireCandidate(iv.getCandidateId());
            c.setStage(RecruitService.STAGE_REJECTED);
            c.setLastStageAt(now);
            c.setRemark(appendRemark(c.getRemark(),
                    "第" + iv.getRoundNo() + "轮面试未通过（" + nvl(iv.getInterviewerName()) + "）",
                    req.getFeedback().trim()));
            candidateMapper.updateById(c);
            return;
        }

        boolean allPass = valid.stream().allMatch(r -> Interview.RESULT_PASS.equals(r.getRoundResult()));
        if (allPass) {
            interviewMapper.update(null, new LambdaUpdateWrapper<Interview>()
                    .eq(Interview::getCandidateId, iv.getCandidateId())
                    .eq(Interview::getRoundNo, iv.getRoundNo())
                    .ne(Interview::getStatus, Interview.STATUS_CANCELED)
                    .set(Interview::getDecidedAt, now)
                    .set(Interview::getDecidedBy, accountId));
            Candidate c = requireCandidate(iv.getCandidateId());
            c.setStage(RecruitService.STAGE_INTERVIEW);
            c.setLastStageAt(now);
            candidateMapper.updateById(c);
        }
    }

    /** HR 取消一场面试（同轮其它面试官不受影响） */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long interviewId, String remark) {
        Interview iv = requireInterview(interviewId);
        if (Interview.STATUS_CANCELED.equals(iv.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该场面试已取消");
        }
        if (iv.getRoundResult() != null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该轮面试已判定，不能取消");
        }
        iv.setStatus(Interview.STATUS_CANCELED);
        iv.setRemark(appendRemark(iv.getRemark(), "取消", remark));
        interviewMapper.updateById(iv);
        if (iv.getInterviewerAccountId() != null) {
            reminderMapper.update(null, new LambdaUpdateWrapper<Reminder>()
                    .eq(Reminder::getType, TYPE_INTERVIEW)
                    .eq(Reminder::getBizId, iv.getId())
                    .eq(Reminder::getReceiverId, iv.getInterviewerAccountId())
                    .set(Reminder::getReadFlag, 1));
        }
        closeNotice(iv.getId());
    }

    /** 已全部面试官通过的最大轮次；无通过记录返回 null */
    public Integer passedRound(Long candidateId) {
        List<Interview> rows = interviewMapper.selectList(new LambdaQueryWrapper<Interview>()
                .eq(Interview::getCandidateId, candidateId));
        Map<Integer, List<Interview>> byRound = rows.stream()
                .filter(r -> !Interview.STATUS_CANCELED.equals(r.getStatus()))
                .collect(Collectors.groupingBy(Interview::getRoundNo));
        return byRound.entrySet().stream()
                .filter(e -> e.getValue().stream()
                        .allMatch(r -> Interview.RESULT_PASS.equals(r.getRoundResult())))
                .map(Map.Entry::getKey)
                .max(Integer::compareTo)
                .orElse(null);
    }

    /** 是否存在未完成（已约未反馈、未判定）的面试 */
    public boolean hasPending(Long candidateId) {
        return interviewMapper.selectCount(new LambdaQueryWrapper<Interview>()
                .eq(Interview::getCandidateId, candidateId)
                .eq(Interview::getStatus, Interview.STATUS_SCHEDULED)
                .isNull(Interview::getRoundResult)) > 0;
    }

    /** 批量摘要：按候选人统计待反馈数与最近安排时间，避免列表 N+1 */
    public Map<Long, Summary> summaries(List<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return Map.of();
        }
        List<Interview> rows = interviewMapper.selectList(new LambdaQueryWrapper<Interview>()
                .in(Interview::getCandidateId, candidateIds));
        Map<Long, Map<Integer, List<Interview>>> byCandidate = new java.util.HashMap<>();
        for (Interview iv : rows) {
            byCandidate.computeIfAbsent(iv.getCandidateId(), k -> new java.util.HashMap<>())
                    .computeIfAbsent(iv.getRoundNo(), k -> new ArrayList<>())
                    .add(iv);
        }
        Map<Long, Summary> map = new java.util.HashMap<>();
        byCandidate.forEach((candidateId, rounds) -> {
            Summary s = new Summary();
            rounds.forEach((roundNo, list) -> {
                List<Interview> valid = list.stream()
                        .filter(r -> !Interview.STATUS_CANCELED.equals(r.getStatus()))
                        .toList();
                if (!valid.isEmpty() && valid.stream()
                        .allMatch(r -> Interview.RESULT_PASS.equals(r.getRoundResult()))) {
                    s.passedRound = s.passedRound == null ? roundNo : Math.max(s.passedRound, roundNo);
                }
                for (Interview iv : valid) {
                    if (Interview.STATUS_SCHEDULED.equals(iv.getStatus()) && iv.getRoundResult() == null) {
                        s.pendingCount++;
                        if (iv.getScheduledAt() != null
                                && (s.nextAt == null || iv.getScheduledAt().isBefore(s.nextAt))) {
                            s.nextAt = iv.getScheduledAt();
                        }
                    }
                }
            });
            map.put(candidateId, s);
        });
        return map;
    }

    /** 候选人面试摘要（列表卡片展示） */
    public static class Summary {
        public Integer passedRound;
        public int pendingCount;
        public LocalDateTime nextAt;
    }

    /* ---------------- 内部 ---------------- */

    private void remind(Employee emp, Candidate c, Interview iv) {
        if (emp.getAccountId() == null) {
            // 未开户的面试官收不到 EHR 待办，HR 需线下通知
            return;
        }
        String title = "面试待办：" + nvl(c.getName()) + " " + nvl(c.getPosition())
                + " 第" + iv.getRoundNo() + "轮";
        String content = "约定时间 " + iv.getScheduledAt().format(FMT_MINUTE)
                + (nvl(iv.getLocation()).isBlank() ? "" : "，地点/链接：" + iv.getLocation());
        try {
            reminderMapper.insertIgnore(IdWorker.getId(), emp.getId(), emp.getAccountId(),
                    TYPE_INTERVIEW, iv.getId(), iv.getScheduledAt().toLocalDate(), title, content);
        } catch (Exception e) {
            log.warn("写入面试待办提醒失败 interview={} interviewer={}", iv.getId(), emp.getId(), e);
        }
        // 同步推一条到统一账号中心「待办提醒」，面试官登录门户即可看到
        try {
            NoticeSendDTO dto = new NoticeSendDTO();
            dto.setRuleCode(NOTICE_RULE_CODE);
            dto.setAccountId(emp.getAccountId());
            dto.setChannel(1);
            dto.setTitle(title);
            dto.setContent(content + "，请准时参加并及时提交面试反馈");
            dto.setBizType(NOTICE_BIZ_TYPE);
            dto.setBizId(String.valueOf(iv.getId()));
            dto.setBizNo(nvl(iv.getInterviewNo()));
            dto.setApplicantName(nvl(c.getName()));
            dto.getVars().put("candidateName", nvl(c.getName()));
            dto.getVars().put("position", nvl(c.getPosition()));
            dto.getVars().put("roundNo", String.valueOf(iv.getRoundNo()));
            dto.getVars().put("time", iv.getScheduledAt().format(FMT_MINUTE));
            noticeApi.send(dto);
        } catch (Exception e) {
            log.warn("推送面试待办到统一账号中心失败 interview={} interviewer={}", iv.getId(), emp.getId(), e);
        }
    }

    /** 面试结束（已反馈/已取消）后关闭统一账号中心对应待办 */
    private void closeNotice(Long interviewId) {
        try {
            NoticeBizHandledDTO dto = new NoticeBizHandledDTO();
            dto.setBizType(NOTICE_BIZ_TYPE);
            dto.setBizId(String.valueOf(interviewId));
            noticeApi.bizHandled(dto);
        } catch (Exception e) {
            log.warn("关闭面试待办失败 interview={}", interviewId, e);
        }
    }

    private boolean roundPassed(List<Interview> existing, int roundNo) {
        List<Interview> round = existing.stream()
                .filter(r -> r.getRoundNo() != null && r.getRoundNo() == roundNo)
                .filter(r -> !Interview.STATUS_CANCELED.equals(r.getStatus()))
                .toList();
        return !round.isEmpty() && round.stream()
                .allMatch(r -> Interview.RESULT_PASS.equals(r.getRoundResult()));
    }

    private int maxRound(List<Interview> existing) {
        return existing.stream().map(Interview::getRoundNo)
                .filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(0);
    }

    private InterviewVO toVO(Interview iv) {
        InterviewVO vo = new InterviewVO();
        vo.setId(String.valueOf(iv.getId()));
        vo.setCandidateId(String.valueOf(iv.getCandidateId()));
        vo.setJobId(iv.getJobId() == null ? null : String.valueOf(iv.getJobId()));
        vo.setInterviewNo(nvl(iv.getInterviewNo()));
        vo.setRoundNo(iv.getRoundNo());
        vo.setInterviewerId(iv.getInterviewerId() == null ? null : String.valueOf(iv.getInterviewerId()));
        vo.setInterviewerName(nvl(iv.getInterviewerName()));
        vo.setScheduledAt(timeOf(iv.getScheduledAt()));
        vo.setMethod(nvl(iv.getMethod()));
        vo.setLocation(nvl(iv.getLocation()));
        vo.setStatus(nvl(iv.getStatus()));
        vo.setScore(iv.getScore());
        vo.setFeedback(nvl(iv.getFeedback()));
        vo.setFeedbackAt(timeOf(iv.getFeedbackAt()));
        vo.setRoundResult(iv.getRoundResult());
        vo.setDecidedAt(timeOf(iv.getDecidedAt()));
        vo.setRemark(nvl(iv.getRemark()));
        return vo;
    }

    private Candidate requireCandidate(Long id) {
        Candidate c = id == null ? null : candidateMapper.selectById(id);
        if (c == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "候选人不存在");
        }
        return c;
    }

    private Interview requireInterview(Long id) {
        Interview iv = id == null ? null : interviewMapper.selectById(id);
        if (iv == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "面试记录不存在");
        }
        return iv;
    }

    private Long currentAccountId() {
        return SecurityUtils.currentUser()
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED))
                .getAccountId();
    }

    /** 关闭某场面试的 EHR 提醒与统一账号中心待办 */
    private void closeInterviewTodo(Interview iv) {
        if (iv.getInterviewerAccountId() != null) {
            reminderMapper.update(null, new LambdaUpdateWrapper<Reminder>()
                    .eq(Reminder::getType, TYPE_INTERVIEW)
                    .eq(Reminder::getBizId, iv.getId())
                    .eq(Reminder::getReceiverId, iv.getInterviewerAccountId())
                    .set(Reminder::getReadFlag, 1));
        }
        closeNotice(iv.getId());
    }

    private LocalDateTime parseDateTime(String text) {
        if (text == null || text.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择面试时间");
        }
        String s = text.trim().replace('T', ' ');
        if (s.endsWith("Z")) {
            s = s.substring(0, s.length() - 1);
        }
        int dot = s.indexOf('.');
        if (dot > 0) {
            s = s.substring(0, dot);
        }
        try {
            return LocalDateTime.parse(s, s.length() > 16 ? FMT_SECOND : FMT_MINUTE);
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "面试时间格式应为 yyyy-MM-dd HH:mm");
        }
    }

    private static String timeOf(LocalDateTime t) {
        return t == null ? null : t.format(FMT_SECOND);
    }

    private String appendRemark(String old, String label, String reason) {
        String add = label + (reason == null || reason.isBlank() ? "" : "：" + reason.trim());
        String text = nvl(old).isBlank() ? add : nvl(old) + "；" + add;
        return text.length() > 255 ? text.substring(0, 255) : text;
    }

    private static String blankTo(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
