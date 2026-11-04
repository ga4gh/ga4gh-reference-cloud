import { redirect } from "next/navigation";
import { headers } from "next/headers";
import ory from "@/utils/ory/sdk";
import HomeLoggedOut from "@/components/pages/home/HomeLoggedOut";

export default async function HomePage() {
  let hasSession = false;
  const reqHeaders = await headers();
  const cookie = reqHeaders.get("cookie") || "";

  try {
    await ory.toSession({cookie: cookie});
    hasSession = true;
  } catch (err: any) {
    const status = err.response?.status;
    console.log("ERROR: ", err.response?.data);

    if (status === 403 || status === 422) {
      redirect("/login?aal=aal2");
    }
  }

  if (hasSession) {
    redirect("/datasets");
  }

  return <HomeLoggedOut />;
}
