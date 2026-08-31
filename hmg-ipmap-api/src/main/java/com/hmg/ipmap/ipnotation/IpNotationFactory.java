package com.hmg.ipmap.ipnotation;

import com.hmg.ipmap.ipnotation.exception.IpNotationBadRequestException;
import com.hmg.ipmap.ipnotation.exception.IpNotationException;
import com.hmg.ipmap.ipnotation.exception.IpNotationUnknownIP;
import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressSeqRange;
import inet.ipaddr.IPAddressString;
import inet.ipaddr.format.IPAddressRange;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Factory for parsing and validating IPv4 notations.
 *
 * <p>Supports three notation types:
 *
 * <ul>
 *   <li>{@link IpNotationType#IP_SINGLE} – a single IPv4 address (e.g., {@code "1.2.3.4"})
 *   <li>{@link IpNotationType#IP_ARRAY} – a comma-separated list of IPv4 addresses
 *   <li>{@link IpNotationType#IP_SPAN} – a range ({@code "a.b.c.d-e.f.g.h"}), CIDR block ({@code
 *       "1.0.0.0/24"}), or wildcard pattern ({@code "192.168.1.*"})
 * </ul>
 */
@Component
public class IpNotationFactory {

    /**
     * Determines the {@link IpNotationType} of the given notation string.
     *
     * @param ipNotation the IP notation string to classify; must not be {@code null}
     * @return an {@link Optional} containing the detected type, or empty if the notation does not
     *     match any known type
     * @throws com.hmg.ipmap.ipnotation.exception.IpNotationException if {@code ipNotation} is
     *     {@code null}
     */
    public Optional<IpNotationType> determineIpNotationType(String ipNotation) {

        if (ipNotation == null) {
            throw new IpNotationException("IP Notation cannot be null");
        }

        if (ipNotation.contains(",")) {
            return Optional.of(IpNotationType.IP_ARRAY);
        }
        if (determineIpSpanType(ipNotation).isPresent()) {
            return Optional.of(IpNotationType.IP_SPAN);
        }
        if (ipNotation.contains(".")) {
            return Optional.of(IpNotationType.IP_SINGLE);
        }
        return Optional.empty();
    }

    /**
     * Parses a comma-separated list of IPv4 addresses into an {@link IpArray}.
     *
     * @param ipNotation a comma-separated string of IPv4 addresses (e.g., {@code
     *     "1.2.3.4,5.6.7.8"})
     * @return an {@link IpArray} containing the original notation and each address as a long value
     * @throws com.hmg.ipmap.ipnotation.exception.IpNotationUnknownIP if any address in the list
     *     cannot be parsed
     */
    public IpArray createIpArray(String ipNotation) {
        String[] parts = ipNotation.split(",");
        long[] longValues = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            try {
                IPAddressString ipStr = new IPAddressString(part);
                longValues[i] = ipStr.toAddress().toIPv4().longValue();
            } catch (AddressStringException _) {
                throw new IpNotationUnknownIP(part);
            }
        }
        return new IpArray(ipNotation, longValues);
    }

    /**
     * Parses {@code ipNotation} and splits it into a list of {@link IpSpan} prefix blocks of size
     * {@code /prefixLength}. Accepts CIDR, RANGE, and WILDCARD notations.
     *
     * @param ipNotation the IP notation string to parse
     * @param prefixLength the subnet prefix length used to divide the address range; a value
     *     outside {@code [0, 32]} produces a single span covering the full range
     * @return list of {@link IpSpan} covering the address range
     * @throws IpNotationException if the notation type cannot be determined or the address is
     *     invalid
     */
    public List<IpSpan> createIpSpans(String ipNotation, int prefixLength) {
        IpSpanType ipSpanType =
                determineIpSpanType(ipNotation)
                        .orElseThrow(
                                () ->
                                        new IpNotationException(
                                                "Unknown IpSpanType from ipNotation: "
                                                        + ipNotation));

        try {
            if (IpSpanType.RANGE == ipSpanType) {
                String[] parts = ipNotation.split("-");
                IPAddressString ipAddressString1 = new IPAddressString(parts[0].trim());
                IPAddressString ipAddressString2 = new IPAddressString(parts[1].trim());
                IPAddressSeqRange range =
                        ipAddressString1.toAddress().spanWithRange(ipAddressString2.toAddress());
                return buildIpSpans(ipNotation, ipSpanType, range, prefixLength);
            }
            IPAddressString ipAddressString = new IPAddressString(ipNotation);
            IPAddress address = ipAddressString.toAddress();
            return buildIpSpans(ipNotation, ipSpanType, address, prefixLength);

        } catch (AddressStringException e) {
            throw new IpNotationUnknownIP(ipNotation, e);
        }
    }

    private List<IpSpan> buildIpSpans(
            String ipNotation,
            IpSpanType ipSpanType,
            IPAddressRange ipAddressRange,
            int prefixLength) {
        long lower = ipAddressRange.getLower().toIPv4().longValue();
        long upper = ipAddressRange.getUpper().toIPv4().longValue();

        if (prefixLength < 0 || prefixLength > 32) {
            return List.of(new IpSpan(ipNotation, ipSpanType, lower, upper));
        }

        List<IpSpan> ipSpans = new ArrayList<>();
        Iterator<? extends IPAddressRange> it =
                ipAddressRange
                        .getLower()
                        .spanWithRange(ipAddressRange.getUpper())
                        .prefixIterator(prefixLength);

        while (it.hasNext()) {
            IPAddressRange prefixBlock = it.next();
            long blockLower = prefixBlock.getLower().toIPv4().longValue();
            long blockUpper = prefixBlock.getUpper().toIPv4().longValue();
            ipSpans.add(new IpSpan(ipNotation, ipSpanType, blockLower, blockUpper));
        }
        return ipSpans;
    }

    /**
     * Determines the {@link IpSpanType} of the given span notation string.
     *
     * @param ipNotation the IP notation string to classify
     * @return an {@link Optional} containing {@link IpSpanType#RANGE} if the notation contains
     *     {@code '-'}, {@link IpSpanType#CIDR} if it contains {@code '/'}, {@link
     *     IpSpanType#WILDCARD} if it contains {@code '*'}, or empty if none match
     */
    public Optional<IpSpanType> determineIpSpanType(String ipNotation) {
        if (ipNotation.contains("-")) {
            return Optional.of(IpSpanType.RANGE);
        } else if (ipNotation.contains("/")) {
            return Optional.of(IpSpanType.CIDR);
        } else if (ipNotation.contains("*")) {
            return Optional.of(IpSpanType.WILDCARD);
        }
        return Optional.empty();
    }

    /**
     * Parses a single IPv4 address string into an {@link IpSingle}.
     *
     * @param ipNotation a plain IPv4 address string (e.g., {@code "1.2.3.4"})
     * @return an {@link IpSingle} containing the normalized address string and its long value
     * @throws com.hmg.ipmap.ipnotation.exception.IpNotationUnknownIP if the address cannot be
     *     parsed
     */
    public IpSingle createIpSingle(String ipNotation) {
        try {
            IPAddressString ipStr = new IPAddressString(ipNotation);
            IPAddress addr = ipStr.toAddress();

            long longValue = addr.toIPv4().longValue();

            return new IpSingle(ipStr.toString(), longValue);
        } catch (AddressStringException _) {
            throw new IpNotationUnknownIP(ipNotation);
        }
    }

    /**
     * Maps an IP address to the subnet it belongs to at the given prefix length.
     *
     * <p>For example, {@code mapIpToSubnet("1.0.100.123", 20)} returns {@code "1.0.96.0/20"},
     * because 1.0.100.123 falls within the /20 block starting at 1.0.96.0.
     *
     * @param ipAddress a plain IPv4 address string (e.g., {@code "1.0.100.123"})
     * @param prefixLength subnet prefix length, must be between 0 and 32 inclusive
     * @return CIDR string of the containing subnet (e.g., {@code "1.0.96.0/20"})
     * @throws IpNotationBadRequestException if the prefix length is out of range
     * @throws IpNotationUnknownIP if the IP address cannot be parsed
     */
    public String mapIpToSubnet(String ipAddress, int prefixLength) {
        if (prefixLength < 0 || prefixLength > 32) {
            throw new IpNotationBadRequestException(
                    "Prefix length must be between 0 and 32, got: " + prefixLength);
        }
        try {
            IPAddressString ipStr = new IPAddressString(ipAddress);
            IPAddress subnetBlock = ipStr.toAddress().toIPv4().toPrefixBlock(prefixLength);
            return subnetBlock.getLower().withoutPrefixLength().toIPv4().toString()
                    + "/"
                    + prefixLength;
        } catch (AddressStringException e) {
            throw new IpNotationUnknownIP(ipAddress, e);
        }
    }

    /**
     * Converts a CIDR subnet to one or more subnets at a different prefix length.
     *
     * <ul>
     *   <li><b>Splitting</b> (targetPrefixLength &gt; input prefix): subdivides the subnet into
     *       smaller blocks. e.g. {@code convertSubnet("1.0.32.0/19", 20)} → {@code ["1.0.32.0/20",
     *       "1.0.48.0/20"]}
     *   <li><b>Aggregating</b> (targetPrefixLength &lt; input prefix): returns the coarser block
     *       that contains the subnet. e.g. {@code convertSubnet("1.0.48.0/20", 19)} → {@code
     *       ["1.0.32.0/19"]}
     *   <li><b>Same prefix</b>: returns a list with the normalized network address of the input.
     * </ul>
     *
     * @param subnet a CIDR notation string (e.g., {@code "1.0.32.0/19"})
     * @param targetPrefixLength target prefix length, must be between 0 and 32 inclusive
     * @return list of CIDR strings at the target prefix length
     * @throws IpNotationBadRequestException if the target prefix length is out of range
     * @throws IpNotationUnknownIP if the subnet cannot be parsed
     */
    public List<String> convertSubnet(String subnet, int targetPrefixLength) {
        if (targetPrefixLength < 0 || targetPrefixLength > 32) {
            throw new IpNotationBadRequestException(
                    "Prefix length must be between 0 and 32, got: " + targetPrefixLength);
        }
        try {
            IPAddressString ipStr = new IPAddressString(subnet);
            IPAddress addr = ipStr.toAddress(); // keep prefix intact — do NOT call toIPv4() here
            Integer inputPrefix = ipStr.getNetworkPrefixLength();
            int inputPrefixLen = inputPrefix != null ? inputPrefix : 32;

            if (targetPrefixLength >= inputPrefixLen) {
                Iterator<? extends IPAddressRange> iterator =
                        addr.getLower()
                                .spanWithRange(addr.getUpper())
                                .prefixIterator(targetPrefixLength);
                List<String> result = new ArrayList<>();
                while (iterator.hasNext()) {
                    IPAddressRange block = iterator.next();
                    result.add(
                            block.getLower().withoutPrefixLength().toIPv4().toString()
                                    + "/"
                                    + targetPrefixLength);
                }
                return result;
            } else {
                IPAddress containing = addr.getLower().toIPv4().toPrefixBlock(targetPrefixLength);
                return List.of(
                        containing.getLower().withoutPrefixLength().toIPv4().toString()
                                + "/"
                                + targetPrefixLength);
            }
        } catch (AddressStringException e) {
            throw new IpNotationUnknownIP(subnet, e);
        }
    }

    /**
     * Validates the given IP notation string, throwing an exception if it is blank, unrecognized,
     * or fails format-specific validation rules.
     *
     * <p>Delegates to type-specific validators:
     *
     * <ul>
     *   <li>{@link IpNotationType#IP_SINGLE} – validates a single IPv4 address
     *   <li>{@link IpNotationType#IP_ARRAY} – validates each address in a comma-separated list
     *   <li>{@link IpNotationType#IP_SPAN} – validates RANGE, CIDR, or WILDCARD format
     * </ul>
     *
     * @param notation the IP notation string to validate
     * @throws com.hmg.ipmap.ipnotation.exception.IpNotationBadRequestException if the notation is
     *     blank, has an unrecognized type, or fails format validation
     * @throws com.hmg.ipmap.ipnotation.exception.IpNotationUnknownIP if the notation is a span of
     *     an unexpected span type
     * @throws UnknownHostException if CIDR validation cannot resolve the host address
     */
    public void validationIpNotation(String notation) throws UnknownHostException {
        if (StringUtils.isBlank(notation)) {
            throw new IpNotationBadRequestException("IP notation must not be null or empty");
        }

        String trimmed = notation.trim();

        Optional<IpNotationType> ipNotationType = determineIpNotationType(trimmed);

        if (ipNotationType.isEmpty()) {
            throw new IpNotationBadRequestException(
                    "Unknown ipNotationType from ipNotationType: " + trimmed);
        }

        if (ipNotationType.get().equals(IpNotationType.IP_SPAN)) {
            Optional<IpSpanType> ipSpanTypeOpt = determineIpSpanType(trimmed);

            if (ipSpanTypeOpt.isEmpty()) {
                throw new IpNotationBadRequestException(
                        "Unknown IpSpanType from ipSpanType: " + trimmed);
            }

            IpSpanType ipSpanType = ipSpanTypeOpt.get();

            switch (ipSpanType) {
                case RANGE -> validateRange(trimmed);
                case CIDR -> validateCidr(trimmed);
                case WILDCARD -> validateWildcard(trimmed);
                default -> throw new IpNotationUnknownIP(trimmed);
            }
        } else if (ipNotationType.get().equals(IpNotationType.IP_SINGLE)) {

            validateSingleIp(trimmed);
        } else if (ipNotationType.get().equals(IpNotationType.IP_ARRAY)) {
            validateIpArray(trimmed);

        } else {
            throw new IpNotationBadRequestException(
                    "Unknown IpNotationType from ipNotation: " + trimmed);
        }
    }

    private static final String IP_OCTET = "(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)";
    private static final Pattern RANGE_PATTERN =
            Pattern.compile(
                    "^" + IP_OCTET + "\\." + IP_OCTET + "\\." + IP_OCTET + "\\." + IP_OCTET + "-"
                            + IP_OCTET + "\\." + IP_OCTET + "\\." + IP_OCTET + "\\." + IP_OCTET
                            + "$");

    private void validateRange(String input) {

        if (!RANGE_PATTERN.matcher(input).matches()) {
            throw new IpNotationBadRequestException(
                    "IP range ("
                            + input
                            + ") format invalid. Accepted format e.g.: 192.168.0.1-192.168.0.254");
        }

        String[] parts = input.split("-");
        if (parts.length != 2) {
            throw new IpNotationBadRequestException(
                    "IP range (" + input + ") must contain exactly one ' - ' separator");
        }
        IPAddressString ipAddressString1 = new IPAddressString(parts[0]);
        IPAddressString ipAddressString2 = new IPAddressString(parts[1]);

        try {
            long ip1 = ipAddressString1.toAddress().toIPv4().longValue();
            long ip2 = ipAddressString2.toAddress().toIPv4().longValue();
            if (ip1 > ip2) {
                throw new IpNotationBadRequestException(
                        "Invalid IP range: start IP is greater than end IP (" + input + ")");
            }
        } catch (AddressStringException e) {
            throw new IpNotationBadRequestException(
                    "Unable to parse the ip address (" + input + "). " + e.getMessage());
        }
    }

    // IPv4 octet: 0-255
    private static final String IP_OCTET_REGEX = "(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)";

    // IPv4 address: four octets separated by dots
    private static final String IPV4_REGEX = IP_OCTET_REGEX + "(\\." + IP_OCTET_REGEX + "){3}";

    // CIDR: IPv4 + '/' + prefix length (0-32)
    private static final String CIDR_REGEX = "^" + IPV4_REGEX + "/(\\d|[12]\\d|3[0-2])$";

    private static final Pattern CIDR_PATTERN = Pattern.compile(CIDR_REGEX);

    private void validateCidr(String input) throws UnknownHostException {

        Matcher matcher = CIDR_PATTERN.matcher(input);
        if (!matcher.matches()) {
            throw new IpNotationBadRequestException("CIDR format invalid: " + input);
        }

        String[] parts = input.split("/");
        String ipPart = parts[0];
        int prefixLength = Integer.parseInt(parts[1]);

        int ipAsInt = byteArrayToInt(InetAddress.getByName(ipPart).getAddress());
        int mask = prefixLength == 0 ? 0 : 0xFFFFFFFF << (32 - prefixLength);
        int networkAddress = ipAsInt & mask;

        if (ipAsInt != networkAddress) {
            throw new IpNotationBadRequestException("CIDR IP is not a network address: " + input);
        }
    }

    private static final String OCTET = "(25[0-5]|2[0-4]\\d|1?\\d{1,2})";
    private static final Pattern WILDCARD_PATTERN =
            Pattern.compile("^" + OCTET + "\\." + OCTET + "\\." + OCTET + "\\.\\*$");

    private void validateWildcard(String input) {

        if (!WILDCARD_PATTERN.matcher(input).matches()) {
            throw new IpNotationBadRequestException("Wildcard format invalid: " + input);
        }
    }

    private int byteArrayToInt(byte[] bytes) {
        int result = 0;
        for (byte b : bytes) {
            result = (result << 8) | (b & 0xFF);
        }
        return result;
    }

    private static final String IP_OCTET_SINGLE = "(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)";
    private static final Pattern IP_ADDRESS_PATTERN =
            Pattern.compile(
                    "^"
                            + IP_OCTET_SINGLE
                            + "\\."
                            + IP_OCTET_SINGLE
                            + "\\."
                            + IP_OCTET_SINGLE
                            + "\\."
                            + IP_OCTET_SINGLE
                            + "$");

    private void validateSingleIp(String input) {

        if (!IP_ADDRESS_PATTERN.matcher(input).matches()) {
            throw new IpNotationBadRequestException("Invalid IP address format: " + input);
        }
    }

    private void validateIpArray(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IpNotationBadRequestException("IP Array cannot be empty.");
        }

        String[] ipAddresses =
                Arrays.stream(input.split(",")).map(String::trim).toArray(String[]::new);

        if (ipAddresses.length == 0) {
            throw new IpNotationBadRequestException("IP list does not contain any addresses.");
        }

        for (String ip : ipAddresses) {
            validateSingleIp(ip);
        }
    }
}
