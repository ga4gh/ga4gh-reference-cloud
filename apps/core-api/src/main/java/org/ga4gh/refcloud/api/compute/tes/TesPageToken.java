package org.ga4gh.refcloud.api.compute.tes;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import org.ga4gh.refcloud.api.compute.task.ComputeTask;
import org.ga4gh.refcloud.api.exception.BadRequestException;

// an opaque GET /tasks page token: the (created_at, id) of the last task on the previous page, so the next page
// starts just after it (tasks are listed newest first)
public record TesPageToken(Instant createdAt, String id) {

    public static TesPageToken after(ComputeTask task) {
        return new TesPageToken(task.getCreatedAt(), task.getId());
    }

    public static TesPageToken decode(String token) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int separator = decoded.indexOf('|');
            if (separator < 0) {
                throw new BadRequestException("invalid page_token");
            }
            return new TesPageToken(Instant.parse(decoded.substring(0, separator)), decoded.substring(separator + 1));
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new BadRequestException("invalid page_token");
        }
    }

    public String encode() {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((createdAt + "|" + id).getBytes(StandardCharsets.UTF_8));
    }
}
