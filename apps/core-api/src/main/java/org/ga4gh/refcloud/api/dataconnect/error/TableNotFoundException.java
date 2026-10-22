package org.ga4gh.refcloud.api.dataconnect.error;

import org.ga4gh.refcloud.api.exception.ResourceNotFoundException;

// used for both missing and inaccessible tables, so restricted tables cannot be discovered
public class TableNotFoundException extends ResourceNotFoundException {
    public TableNotFoundException(String tableName) {
        super("No Data Connect table with name: " + tableName);
    }
}
