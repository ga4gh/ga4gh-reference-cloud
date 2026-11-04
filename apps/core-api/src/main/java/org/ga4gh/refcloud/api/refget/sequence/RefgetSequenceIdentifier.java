package org.ga4gh.refcloud.api.refget.sequence;

import java.util.Locale;
import org.ga4gh.refcloud.api.drs.drsobjectchecksum.DrsChecksumType;
import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;

public record RefgetSequenceIdentifier(
    DrsChecksumType type,
    String digest
) {

    private static final String GA4GH_NAMESPACE = "ga4gh:";
    private static final String MD5_NAMESPACE = "md5:";
    private static final String TRUNC512_NAMESPACE = "TRUNC512:";
    private static final String GA4GH_SEQUENCE_PREFIX = "SQ.";
    private static final int MD5_LENGTH = 32;
    private static final int TRUNC512_LENGTH = 48;

    // converts a refget sequence ID (e.g. "ga4gh:SQ.<digest>", "SQ.<digest>", "md5:<digest>", "<md5 digest>")
    // into the checksum type and unprefixed digest stored in drs_object_checksum
    public static RefgetSequenceIdentifier parse(String id) {
        if (id.startsWith(GA4GH_NAMESPACE + GA4GH_SEQUENCE_PREFIX)) {
            return new RefgetSequenceIdentifier(DrsChecksumType.sha512t24u, id.substring(GA4GH_NAMESPACE.length() + GA4GH_SEQUENCE_PREFIX.length()));
        }
        if (id.startsWith(GA4GH_SEQUENCE_PREFIX)) {
            return new RefgetSequenceIdentifier(DrsChecksumType.sha512t24u, id.substring(GA4GH_SEQUENCE_PREFIX.length()));
        }
        if (id.startsWith(MD5_NAMESPACE)) {
            return hexIdentifier(DrsChecksumType.md5, id.substring(MD5_NAMESPACE.length()));
        }
        if (id.startsWith(TRUNC512_NAMESPACE)) {
            return hexIdentifier(DrsChecksumType.trunc512, id.substring(TRUNC512_NAMESPACE.length()));
        }

        // unprefixed IDs: checksum type is inferred from digest length
        if (id.length() == MD5_LENGTH) {
            return hexIdentifier(DrsChecksumType.md5, id);
        }
        if (id.length() == TRUNC512_LENGTH) {
            return hexIdentifier(DrsChecksumType.trunc512, id);
        }

        throw new ResourceNotFoundException("No refget sequence with ID: " + id);
    }

    // hex digests are case-insensitive, normalize to lowercase to match stored checksums
    private static RefgetSequenceIdentifier hexIdentifier(DrsChecksumType type, String digest) {
        return new RefgetSequenceIdentifier(type, digest.toLowerCase(Locale.ROOT));
    }
}
