package com.hmg.ipmap.user;

import lombok.Getter;

public enum UserResponseTemplateEnum {
    DEFAULT("defaultTemplateResponse");

    @Getter private final String beanName;

    UserResponseTemplateEnum(String beanName) {
        this.beanName = beanName;
    }
}
