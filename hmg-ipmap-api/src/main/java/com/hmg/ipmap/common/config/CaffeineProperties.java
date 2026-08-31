package com.hmg.ipmap.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "cache.caffeine")
public class CaffeineProperties {
    private boolean enabled = true;
    private boolean ipLocationEnabled = true;
}
