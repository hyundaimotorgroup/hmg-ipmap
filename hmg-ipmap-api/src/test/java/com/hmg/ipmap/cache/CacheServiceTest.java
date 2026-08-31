package com.hmg.ipmap.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.cache.dto.IpSpanCacheDto;
import com.hmg.ipmap.cache.entity.IpSpanSortedSetCacheEntity;
import com.hmg.ipmap.cache.exception.CacheDataCorruptedException;
import com.hmg.ipmap.common.config.IpSpanProperties;
import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.common.util.IPv4Util;
import com.hmg.ipmap.ipnotation.IpNotationFactory;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {
    @Mock private IpNotationFactory ipNotationFactory;

    @Mock
    @SuppressWarnings("unused")
    private IpSpanProperties ipSpanProperties;

    @Mock private RedisTemplate<String, String> redisTemplate;

    @Mock private ZSetOperations<String, String> zSetOperations;

    @Mock private HashOperations<String, Object, Object> hashOperations;

    @Mock private ValueOperations<String, String> stringOperations;

    @InjectMocks private CacheServiceImpl cacheService;

    // IpSpan CSV layout: ipUpper,ipMappingId,scope,createdAt,userId,validPeriod
    // IpMapping CSV layout: id,createdAt,ipNotation,notationType,registeredCountryId,
    //     representedCountryId,scope,updatedAt,validPeriod,locationId,userId
    // Location CSV layout:
    // id,attributes,geonameId,locationCode,locationLevel,parentId,userId,scope

    @BeforeEach
    void setUp() {
        when(ipNotationFactory.mapIpToSubnet(anyString(), anyInt())).thenReturn("192.168.0.0/24");
    }

    @Test
    void testFindIpLocation_ShouldReturnIpSpanCacheDto() {
        String ip = "192.168.0.1";
        UserContext user = mockUser();

        try (MockedStatic<IpSpanSortedSetCacheEntity> ipSpanMock =
                        mockStatic(IpSpanSortedSetCacheEntity.class);
                MockedStatic<IPv4Util> ipv4Mock = mockStatic(IPv4Util.class)) {
            ipv4Mock.when(() -> IPv4Util.ipv4ToLong(ip)).thenReturn(3232235521L);
            ipSpanMock
                    .when(() -> IpSpanSortedSetCacheEntity.buildCollectionKey(any()))
                    .thenReturn("collectionKey");

            // IpSpan CSV: ipUpper=3232235600 (>= score 3232235521), ipMappingId=200,
            //             scope=GLOBAL, createdAt=1000, userId=100, validPeriod=(empty)
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRangeByScore(anyString(), anyDouble(), anyDouble()))
                    .thenReturn(Set.of("3232235600,200,GLOBAL,1000,100,"));

            // IpMapping CSV: id=200, createdAt=0, updatedAt=0, validPeriod=0, locationId=loc-1,
            //   userId=0 — primitive long fields must be non-empty to avoid NPE on unboxing
            when(redisTemplate.opsForValue()).thenReturn(stringOperations);
            when(stringOperations.get("ip_mapping:{ip_map:200}")).thenReturn("200,0,,,,,,0,0,1,0");

            // Location CSV: id=loc-1, geonameId=0, userId=0 — same reason
            when(stringOperations.get("location:{loc_id:1}")).thenReturn("1,,0,,,,0,");

            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.values(anyString())).thenReturn(List.of());

            IpSpanCacheDto result = cacheService.findIpLocation(ip, user);

            assertNotNull(result);
            assertEquals(200L, result.getIpMappingId());
            assertEquals(1L, result.getIpMappingDto().getLocationId());
        }
    }

    @Test
    void testFindIpLocation_IpNotFound_ShouldReturnNull() {
        String ip = "10.0.0.1";
        UserContext user = mockUser();

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForZSet().reverseRangeByScore(anyString(), anyDouble(), anyDouble()))
                .thenReturn(Set.of());

        IpSpanCacheDto result = cacheService.findIpLocation(ip, user);
        assertNull(result);
    }

    @Test
    void testFindIpLocation_InvalidJson_ShouldThrowException() {
        String ip = "192.168.0.1";
        UserContext user = mockUser();

        try (MockedStatic<IPv4Util> ipv4Mock = mockStatic(IPv4Util.class);
                MockedStatic<IpSpanSortedSetCacheEntity> ipSpanMock =
                        mockStatic(IpSpanSortedSetCacheEntity.class)) {
            ipv4Mock.when(() -> IPv4Util.ipv4ToLong(ip)).thenReturn(3232235521L);
            ipSpanMock
                    .when(() -> IpSpanSortedSetCacheEntity.buildCollectionKey(any()))
                    .thenReturn("collectionKey");

            // CSV passes matchesRawIpSpanFilter (valid ipUpper/userId/validPeriod)
            // but "INVALID_SCOPE" fails Enum.valueOf → CacheDataCorruptedException
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRangeByScore(anyString(), anyDouble(), anyDouble()))
                    .thenReturn(Set.of("3232235600,200,INVALID_SCOPE,1000,100,"));

            assertThrows(
                    CacheDataCorruptedException.class, () -> cacheService.findIpLocation(ip, user));
        }
    }

    @Test
    void testFindIpLocation_MissingLocation_ShouldReturnPartialDto() {

        String ip = "192.168.0.1";
        UserContext user = mockUser();

        try (MockedStatic<IPv4Util> ipv4Mock = mockStatic(IPv4Util.class);
                MockedStatic<IpSpanSortedSetCacheEntity> ipSpanMock =
                        mockStatic(IpSpanSortedSetCacheEntity.class)) {

            ipv4Mock.when(() -> IPv4Util.ipv4ToLong(ip)).thenReturn(3232235521L);
            ipSpanMock
                    .when(() -> IpSpanSortedSetCacheEntity.buildCollectionKey(any()))
                    .thenReturn("collectionKey");

            // IpSpan CSV
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRangeByScore(anyString(), anyDouble(), anyDouble()))
                    .thenReturn(Set.of("3232235600,200,GLOBAL,1000,100,"));

            // IpMapping CSV: primitive long fields non-empty, locationId=loc-1
            when(redisTemplate.opsForValue()).thenReturn(stringOperations);
            when(stringOperations.get("ip_mapping:{ip_map:200}")).thenReturn("200,0,,,,,,0,0,1,0");

            // Missing location → return null
            when(stringOperations.get("location:{loc_id:1}")).thenReturn(null);

            // Hash ops → no attributes
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.values(anyString())).thenReturn(List.of());

            IpSpanCacheDto result = cacheService.findIpLocation(ip, user);

            assertNotNull(result);
            assertEquals(200L, result.getIpMappingId());
            assertNull(result.getIpMappingDto().getLocationDto());
        }
    }

    @Test
    void testFindIpLocation_WhenMoreThanOneResult_ShouldReturnLatestIpSpanCacheDto() {

        String ip = "192.168.0.1";
        UserContext user = mockUser();

        try (MockedStatic<IPv4Util> ipv4Mock = mockStatic(IPv4Util.class);
                MockedStatic<IpSpanSortedSetCacheEntity> ipSpanMock =
                        mockStatic(IpSpanSortedSetCacheEntity.class)) {

            ipv4Mock.when(() -> IPv4Util.ipv4ToLong(ip)).thenReturn(3232235521L);
            ipSpanMock
                    .when(() -> IpSpanSortedSetCacheEntity.buildCollectionKey(any()))
                    .thenReturn("collectionKey");

            // Two IpSpan CSVs: dto1 (createdAt=1000, ipMappingId=200),
            //                   dto2 (createdAt=2000, ipMappingId=300) — latest wins
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRangeByScore(anyString(), anyDouble(), anyDouble()))
                    .thenReturn(
                            Set.of(
                                    "3232235600,200,GLOBAL,1000,100,",
                                    "3232235600,300,GLOBAL,2000,100,"));

            // IpMapping CSV for latest span (ipMappingId=300): locationId=loc-2
            when(redisTemplate.opsForValue()).thenReturn(stringOperations);
            when(stringOperations.get("ip_mapping:{ip_map:300}")).thenReturn("300,0,,,,,,0,0,2,0");

            // Location CSV: id=loc-2
            when(stringOperations.get("location:{loc_id:2}")).thenReturn("2,,,0,,,,0,");

            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.values(anyString())).thenReturn(List.of());

            IpSpanCacheDto result = cacheService.findIpLocation(ip, user);

            assertNotNull(result);
            assertEquals(300L, result.getIpMappingId()); // latest
            assertEquals(2L, result.getIpMappingDto().getLocationId());
        }
    }

    @Test
    void testFindIpLocation_WithPagination_ShouldFetchAllRecordsInPages() throws Exception {
        // Arrange
        String ip = "192.168.0.1";
        UserContext user = mockUser();
        setPaginationSize(); // page size = 2

        try (MockedStatic<IpSpanSortedSetCacheEntity> ipSpanMock =
                        mockStatic(IpSpanSortedSetCacheEntity.class);
                MockedStatic<IPv4Util> ipv4Mock = mockStatic(IPv4Util.class)) {

            ipv4Mock.when(() -> IPv4Util.ipv4ToLong(ip)).thenReturn(3232235521L);
            ipSpanMock
                    .when(() -> IpSpanSortedSetCacheEntity.buildCollectionKey(any()))
                    .thenReturn("collectionKey");

            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

            // Page 0 → 2 IpSpan CSVs (dto1 createdAt=1000, dto2 createdAt=2000, both GLOBAL)
            when(zSetOperations.reverseRangeByScore(
                            anyString(), anyDouble(), anyDouble(), eq(0L), eq(2L)))
                    .thenReturn(
                            Set.of(
                                    "3232235600,200,GLOBAL,1000,100,",
                                    "3232235600,201,GLOBAL,2000,100,"));

            // Page 2 → 1 IpSpan CSV (dto3 createdAt=3000, CLIENT scope) → end paging
            when(zSetOperations.reverseRangeByScore(
                            anyString(), anyDouble(), anyDouble(), eq(2L), eq(2L)))
                    .thenReturn(Set.of("3232235600,202,CLIENT,3000,100,"));

            // IpMapping CSV for latest span by scope priority (CLIENT > GLOBAL): ipMappingId=202
            when(redisTemplate.opsForValue()).thenReturn(stringOperations);
            when(stringOperations.get("ip_mapping:{ip_map:202}")).thenReturn("202,0,,,,,,0,0,1,0");

            // Location CSV
            when(stringOperations.get("location:{loc_id:1}")).thenReturn("1,,,0,,,,0,");

            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.values(anyString())).thenReturn(List.of());

            IpSpanCacheDto result = cacheService.findIpLocation(ip, user);

            assertNotNull(result);
            assertEquals(202L, result.getIpMappingId());
            assertEquals(1L, result.getIpMappingDto().getLocationId());

            verify(zSetOperations, atLeastOnce())
                    .reverseRangeByScore(anyString(), anyDouble(), anyDouble(), eq(0L), eq(2L));

            verify(zSetOperations, atLeastOnce())
                    .reverseRangeByScore(anyString(), anyDouble(), anyDouble(), eq(2L), eq(2L));
        }
    }

    @Test
    void testFindIpLocation_WithPagination_ExactlyOnePageOfData() throws Exception {
        // Arrange
        String ip = "192.168.0.1";
        UserContext user = mockUser();
        setPaginationSize(); // page size = 2

        try (MockedStatic<IpSpanSortedSetCacheEntity> ipSpanMock =
                        mockStatic(IpSpanSortedSetCacheEntity.class);
                MockedStatic<IPv4Util> ipv4Mock = mockStatic(IPv4Util.class)) {

            ipv4Mock.when(() -> IPv4Util.ipv4ToLong(ip)).thenReturn(3232235521L);
            ipSpanMock
                    .when(() -> IpSpanSortedSetCacheEntity.buildCollectionKey(any()))
                    .thenReturn("collectionKey");

            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

            // First page: 2 CSVs (same ipMappingId=200, different createdAt)
            when(zSetOperations.reverseRangeByScore(
                            anyString(), anyDouble(), anyDouble(), eq(0L), eq(2L)))
                    .thenReturn(
                            Set.of(
                                    "3232235600,200,GLOBAL,1000,100,",
                                    "3232235600,200,GLOBAL,2000,100,"));

            // Second page empty → stop
            when(zSetOperations.reverseRangeByScore(
                            anyString(), anyDouble(), anyDouble(), eq(2L), eq(2L)))
                    .thenReturn(Set.of());

            // IpMapping CSV for ipMappingId=200: locationId=loc-1
            when(redisTemplate.opsForValue()).thenReturn(stringOperations);
            when(stringOperations.get("ip_mapping:{ip_map:200}")).thenReturn("200,0,,,,,,0,0,1,0");

            // Location CSV
            when(stringOperations.get("location:{loc_id:1}")).thenReturn("1,,,0,,,,0,");

            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.values(anyString())).thenReturn(List.of());

            IpSpanCacheDto result = cacheService.findIpLocation(ip, user);

            assertNotNull(result);
            assertEquals(200L, result.getIpMappingId());
            assertEquals(1L, result.getIpMappingDto().getLocationId());

            verify(zSetOperations, atLeastOnce())
                    .reverseRangeByScore(anyString(), anyDouble(), anyDouble(), eq(0L), eq(2L));

            verify(zSetOperations, atLeastOnce())
                    .reverseRangeByScore(anyString(), anyDouble(), anyDouble(), eq(2L), eq(2L));
        }
    }

    private void setPaginationSize() throws Exception {
        Field field = CacheServiceImpl.class.getDeclaredField("paginationSize");
        field.setAccessible(true);
        field.set(cacheService, 2);
    }

    private UserContext mockUser() {
        return new UserContext(100L, "test-user", UserType.CLIENT, null, Scope.CLIENT, null, null);
    }
}
