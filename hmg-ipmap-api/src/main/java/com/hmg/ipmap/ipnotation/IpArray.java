package com.hmg.ipmap.ipnotation;

import java.util.Arrays;
import java.util.Objects;

public record IpArray(String notation, long[] longValues) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IpArray otherArray = (IpArray) o;
        return Objects.equals(notation, otherArray.notation())
                && Arrays.equals(longValues, otherArray.longValues());
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(notation);
        result = 31 * result + Arrays.hashCode(longValues);
        return result;
    }

    @Override
    public String toString() {
        return "IpArray{"
                + "notation='"
                + notation
                + '\''
                + ", longValues="
                + Arrays.toString(longValues)
                + '}';
    }
}
