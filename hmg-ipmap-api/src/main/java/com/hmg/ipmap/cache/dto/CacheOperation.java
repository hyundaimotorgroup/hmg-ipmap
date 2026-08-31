package com.hmg.ipmap.cache.dto;

import com.hmg.ipmap.cache.enums.CacheOpsAction;
import com.hmg.ipmap.common.util.MapperUtil;
import java.util.Map;
import java.util.Optional;
import lombok.Data;

@Data
public class CacheOperation {
    private CacheOpsAction action;
    private Map<String, String> data;

    public String getJsonDataString() {
        return MapperUtil.toJsonSafe(this.data);
    }

    public String getDataString(String key) {
        return Optional.ofNullable(data.get(key)).map(Object::toString).orElse("");
    }

    public Long getDataLong(String key) {
        return Optional.ofNullable(data.get(key)).map(Long::parseLong).orElse(null);
    }
}
