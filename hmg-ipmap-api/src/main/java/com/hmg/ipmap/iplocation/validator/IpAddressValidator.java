package com.hmg.ipmap.iplocation.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IpAddressValidator implements ConstraintValidator<IpAddress, String> {

    private static final int MIN_OCTET_VALUE = 0;
    private static final int MAX_OCTET_VALUE = 255;
    private static final int EXPECTED_OCTETS = 4;

    @Override
    public boolean isValid(String ip, ConstraintValidatorContext context) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        String[] octets = ip.split("\\.", -1);

        // Must have exactly 4 octets
        if (octets.length != EXPECTED_OCTETS) {
            return false;
        }

        for (String octet : octets) {
            if (!isValidOctet(octet)) {
                return false;
            }
        }

        return true;
    }

    private boolean isValidOctet(String octet) {
        // Check if empty or has leading/trailing whitespace
        if (octet == null || octet.isEmpty() || !octet.equals(octet.trim())) {
            return false;
        }

        // Check for invalid leading zeros (e.g., "01", "001") except "0" itself
        if (octet.length() > 1 && octet.startsWith("0")) {
            return false;
        }

        // Parse and validate range
        try {
            int value = Integer.parseInt(octet);
            return value >= MIN_OCTET_VALUE && value <= MAX_OCTET_VALUE;
        } catch (NumberFormatException _) {
            return false;
        }
    }
}
