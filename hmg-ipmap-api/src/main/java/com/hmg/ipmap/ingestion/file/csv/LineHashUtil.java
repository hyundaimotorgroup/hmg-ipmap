package com.hmg.ipmap.ingestion.file.csv;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.openhft.hashing.LongTupleHashFunction;

/** Utility for computing a stable deduplication hash for a raw CSV line. */
final class LineHashUtil {

    private LineHashUtil() {}

    /**
     * Returns the xxHash3 128-bit digest of the given raw CSV line as a {@link UUID}.
     *
     * <p><b>Whitespace normalization:</b> leading and trailing whitespace (including Unicode
     * whitespace recognized by {@link String#strip()}) is removed before hashing. Two lines that
     * differ only in surrounding whitespace therefore produce the same hash and are treated as
     * duplicates. CSV files may contain trailing spaces that carry no semantic meaning.
     *
     * <p><b>Hash stability:</b> the digest is produced by xxHash3 128-bit (zero-allocation-hashing,
     * {@code LongTupleHashFunction.xx128()}) with its default seed. The algorithm and seed must not
     * be changed without also migrating all existing {@code line_hash} values in {@code
     * batch_file_detail}, as stored hashes would no longer match newly computed ones.
     *
     * <p><b>UUID encoding:</b> the two 64-bit words written into {@code hash[0]} and {@code
     * hash[1]} by {@code hashBytes} are mapped directly to the most-significant and
     * least-significant fields of a {@link UUID}. The resulting UUID is <em>not</em> RFC 4122
     * compliant — it is used purely as a compact 128-bit deduplication key.
     */
    static UUID compute(String rawLine) {
        byte[] bytes = rawLine.strip().getBytes(StandardCharsets.UTF_8);
        long[] hash = new long[2];
        LongTupleHashFunction.xx128().hashBytes(bytes, hash);
        return new UUID(hash[0], hash[1]);
    }
}
