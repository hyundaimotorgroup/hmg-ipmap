package com.hmg.ipmap.user.dto;

import com.hmg.ipmap.common.enums.UserType;

public record UserDto(
        Long id, String name, String sourceIp, Long parentId, UserType userType, String apiKey) {}
