package com.hmg.ipmap.ingestion.file.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Application event published after a ZIP file has been successfully uploaded and persisted.
 *
 * <p>Listeners can consume this event to trigger downstream processing (e.g. ZIP extraction)
 * without coupling the upload flow to its consumers.
 */
@Getter
public class UploadEvent extends ApplicationEvent {

    private final long fileZipId;

    /**
     * Creates an {@code UploadEvent} for the given file ZIP record.
     *
     * @param source the object that published this event (typically the uploading service)
     * @param fileZipId the primary key of the persisted {@code BatchFileZipEntity}
     */
    public UploadEvent(Object source, long fileZipId) {
        super(source);
        this.fileZipId = fileZipId;
    }
}
