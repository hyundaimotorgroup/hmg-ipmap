package com.hmg.ipmap.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.ip-span")
public class IpSpanProperties {

    @Min(0)
    @Max(32)
    private int subnetPrefixLength;

    private Rebuild rebuild;

    @Getter
    @Setter
    public static class Rebuild {
        private int chunkSize;
    }
}
