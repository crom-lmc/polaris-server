package com.laimengchao.sso.common;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 所有实体的公共基类：雪花主键 + 时间戳 + 逻辑删除。
 * 各服务按需继承，@TableLogic 默认不走物理删除。
 */
@Data
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 雪花ID，序列化成字符串。
     * <p>
     * 19 位数字超过 JS 的 Number.MAX_SAFE_INTEGER(2^53-1)，前端 JSON.parse 会丢精度
     * （实测 2116193763304861697 变成 ...700），再拿回来查库就是 404。
     * 转成字符串后前端原样回传即可。
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted = 0;
}
