package com.hmg.ipmap.cache.helper;

import com.hmg.ipmap.cache.dto.IpMappingAttributeCacheDto;
import com.hmg.ipmap.cache.dto.IpMappingCacheDto;
import com.hmg.ipmap.cache.dto.IpSpanCacheDto;
import com.hmg.ipmap.cache.dto.LocationCacheDto;
import com.hmg.ipmap.cache.dto.LocationNameCacheDto;
import com.hmg.ipmap.cache.exception.CacheDataCorruptedException;
import com.hmg.ipmap.common.enums.Scope;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class CacheDtoParser {

    private CacheDtoParser() {
        throw new IllegalStateException("helper class");
    }

    // ==================== PARSE METHODS ====================

    public static IpSpanCacheDto parseIpSpan(String csv) {
        if (StringUtils.isBlank(csv)) return null;
        // -1 limit preserves trailing empty tokens (e.g. empty validPeriod)
        String[] t = csv.split(",", -1);
        try {
            IpSpanCacheDto dto = new IpSpanCacheDto();
            dto.setIpUpper(tokenRequiredLong(t, 0));
            dto.setIpMappingId(tokenRequiredLong(t, 1));
            dto.setScope(tokenEnum(t, 2, Scope.class));
            dto.setCreatedAt(tokenRequiredLong(t, 3));
            dto.setUserId(tokenRequiredLong(t, 4));
            dto.setValidPeriod(tokenLong(t, 5));
            return dto;
        } catch (IllegalArgumentException e) {
            log.error("Unable to parse ip_span cache data. data={}", csv, e);
            throw new CacheDataCorruptedException("Unable to parse ip_span cache data", e);
        }
    }

    // IpMappingCacheDto: ipNotation may contain an array of IPs (e.g. "1.0.0.1,2.0.0.1"),
    // so the field is stored as a quoted CSV token — parseTokens handles the quoting correctly.
    public static IpMappingCacheDto parseIpMapping(String csv) {
        if (StringUtils.isBlank(csv)) return null;
        String[] t = CsvCodec.parseTokens(csv);
        IpMappingCacheDto dto = new IpMappingCacheDto();
        dto.setId(tokenRequiredLong(t, 0));
        dto.setCreatedAt(tokenLong(t, 1));
        dto.setIpNotation(tokenString(t, 2));
        dto.setNotationType(tokenString(t, 3));
        dto.setRegisteredCountryGeonameId(tokenString(t, 4));
        dto.setRepresentedCountryGeonameId(tokenString(t, 5));
        dto.setScope(tokenEnum(t, 6, Scope.class));
        dto.setUpdatedAt(tokenLong(t, 7));
        dto.setValidPeriod(tokenLong(t, 8));
        dto.setLocationId(tokenLong(t, 9));
        dto.setUserId(tokenLong(t, 10));
        return dto;
    }

    // IpMappingAttributeCacheDto: 'attributes' field is JSON — use parseTokens to handle
    // commas inside quoted CSV fields without bean reflection.
    public static IpMappingAttributeCacheDto parseIpMappingAttribute(String csv) {
        if (StringUtils.isBlank(csv)) return null;
        String[] t = CsvCodec.parseTokens(csv);
        IpMappingAttributeCacheDto dto = new IpMappingAttributeCacheDto();
        dto.setId(tokenLong(t, 0));
        dto.setAttributes(tokenString(t, 1));
        dto.setObjectName(tokenString(t, 2));
        dto.setIpMappingId(tokenLong(t, 3));
        return dto;
    }

    public static LocationCacheDto parseLocation(String csv) {
        if (StringUtils.isBlank(csv)) return null;
        String[] t = CsvCodec.parseTokens(csv);
        LocationCacheDto dto = new LocationCacheDto();
        dto.setId(tokenRequiredLong(t, 0));
        dto.setAttributes(tokenString(t, 1));
        dto.setGeonameId(tokenLong(t, 2));
        dto.setLocationCode(tokenString(t, 3));
        dto.setLocationLevel(tokenString(t, 4));
        dto.setParentId(tokenLong(t, 5));
        dto.setUserId(tokenLong(t, 6));
        dto.setScope(tokenEnum(t, 7, Scope.class));
        return dto;
    }

    // LocationNameCacheDto: 'name' may contain commas (e.g. "Hong Kong, SAR China") — use
    // parseTokens so quoted names are handled correctly.
    public static LocationNameCacheDto parseLocationName(String csv) {
        if (StringUtils.isBlank(csv)) return null;
        String[] t = CsvCodec.parseTokens(csv);
        LocationNameCacheDto dto = new LocationNameCacheDto();
        dto.setLocaleCode(tokenString(t, 0));
        dto.setName(tokenString(t, 1));
        dto.setLocationId(tokenLong(t, 2));
        return dto;
    }

    // ==================== TOKEN HELPERS ====================

    /**
     * Returns the token at {@code index} as a primitive {@code long}.
     *
     * <p>Throws {@link IllegalArgumentException} if the token is missing or blank, signalling
     * corrupted cache data for a field that must never be absent. This exception is caught by the
     * enclosing parse method and re-thrown as {@link
     * com.hmg.ipmap.cache.exception.CacheDataCorruptedException}.
     */
    private static long tokenRequiredLong(String[] tokens, int index) {
        if (index >= tokens.length || StringUtils.isBlank(tokens[index])) {
            throw new IllegalArgumentException(
                    "Required long token at index " + index + " is blank or missing");
        }
        return Long.parseLong(tokens[index]);
    }

    private static Long tokenLong(String[] tokens, int index) {
        if (index >= tokens.length || StringUtils.isBlank(tokens[index])) return null;
        return Long.parseLong(tokens[index]);
    }

    private static <E extends Enum<E>> E tokenEnum(String[] tokens, int index, Class<E> type) {
        if (index >= tokens.length || StringUtils.isBlank(tokens[index])) return null;
        return Enum.valueOf(type, tokens[index]);
    }

    private static String tokenString(String[] tokens, int index) {
        if (index >= tokens.length || StringUtils.isBlank(tokens[index])) return null;
        return tokens[index];
    }
}
