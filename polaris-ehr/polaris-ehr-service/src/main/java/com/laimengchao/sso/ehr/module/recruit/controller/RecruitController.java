package com.laimengchao.sso.ehr.module.recruit.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.ehr.module.recruit.dto.CandidateCommRequest;
import com.laimengchao.sso.ehr.module.recruit.dto.CandidateRequest;
import com.laimengchao.sso.ehr.module.recruit.dto.InterviewFeedbackRequest;
import com.laimengchao.sso.ehr.module.recruit.dto.InterviewRequest;
import com.laimengchao.sso.ehr.module.recruit.dto.OfferRequest;
import com.laimengchao.sso.ehr.module.recruit.service.InterviewService;
import com.laimengchao.sso.ehr.module.recruit.service.RecruitCommService;
import com.laimengchao.sso.ehr.module.recruit.service.RecruitService;
import com.laimengchao.sso.ehr.module.recruit.vo.CandidateCommVO;
import com.laimengchao.sso.ehr.module.recruit.vo.CandidateVO;
import com.laimengchao.sso.ehr.module.recruit.vo.InterviewTodoVO;
import com.laimengchao.sso.ehr.module.recruit.vo.InterviewVO;
import com.laimengchao.sso.ehr.module.recruit.vo.OfferVO;
import com.laimengchao.sso.ehr.security.Permit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 招聘管道：候选人看板、录用 Offer 与我的面试按页签和按钮分别鉴权。
 */
@RestController
@RequestMapping("/api/ehr/recruits")
@RequiredArgsConstructor
public class RecruitController {

    private final RecruitService recruitService;
    private final InterviewService interviewService;
    private final RecruitCommService recruitCommService;

    @GetMapping("/candidates")
    @Permit({PermissionCodes.RECRUIT_BOARD, PermissionCodes.RECRUIT_OFFERS_CREATE})
    public R<R.Page<CandidateVO>> candidates(
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "200") int size) {
        return R.ok(recruitService.pageCandidates(stage, keyword, page, Math.min(size, 500)));
    }

    @PostMapping("/candidates")
    @Permit(PermissionCodes.RECRUIT_BOARD_CANDIDATE_CREATE)
    public R<CandidateVO> createCandidate(@Valid @RequestBody CandidateRequest req) {
        return R.ok(recruitService.createCandidate(req));
    }

    @PutMapping("/candidates/{id}")
    @Permit(PermissionCodes.RECRUIT_BOARD_CANDIDATE_EDIT)
    public R<CandidateVO> updateCandidate(@PathVariable Long id, @Valid @RequestBody CandidateRequest req) {
        return R.ok(recruitService.updateCandidate(id, req));
    }

    @PostMapping("/candidates/{id}/eliminate")
    @Permit(PermissionCodes.RECRUIT_BOARD_CANDIDATE_ELIMINATE)
    public R<Void> eliminate(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        recruitService.eliminate(id, body == null ? null : body.get("remark"));
        return R.ok();
    }

    @PostMapping("/candidates/{id}/restore")
    @Permit(PermissionCodes.RECRUIT_BOARD_CANDIDATE_RESTORE)
    public R<Void> restore(@PathVariable Long id) {
        recruitService.restore(id);
        return R.ok();
    }

    @GetMapping("/candidates/{id}/resume/download")
    @Permit(PermissionCodes.RECRUIT_BOARD_RESUME_DOWNLOAD)
    public ResponseEntity<Resource> downloadResume(@PathVariable Long id) {
        RecruitService.ResumeDownload resume = recruitService.downloadResume(id);
        String name = URLEncoder.encode(resume.fileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + name)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resume.resource());
    }

    @GetMapping("/offers")
    @Permit(PermissionCodes.RECRUIT_OFFERS)
    public R<R.Page<OfferVO>> offers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(recruitService.pageOffers(status, keyword, page, size));
    }

    @PostMapping("/offers")
    @Permit({PermissionCodes.RECRUIT_BOARD_OFFER_CREATE, PermissionCodes.RECRUIT_OFFERS_CREATE})
    public R<OfferVO> createOffer(@Valid @RequestBody OfferRequest req) {
        return R.ok(recruitService.createOffer(req));
    }

    @PostMapping("/offers/{id}/cancel")
    @Permit(PermissionCodes.RECRUIT_OFFERS_CANCEL)
    public R<Void> cancelOffer(@PathVariable Long id) {
        recruitService.cancelOffer(id);
        return R.ok();
    }

    @PostMapping("/offers/{id}/confirm")
    @Permit(PermissionCodes.RECRUIT_OFFERS_CONFIRM)
    public R<OfferVO> confirm(@PathVariable Long id) {
        return R.ok(recruitService.confirmOffer(id));
    }

    @PostMapping("/offers/{id}/decline")
    @Permit(PermissionCodes.RECRUIT_OFFERS_DECLINE)
    public R<OfferVO> decline(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return R.ok(recruitService.declineOffer(id, body == null ? null : body.get("reason")));
    }

    /** 办理入职：确认后的 Offer 生成待入职员工档案（幂等） */
    @PostMapping("/offers/{id}/onboard")
    @Permit(PermissionCodes.RECRUIT_OFFERS_ONBOARD)
    public R<OfferVO> onboard(@PathVariable Long id) {
        return R.ok(recruitService.onboard(id));
    }

    /* ---------------- 多轮面试 ---------------- */

    @GetMapping("/candidates/{id}/interviews")
    @Permit(PermissionCodes.RECRUIT_BOARD)
    public R<List<InterviewVO>> interviews(@PathVariable Long id) {
        return R.ok(interviewService.listByCandidate(id));
    }

    @PostMapping("/candidates/{id}/interviews")
    @Permit(PermissionCodes.RECRUIT_BOARD_INTERVIEW_SCHEDULE)
    public R<List<InterviewVO>> schedule(@PathVariable Long id, @Valid @RequestBody InterviewRequest req) {
        return R.ok(interviewService.schedule(id, req));
    }

    @PostMapping("/interviews/{id}/cancel")
    @Permit(PermissionCodes.RECRUIT_BOARD_INTERVIEW_CANCEL)
    public R<Void> cancelInterview(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        interviewService.cancel(id, body == null ? null : body.get("remark"));
        return R.ok();
    }

    /** 面试官视角：本人待反馈的面试（服务内只返回本人） */
    @GetMapping("/interviews/my")
    @Permit(PermissionCodes.RECRUIT_INTERVIEWS)
    public R<List<InterviewTodoVO>> myInterviews() {
        return R.ok(interviewService.myTodos());
    }

    /** 面试官填写反馈（服务内校验记录归属） */
    @PostMapping("/interviews/{id}/feedback")
    @Permit(PermissionCodes.RECRUIT_INTERVIEWS_FEEDBACK)
    public R<Void> interviewFeedback(@PathVariable Long id,
                                     @Valid @RequestBody InterviewFeedbackRequest req) {
        interviewService.submitFeedback(id, req);
        return R.ok();
    }

    /* ---------------- 沟通记录 ---------------- */

    @GetMapping("/candidates/{id}/communications")
    @Permit(PermissionCodes.RECRUIT_BOARD)
    public R<List<CandidateCommVO>> communications(@PathVariable Long id) {
        return R.ok(recruitCommService.list(id));
    }

    @PostMapping("/candidates/{id}/communications")
    @Permit(PermissionCodes.RECRUIT_BOARD_COMMUNICATION_ADD)
    public R<CandidateCommVO> addCommunication(@PathVariable Long id,
                                               @Valid @RequestBody CandidateCommRequest req) {
        return R.ok(recruitCommService.add(id, req));
    }
}
