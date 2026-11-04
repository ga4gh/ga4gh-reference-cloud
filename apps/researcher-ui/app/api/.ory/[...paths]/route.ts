import { NextRequest, NextResponse } from "next/server";

function getOryTargetUrl(request: NextRequest) {
  const oryUrl = process.env.ORY_SDK_URL || "http://localhost:4000";
  
  const url = new URL(request.url);
  const pathWithParams = url.pathname.replace("/api/.ory", "") + url.search;
  
  return new URL(pathWithParams, oryUrl).toString();
}

async function handleOryProxy(request: NextRequest) {
  const targetUrl = getOryTargetUrl(request);
  
  const forwardHeaders = new Headers(request.headers);
  
  const originalHost = request.headers.get("host") || "";
  forwardHeaders.set("x-forwarded-host", originalHost);
  
  forwardHeaders.delete("host");
  forwardHeaders.delete("content-length");

  let body: ReadableStream | null = null;
  if (!["GET", "HEAD", "OPTIONS"].includes(request.method)) {
    body = request.body;
  }

  try {
    const response = await fetch(targetUrl, {
      method: request.method,
      headers: forwardHeaders,
      body: body,
      redirect: "manual",
      // @ts-ignore - Next.js fetch cache configurations
      duplex: "half",    // Required by Node fetch specification for tracking input streams
    });

    const responseHeaders = new Headers(response.headers);

    const setCookie = responseHeaders.get("set-cookie");
    if (setCookie && originalHost.includes("vercel.app")) {
      const adjustedCookie = setCookie.replace(/Domain=[^;]+;?/gi, "");
      responseHeaders.set("set-cookie", adjustedCookie);
    }

    return new NextResponse(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: responseHeaders,
    });
  } catch (error) {
    console.error("Ory API Gateway Proxy Crash:", error);
    return NextResponse.json({ error: "Internal Gateway Routing Error" }, { status: 502 });
  }
}

export async function GET(req: NextRequest) { return handleOryProxy(req); }
export async function POST(req: NextRequest) { return handleOryProxy(req); }
export async function PUT(req: NextRequest) { return handleOryProxy(req); }
export async function PATCH(req: NextRequest) { return handleOryProxy(req); }
export async function DELETE(req: NextRequest) { return handleOryProxy(req); }
