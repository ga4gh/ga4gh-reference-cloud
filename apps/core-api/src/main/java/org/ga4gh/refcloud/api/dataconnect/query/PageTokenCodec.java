package org.ga4gh.refcloud.api.dataconnect.query;

import java.util.Base64;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

// Page tokens are base64url-encoded JSON (e.g. {"offset":100}), so fields can be added later.
// They are not signed: every page re-runs validation and access checks for the caller,
// so a modified token can only return rows the caller could already read
@Component
@RequiredArgsConstructor
public class PageTokenCodec {

    private final ObjectMapper objectMapper;

    public String encode(PageToken pageToken) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(pageToken));
    }

    public PageToken decode(String token) {
        PageToken pageToken;
        try {
            pageToken = objectMapper.readValue(Base64.getUrlDecoder().decode(token), PageToken.class);
        } catch (IllegalArgumentException | JacksonException e) {
            throw invalidToken();
        }
        if (pageToken == null || pageToken.offset() < 0) {
            throw invalidToken();
        }
        return pageToken;
    }

    private static BadRequestException invalidToken() {
        return new BadRequestException("invalid page_token");
    }
}
