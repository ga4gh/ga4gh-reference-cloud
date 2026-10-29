package org.ga4gh.refcloud.api.refget.sequence;

import java.util.List;
import org.ga4gh.refcloud.api.refget.sequencealias.RefgetSequenceAliasResponseDTO;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

// checksums are null when the sequence has no stored checksum of that type.
// trunc512 is not part of the refget v2 metadata response, but is included as an additional field
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RefgetSequenceMetadataDTO (
    String md5,
    String trunc512,
    String ga4gh,
    Long length,
    List<RefgetSequenceAliasResponseDTO> aliases
) {}
