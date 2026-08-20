export const dynamic = 'force-dynamic';

import { NextResponse } from 'next/server';

export async function GET() {
  return NextResponse.json({
    UI_BASE_URL: process.env.PUB_UI_BASE_URL || '',
    HYDRA_PUBLIC_API_BROWSER_SIDE_BASE_URL: process.env.PUB_HYDRA_PUBLIC_API_BROWSER_SIDE_BASE_URL || '',
    HYDRA_RESEARCHER_CLIENT_ID: process.env.PUB_HYDRA_RESEARCHER_CLIENT_ID || '',
    REFCLOUD_DOCS_URL: process.env.PUB_REFCLOUD_DOCS_URL || ''
  }, {
    status: 200
  });
}
