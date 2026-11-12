package org.ga4gh.refcloud.api.compute;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import org.ga4gh.refcloud.api.exception.BadRequestException;

// an opaque page token for the compute list endpoints (TES GET /tasks, WES GET /runs and GET /runs/{run_id}/tasks):
// the (created_at, id) of the last item on the previous page, so the next page starts just after it (items are listed
// newest first)
public record ComputePageToken(Instant createdAt, String id) {

    public static ComputePageToken decode(String token) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int separator = decoded.indexOf('|');
            if (separator < 0) {
                throw new BadRequestException("invalid page_token");
            }
            return new ComputePageToken(
                    Instant.parse(decoded.substring(0, separator)), decoded.substring(separator + 1));
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
