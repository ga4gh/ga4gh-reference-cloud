package org.ga4gh.refcloud.api.drs.drsobject;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ga4gh/drs/v1/objects")
public class DrsObjectPublicController {

    private final DrsObjectService drsObjectService;

    public DrsObjectPublicController(DrsObjectService drsObjectService) {
        this.drsObjectService = drsObjectService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<DrsObjectResponseDTO> getDrsObjectById(@PathVariable String id) {
        DrsObjectResponseDTO drsObject = drsObjectService.getDrsObjectById(id);
        return ResponseEntity.ok(drsObject);
    }
}
