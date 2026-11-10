package com.laimengchao.sso.settings.module.embed.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.settings.module.embed.dto.EmbedPointDTO;
import com.laimengchao.sso.settings.module.embed.service.EmbedPointService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 跨服务读取埋点（X-Internal-Token 准入，不经网关）。
 * 业务方按 key 取 JSON 值；不存在时 data 为 null，调用方走自身兜底。
 */
@RestController
@RequestMapping("/internal/embed-points")
public class InternalEmbedPointController {

    private final EmbedPointService embedPointService;

    public InternalEmbedPointController(EmbedPointService embedPointService) {
        this.embedPointService = embedPointService;
    }

    @GetMapping("/{code}")
    public R<EmbedPointDTO> get(@PathVariable String code) {
        return R.ok(embedPointService.getByCode(code));
    }

    @GetMapping
    public R<List<EmbedPointDTO>> list() {
        return R.ok(embedPointService.listAll());
    }
}
