package com.laimengchao.sso.forum.module.member.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class MemberProfileUpdateRequest {

    private Object nickname;
    private Object bio;
    private Object location;
    private Object website;
    private Object avatarAttachmentId;
    private Object coverAttachmentId;

    @JsonIgnore
    private boolean nicknamePresent;
    @JsonIgnore
    private boolean bioPresent;
    @JsonIgnore
    private boolean locationPresent;
    @JsonIgnore
    private boolean websitePresent;
    @JsonIgnore
    private boolean avatarAttachmentIdPresent;
    @JsonIgnore
    private boolean coverAttachmentIdPresent;

    public void setNickname(Object nickname) {
        this.nickname = nickname;
        this.nicknamePresent = true;
    }

    public void setBio(Object bio) {
        this.bio = bio;
        this.bioPresent = true;
    }

    public void setLocation(Object location) {
        this.location = location;
        this.locationPresent = true;
    }

    public void setWebsite(Object website) {
        this.website = website;
        this.websitePresent = true;
    }

    public void setAvatarAttachmentId(Object avatarAttachmentId) {
        this.avatarAttachmentId = avatarAttachmentId;
        this.avatarAttachmentIdPresent = true;
    }

    public void setCoverAttachmentId(Object coverAttachmentId) {
        this.coverAttachmentId = coverAttachmentId;
        this.coverAttachmentIdPresent = true;
    }
}
