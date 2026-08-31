package com.hmg.ipmap.cache.entity;

import com.hmg.ipmap.cache.dto.CacheOperation;
import com.hmg.ipmap.cache.dto.IpSpanCacheDto;
import com.hmg.ipmap.cache.exception.CacheOperationException;
import com.hmg.ipmap.cache.helper.CsvCodec;
import java.util.Map;
import lombok.Getter;

@Getter
public class IpSpanSortedSetCacheEntity extends SortedSetCacheEntity {

    public static IpSpanSortedSetCacheEntity ofCacheOperationRequest(
            CacheOperation cacheOperation, String subnet) throws CacheOperationException {
        IpSpanSortedSetCacheEntity cache = new IpSpanSortedSetCacheEntity();
        cache.cacheOpsAction = cacheOperation.getAction();

        Map<String, String> dataMap = cacheOperation.getData();
        Long ipLower = cacheOperation.getDataLong("ip_lower");

        if (ipLower == null) {
            throw new CacheOperationException("ip_lower is null");
        }

        cache.key = buildCollectionKey(subnet);
        cache.score = ipLower;
        // as member must be unique, we should refer to ip_span collection
        cache.member = CsvCodec.encodeFromMapToCsv(dataMap, IpSpanCacheDto.class);
        cache.sourceData = dataMap;

        return cache;
    }

    public static String buildCollectionKey(String subnet) {
        return String.format("ipspan:{%s}", subnet);
    }
}
