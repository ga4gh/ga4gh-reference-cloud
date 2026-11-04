package org.ga4gh.refcloud.api.dataconnect.error;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

// 403s raised by the security filters for Data Connect paths (e.g. a POST to a path with no
// Data Connect endpoint, rejected by CSRF protection), with an ErrorResponse body.
// Registered for Data Connect paths only, so other APIs keep the default handling
@Component
@RequiredArgsConstructor
public class DataConnectAccessDeniedHandler implements AccessDeniedHandler {

    private final DataConnectErrorWriter dataConnectErrorWriter;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        dataConnectErrorWriter.write(response, DataConnectErrorResponseDTO.of("Forbidden", "access to this resource is denied"));
    }
}
