import type { NextApiRequest, NextApiResponse } from 'next';

export default async function handler(req: NextApiRequest, res: NextApiResponse) {
    const token = req.cookies['access_token'];
    res.json({
        token: token
    })
}