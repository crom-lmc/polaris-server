package com.laimengchao.sso.common;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果封装。与前端 antd-vue 的 Table 分页字段对齐：records/total/page/size。
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> records;
    private long total;
    private long page;
    private long size;

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        PageResult<T> r = new PageResult<>();
        r.setRecords(records);
        r.setTotal(total);
        r.setPage(page);
        r.setSize(size);
        return r;
    }
}
