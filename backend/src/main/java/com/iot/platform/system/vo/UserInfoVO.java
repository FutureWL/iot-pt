package com.iot.platform.system.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserInfoVO {
    private String token;
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private Long tenantId;
    private String tenantCode;
    private String tenantName;
    private List<String> roles;
    private List<String> permissions;
}
