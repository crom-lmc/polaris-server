package com.laimengchao.sso.forum.module.admin.vo;

import lombok.Data;

import java.util.List;

/** 管理端敏感词分页结果：列表 + 总数，避免一次返回全部敏感词拖慢管理端。 */
@Data
public class SensitiveWordPageVO {

    private List<SensitiveWordVO> records;
    private long total;
    private int page;
    private int size;
}
