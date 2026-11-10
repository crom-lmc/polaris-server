package com.laimengchao.sso.settings.module.embed.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.settings.module.embed.dto.EmbedPointCreate;
import com.laimengchao.sso.settings.module.embed.dto.EmbedPointDTO;
import com.laimengchao.sso.settings.module.embed.dto.EmbedPointPatch;
import com.laimengchao.sso.settings.module.embed.entity.EmbedPoint;
import com.laimengchao.sso.settings.module.embed.entity.EmbedPointHistory;
import com.laimengchao.sso.settings.module.embed.mapper.EmbedPointHistoryMapper;
import com.laimengchao.sso.settings.module.embed.mapper.EmbedPointMapper;
import com.laimengchao.sso.settings.module.embed.vo.EmbedPointHistoryVO;
import com.laimengchao.sso.settings.module.embed.vo.EmbedPointVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 埋点配置服务：列表、编辑（带历史快照）、历史查询、跨服务按 key 取值。
 * <p>
 * 值统一存 JSON 文本，编辑时校验可解析，避免下游按 key 取值后解析失败。
 */
@Slf4j
@Service
public class EmbedPointService {

    private final EmbedPointMapper pointMapper;
    private final EmbedPointHistoryMapper historyMapper;
    private final ObjectMapper objectMapper;

    public EmbedPointService(EmbedPointMapper pointMapper, EmbedPointHistoryMapper historyMapper,
                             ObjectMapper objectMapper) {
        this.pointMapper = pointMapper;
        this.historyMapper = historyMapper;
        this.objectMapper = objectMapper;
    }

    public List<EmbedPointVO> list(String keyword) {
        LambdaQueryWrapper<EmbedPoint> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(EmbedPoint::getCode, keyword.trim())
                    .or().like(EmbedPoint::getName, keyword.trim())
                    .or().like(EmbedPoint::getModule, keyword.trim()));
        }
        qw.orderByAsc(EmbedPoint::getSort).orderByAsc(EmbedPoint::getCode);
        return pointMapper.selectList(qw).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Transactional
    public EmbedPointVO create(EmbedPointCreate req) {
        String code = req.getCode() == null ? "" : req.getCode().trim();
        if (code.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "Key 不能为空");
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "描述不能为空");
        }
        assertJson(req.getValue());
        Long exists = pointMapper.selectCount(new LambdaQueryWrapper<EmbedPoint>()
                .eq(EmbedPoint::getCode, code));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.CONFLICT, "Key 已存在");
        }

        EmbedPoint point = new EmbedPoint();
        point.setCode(code);
        point.setAppCode(req.getAppCode() == null ? "" : req.getAppCode().trim());
        point.setModule(StringUtils.hasText(req.getModule()) ? req.getModule().trim() : "未分类");
        point.setName(req.getName().trim());
        point.setValue(req.getValue().trim());
        // 手动新增的不是内置数据
        point.setBuiltIn(0);
        point.setSort(req.getSort() == null ? nextSort() : req.getSort());
        pointMapper.insert(point);
        return toVO(point);
    }

    @Transactional
    public EmbedPointVO update(Long id, EmbedPointPatch patch) {
        EmbedPoint point = requirePoint(id);
        if (patch.getName() != null) {
            if (!StringUtils.hasText(patch.getName())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "描述不能为空");
            }
            point.setName(patch.getName().trim());
        }
        if (patch.getValue() != null) {
            assertJson(patch.getValue());
            point.setValue(patch.getValue().trim());
        }
        if (patch.getAppCode() != null) {
            point.setAppCode(patch.getAppCode().trim());
        }
        if (patch.getModule() != null) {
            point.setModule(patch.getModule().trim());
        }
        if (patch.getSort() != null) {
            point.setSort(patch.getSort());
        }
        pointMapper.updateById(point);

        // 保存成功后写历史快照：历史记录看的是「每次保存后的值」
        EmbedPointHistory history = new EmbedPointHistory();
        history.setPointId(point.getId());
        history.setCode(point.getCode());
        history.setAppCode(point.getAppCode());
        history.setModule(point.getModule());
        history.setName(point.getName());
        history.setValue(point.getValue());
        history.setOperatedBy(SecurityUtils.currentUserId());
        history.setOperatedName(SecurityUtils.currentUsername().orElse(""));
        historyMapper.insert(history);
        return toVO(point);
    }

    public List<EmbedPointHistoryVO> histories(Long pointId) {
        requirePoint(pointId);
        return historyMapper.selectList(new LambdaQueryWrapper<EmbedPointHistory>()
                        .eq(EmbedPointHistory::getPointId, pointId)
                        .orderByDesc(EmbedPointHistory::getCreatedAt)
                        .orderByDesc(EmbedPointHistory::getId))
                .stream().map(this::toHistoryVO).collect(Collectors.toList());
    }

    /** 跨服务按 key 取值，不存在返回 null（调用方走自身兜底） */
    public EmbedPointDTO getByCode(String code) {
        EmbedPoint point = pointMapper.selectOne(new LambdaQueryWrapper<EmbedPoint>()
                .eq(EmbedPoint::getCode, code == null ? "" : code.trim())
                .last("LIMIT 1"));
        return point == null ? null : toDTO(point);
    }

    public List<EmbedPointDTO> listAll() {
        return pointMapper.selectList(new LambdaQueryWrapper<EmbedPoint>()
                        .orderByAsc(EmbedPoint::getSort).orderByAsc(EmbedPoint::getCode))
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    /* ---------------- 内部 ---------------- */

    private EmbedPoint requirePoint(Long id) {
        EmbedPoint point = pointMapper.selectById(id);
        if (point == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "埋点配置不存在");
        }
        return point;
    }

    /** 新增默认排到最后：取当前最大 sort + 1 */
    private int nextSort() {
        EmbedPoint last = pointMapper.selectOne(new LambdaQueryWrapper<EmbedPoint>()
                .orderByDesc(EmbedPoint::getSort)
                .last("LIMIT 1"));
        return last == null || last.getSort() == null ? 1 : last.getSort() + 1;
    }

    /** 埋点值必须是合法 JSON（对象/数组/字符串等均可，只要不是纯文本乱写） */
    private void assertJson(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "埋点值不能为空");
        }
        try {
            objectMapper.readTree(value);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "埋点值必须是合法的 JSON");
        }
    }

    private EmbedPointVO toVO(EmbedPoint p) {
        EmbedPointVO vo = new EmbedPointVO();
        vo.setId(p.getId());
        vo.setCode(p.getCode());
        vo.setAppCode(p.getAppCode());
        vo.setModule(p.getModule());
        vo.setName(p.getName());
        vo.setValue(p.getValue());
        vo.setBuiltIn(p.getBuiltIn());
        vo.setSort(p.getSort());
        vo.setUpdatedAt(p.getUpdatedAt());
        return vo;
    }

    private EmbedPointHistoryVO toHistoryVO(EmbedPointHistory h) {
        EmbedPointHistoryVO vo = new EmbedPointHistoryVO();
        vo.setId(h.getId());
        vo.setPointId(h.getPointId());
        vo.setCode(h.getCode());
        vo.setValue(h.getValue());
        vo.setOperatedBy(h.getOperatedBy());
        vo.setOperatedName(h.getOperatedName());
        vo.setCreatedAt(h.getCreatedAt());
        return vo;
    }

    private EmbedPointDTO toDTO(EmbedPoint p) {
        EmbedPointDTO dto = new EmbedPointDTO();
        dto.setCode(p.getCode());
        dto.setAppCode(p.getAppCode());
        dto.setName(p.getName());
        dto.setValue(p.getValue());
        return dto;
    }
}
