// Vercel Serverless Function
// Liest das Herkunftsland des Besuchers aus den automatisch von Vercel
// gesetzten Geolocation-Headern aus (keine externe API, keine Cookies nötig).
//
// Mit ?signed=1 kommt zusätzlich ein signiertes Kurzzeichen (10 Minuten) mit dem Land: Damit prüft der
// Spielserver der Virtual World das Land für die Casino-Räume in Welten anderer Streamer, ohne dem Browser
// zu vertrauen (gleiches Geheimnis WORLD_TICKET_SECRET wie die Welt-Tickets). Die Angabe ist öffentlich,
// darum darf jede Seite sie abrufen.

import { sign, ticketsConfigured } from "./_lib/ticket.js";

export default function handler(req, res) {
  const raw = String(req.headers['x-vercel-ip-country'] || '').toUpperCase();
  const country = /^[A-Z]{2}$/.test(raw) ? raw : null;

  res.setHeader('Cache-Control', 'no-store');
  if (req.query && req.query.signed) {
    res.setHeader('Access-Control-Allow-Origin', '*');
    const token = country && ticketsConfigured()
      ? sign('geo', { country, exp: Math.floor(Date.now() / 1000) + 10 * 60 })
      : null;
    return res.status(200).json({ country, token });
  }
  return res.status(200).json({ country });
}
