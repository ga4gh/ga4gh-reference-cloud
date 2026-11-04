package org.ga4gh.refcloud.api.refget.sequence;

import java.util.Comparator;
import java.util.List;
import org.ga4gh.refcloud.api.drs.drsobject.DrsObject;
import org.ga4gh.refcloud.api.drs.drsobjectchecksum.DrsChecksumType;
import org.ga4gh.refcloud.api.drs.drsobjectchecksum.DrsObjectChecksum;
import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;
import org.ga4gh.refcloud.api.refget.sequencealias.RefgetSequenceAliasResponseDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RefgetSequenceMetadataService {

    private static final String GA4GH_SEQUENCE_PREFIX = "SQ.";

    private final RefgetSequenceRepository refgetSequenceRepository;

    public RefgetSequenceMetadataResponseDTO getSequenceMetadata(String id) {
        RefgetSequenceIdentifier identifier = RefgetSequenceIdentifier.parse(id);
        RefgetSequence refgetSequence = refgetSequenceRepository.findSequenceWithMetadataByChecksum(identifier.type(), identifier.digest())
            .orElseThrow(() -> new ResourceNotFoundException("No refget sequence with ID: " + id));

        return convertToResponseDTO(refgetSequence);
    }

    private RefgetSequenceMetadataResponseDTO convertToResponseDTO(RefgetSequence refgetSequence) {
        DrsObject drsObject = refgetSequence.getDrsObject();

        String sha512t24u = findChecksum(drsObject, DrsChecksumType.sha512t24u);

        // sorted so the alias list is returned in a stable order
        List<RefgetSequenceAliasResponseDTO> aliasDtos = refgetSequence.getAliases()
            .stream()
            .map(alias -> new RefgetSequenceAliasResponseDTO(alias.getId().getAlias(), alias.getId().getNamingAuthority()))
            .sorted(Comparator.comparing(RefgetSequenceAliasResponseDTO::namingAuthority).thenComparing(RefgetSequenceAliasResponseDTO::alias))
            .toList();

        return new RefgetSequenceMetadataResponseDTO(
            new RefgetSequenceMetadataDTO(
                findChecksum(drsObject, DrsChecksumType.md5),
                findChecksum(drsObject, DrsChecksumType.trunc512),
                sha512t24u == null ? null : GA4GH_SEQUENCE_PREFIX + sha512t24u,
                drsObject.getSize(),
                aliasDtos
            )
        );
    }

    // null if the DRS object has no checksum of the given type
    private String findChecksum(DrsObject drsObject, DrsChecksumType type) {
        return drsObject.getChecksums()
            .stream()
            .filter(checksum -> checksum.getType() == type)
            .map(DrsObjectChecksum::getChecksum)
            .findFirst()
            .orElse(null);
    }
}
