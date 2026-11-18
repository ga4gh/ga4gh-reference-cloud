package org.ga4gh.refcloud.api.compute.storage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.StsException;

// records each AssumeRole request and answers it with made-up credentials, or refuses it when refuse is set
public class FakeStsClient implements StsClient {

    public final List<AssumeRoleRequest> requests = new ArrayList<>();

    public String refuse;

    @Override
    public AssumeRoleResponse assumeRole(AssumeRoleRequest request) {
        requests.add(request);
        if (refuse != null) {
            throw StsException.builder().statusCode(403).message(refuse).build();
        }
        return AssumeRoleResponse.builder()
                .credentials(Credentials.builder()
                        .accessKeyId("ASIAFAKEKEYID")
                        .secretAccessKey("fake-secret")
                        .sessionToken("fake-session-token")
                        .expiration(Instant.parse("2026-11-18T12:00:00Z"))
                        .build())
                .build();
    }

    @Override
    public String serviceName() {
        return "sts";
    }

    @Override
    public void close() {}
}
