package com.laimengchao.sso.ehr.module.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.ehr.common.BizNoService;
import com.laimengchao.sso.ehr.module.attachment.service.AttachmentService;
import com.laimengchao.sso.ehr.module.recruit.entity.Candidate;
import com.laimengchao.sso.ehr.module.recruit.entity.Interview;
import com.laimengchao.sso.ehr.module.recruit.entity.JobPost;
import com.laimengchao.sso.ehr.module.recruit.mapper.CandidateMapper;
import com.laimengchao.sso.ehr.module.recruit.mapper.InterviewMapper;
import com.laimengchao.sso.ehr.module.recruit.vo.ApplyProgressVO;
import com.laimengchao.sso.ehr.module.recruit.vo.JobPostVO;
import com.laimengchao.sso.common.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 门户招聘公开能力：职位列表/详情、简历投递、进度查询。
 * <p>免登录，必须自带防护：隐私同意、手机号校验、文件类型/大小限制、投递去重、
 * 查询码+手机号双因子、按 IP 的小时级限流。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicRecruitService {

    private static final long MAX_RESUME = 10L * 1024 * 1024;
    private static final List<String> RESUME_EXTS = List.of("pdf", "doc", "docx", "jpg", "jpeg", "png");
    private static final String CONSENT_VERSION = "v1";
    private static final int RETAIN_DAYS = 180;
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 限流窗口：key=scene:ip，value=窗口内计数 + 窗口起始毫秒 */
    private final Map<String, int[]> windows = new ConcurrentHashMap<>();
    private static final int APPLY_PER_HOUR = 6;
    private static final int QUERY_PER_HOUR = 30;

    private final JobService jobService;
    private final CandidateMapper candidateMapper;
    private final InterviewMapper interviewMapper;
    private final AttachmentService attachmentService;
    private final BizNoService bizNoService;

    public R.Page<JobPostVO> jobs(String keyword, String jobType, int page, int size) {
        return jobService.publicPage(keyword, jobType, page, size);
    }

    public JobPostVO job(Long id) {
        return jobService.publicDetail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ApplyProgressVO apply(Long jobId, MultipartFile resume, String name, String mobile, String email,
                                 String education, String school, String major, String workYears,
                                 String currentCompany, String expectedSalary, String remark,
                                 Boolean consent, String ip) {
        limit("apply", ip, APPLY_PER_HOUR);
        if (!Boolean.TRUE.equals(consent)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请先阅读并同意隐私声明");
        }
        String phone = trim(mobile);
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请填写正确的手机号");
        }
        String candidateName = trim(name);
        if (candidateName.isEmpty() || candidateName.length() > 32) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请填写正确的姓名");
        }
        validateResume(resume);
        JobPost job = jobService.requirePublished(jobId);

        String applyKey = jobId + ":" + sha256(phone);
        Long dup = candidateMapper.selectCount(new LambdaQueryWrapper<Candidate>()
                .eq(Candidate::getApplyKey, applyKey));
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "您已投递过该职位，请耐心等待 HR 联系");
        }
        // 面试未通过（已淘汰）后半年内禁止再次网投；HR 后台手动新增不受此限制
        Long banned = candidateMapper.selectCount(new LambdaQueryWrapper<Candidate>()
                .eq(Candidate::getMobile, phone)
                .eq(Candidate::getStage, RecruitService.STAGE_REJECTED)
                .ge(Candidate::getLastStageAt, java.time.LocalDateTime.now().minusMonths(6)));
        if (banned != null && banned > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "您近半年内面试未通过，暂无法再次投递，可联系 HR");
        }
        // 同一手机号同时最多 3 个在途投递，避免刷岗；已淘汰的不占额度
        Long active = candidateMapper.selectCount(new LambdaQueryWrapper<Candidate>()
                .eq(Candidate::getMobile, phone)
                .ne(Candidate::getStage, RecruitService.STAGE_REJECTED)
                .ge(Candidate::getAppliedAt, java.time.LocalDateTime.now().minusDays(90)));
        if (active != null && active >= 3) {
            throw new BizException(ErrorCode.PARAM_ERROR, "在途投递已达上限，请等待现有流程结束");
        }

        Long resumeFileId = attachmentService.upload(resume, "CANDIDATE", null, null).getId();

        Candidate c = new Candidate();
        c.setCandidateNo(bizNoService.next("ehr_candidate"));
        c.setName(candidateName);
        c.setMobile(phone);
        c.setEmail(trim(email));
        c.setSource("PORTAL");
        c.setPosition(job.getTitle());
        c.setOrgId(job.getOrgId());
        c.setJobId(jobId);
        c.setStage(RecruitService.STAGE_RESUME);
        c.setRemark(trim(remark));
        c.setEducation(trim(education));
        c.setSchool(trim(school));
        c.setMajor(trim(major));
        c.setWorkYears(parseDecimal(workYears));
        c.setCurrentCompany(trim(currentCompany));
        c.setExpectedSalary(parseDecimal(expectedSalary));
        c.setResumeFileId(resumeFileId);
        c.setApplyKey(applyKey);
        c.setTrackCode(nextTrackCode());
        c.setAppliedAt(java.time.LocalDateTime.now());
        c.setLastStageAt(c.getAppliedAt());
        c.setConsentAt(c.getAppliedAt());
        c.setConsentVersion(CONSENT_VERSION);
        c.setRetainUntil(LocalDate.now().plusDays(RETAIN_DAYS));
        candidateMapper.insert(c);
        return progressOf(c);
    }

    public ApplyProgressVO progress(String trackCode, String mobile, String ip) {
        limit("query", ip, QUERY_PER_HOUR);
        String code = trim(trackCode).toUpperCase(Locale.ROOT);
        String phone = trim(mobile);
        if (phone.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请填写手机号");
        }
        LambdaQueryWrapper<Candidate> wrapper = new LambdaQueryWrapper<Candidate>()
                .eq(Candidate::getMobile, phone);
        if (!code.isEmpty()) {
            wrapper.eq(Candidate::getTrackCode, code);
        }
        Candidate c = candidateMapper.selectOne(wrapper
                .orderByDesc(Candidate::getCreatedAt)
                .last("LIMIT 1"));
        if (c == null) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    code.isEmpty() ? "未找到该手机号的投递记录，请核对手机号" : "未找到投递记录，请核对查询码与手机号");
        }
        return progressOf(c);
    }

    private ApplyProgressVO progressOf(Candidate c) {
        ApplyProgressVO vo = new ApplyProgressVO();
        vo.setTrackCode(c.getTrackCode());
        vo.setCandidateName(maskName(c.getName()));
        String title = c.getPosition();
        if (c.getJobId() != null) {
            try {
                title = jobService.detail(c.getJobId()).getTitle();
            } catch (Exception ignored) {
                // 职位已删除时退回候选人快照职位名
            }
        }
        vo.setJobTitle(title);
        String stage = c.getStage() == null ? RecruitService.STAGE_RESUME : c.getStage();
        vo.setStage(stage);
        vo.setStageLabel(stageLabel(stage));
        vo.setAppliedAt(c.getAppliedAt() == null ? null : c.getAppliedAt().toString());
        vo.setLastStageAt(c.getLastStageAt() == null ? null : c.getLastStageAt().toString());
        List<ApplyProgressVO.RoundItem> rounds = roundsOf(c);
        vo.setRounds(rounds);
        vo.setTimeline(timeline(stage, c, rounds));
        return vo;
    }

    /** 各轮面试进度：只取有效场次，按轮次聚合；未安排过面试时给一个「待安排」的第 1 轮 */
    private List<ApplyProgressVO.RoundItem> roundsOf(Candidate c) {
        List<Interview> rows = interviewMapper.selectList(new LambdaQueryWrapper<Interview>()
                .eq(Interview::getCandidateId, c.getId())
                .ne(Interview::getStatus, Interview.STATUS_CANCELED)
                .orderByAsc(Interview::getRoundNo)
                .orderByAsc(Interview::getScheduledAt));
        List<ApplyProgressVO.RoundItem> items = new ArrayList<>();
        Map<Integer, List<Interview>> byRound = new java.util.LinkedHashMap<>();
        for (Interview iv : rows) {
            byRound.computeIfAbsent(iv.getRoundNo() == null ? 1 : iv.getRoundNo(),
                    k -> new ArrayList<>()).add(iv);
        }
        byRound.forEach((roundNo, list) -> {
            ApplyProgressVO.RoundItem item = new ApplyProgressVO.RoundItem();
            item.setRoundNo(roundNo);
            item.setLabel("第 " + roundNo + " 轮面试");
            Interview first = list.get(0);
            item.setTime(first.getScheduledAt() == null ? null
                    : first.getScheduledAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            item.setMethod(first.getMethod());
            item.setMethodLabel(interviewMethodLabel(first.getMethod()));
            item.setLocation(first.getLocation());
            String result = list.stream().map(Interview::getRoundResult)
                    .filter(java.util.Objects::nonNull).findFirst().orElse(null);
            if (Interview.RESULT_PASS.equals(result)) {
                item.setStatus(Interview.RESULT_PASS);
                item.setStatusLabel("已通过");
            } else if (Interview.RESULT_FAIL.equals(result)) {
                item.setStatus(Interview.RESULT_FAIL);
                item.setStatusLabel("未通过");
            } else {
                item.setStatus("PENDING");
                item.setStatusLabel("待面试");
            }
            items.add(item);
        });
        if (items.isEmpty() && !RecruitService.STAGE_REJECTED.equals(c.getStage())) {
            ApplyProgressVO.RoundItem item = new ApplyProgressVO.RoundItem();
            item.setRoundNo(1);
            item.setLabel("第 1 轮面试");
            item.setStatus("PENDING");
            item.setStatusLabel("待安排");
            items.add(item);
        }
        return items;
    }

    private List<ApplyProgressVO.TimelineItem> timeline(String current, Candidate c,
                                                        List<ApplyProgressVO.RoundItem> rounds) {
        String[] stages = {RecruitService.STAGE_RESUME, RecruitService.STAGE_INTERVIEW, RecruitService.STAGE_OFFER};
        int currentIndex = RecruitService.STAGE_REJECTED.equals(current) ? -1 : indexOf(stages, current);
        List<ApplyProgressVO.TimelineItem> items = new ArrayList<>();
        for (int i = 0; i < stages.length; i++) {
            ApplyProgressVO.TimelineItem item = new ApplyProgressVO.TimelineItem();
            item.setStage(stages[i]);
            item.setLabel(stageLabel(stages[i]));
            item.setDone(currentIndex >= i);
            item.setTime(i == 0 && c.getAppliedAt() != null ? c.getAppliedAt().toString() : null);
            items.add(item);
            if (RecruitService.STAGE_INTERVIEW.equals(stages[i])) {
                for (ApplyProgressVO.RoundItem round : rounds) {
                    ApplyProgressVO.TimelineItem rt = new ApplyProgressVO.TimelineItem();
                    rt.setStage("INTERVIEW_ROUND");
                    rt.setLabel(round.getLabel() + (round.getStatusLabel() == null ? "" : " · " + round.getStatusLabel()));
                    rt.setTime(round.getTime());
                    rt.setDone(!"PENDING".equals(round.getStatus()));
                    items.add(rt);
                }
            }
        }
        if (RecruitService.STAGE_REJECTED.equals(current)) {
            ApplyProgressVO.TimelineItem item = new ApplyProgressVO.TimelineItem();
            item.setStage(RecruitService.STAGE_REJECTED);
            item.setLabel("流程结束");
            item.setDone(true);
            item.setTime(c.getLastStageAt() == null ? null : c.getLastStageAt().toString());
            items.add(item);
        }
        return items;
    }

    private int indexOf(String[] stages, String target) {
        for (int i = 0; i < stages.length; i++) {
            if (stages[i].equals(target)) {
                return i;
            }
        }
        return 0;
    }

    private String interviewMethodLabel(String method) {
        if (method == null) {
            return null;
        }
        return switch (method) {
            case "ONSITE" -> "现场面试";
            case "VIDEO" -> "视频面试";
            case "PHONE" -> "电话面试";
            default -> method;
        };
    }

    private String stageLabel(String stage) {
        return switch (stage) {
            case RecruitService.STAGE_INTERVIEW -> "面试评估中";
            case RecruitService.STAGE_OFFER -> "录用沟通中";
            case RecruitService.STAGE_REJECTED -> "流程已结束";
            default -> "简历筛选中";
        };
    }

    private void validateResume(MultipartFile resume) {
        if (resume == null || resume.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请上传简历");
        }
        if (resume.getSize() > MAX_RESUME) {
            throw new BizException(ErrorCode.PARAM_ERROR, "简历不能超过 10MB");
        }
        String name = resume.getOriginalFilename() == null ? "" : resume.getOriginalFilename();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!RESUME_EXTS.contains(ext)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "简历仅支持 PDF / Word / 图片");
        }
    }

    private void limit(String scene, String ip, int maxPerHour) {
        String key = scene + ":" + (ip == null || ip.isBlank() ? "unknown" : ip);
        long now = System.currentTimeMillis();
        int[] slot = windows.compute(key, (k, old) -> {
            if (old == null || now - old[1] > 3_600_000L) {
                return new int[]{1, (int) (now / 1000)};
            }
            old[0]++;
            return old;
        });
        if (slot[0] > maxPerHour) {
            throw new BizException(ErrorCode.PARAM_ERROR, "操作过于频繁，请稍后再试");
        }
        if (windows.size() > 5000) {
            windows.entrySet().removeIf(e -> now / 1000 - e.getValue()[1] > 3600);
        }
    }

    private String nextTrackCode() {
        for (int i = 0; i < 10; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 8; j++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            String code = sb.toString();
            if (candidateMapper.selectCount(new LambdaQueryWrapper<Candidate>()
                    .eq(Candidate::getTrackCode, code)) == 0) {
                return code;
            }
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "查询码生成失败，请重试");
    }

    private String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "投递校验失败");
        }
    }

    private java.math.BigDecimal parseDecimal(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return new java.math.BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.charAt(0) + "*".repeat(Math.max(1, name.length() - 1));
    }

    private String trim(String v) {
        return v == null ? "" : v.trim();
    }
}
