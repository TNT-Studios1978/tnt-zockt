// Vercel Serverless Function
// Liest das Herkunftsland des Besuchers aus den automatisch von Vercel
// gesetzten Geolocation-Headern aus (keine externe API, keine Cookies nötig).

export default function handler(req, res) {
  const country =
    req.headers['x-vercel-ip-country'] ||
    null;

  res.setHeader('Cache-Control', 'no-store');
  return res.status(200).json({ country });
}
