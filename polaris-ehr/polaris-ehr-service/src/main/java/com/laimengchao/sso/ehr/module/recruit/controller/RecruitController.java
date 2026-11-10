package com.laimengchao.sso.ehr.module.recruit.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 招聘管道：候选人看板与录用 Offer。
 * <p>recruit:view 只读；recruit:manage 维护候选人、发起/撤销 Offer、转建档。
 */
@RestController
@RequestMapping("/api/ehr/recruits")
@RequiredArgsConstructor
public class RecruitController {

    private final RecruitService recruitService;
    private final InterviewService interviewService;
    private final RecruitCommService recruitCommService;

    @GetMapping("/candidates")
    @Permit(PermissionCodes.RECRUIT_VIEW)
    public R<R.Page<CandidateVO>> candidates(
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "200") int size) {
        return R.ok(recruitService.pageCandidates(stage, keyword, page, Math.min(size, 500)));
    }

    @PostMapping("/candidates")
    @Permit(PermissionCodes.RECRUIT_MANAGE)
    public R<CandidateVO> createCandidate(@Valid @RequestBody CandidateRequest req) {
        return R.ok(recruitService.createCandidate(req));
    }

    @PutMapping("/candidates/{id}")
    @Permit(PermissionCodes.RECRUIT_MANAGE)
    public R<CandidateVO> updateCandidate(@PathVariable Long id, @Valid @RequestBody CandidateRequest req) {
        return R.ok(recruitService.updateCandidate(id, req));
    }

    @PostMapping("/candidates/{id}/eliminate")
    @Permit(PermissionCodes.RECRUIT_MANAGE)
    public R<Void> eliminate(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        recruitService.eliminate(id, body == null ? null : body.get("remark"));
        return R.ok();
    }

    @PostMapping("/candidates/{id}/restore")
    @Permit(PermissionCodes.RECRUIT_MANAGE)
    public R<Void> restore(@PathVariable Long id) {
        recruitService.restore(id);
        return R.ok();
    }

    @GetMapping("/offers")
    @Permit(PermissionCodes.RECRUIT_VIEW)
    public R<R.Page<OfferVO>> offers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(recruitService.pageOffers(status, keyword, page, size));
    }

    @PostMapping("/offers")
    @Permit(PermissionCodes.RECRUIT_MANAGE)
    public R<OfferVO> createOffer(@Valid @RequestBody OfferRequest req) {
        return R.ok(recruitService.createOffer(req));
    }

    @PostMapping("/offers/{id}/cancel")
    @Permit(PermissionCodes.RECRUIT_MANAGE)
    public R<Void> cancelOffer(@PathVariable Long id) {
        recruitService.cancelOffer(id);
        return R.ok();
    }

    @PostMapping("/offers/{id}/confirm")
    @Permit(PermissionCodes.RECRUIT_MANAGE)
    public R<OfferVO> confirm(@PathVariable Long id) {
        return R.ok(recruitService.confirmOffer(id));
    }

    @PostMapping("/offers/{id}/decline")
    @Permit(PermissionCodes.RECRUIT_MANAGE)
    public R<OfferVO> decline(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return R.ok(recruitService.declineOffer(id, body == null ? null : body.get("reason")));
    }

    /** 办理入职：确认后的 Offer 生成待入职员工档案（幂等） */
    @PostMapping("/offers/{id}/onboard")
    @Permit(PermissionCodes.RECRUIT_MANAGE)
    public R<OfferVO> onboard(@PathVariable Long id) {
        return R.ok(recruitService.onboard(id));
    }

    /* ---------------- 多轮面试 ---------------- */

    @GetMapping("/candidates/{id}/interviews")
    @Permit(PermissionCodes.RECRUIT_VIEW)
    public R<List<InterviewVO>> interviews(@PathVariable Long id) {
        return R.ok(interviewService.listByCandidate(id));
    }

    @PostMapping("/candidates/{id}/interviews")
    @Permit(PermissionCodes.RECRUIT_MANAGE)
    public R<List<InterviewVO>> schedule(@PathVariable Long id, @Valid @RequestBody InterviewRequest req) {
        return R.ok(interviewService.schedule(id, req));
    }

    @PostMapping("/interviews/{id}/cancel")
    @Permit(PermissionCodes.RECRUIT_MANAGE)
    public R<Void> cancelInterview(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        interviewService.cancel(id, body == null ? null : body.get("remark"));
        return R.ok();
    }

    /** 面试官视角：本人待反馈的面试（登录即可，服务内只返回本人） */
    @GetMapping("/interviews/my")
    public R<List<InterviewTodoVO>> myInterviews() {
        return R.ok(interviewService.myTodos());
    }

    /** 面试官填写反馈（登录即可，服务内校验记录归属） */
    @PostMapping("/interviews/{id}/feedback")
    public R<Void> interviewFeedback(@PathVariable Long id,
                                     @Valid @RequestBody InterviewFeedbackRequest req) {
        interviewService.submitFeedback(id, req);
        return R.ok();
    }

    /* ---------------- 沟通记录 ---------------- */

    @GetMapping("/candidates/{id}/communications")
    @Permit(PermissionCodes.RECRUIT_VIEW)
    public R<List<CandidateCommVO>> communications(@PathVariable Long id) {
        return R.ok(recruitCommService.list(id));
    }

    @PostMapping("/candidates/{id}/communications")
    @Permit(PermissionCodes.RECRUIT_MANAGE)
    public R<CandidateCommVO> addCommunication(@PathVariable Long id,
                                               @Valid @RequestBody CandidateCommRequest req) {
        return R.ok(recruitCommService.add(id, req));
    }
}
