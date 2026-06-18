package org.ga4gh.refcloud.api.security;

import java.util.Map;

public class KratosSessionResponse {
    private boolean active;
    private Identity identity;

    // Getters and Setters
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Identity getIdentity() { return identity; }
    public void setIdentity(Identity identity) { this.identity = identity; }

    public static class Identity {
        private String id;
        private Map<String, Object> traits;

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public Map<String, Object> getTraits() { return traits; }
        public void setTraits(Map<String, Object> traits) { this.traits = traits; }
    }
}