package com.hmg.ipmap.ipnotation;

/**
 * Immutable value object representing a resolved IP address range derived from a notation.
 *
 * <p>Stores the original notation string, the span type used to derive the range, and the
 * lower/upper bounds as {@code long} values for efficient numeric range comparison.
 *
 * @param notation the original IP notation string (e.g., {@code "192.168.0.0/24"})
 * @param spanType the type of span: {@link IpSpanType#CIDR}, {@link IpSpanType#RANGE}, or {@link
 *     IpSpanType#WILDCARD}
 * @param lower inclusive lower bound of the IP range as a long value
 * @param upper inclusive upper bound of the IP range as a long value
 */
public record IpSpan(String notation, IpSpanType spanType, long lower, long upper) {}
