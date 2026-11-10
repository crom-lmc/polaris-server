package com.laimengchao.sso.ehr.module.recruit.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.recruit.service.PublicRecruitService;
import com.laimengchao.sso.ehr.module.recruit.vo.ApplyProgressVO;
import com.laimengchao.sso.ehr.module.recruit.vo.JobPostVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 门户招聘公开接口：免登录（网关白名单 + EHR SecurityConfig 放行）。
 * 所有写操作在 service 内做隐私同意、防刷、去重校验。
 */
@RestController
@RequestMapping("/api/ehr/public/recruit")
@RequiredArgsConstructor
public class PublicRecruitController {

    private final PublicRecruitService publicRecruitService;

    @GetMapping("/jobs")
    public R<R.Page<JobPostVO>> jobs(@RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String jobType,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return R.ok(publicRecruitService.jobs(keyword, jobType, page, size));
    }

    @GetMapping("/jobs/{id}")
    public R<JobPostVO> job(@PathVariable Long id) {
        return R.ok(publicRecruitService.job(id));
    }

    @PostMapping("/jobs/{id}/apply")
    public R<ApplyProgressVO> apply(@PathVariable Long id,
                                    @RequestParam("resume") MultipartFile resume,
                                    @RequestParam String name,
                                    @RequestParam String mobile,
                                    @RequestParam(required = false) String email,
                                    @RequestParam(required = false) String education,
                                    @RequestParam(required = false) String school,
                                    @RequestParam(required = false) String major,
                                    @RequestParam(required = false) String workYears,
                                    @RequestParam(required = false) String currentCompany,
                                    @RequestParam(required = false) String expectedSalary,
                                    @RequestParam(required = false) String remark,
                                    @RequestParam(defaultValue = "false") Boolean consent,
                                    HttpServletRequest request) {
        return R.ok(publicRecruitService.apply(id, resume, name, mobile, email, education, school,
                major, workYears, currentCompany, expectedSalary, remark, consent, clientIp(request)));
    }

    @GetMapping("/progress")
    public R<ApplyProgressVO> progress(@RequestParam(required = false) String trackCode,
                                       @RequestParam String mobile,
                                       HttpServletRequest request) {
        return R.ok(publicRecruitService.progress(trackCode, mobile, clientIp(request)));
    }

    /** 网关会带 X-Forwarded-For，取第一段作为真实客户端 IP */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
