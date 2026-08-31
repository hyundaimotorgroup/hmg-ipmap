package com.hmg.ipmap.common.context;

import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.user.UserResponseTemplateEnum;

public record UserContext(
        Long id,
        String username,
        UserType userType,
        String sourceIp,
        Scope scope,
        UserContext parent,
        UserResponseTemplateEnum responseTemplate) {}
