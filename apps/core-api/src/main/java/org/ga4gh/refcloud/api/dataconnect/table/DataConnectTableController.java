package org.ga4gh.refcloud.api.dataconnect.table;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/ga4gh/data-connect/v1")
@RequiredArgsConstructor
public class DataConnectTableController {

    private final DataConnectTableService dataConnectTableService;

    // open endpoint: anonymous requests (jwt == null) list only platform-wide and open tables,
    // a passport token additionally lists tables in datasets the user has an approved visa for
    @GetMapping("/tables")
    public ResponseEntity<ListTablesResponseDTO> listTables(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt == null ? null : jwt.getSubject();
        return ResponseEntity.ok(dataConnectTableService.listTables(userId));
    }

    @GetMapping("/table/{table_name}/info")
    @PreAuthorize("@GA4GHPassportTokenEvaluator.canAccessDataConnectTable(authentication, #tableName)")
    public ResponseEntity<DataConnectTableResponseDTO> getTableInfo(@PathVariable("table_name") String tableName) {
        return ResponseEntity.ok(dataConnectTableService.getTable(tableName));
    }

    @GetMapping("/table/{table_name}/data")
    public ResponseEntity<String> getTableData(@PathVariable("table_name") String tableName) {
        return ResponseEntity.ok("you have reached the /table/" + tableName + "/data endpoint");
    }
}
