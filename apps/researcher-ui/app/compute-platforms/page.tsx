import { cookies, headers } from "next/headers";
import { redirect } from "next/navigation";
import ory from "@/utils/ory/sdk";
import { fetchComputePlatforms } from "@/utils/api/computePlatforms";
import ComputePlatformsClient, { ComputePlatform } from "@/components/pages/compute-platforms/ComputePlatformsClient";

export default async function ComputePlatformsPage() {
  const reqHeaders = await headers();
  const cookie = reqHeaders.get("cookie") || "";

  try {
    await ory.toSession({ cookie });
  } catch {
    redirect("/login");
  }

  // null when the server-side fetch fails, so the client fetches instead
  let initialPlatforms: ComputePlatform[] | null = null;

  // call core-api directly over the internal URL, not this app's public /api/compute-platforms route
  const sessionToken = (await cookies()).get("ory_kratos_session")?.value;
  if (sessionToken) {
    try {
      const { status, data } = await fetchComputePlatforms(sessionToken);
      if (status >= 200 && status < 300 && Array.isArray(data)) {
        initialPlatforms = data as ComputePlatform[];
      }
    } catch (error) {
      console.error("Server-side compute platform fetch failed:", error);
    }
  }

  return <ComputePlatformsClient initialPlatforms={initialPlatforms} />;
}
