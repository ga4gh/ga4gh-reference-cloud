package org.ga4gh.refcloud.api.dataconnect.table;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ga4gh/data-connect/v1")
public class DataConnectTableController {

    @GetMapping("/tables")
    public ResponseEntity<String> listTables() {
        return ResponseEntity.ok("you have reached the /tables endpoint");
    }

    @GetMapping("/table/{table_name}/info")
    public ResponseEntity<String> getTableInfo(@PathVariable("table_name") String tableName) {
        return ResponseEntity.ok("you have reached the /table/" + tableName + "/info endpoint");
    }

    @GetMapping("/table/{table_name}/data")
    public ResponseEntity<String> getTableData(@PathVariable("table_name") String tableName) {
        return ResponseEntity.ok("you have reached the /table/" + tableName + "/data endpoint");
    }
}
