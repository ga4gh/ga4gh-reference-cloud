package org.ga4gh.refcloud.api.drs.drsobject;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DrsObjectService {

    private final DrsObjectRepository drsObjectRepository;

    public DrsObjectService(DrsObjectRepository drsObjectRepository) {
        this.drsObjectRepository = drsObjectRepository;
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
        return new DrsObjectResponseDTO(
            drsObject.getId(),
            drsObject.getDescription(),
            drsObject.getCreatedTime(),
            drsObject.getMimeType(),
            drsObject.getName(),
            drsObject.getSize(),
            drsObject.getUpdatedTime(),
            drsObject.getVersion()
        );
    }
}
