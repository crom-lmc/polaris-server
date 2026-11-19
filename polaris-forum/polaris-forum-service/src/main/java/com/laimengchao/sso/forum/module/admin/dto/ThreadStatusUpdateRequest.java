package com.laimengchao.sso.forum.module.admin.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class ThreadStatusUpdateRequest {

    private Object deleted;
    private Object pinned;
    private Object solved;
    private Object recommended;

    @JsonIgnore
    private boolean deletedPresent;
    @JsonIgnore
    private boolean pinnedPresent;
    @JsonIgnore
    private boolean solvedPresent;
    @JsonIgnore
    private boolean recommendedPresent;
    @JsonIgnore
    private Set<String> unsupportedFields = new HashSet<>();

    public void setDeleted(Object deleted) {
        this.deleted = deleted;
        this.deletedPresent = true;
    }

    public void setPinned(Object pinned) {
        this.pinned = pinned;
        this.pinnedPresent = true;
    }

    public void setSolved(Object solved) {
        this.solved = solved;
        this.solvedPresent = true;
    }

    public void setRecommended(Object recommended) {
        this.recommended = recommended;
        this.recommendedPresent = true;
    }

    @JsonAnySetter
    public void unsupportedField(String name, Object value) {
        unsupportedFields.add(name);
    }

    public int fieldCount() {
        return (deletedPresent ? 1 : 0) + (pinnedPresent ? 1 : 0) + (solvedPresent ? 1 : 0)
                + (recommendedPresent ? 1 : 0) + unsupportedFields.size();
    }
}
