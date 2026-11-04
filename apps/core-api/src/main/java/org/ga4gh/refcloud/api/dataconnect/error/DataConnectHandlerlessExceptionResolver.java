package org.ga4gh.refcloud.api.dataconnect.error;

import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

// Data Connect errors raised before a controller is chosen, e.g. 405 (wrong method), 415 (wrong
// content type) or 404 (unknown path). DataConnectExceptionHandler is scoped to the Data Connect
// controllers, so it never sees these; this resolver runs first and hands them to it by path.
// Everything else is left to Spring's usual handling
@Component
@RequiredArgsConstructor
public class DataConnectHandlerlessExceptionResolver implements HandlerExceptionResolver, Ordered {

    private final DataConnectExceptionHandler dataConnectExceptionHandler;

    private final DataConnectErrorWriter dataConnectErrorWriter;

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        // exceptions from a controller method are handled by DataConnectExceptionHandler
        if (handler instanceof HandlerMethod || !DataConnectErrorWriter.isDataConnectRequest(request)) {
            return null;
        }

        ResponseEntity<DataConnectErrorResponseDTO> error = dataConnectExceptionHandler.handleException(exception);
        try {
            response.setStatus(error.getStatusCode().value());
            error.getHeaders().forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
            dataConnectErrorWriter.write(response, error.getBody());
        } catch (IOException e) {
            return null; // fall back to Spring's handling
        }
        return new ModelAndView();
    }

    // ahead of Spring MVC's resolvers, which would otherwise send these to Spring Boot's /error page
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
