package org.ga4gh.refcloud.api.dataconnect.search;

import org.ga4gh.refcloud.api.dataconnect.query.TableDataResponseDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/ga4gh/data-connect/v1/search")
@RequiredArgsConstructor
public class DataConnectSearchController {

    private final DataConnectSearchService dataConnectSearchService;

    // open endpoint: anonymous requests (jwt == null) can query only platform-wide and open tables,
    // a passport token additionally allows tables in datasets the user has an approved visa for
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TableDataResponseDTO> search(
        @Valid @RequestBody SearchRequestDTO searchRequest,
        @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt == null ? null : jwt.getSubject();
        return ResponseEntity.ok(dataConnectSearchService.search(searchRequest, userId));
    }

    // later pages of a search (pagination.next_page_url); not part of the spec's api.yaml,
    // which leaves the pagination URL scheme to the server
    @GetMapping("/{search_id}")
    public ResponseEntity<TableDataResponseDTO> getSearchPage(
        @PathVariable("search_id") String searchId,
        @RequestParam(name = "page_token", required = false) String pageToken,
        @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt == null ? null : jwt.getSubject();
        return ResponseEntity.ok(dataConnectSearchService.getPage(searchId, userId, pageToken));
    }
}
