package com.hmg.ipmap.ipnotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hmg.ipmap.ipnotation.exception.IpNotationBadRequestException;
import com.hmg.ipmap.ipnotation.exception.IpNotationUnknownIP;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IpNotationFactoryTest {

    private final IpNotationFactory factory = new IpNotationFactory();

    // -------------------------------------------------------------------------
    // mapIpToSubnet — /20 prefix block
    // -------------------------------------------------------------------------

    @Test
    void mapIpToSubnet_prefixLength20_midBlockIp() {
        // 1.0.100.123 → 1.0.96.0/20  (100 = 0110_0100 → first 4 bits = 0110 = 96)
        assertThat(factory.mapIpToSubnet("1.0.100.123", 20)).isEqualTo("1.0.96.0/20");
    }

    @Test
    void mapIpToSubnet_prefixLength20_networkAddress() {
        // IP already at the block boundary → same subnet
        assertThat(factory.mapIpToSubnet("1.0.96.0", 20)).isEqualTo("1.0.96.0/20");
    }

    @Test
    void mapIpToSubnet_prefixLength20_broadcastAddress() {
        // Last IP in the /20 block → same subnet
        assertThat(factory.mapIpToSubnet("1.0.111.255", 20)).isEqualTo("1.0.96.0/20");
    }

    // -------------------------------------------------------------------------
    // mapIpToSubnet — /24 prefix block
    // -------------------------------------------------------------------------

    @Test
    void mapIpToSubnet_prefixLength24() {
        assertThat(factory.mapIpToSubnet("1.0.100.123", 24)).isEqualTo("1.0.100.0/24");
    }

    // -------------------------------------------------------------------------
    // mapIpToSubnet — /16 prefix block
    // -------------------------------------------------------------------------

    @Test
    void mapIpToSubnet_prefixLength16() {
        assertThat(factory.mapIpToSubnet("1.0.100.123", 16)).isEqualTo("1.0.0.0/16");
    }

    // -------------------------------------------------------------------------
    // mapIpToSubnet — /32 host route
    // -------------------------------------------------------------------------

    @Test
    void mapIpToSubnet_prefixLength32_returnsHostItself() {
        // /32 → the subnet is just the host address itself
        assertThat(factory.mapIpToSubnet("1.0.100.123", 32)).isEqualTo("1.0.100.123/32");
    }

    // -------------------------------------------------------------------------
    // mapIpToSubnet — /0 catch-all
    // -------------------------------------------------------------------------

    @Test
    void mapIpToSubnet_prefixLength0_returnsDefaultRoute() {
        // /0 → the entire IPv4 space
        assertThat(factory.mapIpToSubnet("1.0.100.123", 0)).isEqualTo("0.0.0.0/0");
    }

    // -------------------------------------------------------------------------
    // mapIpToSubnet — error cases
    // -------------------------------------------------------------------------

    @Test
    void mapIpToSubnet_invalidPrefixLengthAbove32_throwsBadRequest() {
        assertThatThrownBy(() -> factory.mapIpToSubnet("1.0.100.123", 33))
                .isInstanceOf(IpNotationBadRequestException.class)
                .hasMessageContaining("33");
    }

    @Test
    void mapIpToSubnet_negativePrefixLength_throwsBadRequest() {
        assertThatThrownBy(() -> factory.mapIpToSubnet("1.0.100.123", -1))
                .isInstanceOf(IpNotationBadRequestException.class)
                .hasMessageContaining("-1");
    }

    @Test
    void mapIpToSubnet_invalidIpAddress_throwsUnknownIp() {
        assertThatThrownBy(() -> factory.mapIpToSubnet("not-an-ip", 20))
                .isInstanceOf(IpNotationUnknownIP.class);
    }

    // -------------------------------------------------------------------------
    // convertSubnet — splitting (target prefix finer than input)
    // -------------------------------------------------------------------------

    @Test
    void convertSubnet_split_19into20_returnsTwoBlocks() {
        // 1.0.32.0/19 spans 1.0.32.0–1.0.63.255 → two /20 halves
        assertThat(factory.convertSubnet("1.0.32.0/19", 20))
                .containsExactlyInAnyOrder("1.0.32.0/20", "1.0.48.0/20");
    }

    @Test
    void convertSubnet_split_16into20_returns16Blocks() {
        // /16 → /20 produces 2^(20-16) = 16 blocks
        assertThat(factory.convertSubnet("1.0.0.0/16", 20)).hasSize(16);
    }

    @Test
    void convertSubnet_split_16into24_returns256Blocks() {
        assertThat(factory.convertSubnet("1.0.0.0/16", 24)).hasSize(256);
    }

    // -------------------------------------------------------------------------
    // convertSubnet — aggregating (target prefix coarser than input)
    // -------------------------------------------------------------------------

    @Test
    void convertSubnet_aggregate_20into19_returnsContainingBlock() {
        // 1.0.48.0/20 sits inside 1.0.32.0/19
        assertThat(factory.convertSubnet("1.0.48.0/20", 19)).containsExactly("1.0.32.0/19");
    }

    @Test
    void convertSubnet_aggregate_24into20_returnsContainingBlock() {
        // 1.0.100.0/24 sits inside 1.0.96.0/20
        assertThat(factory.convertSubnet("1.0.100.0/24", 20)).containsExactly("1.0.96.0/20");
    }

    // -------------------------------------------------------------------------
    // convertSubnet — same prefix length
    // -------------------------------------------------------------------------

    @Test
    void convertSubnet_samePrefixLength_returnsSameSubnet() {
        assertThat(factory.convertSubnet("1.0.32.0/20", 20)).containsExactly("1.0.32.0/20");
    }

    // -------------------------------------------------------------------------
    // convertSubnet — error cases
    // -------------------------------------------------------------------------

    @Test
    void convertSubnet_invalidPrefixLength_throwsBadRequest() {
        assertThatThrownBy(() -> factory.convertSubnet("1.0.32.0/19", 33))
                .isInstanceOf(IpNotationBadRequestException.class)
                .hasMessageContaining("33");
    }

    @Test
    void convertSubnet_invalidSubnet_throwsUnknownIp() {
        assertThatThrownBy(() -> factory.convertSubnet("not-a-subnet", 20))
                .isInstanceOf(IpNotationUnknownIP.class);
    }

    // -------------------------------------------------------------------------
    // createIpSpans — /18 CIDR split into /20 subnets
    // -------------------------------------------------------------------------

    @Test
    void createIpSpans_cidr18_splitTo20_returnsFourSpans() {
        List<IpSpan> spans = factory.createIpSpans("1.0.0.0/18", 20);

        assertThat(spans).hasSize(4);
    }

    @Test
    void createIpSpans_cidr18_splitTo20_exactBoundariesForAllFourBlocks() {
        List<IpSpan> spans = factory.createIpSpans("1.0.0.0/18", 20);

        // Block 0: 1.0.0.0/20  → 1.0.0.0  – 1.0.15.255
        assertThat(spans.get(0).lower()).isEqualTo(ip(1, 0, 0, 0));
        assertThat(spans.get(0).upper()).isEqualTo(ip(1, 0, 15, 255));
        // Block 1: 1.0.16.0/20 → 1.0.16.0 – 1.0.31.255
        assertThat(spans.get(1).lower()).isEqualTo(ip(1, 0, 16, 0));
        assertThat(spans.get(1).upper()).isEqualTo(ip(1, 0, 31, 255));
        // Block 2: 1.0.32.0/20 → 1.0.32.0 – 1.0.47.255
        assertThat(spans.get(2).lower()).isEqualTo(ip(1, 0, 32, 0));
        assertThat(spans.get(2).upper()).isEqualTo(ip(1, 0, 47, 255));
        // Block 3: 1.0.48.0/20 → 1.0.48.0 – 1.0.63.255
        assertThat(spans.get(3).lower()).isEqualTo(ip(1, 0, 48, 0));
        assertThat(spans.get(3).upper()).isEqualTo(ip(1, 0, 63, 255));
    }

    @Test
    void createIpSpans_cidr18_splitTo20_spansAreContiguous() {
        List<IpSpan> spans = factory.createIpSpans("1.0.0.0/18", 20);

        for (int i = 0; i < spans.size() - 1; i++) {
            assertThat(spans.get(i).upper() + 1)
                    .as("gap between span %d and %d", i, i + 1)
                    .isEqualTo(spans.get(i + 1).lower());
        }
    }

    @Test
    void createIpSpans_cidr18_splitTo20_allSpansHaveCidrType() {
        List<IpSpan> spans = factory.createIpSpans("1.0.0.0/18", 20);

        assertThat(spans).isNotEmpty().allMatch(s -> s.spanType() == IpSpanType.CIDR);
    }

    // -------------------------------------------------------------------------
    // createIpSpans — single-span cases
    // -------------------------------------------------------------------------

    static Stream<Arguments> singleSpanCases() {
        return Stream.of(
                Arguments.of(
                        "prefix: 1.0.5.0/24 should not be bucketed into containing /20 block",
                        "1.0.5.0/24",
                        20,
                        ip(1, 0, 5, 0),
                        ip(1, 0, 5, 255)),
                Arguments.of(
                        "exact prefix match: 1.0.0.0/20 split by /20 yields one span",
                        "1.0.0.0/20",
                        20,
                        ip(1, 0, 0, 0),
                        ip(1, 0, 15, 255)),
                Arguments.of(
                        "prefixLength > 32 falls back to single span covering exact input range",
                        "1.0.0.0/24",
                        33,
                        ip(1, 0, 0, 0),
                        ip(1, 0, 0, 255)),
                Arguments.of(
                        "prefixLength -1 (no-split) returns exact range bounds",
                        "1.0.0.0/18",
                        -1,
                        ip(1, 0, 0, 0),
                        ip(1, 0, 63, 255)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("singleSpanCases")
    void createIpSpans_returnsExactlyOneSpan(
            String description,
            String ipNotation,
            int prefixLength,
            long expectedLower,
            long expectedUpper) {
        List<IpSpan> spans = factory.createIpSpans(ipNotation, prefixLength);

        assertThat(spans).hasSize(1);
        assertThat(spans.getFirst().lower()).isEqualTo(expectedLower);
        assertThat(spans.getFirst().upper()).isEqualTo(expectedUpper);
    }

    // -------------------------------------------------------------------------
    // createIpSpans — wide CIDR (/8 → /20)
    // -------------------------------------------------------------------------

    @Test
    void createIpSpans_cidr8_splitTo20_returns4096Spans() {
        // 2^(20-8) = 4 096 /20 blocks inside a /8
        List<IpSpan> spans = factory.createIpSpans("2.0.0.0/8", 20);

        assertThat(spans).hasSize(4096);
        assertThat(spans.getFirst().lower()).isEqualTo(ip(2, 0, 0, 0));
        assertThat(spans.getLast().upper()).isEqualTo(ip(2, 255, 255, 255));
    }

    // -------------------------------------------------------------------------
    // createIpSpans — range notation
    // -------------------------------------------------------------------------

    @Test
    void createIpSpans_rangeEquivalentTo18_splitTo20_returnsFourSpansWithRangeType() {
        // 1.0.0.0-1.0.63.255 is the same address space as 1.0.0.0/18
        List<IpSpan> spans = factory.createIpSpans("1.0.0.0-1.0.63.255", 20);

        assertThat(spans).hasSize(4);
        assertThat(spans.getFirst().lower()).isEqualTo(ip(1, 0, 0, 0));
        assertThat(spans.get(3).upper()).isEqualTo(ip(1, 0, 63, 255));
        assertThat(spans).isNotEmpty().allMatch(s -> s.spanType() == IpSpanType.RANGE);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /** Converts dotted-decimal octets to the 32-bit long value used in IpSpan. */
    private static long ip(int a, int b, int c, int d) {
        return ((long) a << 24) | ((long) b << 16) | ((long) c << 8) | d;
    }
}
