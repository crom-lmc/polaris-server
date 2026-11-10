-- P8：工作日历年度更新记录表（幂等，可在已有库直接执行）
CREATE TABLE IF NOT EXISTS `ehr_work_calendar_year` (
  `cal_year` int(4) NOT NULL COMMENT '日历年份',
  `applied_at` datetime DEFAULT NULL COMMENT '一键更新执行时间',
  `applied_by` varchar(64) DEFAULT '' COMMENT '执行人账号名',
  PRIMARY KEY (`cal_year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作日历年度更新记录';
