package org.ga4gh.refcloud.api.dataconnect.table;

import java.util.List;

// Data Connect "ListTablesResponse" object. pagination is optional in the spec and not yet implemented
public record ListTablesResponseDTO(
    List<DataConnectTableResponseDTO> tables
) {}
