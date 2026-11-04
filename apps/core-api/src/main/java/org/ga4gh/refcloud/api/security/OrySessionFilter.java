package org.ga4gh.refcloud.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;

@Component
public class OrySessionFilter extends OncePerRequestFilter {

    @Value("${ga4gh.refcloud.security.kratos.public-base-url}")
    private String kratosPublicBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Extract the custom token header
        String sessionToken = request.getHeader("X-Session-Token");

        if (sessionToken != null && !sessionToken.isEmpty()) {
            try {
                String kratosUrl = kratosPublicBaseUrl + "/sessions/whoami";
                // 2. Set up headers for the Ory Kratos request
                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.COOKIE, "ory_kratos_session=" + sessionToken + ";");
                HttpEntity<String> entity = new HttpEntity<>(headers);

                // 3. Query Kratos to validate the token
                ResponseEntity<KratosSessionResponse> kratosResponse = restTemplate.exchange(
                        kratosUrl, HttpMethod.GET, entity, KratosSessionResponse.class);

                if (kratosResponse.getStatusCode().is2xxSuccessful() && kratosResponse.getBody() != null) {
                    KratosSessionResponse session = kratosResponse.getBody();
                    
                    // 4. Populate Spring Security Context with the user's details
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            session.getIdentity(), null, Collections.emptyList());
                    
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                // Token is invalid, expired, or Kratos is unreachable
                System.out.println("exception encountered in OrySessionFilter:" + e);
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
