import type { NextApiRequest, NextApiResponse } from 'next';

export default async function handler(req: NextApiRequest, res: NextApiResponse) {
  const apiBaseUrl = process.env.REFCLOUD_API_BASE_URL
  const sessionToken = req.cookies['ory_kratos_session']

  if (!apiBaseUrl) {
    return res.status(500).json({ message: 'Internal Server Error' })
  }

  if (! sessionToken) {
    return res.status(401).json({ message: 'Unauthorized' })
  }

  const result = await fetch(`${apiBaseUrl}/datasets`, {headers: {'X-Session-Token': sessionToken}})
  const data = await result.json().catch(() => null)
  return res.status(result.status).json(data)
}
