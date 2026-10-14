package org.ga4gh.refcloud.api.refget.sequence;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/ga4gh/refget/v2/sequence")
@RequiredArgsConstructor
public class RefgetSequenceController {

    private static final String REFGET_PLAIN_TEXT = "text/vnd.ga4gh.refget.v2.0.0+plain";

    private static final MediaType REFGET_PLAIN_TEXT_ASCII = new MediaType("text", "vnd.ga4gh.refget.v2.0.0+plain", StandardCharsets.US_ASCII);

    private final RefgetSequenceService refgetSequenceService;

    // unsupported Accept headers are rejected by Spring with 406 Not Acceptable
    @GetMapping(path = "/{id}", produces = { REFGET_PLAIN_TEXT, MediaType.TEXT_PLAIN_VALUE })
    @PreAuthorize("@GA4GHPassportTokenEvaluator.canAccessRefgetSequence(authentication, #id)")
    public ResponseEntity<StreamingResponseBody> getSequenceById(
        @PathVariable String id,
        @RequestParam(required = false) @PositiveOrZero Long start,
        @RequestParam(required = false) @PositiveOrZero Long end,
        @RequestHeader(name = HttpHeaders.RANGE, required = false) String range
    ) {
        RefgetSequenceContent sequenceContent = refgetSequenceService.getSequence(id, start, end, range);

        ResponseEntity.BodyBuilder response = switch (sequenceContent.requestType()) {
            case FULL_SEQUENCE -> ResponseEntity.ok();
            case START_END_PARAMETERS -> ResponseEntity.ok()
                .header(HttpHeaders.ACCEPT_RANGES, "none");
            case RANGE_HEADER -> ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + sequenceContent.start() + "-" + (sequenceContent.end() - 1) + "/" + sequenceContent.sequenceLength());
        };

        return response
            .contentType(REFGET_PLAIN_TEXT_ASCII)
            .contentLength(sequenceContent.contentLength())
            .body(outputStream -> {
                try (InputStream content = sequenceContent.content()) {
                    content.transferTo(outputStream);
                }
            });
    }
}
