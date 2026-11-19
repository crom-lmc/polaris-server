package com.laimengchao.sso.forum.module.attachment.mapper;

import com.laimengchao.sso.forum.module.attachment.entity.Attachment;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AttachmentMapper {

    int insert(Attachment values);

    Attachment findById(@Param("id") long id);

    Attachment findByIdForUpdate(@Param("id") long id);

    Attachment findPublicById(@Param("id") long id);

    List<Attachment> listByThread(@Param("threadId") long threadId);

    List<Attachment> listByDraft(@Param("draftId") long draftId);

    int clearDraft(@Param("draftId") long draftId);

    int bindDraft(@Param("id") long id, @Param("draftId") long draftId, @Param("accountId") long accountId);

    int bindDirectThread(@Param("id") long id, @Param("threadId") long threadId, @Param("accountId") long accountId);

    int bindDraftThread(@Param("id") long id, @Param("draftId") long draftId,
                        @Param("threadId") long threadId, @Param("accountId") long accountId);

    int clearProfile(@Param("accountId") long accountId, @Param("usageType") String usageType,
                     @Param("keepId") Long keepId);

    int bindProfile(@Param("id") long id, @Param("accountId") long accountId, @Param("usageType") String usageType);
}
