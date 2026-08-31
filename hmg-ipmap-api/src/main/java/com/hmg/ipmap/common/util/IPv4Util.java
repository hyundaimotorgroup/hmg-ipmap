package com.hmg.ipmap.common.util;

public final class IPv4Util {

    private IPv4Util() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static int[] longToIPv4Array(long ip) {
        return new int[] {
            (int) ((ip >> 24) & 0xFF),
            (int) ((ip >> 16) & 0xFF),
            (int) ((ip >> 8) & 0xFF),
            (int) (ip & 0xFF)
        };
    }

    public static String longToIpString(long ip) {
        return String.format(
                "%d.%d.%d.%d", (ip >> 24) & 0xFF, (ip >> 16) & 0xFF, (ip >> 8) & 0xFF, ip & 0xFF);
    }

    public static long ipv4ToLong(String ipAddress) {
        String[] octets = ipAddress.split("\\.");
        if (octets.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + ipAddress);
        }

        long result = 0;
        for (int i = 0; i < 4; i++) {
            int octet = Integer.parseInt(octets[i]);
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("Invalid octet value: " + octet);
            }
            result |= ((long) octet << (24 - (8 * i)));
        }
        return result;
    }
}
