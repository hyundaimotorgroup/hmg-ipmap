package com.hmg.ipmap.ingestion.file.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.hmg.ipmap.ingestion.file.job.model.IpBlock;
import com.hmg.ipmap.ipmapping.IpMappingEntity;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.user.UserEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IpBlockProcessingContextTest {

    private static final Long GEONAME_KR = 1835841L;
    private static final Long GEONAME_AU = 2077456L;
    private static final Long GEONAME_UNKNOWN = 999999L;

    private UserEntity user;
    private IpBlock item;
    private IpBlockProcessingContext ctx;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);

        item = new IpBlock();
        item.setFileDetailId(1L);
        item.setGeonameId(GEONAME_KR);

        ctx = new IpBlockProcessingContext(List.of(item), user);
    }

    @Test
    void constructor_ShouldStoreIpBlockItemsAndUserEntity() {
        assertThat(ctx.getIpBlocks()).containsExactly(item);
        assertThat(ctx.getUserEntity()).isSameAs(user);
    }

    @Test
    void constructor_ShouldStartWithEmptyLocationCache() {
        assertThat(ctx.getLocation(GEONAME_KR)).isNull();
    }

    @Test
    void getLocation_ShouldReturnNullWhenGeonameIdNotInCache() {
        assertThat(ctx.getLocation(GEONAME_UNKNOWN)).isNull();
    }

    @Test
    void putLocation_ShouldStoreAndRetrieveLocation() {
        LocationEntity location = new LocationEntity();
        location.setId(1L);

        ctx.putLocation(GEONAME_KR, location);

        assertThat(ctx.getLocation(GEONAME_KR)).isSameAs(location);
    }

    @Test
    void putLocation_ShouldStoreMultipleLocationsIndependently() {
        LocationEntity locationKr = new LocationEntity();
        locationKr.setId(1L);

        LocationEntity locationAu = new LocationEntity();
        locationAu.setId(2L);

        ctx.putLocation(GEONAME_KR, locationKr);
        ctx.putLocation(GEONAME_AU, locationAu);

        assertThat(ctx.getLocation(GEONAME_KR)).isSameAs(locationKr);
        assertThat(ctx.getLocation(GEONAME_AU)).isSameAs(locationAu);
    }

    @Test
    void putLocation_ShouldOverwriteExistingEntryForSameGeonameId() {
        LocationEntity original = new LocationEntity();
        original.setId(1L);

        LocationEntity replacement = new LocationEntity();
        replacement.setId(2L);

        ctx.putLocation(GEONAME_KR, original);
        ctx.putLocation(GEONAME_KR, replacement);

        assertThat(ctx.getLocation(GEONAME_KR)).isSameAs(replacement);
    }

    @Test
    void clear_ShouldRemoveAllCachedLocations() {
        LocationEntity locationKr = new LocationEntity();
        LocationEntity locationAu = new LocationEntity();

        ctx.putLocation(GEONAME_KR, locationKr);
        ctx.putLocation(GEONAME_AU, locationAu);

        ctx.clear();

        assertThat(ctx.getLocation(GEONAME_KR)).isNull();
        assertThat(ctx.getLocation(GEONAME_AU)).isNull();
    }

    @Test
    void clear_ShouldAllowCacheToBeReusedAfterClearing() {
        LocationEntity before = new LocationEntity();
        before.setId(1L);

        ctx.putLocation(GEONAME_KR, before);
        ctx.clear();

        LocationEntity after = new LocationEntity();
        after.setId(2L);

        ctx.putLocation(GEONAME_KR, after);

        assertThat(ctx.getLocation(GEONAME_KR)).isSameAs(after);
    }

    @Test
    void getUserEntity_ShouldReturnSameInstanceAcrossMultipleCalls() {
        UserEntity first = ctx.getUserEntity();
        UserEntity second = ctx.getUserEntity();
        assertThat(first).isSameAs(second);
    }

    @Test
    void getIpBlockItems_ShouldReturnSameListAcrossMultipleCalls() {
        List<IpBlock> first = ctx.getIpBlocks();
        List<IpBlock> second = ctx.getIpBlocks();
        assertThat(first).isSameAs(second);
    }

    @Test
    void putIpMapping_ShouldStoreAndRetrieveIpMapping() {
        IpMappingEntity mapping = new IpMappingEntity();

        ctx.putIpMapping("1.0.0.0/24", mapping);

        assertThat(ctx.getIpMapping("1.0.0.0/24")).isSameAs(mapping);
    }

    @Test
    void getIpMapping_ShouldReturnNullWhenNotInCache() {
        assertThat(ctx.getIpMapping("10.0.0.0/8")).isNull();
    }

    @Test
    void putIpMapping_ShouldOverwriteExistingEntryForSameIpNotation() {
        IpMappingEntity original = new IpMappingEntity();
        IpMappingEntity replacement = new IpMappingEntity();

        ctx.putIpMapping("1.0.0.0/24", original);
        ctx.putIpMapping("1.0.0.0/24", replacement);

        assertThat(ctx.getIpMapping("1.0.0.0/24")).isSameAs(replacement);
    }

    @Test
    void clear_ShouldAlsoClearIpMappingCache() {
        IpMappingEntity mapping = new IpMappingEntity();
        ctx.putIpMapping("1.0.0.0/24", mapping);

        ctx.clear();

        assertThat(ctx.getIpMapping("1.0.0.0/24")).isNull();
    }
}
