package org.ga4gh.refcloud.api.refget.sequence;

import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.ga4gh.refcloud.api.drs.awss3accessobject.AwsS3AccessObject;
import org.ga4gh.refcloud.api.drs.awss3accessobject.AwsS3ObjectReader;
import org.ga4gh.refcloud.api.drs.drsobject.DrsObject;
import org.ga4gh.refcloud.api.drs.drsobject.DrsObjectService;
import org.ga4gh.refcloud.api.exception.BadRequestException;
import org.ga4gh.refcloud.api.exception.NotImplementedException;
import org.ga4gh.refcloud.api.exception.RangeNotSatisfiableException;
import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;
import org.ga4gh.refcloud.api.refget.RefgetConfig;
import org.ga4gh.refcloud.api.refget.RefgetConfig.Sequence.ServiceInfo.ServiceInfoRefgetCustom;
import org.ga4gh.refcloud.api.refget.sequence.RefgetSequenceContent.RequestType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RefgetSequenceService {

    // single byte range only, either "bytes=first-last" or "bytes=first-"
    private static final Pattern RANGE_HEADER_PATTERN = Pattern.compile("^bytes=(\\d+)-(\\d*)$");

    private final RefgetSequenceRepository refgetSequenceRepository;

    private final DrsObjectService drsObjectService;

    private final AwsS3ObjectReader awsS3ObjectReader;

    private final RefgetConfig refgetConfig;

    // userId is null for anonymous requests, which may only access sequences in open datasets
    public boolean validateUserIsAuthorizedForSequence(String userId, String id) {
        RefgetSequence refgetSequence = loadSequence(id);

        if (refgetSequence.getDrsObject().getDataset().getPassportVisa() == null) {
            return true; // open dataset, no visa required
        }

        if (userId == null) {
            return false;
        }

        return drsObjectService.validateUserIsAuthorizedForDrsObject(userId, refgetSequence.getDrsObjectId());
    }

    // start/end follow refget query parameter semantics (0-based, end exclusive),
    // rangeHeader follows HTTP Range semantics (0-based, last inclusive)
    public RefgetSequenceContent getSequence(String id, Long start, Long end, String rangeHeader) {
        RefgetSequence refgetSequence = loadSequence(id);
        DrsObject drsObject = refgetSequence.getDrsObject();
        long sequenceLength = drsObject.getSize();

        if (rangeHeader != null) {
            if (start != null || end != null) {
                throw new BadRequestException("start/end parameters cannot be combined with a Range header");
            }
            return getSequenceByRangeHeader(drsObject, rangeHeader, sequenceLength);
        }

        if (start != null || end != null) {
            return getSequenceByStartEnd(refgetSequence, start, end, sequenceLength);
        }

        return new RefgetSequenceContent(
            readSequence(drsObject, 0, sequenceLength),
            sequenceLength,
            RequestType.FULL_SEQUENCE,
            0,
            sequenceLength,
            sequenceLength
        );
    }

    private RefgetSequenceContent getSequenceByRangeHeader(DrsObject drsObject, String rangeHeader, long sequenceLength) {
        Matcher matcher = RANGE_HEADER_PATTERN.matcher(rangeHeader.trim());
        if (!matcher.matches()) {
            throw new BadRequestException("Range header must specify a single byte range: " + rangeHeader);
        }

        long first = parseCoordinate(matcher.group(1));
        long last = matcher.group(2).isEmpty() ? sequenceLength - 1 : parseCoordinate(matcher.group(2));

        if (first > last) {
            throw new BadRequestException("Range header first byte cannot be greater than last byte: " + rangeHeader);
        }
        if (last >= sequenceLength) {
            throw new BadRequestException("Range header is out of bounds of the sequence: " + rangeHeader);
        }

        long end = last + 1;
        return new RefgetSequenceContent(
            readSequence(drsObject, first, end),
            end - first,
            RequestType.RANGE_HEADER,
            first,
            end,
            sequenceLength
        );
    }

    private RefgetSequenceContent getSequenceByStartEnd(RefgetSequence refgetSequence, Long start, Long end, long sequenceLength) {
        long resolvedStart = start == null ? 0 : start;
        long resolvedEnd = end == null ? sequenceLength : end;

        if (resolvedStart < 0 || resolvedEnd < 0) {
            throw new BadRequestException("start and end cannot be negative");
        }
        if (resolvedStart > sequenceLength) {
            throw new BadRequestException("start cannot be greater than the sequence length");
        }
        if (resolvedEnd > sequenceLength) {
            throw new RangeNotSatisfiableException("end cannot be greater than the sequence length");
        }

        ServiceInfoRefgetCustom refgetServiceInfo = refgetConfig.sequence().serviceInfo().refget();
        DrsObject drsObject = refgetSequence.getDrsObject();
        InputStream content;
        long contentLength;

        if (resolvedStart > resolvedEnd) {
            if (!refgetServiceInfo.circularSupported()) {
                throw new NotImplementedException("circular sequences are not supported, start cannot be greater than end");
            }
            if (!refgetSequence.getIsCircular()) {
                throw new RangeNotSatisfiableException("start cannot be greater than end for a non-circular sequence");
            }

            // wrap around the origin: start to the end of the sequence, then the beginning of the sequence to end
            contentLength = (sequenceLength - resolvedStart) + resolvedEnd;
            requireWithinSubsequenceLimit(refgetServiceInfo, contentLength);
            content = new SequenceInputStream(
                readSequence(drsObject, resolvedStart, sequenceLength),
                readSequence(drsObject, 0, resolvedEnd)
            );
        } else {
            contentLength = resolvedEnd - resolvedStart;
            requireWithinSubsequenceLimit(refgetServiceInfo, contentLength);
            content = readSequence(drsObject, resolvedStart, resolvedEnd);
        }

        return new RefgetSequenceContent(
            content,
            contentLength,
            RequestType.START_END_PARAMETERS,
            resolvedStart,
            resolvedEnd,
            sequenceLength
        );
    }

    private void requireWithinSubsequenceLimit(ServiceInfoRefgetCustom refgetServiceInfo, long contentLength) {
        Long subsequenceLimit = refgetServiceInfo.subsequenceLimit();
        if (subsequenceLimit != null && contentLength > subsequenceLimit) {
            throw new RangeNotSatisfiableException("requested subsequence exceeds the subsequence limit of " + subsequenceLimit);
        }
    }

    // start inclusive, end exclusive
    private InputStream readSequence(DrsObject drsObject, long start, long end) {
        if (start >= end) {
            return InputStream.nullInputStream(); // S3 cannot serve an empty byte range
        }

        AwsS3AccessObject awsS3AccessObject = drsObject.getAwsS3AccessObjects()
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No S3 access object for DRS Object with ID: " + drsObject.getId()));

        return awsS3ObjectReader.readByteRange(awsS3AccessObject, start, end - 1);
    }

    private long parseCoordinate(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new BadRequestException("invalid byte position: " + value);
        }
    }

    private RefgetSequence loadSequence(String id) {
        RefgetSequenceIdentifier identifier = RefgetSequenceIdentifier.parse(id);
        return refgetSequenceRepository.findSequenceByChecksum(identifier.type(), identifier.digest())
            .orElseThrow(() -> new ResourceNotFoundException("No refget sequence with ID: " + id));
    }
}
