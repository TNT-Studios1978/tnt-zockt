// Vercel Serverless Function
// Stellt ein kurz gültiges, signiertes Ticket für die TNT-Zockt Virtual World aus.
// Damit erscheint man dort mit dem Namen und Bild des Website-Logins (Twitch oder Kick).

import { SUPABASE_URL, SUPABASE_ANON_KEY } from "./_lib/coins.js";
import { sign, ticketsConfigured, verify } from "./_lib/ticket.js";

const TICKET_SECONDS = 6 * 60 * 60;

/** Twitch über Supabase. Name und Bild aus identity_data: diese Werte kann der Nutzer nicht selbst ändern. */
async function twitchFromToken(token) {
  const r = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { Authorization: `Bearer ${token}`, apikey: SUPABASE_ANON_KEY }
  });
  if (!r.ok) return null;
  const user = await r.json();
  const twitch = (user.identities || []).find((identity) => identity.provider === "twitch");
  if (!twitch) return null;
  const data = twitch.identity_data || {};
  const id = data.sub || data.provider_id || twitch.id;
  const name = data.preferred_username || data.nickname || data.name || data.user_name || "";
  if (!id || !name) return null;
  return { sub: `twitch:${id}`, name: String(name).slice(0, 24), avatar: data.avatar_url || data.picture || "" };
}

export default async function handler(req, res) {
  if (req.method !== "POST") return res.status(405).json({ error: "Nur POST erlaubt" });
  if (!ticketsConfigured()) return res.status(503).json({ error: "Welt-Tickets nicht eingerichtet" });

  try {
    let player = null;
    const match = /^Bearer\s+(.+)$/i.exec(req.headers["authorization"] || "");
    if (match) player = await twitchFromToken(match[1]);
    else {
      // Kick: das signierte Sitzungszeichen aus kick-auth.
      const session = verify("kicks", (req.body || {}).kickSession);
      if (session) player = { sub: session.sub, name: session.name, avatar: session.avatar || "" };
    }
    if (!player) return res.status(401).json({ error: "Nicht angemeldet" });

    const exp = Math.floor(Date.now() / 1000) + TICKET_SECONDS;
    return res.status(200).json({ ticket: sign("world", { ...player, exp }), exp });
  } catch (err) {
    return res.status(500).json({ error: "Ticket konnte nicht ausgestellt werden" });
  }
}
