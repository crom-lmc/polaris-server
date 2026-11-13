package com.laimengchao.sso.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

/** 上传目录统一下载策略：只允许图片内联展示，其余文件强制下载。 */
public class DownloadSecurityInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!FileTypeValidator.isImageFilename(request.getRequestURI())) {
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment");
        }
        return true;
    }
}
