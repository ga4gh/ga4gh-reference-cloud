import type { NextApiRequest, NextApiResponse } from 'next';

export default async function handler(req: NextApiRequest, res: NextApiResponse) {
  const { id } = req.query
  const apiBaseUrl = process.env.REFCLOUD_API_BASE_URL
  const accessToken = req.cookies['access_token']

  if (!apiBaseUrl || !accessToken) {
    return res.status(500).json({ message: 'Internal Server Error' })
  }

  const result = await fetch(
    `${apiBaseUrl}/ga4gh/drs/v1/objects/${id}`,
    {
      method: 'GET',
      headers: {'Authorization': `Bearer ${accessToken}`}
    }
  )

  const data = await result.json().catch(() => null)
  return res.status(result.status).json(data)
}