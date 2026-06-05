package org.ga4gh.refcloud.api.drs.drsobject;

import java.util.Set;
import java.util.stream.Collectors;

import org.ga4gh.refcloud.api.drs.DrsConfig;
import org.ga4gh.refcloud.api.drs.drsobjectalias.DrsObjectAlias;
import org.ga4gh.refcloud.api.drs.drsobjectalias.DrsObjectAliasId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DrsObjectService {

    private final DrsObjectRepository drsObjectRepository;

    private final DrsConfig drsConfig;

    public DrsObjectService(DrsObjectRepository drsObjectRepository, DrsConfig drsConfig) {
        this.drsObjectRepository = drsObjectRepository;
        this.drsConfig = drsConfig;
    }

    @Transactional(readOnly = true)
    public DrsObjectResponseDTO getDrsObjectById(String id) {
        DrsObject drsObject = drsObjectRepository.findById(id).orElse(null);
        if (drsObject == null) {
            return null;
        }
        return convertToResponseDTO(drsObject);
    }

    private DrsObjectResponseDTO convertToResponseDTO(DrsObject drsObject) {
        Set<String> aliases = drsObject.getAliases()
                .stream()
                .map(DrsObjectAlias::getId)
                .map(DrsObjectAliasId::getAlias)
                .collect(Collectors.toSet());

        return new DrsObjectResponseDTO(
            drsObject.getId(),
            drsObject.getName(),
            generateDrsUri(drsObject.getId()),
            drsObject.getSize(),
            drsObject.getCreatedTime(),
            drsObject.getUpdatedTime(),
            drsObject.getVersion(),
            drsObject.getMimeType(),
            drsObject.getDescription(),
            aliases
        );
    }

    private String generateDrsUri(String id) {
        return "drs://" + drsConfig.hostDomain() + "/" + id;
    }
}
