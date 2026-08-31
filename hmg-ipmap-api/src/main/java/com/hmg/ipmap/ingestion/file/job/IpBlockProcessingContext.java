package com.hmg.ipmap.ingestion.file.job;

import com.hmg.ipmap.ingestion.file.job.model.IpBlock;
import com.hmg.ipmap.ipmapping.IpMappingEntity;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.user.UserEntity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;

public class IpBlockProcessingContext {
    @Getter private final List<IpBlock> ipBlocks;
    @Getter private final UserEntity userEntity;
    private final Map<Long, LocationEntity> locationCache = new ConcurrentHashMap<>();
    @Getter private final Map<String, IpMappingEntity> ipMappingCache = new ConcurrentHashMap<>();

    public IpBlockProcessingContext(List<IpBlock> ipBlocks, UserEntity userEntity) {
        this.ipBlocks = ipBlocks;
        this.userEntity = userEntity;
    }

    public LocationEntity getLocation(Long geonameId) {
        return locationCache.get(geonameId);
    }

    public void putLocation(Long geonameId, LocationEntity location) {
        locationCache.put(geonameId, location);
    }

    public IpMappingEntity getIpMapping(String ipNotation) {
        return ipMappingCache.get(ipNotation);
    }

    public void putIpMapping(String ipNotation, IpMappingEntity ipMapping) {
        ipMappingCache.put(ipNotation, ipMapping);
    }

    public void clear() {
        locationCache.clear();
        ipMappingCache.clear();
    }
}
