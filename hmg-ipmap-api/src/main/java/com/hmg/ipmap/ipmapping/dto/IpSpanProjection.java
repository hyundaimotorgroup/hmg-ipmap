package com.hmg.ipmap.ipmapping.dto;

import com.hmg.ipmap.common.enums.Scope;
import java.time.Instant;

public interface IpSpanProjection {
    Long getId();

    Long getIpMappingId();

    Scope getScope();

    Instant getCreatedAt();
}
