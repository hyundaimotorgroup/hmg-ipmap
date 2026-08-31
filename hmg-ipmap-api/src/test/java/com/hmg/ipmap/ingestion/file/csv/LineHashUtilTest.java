package com.hmg.ipmap.ingestion.file.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LineHashUtilTest {

    // -------------------------------------------------------------------------
    // Stability — known inputs must always produce the same UUID
    // -------------------------------------------------------------------------

    /**
     * Pin the exact UUID values produced for known inputs. If the hash algorithm, seed, or byte
     * encoding ever changes these assertions will fail, preventing silent migration of stored
     * {@code line_hash} values without a DB migration.
     */
    @Test
    void knownInput_producesStableHash() {
        UUID hash = LineHashUtil.compute("2750718,2750718,,NL,Netherlands,0,1,1,1,1,0,nl");
        // Value pinned on first passing run — must never change without a DB migration.
        assertThat(hash).isEqualTo(UUID.fromString("91741948-5b94-18d5-7a40-18b3c81d5f77"));
    }

    @Test
    void emptyString_producesStableHash() {
        UUID first = LineHashUtil.compute("");
        UUID second = LineHashUtil.compute("");
        assertThat(first).isEqualTo(second);
    }

    // -------------------------------------------------------------------------
    // Whitespace normalization
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] ''{0}'' and ''{1}'' should produce the same hash")
    @CsvSource({
        "'a,b,c', 'a,b,c '",
        "'a,b,c', ' a,b,c'",
        "'a,b,c', '  a,b,c  '",
        "'a,b,c', '\ta,b,c\t'"
    })
    void surroundingWhitespace_treatedAsIdentical(String base, String padded) {
        assertThat(LineHashUtil.compute(base)).isEqualTo(LineHashUtil.compute(padded));
    }

    @Test
    void internalWhitespace_preservedInHash() {
        // Whitespace inside the line is NOT stripped — these must differ.
        UUID withSpace = LineHashUtil.compute("a, b,c");
        UUID withoutSpace = LineHashUtil.compute("a,b,c");
        assertThat(withSpace).isNotEqualTo(withoutSpace);
    }

    // -------------------------------------------------------------------------
    // Collision resistance (basic)
    // -------------------------------------------------------------------------

    @Test
    void differentLines_produceDifferentHashes() {
        UUID hash1 = LineHashUtil.compute("2750718,NL,Netherlands");
        UUID hash2 = LineHashUtil.compute("2750719,NL,Netherlands");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    // -------------------------------------------------------------------------
    // Determinism
    // -------------------------------------------------------------------------

    @Test
    void sameInput_alwaysProducesSameHash() {
        String line = "2750718,2750718,,NL,Netherlands,0,1,1,1,1,0,nl";
        assertThat(LineHashUtil.compute(line)).isEqualTo(LineHashUtil.compute(line));
    }
}
