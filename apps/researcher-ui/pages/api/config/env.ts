import type { NextApiRequest, NextApiResponse } from 'next';

export default function handler(req: NextApiRequest, res: NextApiResponse) {
  res.status(200).json({
    UI_BASE_URL: process.env.PUB_UI_BASE_URL || '',
    HYDRA_PUBLIC_API_BROWSER_SIDE_BASE_URL: process.env.PUB_HYDRA_PUBLIC_API_BROWSER_SIDE_BASE_URL || '',
    HYDRA_RESEARCHER_CLIENT_ID: process.env.PUB_HYDRA_RESEARCHER_CLIENT_ID || '',
    REFCLOUD_DOCS_URL: process.env.PUB_REFCLOUD_DOCS_URL || ''
  });
}