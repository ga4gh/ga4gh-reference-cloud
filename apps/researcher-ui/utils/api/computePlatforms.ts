// Server-side only, like datasets.ts: calls core-api directly over its internal URL (REFCLOUD_API_BASE_URL).

import { CoreApiResponse } from "@/utils/api/datasets";

// fetches the enabled compute platforms, with the researcher's access status for each, for the given Kratos session
export async function fetchComputePlatforms(sessionToken: string): Promise<CoreApiResponse> {
  const apiBaseUrl = process.env.REFCLOUD_API_BASE_URL;
  if (!apiBaseUrl) {
    throw new Error("REFCLOUD_API_BASE_URL is not set");
  }

  const result = await fetch(`${apiBaseUrl}/compute-platforms`, {
    headers: { "X-Session-Token": sessionToken },
  });
  const data = await result.json().catch(() => null);

  return { status: result.status, data };
}
