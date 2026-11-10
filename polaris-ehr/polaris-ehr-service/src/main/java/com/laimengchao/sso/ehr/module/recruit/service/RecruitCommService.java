package com.laimengchao.sso.ehr.module.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.ehr.common.BizNoService;
import com.laimengchao.sso.ehr.module.recruit.dto.CandidateCommRequest;
import com.laimengchao.sso.ehr.module.recruit.entity.Candidate;
import com.laimengchao.sso.ehr.module.recruit.entity.CandidateComm;
import com.laimengchao.sso.ehr.module.recruit.mapper.CandidateCommMapper;
import com.laimengchao.sso.ehr.module.recruit.mapper.CandidateMapper;
import com.laimengchao.sso.ehr.module.recruit.vo.CandidateCommVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** HR 与候选人的沟通留痕（期望薪资、到岗时间、电话沟通等） */
@Service
@RequiredArgsConstructor
public class RecruitCommService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FMT_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CandidateCommMapper commMapper;
    private final CandidateMapper candidateMapper;
    private final BizNoService bizNoService;

    public List<CandidateCommVO> list(Long candidateId) {
        requireCandidate(candidateId);
        return commMapper.selectList(new LambdaQueryWrapper<CandidateComm>()
                        .eq(CandidateComm::getCandidateId, candidateId)
                        .orderByDesc(CandidateComm::getOccurredAt)
                        .orderByDesc(CandidateComm::getId))
                .stream().map(this::toVO).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public CandidateCommVO add(Long candidateId, CandidateCommRequest req) {
        requireCandidate(candidateId);
        SecurityUser user = SecurityUtils.currentUser()
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        CandidateComm comm = new CandidateComm();
        comm.setCommNo(bizNoService.next("ehr_candidate_comm"));
        comm.setCandidateId(candidateId);
        comm.setType(req.getType() == null || req.getType().isBlank()
                ? "PHONE" : req.getType().trim().toUpperCase());
        comm.setContent(req.getContent().trim());
        comm.setExpectedSalary(req.getExpectedSalary());
        comm.setOccurredAt(parseDateTime(req.getOccurredAt()));
        comm.setOperatorId(user.getAccountId());
        comm.setOperatorName(user.getUsername() == null ? "" : user.getUsername());
        commMapper.insert(comm);
        return toVO(comm);
    }

    /** 批量取每位候选人最近一条沟通记录，供看板卡片展示 */
    public Map<Long, CandidateComm> lastCommMap(List<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, CandidateComm> map = new HashMap<>();
        for (CandidateComm comm : commMapper.selectList(new LambdaQueryWrapper<CandidateComm>()
                .in(CandidateComm::getCandidateId, candidateIds)
                .orderByDesc(CandidateComm::getOccurredAt)
                .orderByDesc(CandidateComm::getId))) {
            map.putIfAbsent(comm.getCandidateId(), comm);
        }
        return map;
    }

    private void requireCandidate(Long candidateId) {
        Candidate c = candidateId == null ? null : candidateMapper.selectById(candidateId);
        if (c == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "候选人不存在");
        }
    }

    private CandidateCommVO toVO(CandidateComm comm) {
        CandidateCommVO vo = new CandidateCommVO();
        vo.setId(String.valueOf(comm.getId()));
        vo.setCandidateId(String.valueOf(comm.getCandidateId()));
        vo.setType(comm.getType());
        vo.setContent(comm.getContent());
        vo.setExpectedSalary(comm.getExpectedSalary() == null ? null : comm.getExpectedSalary().toPlainString());
        vo.setOccurredAt(comm.getOccurredAt() == null ? null : comm.getOccurredAt().format(FMT));
        vo.setOperatorName(comm.getOperatorName());
        vo.setCreatedAt(comm.getCreatedAt() == null ? null : comm.getCreatedAt().format(FMT));
        return vo;
    }

    private LocalDateTime parseDateTime(String text) {
        if (text == null || text.isBlank()) {
            return LocalDateTime.now();
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
            return LocalDateTime.parse(s, s.length() > 16 ? FMT : FMT_MINUTE);
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "沟通时间格式应为 yyyy-MM-dd HH:mm");
        }
    }
}
