import { NextResponse } from "next/server";
import { cookies } from "next/headers";
import { fetchComputePlatforms } from "@/utils/api/computePlatforms";

export async function GET() {
  const apiBaseUrl = process.env.REFCLOUD_API_BASE_URL;

  const cookieStore = await cookies();
  const sessionToken = cookieStore.get("ory_kratos_session")?.value;

  if (!apiBaseUrl) {
    return NextResponse.json({ message: "Internal Server Error" }, { status: 500 });
  }

  if (!sessionToken) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  try {
    const { status, data } = await fetchComputePlatforms(sessionToken);
    return NextResponse.json(data, { status });
  } catch (error) {
    console.error("Backend fetch error on compute platforms endpoint:", error);
    return NextResponse.json({ message: "Gateway Fetch Failure" }, { status: 502 });
  }
}
