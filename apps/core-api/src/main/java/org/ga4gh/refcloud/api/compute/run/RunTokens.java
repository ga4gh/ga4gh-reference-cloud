package org.ga4gh.refcloud.api.compute.run;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

// run-scoped TES tokens (D12, Task 7): opaque, random, and only ever stored as a sha-256 hash. the prefix lets the
// security chain tell them apart from hydra jwts
public final class RunTokens {

    public static final String PREFIX = "rcrun_";

    private static final SecureRandom RANDOM = new SecureRandom();

    private RunTokens() {}

    public static String mint() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static boolean looksLikeRunToken(String token) {
        return token != null && token.startsWith(PREFIX);
    }

    public static String hash(String token) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    // the bearer token from an Authorization header, if it's a run token
    public static Optional<String> fromAuthorizationHeader(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Optional.empty();
        }
        String token = header.substring(7).strip();
        return looksLikeRunToken(token) ? Optional.of(token) : Optional.empty();
    }
}
