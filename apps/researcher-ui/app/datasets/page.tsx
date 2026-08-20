import { headers } from "next/headers";
import { redirect } from "next/navigation";
import ory from "@/pkg/sdk";
import DatasetsClient, { Dataset } from "./DatasetsClient";

export default async function DatasetsPage() {
  const reqHeaders = await headers();
  const cookie = reqHeaders.get("cookie") || "";

  try {
    await ory.toSession({ cookie });
  } catch (err: any) {
    redirect("/login");
  }

  let initialDatasets: Dataset[] = [];

  try {
    const baseUrl = process.env.PUB_UI_BASE_URL
    const response = await fetch(`${baseUrl}/api/datasets`, {
      headers: { cookie }
    });

    if (response.ok) {
      initialDatasets = await response.json();
    }
  } catch (error) {
    console.error("Server-side dataset fetch failed:", error);
  }

  return <DatasetsClient initialDatasets={initialDatasets} />;
}
