package com.laimengchao.sso.forum.module.taxonomy.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.forum.module.forum.mapper.ForumMapper;
import com.laimengchao.sso.forum.module.taxonomy.dto.BoardRequest;
import com.laimengchao.sso.forum.module.taxonomy.dto.TagCreateRequest;
import com.laimengchao.sso.forum.module.taxonomy.dto.TagUpdateRequest;
import com.laimengchao.sso.forum.module.taxonomy.entity.Board;
import com.laimengchao.sso.forum.module.taxonomy.entity.Tag;
import com.laimengchao.sso.forum.module.taxonomy.mapper.TaxonomyMapper;
import com.laimengchao.sso.forum.module.taxonomy.vo.BoardVO;
import com.laimengchao.sso.forum.module.taxonomy.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaxonomyService {

    private static final Pattern BOARD_KEY = Pattern.compile("[a-z][a-z0-9-]{1,31}");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    private final TaxonomyMapper mapper;
    private final ForumMapper forumMapper;
    private final ObjectMapper objectMapper;

    public List<BoardVO> boards() {
        List<Board> all = mapper.listBoards(true);
        Map<String, List<Board>> children = all.stream()
                .filter(row -> row.getParentKey() != null && !row.getParentKey().isBlank())
                .collect(Collectors.groupingBy(Board::getParentKey));
        return all.stream()
                .filter(row -> row.getParentKey() == null || row.getParentKey().isBlank())
                .map(row -> publicBoard(row, children.getOrDefault(row.getBoardKey(), List.of())))
                .toList();
    }

    public List<TagVO> tags(String boardKey) {
        requireBoard(boardKey, false);
        return mapper.listTags(boardKey, true).stream()
                .map(row -> tagView(row, tagCounts(boardKey))).toList();
    }

    public List<BoardVO> adminBoards() {
        return mapper.listBoards(false).stream().map(this::adminBoard).toList();
    }

    public List<TagVO> adminTags(String boardKey) {
        if (boardKey != null && !boardKey.isBlank()) requireBoard(boardKey, false);
        String board = boardKey == null ? "" : boardKey.trim();
        Map<String, Map<String, Integer>> counts = new HashMap<>();
        return mapper.listTags(board, false).stream().map(row -> {
            String key = row.getBoardKey();
            return tagView(row, counts.computeIfAbsent(key, this::tagCounts));
        }).toList();
    }

    @Transactional
    public BoardVO createBoard(BoardRequest body) {
        String key = text(body.getKey()).trim();
        if (!BOARD_KEY.matcher(key).matches()) throw param("板块标识应为 2-32 位小写字母、数字或连字符，且以字母开头");
        String name = requiredText(body.getName(), "板块名称", 80);
        String parentKey = text(body.getParentKey()).trim();
        if (!parentKey.isBlank()) {
            Board parent = mapper.findBoard(parentKey);
            if (parent == null) throw param("父级板块不存在");
            if (parent.getParentKey() != null && !parent.getParentKey().isBlank()) {
                throw param("二级板块下不能再创建子板块");
            }
        } else {
            parentKey = "";
        }
        Board values = new Board();
        values.setBoardKey(key);
        values.setParentKey(parentKey);
        values.setName(name);
        values.setDescription(limitedText(body.getDescription(), "板块描述", 300));
        values.setColor(color(body.getColor()));
        values.setSortNo(intValue(body.getSortNo()));
        values.setEnabled(body.isEnabledPresent() ? bool(body.getEnabled(), "enabled") : true);
        try {
            mapper.insertBoard(values);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.CONFLICT, "板块标识已存在");
        }
        return adminBoard(mapper.findBoard(key));
    }

    public BoardVO updateBoard(String key, BoardRequest body) {
        Board current = mapper.findBoard(key);
        if (current == null) throw new BizException(ErrorCode.NOT_FOUND, "板块不存在");
        Board values = new Board();
        values.setBoardKey(key);
        values.setName(body.isNamePresent() ? requiredText(body.getName(), "板块名称", 80) : current.getName());
        values.setDescription(body.isDescriptionPresent()
                ? limitedText(body.getDescription(), "板块描述", 300) : current.getDescription());
        values.setColor(body.isColorPresent() ? color(body.getColor()) : current.getColor());
        values.setSortNo(body.isSortNoPresent() ? intValue(body.getSortNo()) : intValue(current.getSortNo()));
        values.setEnabled(body.isEnabledPresent() ? bool(body.getEnabled(), "enabled")
                : Boolean.TRUE.equals(current.getEnabled()));
        mapper.updateBoard(values);
        return adminBoard(mapper.findBoard(key));
    }

    @Transactional
    public TagVO createTag(TagCreateRequest body) {
        String boardKey = text(body.getBoard()).trim();
        if (boardKey.isBlank()) boardKey = text(body.getBoardKey()).trim();
        requireBoard(boardKey, false);
        long id = IdWorker.getId();
        Tag values = new Tag();
        values.setId(id);
        values.setBoardKey(boardKey);
        values.setName(tagName(body.getName()));
        values.setSortNo(intValue(body.getSortNo()));
        values.setEnabled(body.isEnabledPresent() ? bool(body.getEnabled(), "enabled") : true);
        try {
            mapper.insertTag(values);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.CONFLICT, "该板块下标签已存在");
        }
        return tagView(mapper.findTag(id), tagCounts(boardKey));
    }

    public TagVO updateTag(long id, TagUpdateRequest body) {
        if (!body.getUnsupportedFields().isEmpty()) {
            throw param("标签仅支持修改 sortNo 和 enabled");
        }
        Tag current = mapper.findTag(id);
        if (current == null) throw new BizException(ErrorCode.NOT_FOUND, "标签不存在");
        Tag values = new Tag();
        values.setId(id);
        values.setSortNo(body.isSortNoPresent() ? intValue(body.getSortNo()) : intValue(current.getSortNo()));
        values.setEnabled(body.isEnabledPresent() ? bool(body.getEnabled(), "enabled")
                : Boolean.TRUE.equals(current.getEnabled()));
        mapper.updateTag(values);
        return tagView(mapper.findTag(id), tagCounts(current.getBoardKey()));
    }

    public Board requireBoard(String boardKey, boolean enabledOnly) {
        Board board = boardKey == null ? null : mapper.findBoard(boardKey.trim());
        if (board == null || enabledOnly && !Boolean.TRUE.equals(board.getEnabled())) {
            throw param(enabledOnly ? "板块不存在或已停用" : "不支持的板块");
        }
        return board;
    }

    public void registerTags(String boardKey, List<String> tags) {
        for (String tag : tags) mapper.registerTag(IdWorker.getId(), boardKey, tag);
    }

    public void preparePublishTags(String boardKey, List<String> tags) {
        for (String tag : tags) {
            Tag existing = mapper.findTagByName(boardKey, tag);
            if (existing != null && !Boolean.TRUE.equals(existing.getEnabled())) {
                throw param("标签「" + tag + "」已停用，请移除后再发布");
            }
            if (existing == null) mapper.registerTag(IdWorker.getId(), boardKey, tag);
        }
    }

    private BoardVO publicBoard(Board row) {
        return publicBoard(row, List.of());
    }

    private BoardVO publicBoard(Board row, List<Board> children) {
        BoardVO view = new BoardVO();
        String key = row.getBoardKey();
        view.setName(row.getName());
        view.setDescription(row.getDescription());
        view.setColor(row.getColor());
        view.setSortNo(row.getSortNo());
        view.setKey(key);
        view.setParentKey(row.getParentKey() == null ? "" : row.getParentKey());
        view.setCount(forumMapper.countBoard(key));
        Map<String, Integer> counts = tagCounts(key);
        view.setTags(mapper.listTags(key, true).stream().map(tag -> tagView(tag, counts)).toList());
        // 二级板块归属一级板块，帖子 board_key 仍为一级 key、二级栏目名写入 tags，故用父级 counts 统计使用次数
        view.setChildren(children.stream()
                .map(child -> childBoardView(child, counts))
                .toList());
        return view;
    }

    private BoardVO childBoardView(Board row, Map<String, Integer> counts) {
        BoardVO view = new BoardVO();
        view.setKey(row.getBoardKey());
        view.setParentKey(row.getParentKey() == null ? "" : row.getParentKey());
        view.setName(row.getName());
        view.setDescription(row.getDescription());
        view.setColor(row.getColor());
        view.setSortNo(row.getSortNo());
        view.setEnabled(Boolean.TRUE.equals(row.getEnabled()));
        view.setCount(counts.getOrDefault(row.getName(), 0));
        return view;
    }

    private BoardVO adminBoard(Board row) {
        BoardVO view = publicBoard(row);
        view.setEnabled(Boolean.TRUE.equals(row.getEnabled()));
        view.setCreatedAt(row.getCreatedAt());
        view.setUpdatedAt(row.getUpdatedAt());
        return view;
    }

    private TagVO tagView(Tag row, Map<String, Integer> counts) {
        TagVO view = new TagVO();
        view.setId(row.getId());
        view.setBoardKey(row.getBoardKey());
        view.setName(row.getName());
        view.setSortNo(row.getSortNo());
        view.setEnabled(Boolean.TRUE.equals(row.getEnabled()));
        view.setCount(counts.getOrDefault(row.getName(), 0));
        return view;
    }

    private Map<String, Integer> tagCounts(String boardKey) {
        Map<String, Integer> counts = new HashMap<>();
        for (String json : mapper.listThreadTags(boardKey)) {
            try {
                List<String> tags = objectMapper.readValue(json, new TypeReference<>() {});
                for (String tag : tags) counts.merge(tag, 1, Integer::sum);
            } catch (Exception ignored) {
                // 历史非法 JSON 不参与标签计数。
            }
        }
        return counts;
    }

    private String tagName(Object value) {
        String name = text(value).trim().replace("#", "");
        if (name.isBlank() || name.length() > 20) throw param("标签长度应为 1-20 个字符");
        return name;
    }

    private String requiredText(Object value, String label, int maxLength) {
        String text = limitedText(value, label, maxLength);
        if (text.isBlank()) throw param(label + "不能为空");
        return text;
    }

    private String limitedText(Object value, String label, int maxLength) {
        String text = text(value).trim();
        if (text.length() > maxLength) throw param(label + "不能超过 " + maxLength + " 个字符");
        return text;
    }

    private String color(Object value) {
        String color = text(value).trim();
        if (color.isBlank()) return "#5b67f1";
        if (!COLOR.matcher(color).matches()) throw param("板块颜色应为 6 位十六进制颜色值");
        return color;
    }

    private boolean bool(Object value, String name) {
        if (value instanceof Boolean bool) return bool;
        if ("true".equalsIgnoreCase(text(value)) || "false".equalsIgnoreCase(text(value))) {
            return Boolean.parseBoolean(text(value));
        }
        throw param(name + " 必须为布尔值");
    }

    private BizException param(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null || String.valueOf(value).isBlank() ? 0 : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException e) { return 0; }
    }
}
