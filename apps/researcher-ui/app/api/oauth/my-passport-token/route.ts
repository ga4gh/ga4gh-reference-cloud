import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';

export async function GET() {
  const cookieStore = await cookies();
  const token = cookieStore.get('access_token')?.value || null;

  return NextResponse.json({
    token: token
  });
}
