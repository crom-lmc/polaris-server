package com.laimengchao.sso.ehr.module.importexport.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 批量导入预览 / 结果 / 任务详情视图。 */
public final class ImportVos {

    private ImportVos() {
    }

    @Data
    public static class RowError {
        /** 工作表名，如「员工基础信息」 */
        private String sheet;
        /** Excel 行号（从 1 开始，含表头） */
        private Integer row;
        private String name;
        private String message;
    }

    @Data
    public static class Preview {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long taskId;
        private String fileName;
        private Integer total;
        private Integer validCount;
        private Integer invalidCount;
        /** 最多返回前 500 条错误，完整明细在确认后生成错误文件 */
        private List<RowError> errors = new ArrayList<>();
    }

    @Data
    public static class Result {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long taskId;
        private String status;
        private Integer total;
        private Integer success;
        private Integer failed;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long errorFileId;
        private List<RowError> errors = new ArrayList<>();
    }

    @Data
    public static class TaskView {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String type;
        private String fileName;
        private String status;
        private Integer total;
        private Integer validCount;
        private Integer invalidCount;
        private Integer success;
        private Integer failed;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long errorFileId;
        private String createdAt;
    }
}
