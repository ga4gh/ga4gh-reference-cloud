import { cookies, headers } from "next/headers";
import { redirect } from "next/navigation";
import ory from "@/utils/ory/sdk";
import { fetchDatasets } from "@/utils/api/datasets";
import DatasetsClient, { Dataset } from "@/components/pages/datasets/DatasetsClient";

export default async function DatasetsPage() {
  const reqHeaders = await headers();
  const cookie = reqHeaders.get("cookie") || "";

  try {
    await ory.toSession({ cookie });
  } catch (err: any) {
    redirect("/login");
  }

  let initialDatasets: Dataset[] = [];

  // call core-api directly over the internal URL, not this app's public /api/datasets route
  const sessionToken = (await cookies()).get("ory_kratos_session")?.value;
  if (sessionToken) {
    try {
      const { status, data } = await fetchDatasets(sessionToken);
      if (status >= 200 && status < 300 && Array.isArray(data)) {
        initialDatasets = data as Dataset[];
      }
    } catch (error) {
      console.error("Server-side dataset fetch failed:", error);
    }
  }

  return <DatasetsClient initialDatasets={initialDatasets} />;
}
