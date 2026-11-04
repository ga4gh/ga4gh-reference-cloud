package org.ga4gh.refcloud.api.refget.sequence;

import java.io.InputStream;

// sequence bases to stream back to the client, along with what the controller needs to build
// response headers (206 + Content-Range for Range requests, Accept-Ranges: none for start/end requests).
// start is inclusive, end is exclusive; for circular sequences that wrap the origin, start > end
public record RefgetSequenceContent(
    InputStream content,
    long contentLength,
    RequestType requestType,
    long start,
    long end,
    long sequenceLength
) {

    public enum RequestType {
        FULL_SEQUENCE,
        START_END_PARAMETERS,
        RANGE_HEADER
    }
}
