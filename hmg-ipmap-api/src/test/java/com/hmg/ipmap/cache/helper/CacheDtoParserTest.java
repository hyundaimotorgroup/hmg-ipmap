package com.hmg.ipmap.cache.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hmg.ipmap.cache.dto.IpMappingAttributeCacheDto;
import com.hmg.ipmap.cache.dto.IpMappingCacheDto;
import com.hmg.ipmap.cache.dto.IpSpanCacheDto;
import com.hmg.ipmap.cache.dto.LocationCacheDto;
import com.hmg.ipmap.cache.dto.LocationNameCacheDto;
import com.hmg.ipmap.cache.exception.CacheDataCorruptedException;
import com.hmg.ipmap.common.enums.Scope;
import org.junit.jupiter.api.Test;

class CacheDtoParserTest {

    // ==================== parseIpSpan ====================

    @Test
    void parseIpSpan_returnsNull_whenInputIsNull() {
        assertThat(CacheDtoParser.parseIpSpan(null)).isNull();
    }

    @Test
    void parseIpSpan_returnsNull_whenInputIsBlank() {
        assertThat(CacheDtoParser.parseIpSpan("   ")).isNull();
    }

    @Test
    void parseIpSpan_mapsAllFields_whenFullValidCsv() {
        // format: ipUpper,ipMappingId,scope,createdAt,userId,validPeriod
        String csv = "16805887,1054008,GLOBAL,1770949743231,1,253402214400000";

        IpSpanCacheDto result = CacheDtoParser.parseIpSpan(csv);

        assertThat(result).isNotNull();
        assertThat(result.getIpUpper()).isEqualTo(16805887L);
        assertThat(result.getIpMappingId()).isEqualTo(1054008L);
        assertThat(result.getScope()).isEqualTo(Scope.GLOBAL);
        assertThat(result.getCreatedAt()).isEqualTo(1770949743231L);
        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getValidPeriod()).isEqualTo(253402214400000L);
    }

    @Test
    void parseIpSpan_setsValidPeriodToNull_whenLastTokenIsEmpty() {
        // trailing empty token — validPeriod is nullable
        String csv = "16805887,1054008,CLIENT,1770949743231,1,";

        IpSpanCacheDto result = CacheDtoParser.parseIpSpan(csv);

        assertThat(result).isNotNull();
        assertThat(result.getScope()).isEqualTo(Scope.CLIENT);
        assertThat(result.getValidPeriod()).isNull();
    }

    @Test
    void parseIpSpan_setsMissingFieldsToNull_whenCsvHasFewerTokens() {
        String csv = "16805887,1054008,GLOBAL,1770949743231,8";

        IpSpanCacheDto result = CacheDtoParser.parseIpSpan(csv);

        assertThat(result).isNotNull();
        assertThat(result.getIpUpper()).isEqualTo(16805887L);
        assertThat(result.getIpMappingId()).isEqualTo(1054008L);
        assertThat(result.getScope()).isEqualTo(Scope.GLOBAL);
        assertThat(result.getCreatedAt()).isEqualTo(1770949743231L);
        assertThat(result.getUserId()).isEqualTo(8L);
        assertThat(result.getValidPeriod()).isNull();
    }

    @Test
    void parseIpSpan_throwsCacheDataCorruptedException_whenLongFieldIsMalformed() {
        // "notALong" at index 0 triggers NumberFormatException → wrapped as
        // CacheDataCorruptedException
        String csv = "notALong,1054008,GLOBAL,1770949743231,1,";

        assertThatThrownBy(() -> CacheDtoParser.parseIpSpan(csv))
                .isInstanceOf(CacheDataCorruptedException.class)
                .hasMessageContaining("Unable to parse ip_span cache data");
    }

    // ==================== parseIpMapping ====================

    @Test
    void parseIpMapping_returnsNull_whenInputIsBlank() {
        assertThat(CacheDtoParser.parseIpMapping("")).isNull();
    }

    @Test
    void parseIpMapping_mapsAllFields_whenFullValidCsv() {
        // format: id,createdAt,ipNotation,notationType,registeredCountryId,
        //         representedCountryId,scope,updatedAt,validPeriod,locationId,userId
        String csv =
                "42,1770949743231,192.168.1.0/24,CIDR,US,US,CLIENT,1770949743999,253402214400000,1,7";

        IpMappingCacheDto result = CacheDtoParser.parseIpMapping(csv);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getCreatedAt()).isEqualTo(1770949743231L);
        assertThat(result.getIpNotation()).isEqualTo("192.168.1.0/24");
        assertThat(result.getNotationType()).isEqualTo("CIDR");
        assertThat(result.getRegisteredCountryGeonameId()).isEqualTo("US");
        assertThat(result.getRepresentedCountryGeonameId()).isEqualTo("US");
        assertThat(result.getScope()).isEqualTo(Scope.CLIENT);
        assertThat(result.getUpdatedAt()).isEqualTo(1770949743999L);
        assertThat(result.getValidPeriod()).isEqualTo(253402214400000L);
        assertThat(result.getLocationId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo(7L);
    }

    @Test
    void parseIpMapping_setsNullableFieldsToNull_whenTokensAreEmpty() {
        // CsvParser returns null for empty unquoted fields, so both Long and String
        // empty tokens produce null
        String csv = "42,1770949743231,192.168.1.0/24,CIDR,US,US,GLOBAL,1770949743999,,1,7";

        IpMappingCacheDto result = CacheDtoParser.parseIpMapping(csv);

        assertThat(result).isNotNull();
        assertThat(result.getValidPeriod()).isNull();
        assertThat(result.getLocationId()).isNotZero();
        assertThat(result.getUserId()).isEqualTo(7L);
    }

    @Test
    void parseIpMapping_handlesMultipleIpAddresses_whenIpNotationIsQuoted() {
        // ipNotation can be an array of IPs separated by commas.
        // The encode side (CsvWriter) stores them as a single quoted field:
        //   "1.0.1.1,56.122.12.12,13.1.12.22"
        // Without quoting, split(",") would misalign every subsequent field.
        String csv =
                "42,1770949743231,\"1.0.1.1,56.122.12.12,13.1.12.22\",CIDR,US,US,GLOBAL,1770949743999,,1,7";

        IpMappingCacheDto result = CacheDtoParser.parseIpMapping(csv);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getIpNotation()).isEqualTo("1.0.1.1,56.122.12.12,13.1.12.22");
        assertThat(result.getScope()).isEqualTo(Scope.GLOBAL);
        assertThat(result.getLocationId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo(7L);
    }

    // ==================== parseIpMappingAttribute ====================

    @Test
    void parseIpMappingAttribute_returnsNull_whenInputIsBlank() {
        assertThat(CacheDtoParser.parseIpMappingAttribute("  ")).isNull();
    }

    @Test
    void parseIpMappingAttribute_mapsAllFields_whenValidCsv() {
        // format: id,attributes,objectName,ipMappingId
        // attributes JSON is quoted in CSV because it contains double quotes
        // CSV:  1,"{""level"":5}",LOCATION,2
        String csv = "1,\"{\"\"level\"\":5}\",LOCATION,2";

        IpMappingAttributeCacheDto result = CacheDtoParser.parseIpMappingAttribute(csv);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getAttributes()).isEqualTo("{\"level\":5}");
        assertThat(result.getObjectName()).isEqualTo("LOCATION");
        assertThat(result.getIpMappingId()).isEqualTo(2L);
    }

    @Test
    void parseIpMappingAttribute_handlesCommasInsideAttributesJson() {
        // attributes JSON contains commas — must be stored as a quoted CSV field
        // JSON: {"a":1,"b":2}  →  CSV field: "{""a"":1,""b"":2}"
        String csv = "5,\"{\"\"a\"\":1,\"\"b\"\":2}\",TRAITS,10";

        IpMappingAttributeCacheDto result = CacheDtoParser.parseIpMappingAttribute(csv);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getAttributes()).isEqualTo("{\"a\":1,\"b\":2}");
        assertThat(result.getObjectName()).isEqualTo("TRAITS");
        assertThat(result.getIpMappingId()).isEqualTo(10L);
    }

    @Test
    void parseIpMappingAttribute_setsAttributesToNull_whenTokenIsEmpty() {
        String csv = "1,,CONFIDENCE,2";

        IpMappingAttributeCacheDto result = CacheDtoParser.parseIpMappingAttribute(csv);

        assertThat(result).isNotNull();
        assertThat(result.getAttributes()).isNull();
        assertThat(result.getObjectName()).isEqualTo("CONFIDENCE");
    }

    // ==================== parseLocation ====================

    @Test
    void parseLocation_returnsNull_whenInputIsBlank() {
        assertThat(CacheDtoParser.parseLocation("")).isNull();
    }

    @Test
    void parseLocation_mapsAllFields_whenValidCsv() {
        String csv = "1,\"{\"\"tz\"\":\"\"Asia/Seoul\"\"}\",1835847,KR,CITY,2,7,GLOBAL";

        LocationCacheDto result = CacheDtoParser.parseLocation(csv);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getAttributes()).isEqualTo("{\"tz\":\"Asia/Seoul\"}");
        assertThat(result.getGeonameId()).isEqualTo(1835847L);
        assertThat(result.getLocationCode()).isEqualTo("KR");
        assertThat(result.getLocationLevel()).isEqualTo("CITY");
        assertThat(result.getParentId()).isEqualTo(2L);
        assertThat(result.getUserId()).isEqualTo(7L);
        assertThat(result.getScope()).isEqualTo(Scope.GLOBAL);
    }

    @Test
    void parseLocation_setsOptionalFieldsToNull_whenTokensAreEmpty() {
        // parentId (index 6) is empty — location has no parent
        String csv = "1,\"{\"\"k\"\":1}\",1835847,KR,CITY,,7,GLOBAL";

        LocationCacheDto result = CacheDtoParser.parseLocation(csv);

        assertThat(result).isNotNull();
        assertThat(result.getParentId()).isNull();
    }

    // ==================== parseLocationName ====================

    @Test
    void parseLocationName_returnsNull_whenInputIsBlank() {
        assertThat(CacheDtoParser.parseLocationName("  ")).isNull();
    }

    @Test
    void parseLocationName_mapsAllFields_whenSimpleCsv() {
        // format: localeCode,name,locationId
        String csv = "en,Seoul,1";

        LocationNameCacheDto result = CacheDtoParser.parseLocationName(csv);

        assertThat(result).isNotNull();
        assertThat(result.getLocaleCode()).isEqualTo("en");
        assertThat(result.getName()).isEqualTo("Seoul");
        assertThat(result.getLocationId()).isEqualTo(1L);
    }

    @Test
    void parseLocationName_handlesCommasInName_whenQuotedCsv() {
        // Name "Hong Kong, SAR China" contains a comma — stored as a quoted CSV field
        String csv = "en,\"Hong Kong, SAR China\",2";

        LocationNameCacheDto result = CacheDtoParser.parseLocationName(csv);

        assertThat(result).isNotNull();
        assertThat(result.getLocaleCode()).isEqualTo("en");
        assertThat(result.getName()).isEqualTo("Hong Kong, SAR China");
        assertThat(result.getLocationId()).isEqualTo(2L);
    }

    @Test
    void parseLocationName_setsNameToNull_whenTokenIsEmpty() {
        String csv = "en,,1";

        LocationNameCacheDto result = CacheDtoParser.parseLocationName(csv);

        assertThat(result).isNotNull();
        assertThat(result.getLocaleCode()).isEqualTo("en");
        assertThat(result.getName()).isNull();
        assertThat(result.getLocationId()).isEqualTo(1L);
    }
}
