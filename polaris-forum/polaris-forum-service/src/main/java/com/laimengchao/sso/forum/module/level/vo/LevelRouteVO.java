package com.laimengchao.sso.forum.module.level.vo;

import lombok.Data;

import java.util.List;

/** 社区等级晋升路线：全部等级 + 当前用户所处等级与升级进度 */
@Data
public class LevelRouteVO {

    private List<LevelVO> levels;
    /** 当前等级序号，未登录或未达最低门槛为 1 */
    private Integer currentLevel;
    private String currentLevelName;
    private String currentLevelColor;
    /** 下一等级序号，已是最高等级时为 null */
    private Integer nextLevel;
    private String nextLevelName;
    /** 距下一等级还差多少累计经验，已是最高等级为 0 */
    private Integer experienceToNext;
    /** 当前等级到下一等级的进度百分比 0-100，已是最高等级为 100 */
    private Integer progress;
    /** 当前累计经验 */
    private Long totalExperience;
}
