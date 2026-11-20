package com.laimengchao.sso.forum.module.level.dto;

import lombok.Data;

/** 根据累计经验计算出的当前等级与升级进度 */
@Data
public class LevelProgress {

    private Integer level;
    private String levelName;
    private String levelColor;
    private Integer nextLevel;
    private String nextLevelName;
    /** 距下一等级还差的累计经验，已是最高等级为 0 */
    private Integer experienceToNext;
    /** 当前等级到下一等级的进度 0-100，已是最高等级为 100 */
    private Integer progress;
}
