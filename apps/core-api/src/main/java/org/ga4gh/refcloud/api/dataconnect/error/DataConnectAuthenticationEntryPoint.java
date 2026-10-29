package org.ga4gh.refcloud.api.dataconnect.error;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

// Bearer token authentication failures (e.g. an invalid or expired token). The standard bearer
// entry point sets the status and WWW-Authenticate header as before; Data Connect requests also
// get an ErrorResponse body, other APIs keep the empty body
@Component
@RequiredArgsConstructor
public class DataConnectAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint bearerTokenAuthenticationEntryPoint = new BearerTokenAuthenticationEntryPoint();

    private final DataConnectErrorWriter dataConnectErrorWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        bearerTokenAuthenticationEntryPoint.commence(request, response, authException);
        if (!DataConnectErrorWriter.isDataConnectRequest(request)) {
            return;
        }

        HttpStatus status = HttpStatus.resolve(response.getStatus());
        String title = status == null ? "Unauthorized" : status.getReasonPhrase();
        String detail = authException instanceof OAuth2AuthenticationException
            ? "the bearer token is invalid or expired"
            : "a valid bearer token is required";
        dataConnectErrorWriter.write(response, DataConnectErrorResponseDTO.of(title, detail));
    }
}
