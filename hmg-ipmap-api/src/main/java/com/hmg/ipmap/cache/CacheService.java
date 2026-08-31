package com.hmg.ipmap.cache;

import com.hmg.ipmap.cache.dto.IpSpanCacheDto;
import com.hmg.ipmap.common.context.UserContext;

public interface CacheService {
    IpSpanCacheDto findIpLocation(String ip, UserContext user);
}
