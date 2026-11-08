package com.laimengchao.sso.ehr.module.announcement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.ehr.module.announcement.entity.AnnouncementRead;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface AnnouncementReadMapper extends BaseMapper<AnnouncementRead> {

    /**
     * 幂等记读：依赖 (announcement_id, account_id) 唯一键，重复打开返回 0，
     * 调用方据此决定要不要给冗余的阅读人数 +1。
     */
    @Insert("INSERT IGNORE INTO ehr_announcement_read "
            + "(id, announcement_id, account_id, read_at, deleted, created_at, updated_at) "
            + "VALUES (#{id}, #{announcementId}, #{accountId}, #{readAt}, 0, NOW(), NOW())")
    int insertIgnore(@Param("id") Long id,
                     @Param("announcementId") Long announcementId,
                     @Param("accountId") Long accountId,
                     @Param("readAt") LocalDateTime readAt);
}
