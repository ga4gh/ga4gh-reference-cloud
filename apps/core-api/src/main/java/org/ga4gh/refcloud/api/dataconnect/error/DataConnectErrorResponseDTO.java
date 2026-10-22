package org.ga4gh.refcloud.api.dataconnect.error;

import java.util.List;

// Data Connect "ErrorResponse", returned when no part of a request can be fulfilled
public record DataConnectErrorResponseDTO(
    List<DataConnectErrorDTO> errors
) {
    public static DataConnectErrorResponseDTO of(String title, String detail) {
        return new DataConnectErrorResponseDTO(List.of(new DataConnectErrorDTO(title, detail)));
    }
}
