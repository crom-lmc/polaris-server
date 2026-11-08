package com.laimengchao.sso.ehr.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.module.approval.entity.HandoverItem;
import com.laimengchao.sso.ehr.module.approval.mapper.HandoverItemMapper;
import com.laimengchao.sso.ehr.module.approval.vo.HandoverItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作交接项目：落清单、读清单、逐项确认。
 * <p>
 * 这里只管「交接项」本身；「全部确认后把申请单的 handoverStatus 置 1」由
 * {@link ResignApplyService} 收口，避免审批状态散落在两个 Service 里。
 */
@Service
@RequiredArgsConstructor
public class HandoverService {

    /** 一次最多 20 项：再多就不像交接清单，像待办列表了 */
    public static final int MAX_ITEMS = 20;
    public static final int MAX_CONTENT = 200;

    private static final Map<Integer, String> STATUS_NAMES = Map.of(
            0, "待确认",
            1, "已确认");

    private final HandoverItemMapper itemMapper;

    /** 确认结果：已确认数 / 总数 / 是否已全部确认 */
    public record Progress(int done, int total, boolean allDone) {
        public String text() {
            return total == 0 ? "无需交接" : done + "/" + total + " 已确认";
        }
    }

    /* ---------------- 写 ---------------- */

    /**
     * 清洗并校验交接内容：去空白、去空行、去重，最多 {@link #MAX_ITEMS} 条。
     * 校验放在写库之前，免得半截数据落库。
     */
    public List<String> normalize(Collection<String> raw) {
        if (raw == null) {
            return List.of();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : raw) {
            if (s == null) {
                continue;
            }
            String v = s.trim();
            if (v.isEmpty()) {
                continue;
            }
            if (v.length() > MAX_CONTENT) {
                throw new BizException(EhrErrorCode.HANDOVER_ITEM_INVALID,
                        "单条交接内容不能超过 " + MAX_CONTENT + " 字：" + v.substring(0, 20) + "…");
            }
            set.add(v);
        }
        if (set.size() > MAX_ITEMS) {
            throw new BizException(EhrErrorCode.HANDOVER_ITEM_INVALID,
                    "交接项目最多 " + MAX_ITEMS + " 条，当前 " + set.size() + " 条");
        }
        return new ArrayList<>(set);
    }

    /** 落清单：先清空该单旧项，再按顺序插入（重提时不会有残留） */
    @Transactional(rollbackFor = Exception.class)
    public void replaceItems(Long applyId, List<String> contents) {
        replaceItems(applyId, null, contents);
    }

    /**
     * 落清单（带所属员工）：{@code employeeId} 是被交接的员工，冗余写到每一行，
     * 这样不 join 申请单也能按人查出全部交接项。
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceItems(Long applyId, Long employeeId, List<String> contents) {
        itemMapper.delete(new LambdaQueryWrapper<HandoverItem>().eq(HandoverItem::getApplyId, applyId));
        int seq = 1;
        for (String content : contents) {
            HandoverItem item = new HandoverItem();
            item.setApplyId(applyId);
            item.setEmployeeId(employeeId);
            item.setSeq(seq++);
            item.setContent(content);
            item.setStatus(0);
            item.setNote("");
            itemMapper.insert(item);
        }
    }

    /**
     * 交接人确认：itemIds 为空表示全部确认，否则只确认指定项（已确认的跳过，幂等）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Progress confirm(Long applyId, List<Long> itemIds, String note, Long accountId) {
        List<HandoverItem> items = listByApply(applyId);
        if (items.isEmpty()) {
            return new Progress(0, 0, true);
        }
        boolean all = itemIds == null || itemIds.isEmpty();
        String trimmed = note == null ? "" : note.trim();
        for (HandoverItem item : items) {
            if (!all && !itemIds.contains(item.getId())) {
                continue;
            }
            if (item.getStatus() != null && item.getStatus() == 1) {
                continue;
            }
            item.setStatus(1);
            item.setConfirmerAccountId(accountId);
            item.setConfirmedAt(LocalDateTime.now());
            if (!trimmed.isEmpty()) {
                item.setNote(trimmed);
            }
            itemMapper.updateById(item);
        }
        return progressOf(listByApply(applyId));
    }

    /* ---------------- 读 ---------------- */

    public List<HandoverItem> listByApply(Long applyId) {
        if (applyId == null) {
            return List.of();
        }
        return itemMapper.selectList(new LambdaQueryWrapper<HandoverItem>()
                .eq(HandoverItem::getApplyId, applyId)
                .orderByAsc(HandoverItem::getSeq)
                .orderByAsc(HandoverItem::getId));
    }

    /** 批量取：审批中心一屏几十张单，逐张查会 N+1 */
    public Map<Long, List<HandoverItem>> mapByApplies(Collection<Long> applyIds) {
        if (applyIds == null || applyIds.isEmpty()) {
            return Map.of();
        }
        List<HandoverItem> all = itemMapper.selectList(new LambdaQueryWrapper<HandoverItem>()
                .in(HandoverItem::getApplyId, applyIds)
                .orderByAsc(HandoverItem::getSeq)
                .orderByAsc(HandoverItem::getId));
        Map<Long, List<HandoverItem>> map = new HashMap<>();
        for (HandoverItem i : all) {
            map.computeIfAbsent(i.getApplyId(), k -> new ArrayList<>()).add(i);
        }
        return map;
    }

    public static Progress progressOf(List<HandoverItem> items) {
        if (items == null || items.isEmpty()) {
            return new Progress(0, 0, true);
        }
        int done = (int) items.stream().filter(i -> i.getStatus() != null && i.getStatus() == 1).count();
        return new Progress(done, items.size(), done == items.size());
    }

    public static List<HandoverItemVO> toVOList(List<HandoverItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream().map(i -> {
            HandoverItemVO vo = new HandoverItemVO();
            vo.setId(i.getId());
            vo.setEmployeeId(i.getEmployeeId());
            vo.setApplyId(i.getApplyId());
            vo.setSeq(i.getSeq());
            vo.setContent(i.getContent());
            vo.setStatus(i.getStatus());
            vo.setStatusName(STATUS_NAMES.getOrDefault(i.getStatus(), "未知"));
            vo.setNote(i.getNote());
            vo.setConfirmedAt(i.getConfirmedAt() == null ? null
                    : i.getConfirmedAt().toString().replace('T', ' '));
            return vo;
        }).collect(Collectors.toList());
    }

    /** 多个申请单的进度文本，供列表页展示 */
    public Map<Long, String> progressTextMap(Collection<Long> applyIds) {
        Map<Long, List<HandoverItem>> map = mapByApplies(applyIds);
        Map<Long, String> out = new HashMap<>();
        for (Map.Entry<Long, List<HandoverItem>> e : map.entrySet()) {
            out.put(e.getKey(), progressOf(e.getValue()).text());
        }
        return out;
    }
}
