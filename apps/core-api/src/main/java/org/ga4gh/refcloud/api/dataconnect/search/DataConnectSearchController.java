package org.ga4gh.refcloud.api.dataconnect.search;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ga4gh/data-connect/v1/search")
public class DataConnectSearchController {

    // TODO: replace raw String body with a validated SearchRequest DTO record
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> search(@RequestBody String searchRequest) {
        return ResponseEntity.ok("you have reached the /search endpoint");
    }
}
