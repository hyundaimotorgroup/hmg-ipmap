package com.hmg.ipmap.ingestion.file.enums;

/**
 * Marker interface for file type classifications. Implemented by both generic ({@link
 * DefaultFileType}) and provider-specific enums, allowing the batch pipeline to be
 * provider-agnostic while each provider can define its own granular types.
 *
 * <p>All implementations are expected to be enums, so {@link #name()} returns the enum constant
 * name, which is used as the database column value.
 */
public interface FileType {
    String name();
}
