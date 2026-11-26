import { cookies, headers } from "next/headers";
import { redirect } from "next/navigation";
import ory from "@/utils/ory/sdk";
import { fetchComputePlatforms } from "@/utils/api/computePlatforms";
import { ComputePlatform } from "@/components/pages/compute-platforms/ComputePlatformsClient";
import WorkflowRunClient from "@/components/pages/workflow-runs/WorkflowRunClient";

interface WorkflowRunPageProps {
  params: Promise<{ platformId: string; runId: string }>;
}

// one of the researcher's workflow runs (spec 0014): its details, outputs, tasks and logs, and cancelling it. The
// platforms (for the platform's and workflow's names) come from core-api with the Kratos session, as on the list of
// runs; the run itself from the platform's WES API, with the passport, in the browser
export default async function WorkflowRunPage({ params }: WorkflowRunPageProps) {
  const { platformId, runId } = await params;
  const reqHeaders = await headers();
  const cookie = reqHeaders.get("cookie") || "";

  try {
    await ory.toSession({ cookie });
  } catch {
    redirect("/login");
  }

  // null when the server-side fetch fails: the page then shows the platform's ID and the workflow's URL instead
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

  return <WorkflowRunClient platformId={platformId} runId={runId} platforms={initialPlatforms} />;
}
