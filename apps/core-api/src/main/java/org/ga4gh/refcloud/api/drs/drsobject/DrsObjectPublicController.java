package org.ga4gh.refcloud.api.drs.drsobject;

import org.ga4gh.refcloud.api.drs.authinfo.MultiDrsObjectAuthInfoRequestDTO;
import org.ga4gh.refcloud.api.drs.authinfo.MultiDrsObjectAuthInfoResponseDTO;
import org.ga4gh.refcloud.api.drs.authinfo.MultiDrsObjectRequestDTO;
import org.ga4gh.refcloud.api.drs.authinfo.MultiDrsObjectResponseDTO;
import org.ga4gh.refcloud.api.drs.authinfo.SingleDrsObjectAuthInfoResponseDTO;
import org.ga4gh.refcloud.api.drs.authinfo.SingleDrsObjectRequestDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;

@RestController
@RequestMapping("/ga4gh/drs/v1/objects")
public class DrsObjectPublicController {

    private final DrsObjectService drsObjectService;

    public DrsObjectPublicController(DrsObjectService drsObjectService) {
        this.drsObjectService = drsObjectService;
    }

    @RequestMapping(path = "/{id}", method = RequestMethod.OPTIONS)
    public ResponseEntity<SingleDrsObjectAuthInfoResponseDTO> getSingleDrsObjectAuthInfo(@PathVariable String id) {
        return ResponseEntity.ok(drsObjectService.getDrsObjectAuthInfo(id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@GA4GHPassportTokenEvaluator.canAccessDrsObject(authentication, #id)")
    public ResponseEntity<DrsObjectResponseDTO> getDrsObjectById(@PathVariable String id) {
        DrsObjectResponseDTO drsObject = drsObjectService.getDrsObjectById(id);
        return ResponseEntity.ok(drsObject);
    }

    @PostMapping("/{id}")
    @PreAuthorize("@GA4GHPassportTokenEvaluator.canAccessDrsObject(authentication, #id, #requestBody)")
    public ResponseEntity<DrsObjectResponseDTO> getDrsObjectByIdPostMethod(@PathVariable String id, @Valid @RequestBody SingleDrsObjectRequestDTO requestBody) {
        return ResponseEntity.ok(drsObjectService.getDrsObjectById(id));
    }

    @PreAuthorize("@GA4GHPassportTokenEvaluator.validateBulkAuthInfoRequest(authentication, #requestBody)")
    @RequestMapping(method=RequestMethod.OPTIONS)
    public ResponseEntity<MultiDrsObjectAuthInfoResponseDTO> getMultipleDrsObjectsAuthInfo(@Valid @RequestBody MultiDrsObjectAuthInfoRequestDTO requestBody) {
        return ResponseEntity.ok(drsObjectService.getMultiDrsObjectsAuthInfo(requestBody.bulkObjectIds()));
    }

    @PostMapping()
    @PreAuthorize("@GA4GHPassportTokenEvaluator.validateAllPassports(authentication, #requestBody)")
    public ResponseEntity<MultiDrsObjectResponseDTO> getMultipleDrsObjects(@Valid @RequestBody MultiDrsObjectRequestDTO requestBody) {
        return ResponseEntity.ok(drsObjectService.getMultiDrsObjects(requestBody.passports(), requestBody.bulkObjectIds()));
    }
}
