package com.laimengchao.sso.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Contact {
    private Long accountId;
    private String username;
    private String nickname;
    private String email;
}
