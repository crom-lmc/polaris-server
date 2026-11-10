package com.laimengchao.sso.ehr.module.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.ehr.common.BizNoService;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.ehr.module.recruit.dto.JobPostRequest;
import com.laimengchao.sso.ehr.module.recruit.entity.Candidate;
import com.laimengchao.sso.ehr.module.recruit.entity.JobPost;
import com.laimengchao.sso.ehr.module.recruit.mapper.CandidateMapper;
import com.laimengchao.sso.ehr.module.recruit.mapper.JobPostMapper;
import com.laimengchao.sso.ehr.module.recruit.vo.JobPostVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 招聘职位：后台维护 + 门户公开列表共用，发布态与截止日期是唯一对外口径 */
@Service
@RequiredArgsConstructor
public class JobService {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String CLOSED = "CLOSED";

    private final JobPostMapper jobMapper;
    private final CandidateMapper candidateMapper;
    private final OrgNodeMapper orgMapper;
    private final BizNoService bizNoService;

    public R.Page<JobPostVO> page(String status, String keyword, int page, int size) {
        LambdaQueryWrapper<JobPost> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            w.eq(JobPost::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(JobPost::getTitle, keyword.trim())
                    .or().like(JobPost::getJobNo, keyword.trim()));
        }
        w.orderByDesc(JobPost::getSortNo).orderByDesc(JobPost::getCreatedAt);
        IPage<JobPost> p = jobMapper.selectPage(new Page<>(page, size), w);
        List<JobPostVO> list = toVOList(p.getRecords());
        return R.Page.of(list, p.getTotal());
    }

    /** 门户职位列表：只返回已发布且未截止的职位 */
    public R.Page<JobPostVO> publicPage(String keyword, String jobType, int page, int size) {
        LambdaQueryWrapper<JobPost> w = new LambdaQueryWrapper<JobPost>()
                .eq(JobPost::getStatus, PUBLISHED)
                .and(q -> q.isNull(JobPost::getCloseAt).or().ge(JobPost::getCloseAt, LocalDate.now()));
        if (jobType != null && !jobType.isBlank()) {
            w.eq(JobPost::getJobType, jobType.trim().toUpperCase());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            w.and(q -> q.like(JobPost::getTitle, kw)
                    .or().like(JobPost::getWorkCity, kw)
                    .or().like(JobPost::getTags, kw));
        }
        w.orderByDesc(JobPost::getSortNo).orderByDesc(JobPost::getPublishAt);
        IPage<JobPost> p = jobMapper.selectPage(new Page<>(page, Math.min(size, 50)), w);
        return R.Page.of(toVOList(p.getRecords()), p.getTotal());
    }

    public JobPostVO publicDetail(Long id) {
        JobPost job = requirePublished(id);
        return toVO(job, countByJob(List.of(id)).getOrDefault(id, 0L));
    }

    public JobPostVO detail(Long id) {
        JobPost job = require(id);
        return toVO(job, countByJob(List.of(id)).getOrDefault(id, 0L));
    }

    @Transactional(rollbackFor = Exception.class)
    public JobPostVO create(JobPostRequest req) {
        JobPost job = new JobPost();
        apply(job, req);
        job.setJobNo(bizNoService.next("ehr_job_post"));
        job.setStatus(DRAFT);
        var user = SecurityUtils.currentUser().orElse(null);
        job.setCreatedBy(user == null ? null : user.getAccountId());
        if (job.getOwnerId() == null && user != null) {
            job.setOwnerId(user.getAccountId());
        }
        jobMapper.insert(job);
        return toVO(job, 0L);
    }

    @Transactional(rollbackFor = Exception.class)
    public JobPostVO update(Long id, JobPostRequest req) {
        JobPost job = require(id);
        apply(job, req);
        jobMapper.updateById(job);
        return toVO(job, countByJob(List.of(id)).getOrDefault(id, 0L));
    }

    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        JobPost job = require(id);
        if (job.getTitle() == null || job.getTitle().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "职位名称不能为空");
        }
        job.setStatus(PUBLISHED);
        job.setPublishAt(LocalDateTime.now());
        jobMapper.updateById(job);
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        JobPost job = require(id);
        job.setStatus(CLOSED);
        jobMapper.updateById(job);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        JobPost job = require(id);
        Long applies = candidateMapper.selectCount(new LambdaQueryWrapper<Candidate>()
                .eq(Candidate::getJobId, id));
        if (applies != null && applies > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该职位已有投递记录，只能关闭不能删除");
        }
        jobMapper.deleteById(id);
    }

    public JobPost requirePublished(Long id) {
        JobPost job = id == null ? null : jobMapper.selectById(id);
        if (job == null || !PUBLISHED.equals(job.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "职位不存在或已下架");
        }
        if (job.getCloseAt() != null && job.getCloseAt().isBefore(LocalDate.now())) {
            throw new BizException(ErrorCode.NOT_FOUND, "职位已过投递截止日期");
        }
        return job;
    }

    private JobPost require(Long id) {
        JobPost job = jobMapper.selectById(id);
        if (job == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "职位不存在");
        }
        return job;
    }

    private void apply(JobPost job, JobPostRequest req) {
        job.setTitle(req.getTitle().trim());
        job.setOrgId(req.getOrgId());
        job.setJobType(nvl(req.getJobType(), "FULL_TIME").toUpperCase());
        job.setWorkCity(nvl(req.getWorkCity(), ""));
        job.setHeadcount(req.getHeadcount() == null ? 1 : Math.max(1, req.getHeadcount()));
        job.setSalaryMin(req.getSalaryMin());
        job.setSalaryMax(req.getSalaryMax());
        if (job.getSalaryMin() != null && job.getSalaryMax() != null
                && job.getSalaryMin().compareTo(job.getSalaryMax()) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "薪资下限不能大于上限");
        }
        job.setEducationReq(nvl(req.getEducationReq(), ""));
        job.setExperienceReq(nvl(req.getExperienceReq(), ""));
        job.setDescription(nvl(req.getDescription(), ""));
        job.setRequirement(nvl(req.getRequirement(), ""));
        job.setTags(nvl(req.getTags(), ""));
        job.setCloseAt(req.getCloseAt());
        job.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        job.setOwnerId(req.getOwnerId());
    }

    private Map<Long, Long> countByJob(List<Long> jobIds) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        return candidateMapper.selectList(new LambdaQueryWrapper<Candidate>()
                        .in(Candidate::getJobId, jobIds)
                        .ne(Candidate::getStage, RecruitService.STAGE_REJECTED))
                .stream().collect(Collectors.groupingBy(Candidate::getJobId, Collectors.counting()));
    }

    private List<JobPostVO> toVOList(List<JobPost> jobs) {
        if (jobs.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = countByJob(jobs.stream().map(JobPost::getId).toList());
        return jobs.stream().map(j -> toVO(j, counts.getOrDefault(j.getId(), 0L))).toList();
    }

    private JobPostVO toVO(JobPost job, Long applyCount) {
        JobPostVO vo = new JobPostVO();
        vo.setId(String.valueOf(job.getId()));
        vo.setJobNo(nvl(job.getJobNo(), ""));
        vo.setTitle(nvl(job.getTitle(), ""));
        vo.setOrgId(job.getOrgId() == null ? null : String.valueOf(job.getOrgId()));
        vo.setOrgPath(orgPathOf(job.getOrgId()));
        vo.setJobType(nvl(job.getJobType(), ""));
        vo.setWorkCity(nvl(job.getWorkCity(), ""));
        vo.setHeadcount(job.getHeadcount());
        vo.setSalaryMin(job.getSalaryMin() == null ? null : job.getSalaryMin().toPlainString());
        vo.setSalaryMax(job.getSalaryMax() == null ? null : job.getSalaryMax().toPlainString());
        vo.setSalaryRange(salaryRange(job));
        vo.setEducationReq(nvl(job.getEducationReq(), ""));
        vo.setExperienceReq(nvl(job.getExperienceReq(), ""));
        // 纯文本展示：统一换行，前端按空白保留渲染
        vo.setDescription(nvl(job.getDescription(), "").replace("\r\n", "\n"));
        vo.setRequirement(nvl(job.getRequirement(), "").replace("\r\n", "\n"));
        vo.setTags(nvl(job.getTags(), ""));
        vo.setStatus(job.getStatus());
        vo.setPublishAt(job.getPublishAt() == null ? null : job.getPublishAt().toString());
        vo.setCloseAt(job.getCloseAt() == null ? null : job.getCloseAt().toString());
        vo.setSortNo(job.getSortNo());
        vo.setOwnerId(job.getOwnerId() == null ? null : String.valueOf(job.getOwnerId()));
        vo.setApplyCount(applyCount);
        vo.setCreatedAt(job.getCreatedAt() == null ? null : job.getCreatedAt().toString());
        return vo;
    }

    private String salaryRange(JobPost job) {
        if (job.getSalaryMin() == null && job.getSalaryMax() == null) {
            return "面议";
        }
        String unit = "K";
        String min = job.getSalaryMin() == null ? "" : decimalK(job.getSalaryMin());
        String max = job.getSalaryMax() == null ? "" : decimalK(job.getSalaryMax());
        if (min.isEmpty()) {
            return max + unit + "以内";
        }
        if (max.isEmpty()) {
            return min + unit + "起";
        }
        return min + "-" + max + unit;
    }

    private String decimalK(java.math.BigDecimal v) {
        return v.divide(java.math.BigDecimal.valueOf(1000), 1, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private String orgPathOf(Long orgId) {
        if (orgId == null) {
            return "";
        }
        Map<Long, OrgNode> map = new HashMap<>();
        for (OrgNode n : orgMapper.selectList(new LambdaQueryWrapper<OrgNode>())) {
            map.put(n.getId(), n);
        }
        OrgNode node = map.get(orgId);
        if (node == null) {
            return "";
        }
        List<String> names = new ArrayList<>();
        if (node.getPath() != null) {
            for (String part : node.getPath().split("/")) {
                if (part.isBlank()) {
                    continue;
                }
                try {
                    OrgNode p = map.get(Long.valueOf(part));
                    if (p != null) {
                        names.add(nvl(p.getName(), ""));
                    }
                } catch (NumberFormatException ignored) {
                    // 脏 path 跳过
                }
            }
        }
        if (names.isEmpty()) {
            names.add(nvl(node.getName(), ""));
        }
        return String.join(" / ", names);
    }

    private String nvl(String v, String fallback) {
        return v == null ? fallback : v;
    }
}
