// Server-side only: calls core-api directly over its internal URL (REFCLOUD_API_BASE_URL), e.g.
// http://127.0.0.1:8080 in native mode or http://refcloud-api:8080 in helm mode. Server code must not
// call the public hostname: *.localhost resolves to the pod itself inside the cluster, and in AWS it
// would send a request out through the load balancer just to reach this same app.

export interface CoreApiResponse {
  status: number;
  data: unknown;
}

// fetches the datasets visible to the researcher with the given Kratos session token
export async function fetchDatasets(sessionToken: string): Promise<CoreApiResponse> {
  const apiBaseUrl = process.env.REFCLOUD_API_BASE_URL;
  if (!apiBaseUrl) {
    throw new Error("REFCLOUD_API_BASE_URL is not set");
  }

  const result = await fetch(`${apiBaseUrl}/datasets`, {
    headers: { "X-Session-Token": sessionToken },
  });
  const data = await result.json().catch(() => null);

  return { status: result.status, data };
}
