package org.ga4gh.refcloud.api.dataconnect.error;

import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

// Writes an ErrorResponse body directly to the servlet response, for errors that happen outside
// a controller (security filters, request mapping)
@Component
@RequiredArgsConstructor
public class DataConnectErrorWriter {

    // every Data Connect endpoint is under this path
    public static final String PATH_PREFIX = "/ga4gh/data-connect/";

    private final ObjectMapper objectMapper;

    public static boolean isDataConnectRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith(PATH_PREFIX);
    }

    // status and any headers must already be set on the response
    public void write(HttpServletResponse response, DataConnectErrorResponseDTO body) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
