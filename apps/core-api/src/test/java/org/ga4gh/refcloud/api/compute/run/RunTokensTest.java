package org.ga4gh.refcloud.api.compute.run;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.testng.annotations.Test;

public class RunTokensTest {

    @Test
    public void mintedTokensArePrefixedRandomAndUrlSafe() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String token = RunTokens.mint();
            assertTrue(token.startsWith("rcrun_"), token);
            // 32 bytes, base64url without padding
            assertEquals(token.length(), "rcrun_".length() + 43, token);
            assertTrue(token.substring(6).matches("[A-Za-z0-9_-]+"), token);
            tokens.add(token);
        }
        assertEquals(tokens.size(), 100);
    }

    @Test
    public void hashIsSha256Hex() {
        assertEquals(RunTokens.hash("abc"), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertNotEquals(RunTokens.hash("rcrun_a"), RunTokens.hash("rcrun_b"));
    }

    @Test
    public void runTokensAreRecognizedFromTheAuthorizationHeader() {
        assertEquals(RunTokens.fromAuthorizationHeader("Bearer rcrun_abc"), Optional.of("rcrun_abc"));
        assertEquals(RunTokens.fromAuthorizationHeader("bearer rcrun_abc "), Optional.of("rcrun_abc"));
        // hydra jwts, other schemes and missing headers aren't run tokens
        assertFalse(RunTokens.fromAuthorizationHeader("Bearer eyJhbGciOi.x.y").isPresent());
        assertFalse(RunTokens.fromAuthorizationHeader("Basic cmNydW5fYWJj").isPresent());
        assertFalse(RunTokens.fromAuthorizationHeader(null).isPresent());
    }
}
