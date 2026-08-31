package com.hmg.ipmap.ingestion.file.job.error;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class ErrorCollector {

    private ErrorCollector() {}

    private static final ThreadLocal<List<FileDetailError>> TL_ERRORS =
            ThreadLocal.withInitial(ArrayList::new);

    /**
     * Clears all errors accumulated by the current thread.
     *
     * <p>Must be called at the start of each chunk to ensure errors from a previous chunk do not
     * bleed into the next.
     */
    public static void startChunk() {
        TL_ERRORS.get().clear();
    }

    /**
     * Appends an error for the given file detail record to the current thread's error list.
     *
     * @param fileDetailId the ID of the file detail record that failed
     * @param message a description of the error
     */
    public static void add(Long fileDetailId, String message) {
        TL_ERRORS
                .get()
                .add(FileDetailError.builder().fileDetailId(fileDetailId).message(message).build());
    }

    /**
     * Passes the current thread's error list to the given {@code sink} consumer if it is non-empty,
     * then clears the list and removes the thread-local to prevent memory leaks.
     *
     * <p>The list passed to the sink is unmodifiable.
     *
     * @param sink the consumer that persists or processes the collected errors
     */
    public static void drain(Consumer<List<FileDetailError>> sink) {
        List<FileDetailError> list = TL_ERRORS.get();
        if (!list.isEmpty()) {
            sink.accept(Collections.unmodifiableList(list));
            list.clear();
        }
        TL_ERRORS.remove();
    }

    /**
     * Returns {@code true} if no error has been recorded for the given file detail ID in the
     * current thread's error list.
     *
     * @param fileDetailId the file detail record ID to check
     * @return {@code true} if the record has no associated errors, {@code false} otherwise
     */
    public static boolean hasNoError(Long fileDetailId) {
        return TL_ERRORS.get().stream()
                .noneMatch(error -> error.getFileDetailId().equals(fileDetailId));
    }
}
