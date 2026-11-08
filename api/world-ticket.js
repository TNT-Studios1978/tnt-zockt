// Vercel Serverless Function
// Stellt ein kurz gültiges, signiertes Ticket für die TNT-Zockt Virtual World aus.
// Damit erscheint man dort mit dem Namen und Bild des Website-Logins (Twitch oder Kick).
// Für Twitch-Konten steht die Abo-Stufe beim Kanal im Ticket (tier 0 bis 3, für die Abo-Skins).
//
// Erneuern aus der Welt: POST mit "Authorization: World <ticket>" liefert ein neues Ticket mit
// aktueller Abo-Stufe, aber derselben Ablaufzeit. So zählt ein frisches Abo ohne neues Einloggen.

import { SUPABASE_URL, SUPABASE_ANON_KEY } from "./_lib/coins.js";
import { sign, ticketsConfigured, verify } from "./_lib/ticket.js";
import { subTierOf } from "./_lib/twitchSubs.js";

const TICKET_SECONDS = 6 * 60 * 60;

// Nur die Welt darf Tickets aus dem Browser erneuern.
const ORIGINS = new Set(
  ["https://144-24-253-88.sslip.io", "https://virtual.tnt-zockt.ch"]
    .concat((process.env.WORLD_ORIGINS || "").split(","))
    .map((value) => value.trim())
    .filter(Boolean)
);

function cors(req, res) {
  const origin = req.headers.origin;
  if (origin && ORIGINS.has(origin)) {
    res.setHeader("Access-Control-Allow-Origin", origin);
    res.setHeader("Vary", "Origin");
    res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
    res.setHeader("Access-Control-Max-Age", "600");
  }
}

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
  // login: derselbe Schlüssel wie bei den TNT-Coins der Website (api/_lib/coins.js), klein geschrieben.
  const login = String(name).toLowerCase();
  return {
    sub: `twitch:${id}`,
    name: String(name).slice(0, 24),
    avatar: data.avatar_url || data.picture || "",
    login: /^[a-z0-9_]{1,25}$/.test(login) ? login : undefined
  };
}

/** Abo-Stufe nur für Twitch-Konten. */
async function tierFor(player) {
  const sub = String(player.sub || "");
  return sub.startsWith("twitch:") ? subTierOf(sub.slice("twitch:".length)) : 0;
}

export default async function handler(req, res) {
  cors(req, res);
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") return res.status(405).json({ error: "Nur POST erlaubt" });
  if (!ticketsConfigured()) return res.status(503).json({ error: "Welt-Tickets nicht eingerichtet" });
  res.setHeader("Cache-Control", "no-store");

  try {
    const auth = req.headers["authorization"] || "";

    // Erneuern: Identität aus dem alten Ticket, Ablauf bleibt gleich
    const renew = /^World\s+(\S+)$/i.exec(auth);
    if (renew) {
      const old = verify("world", renew[1]);
      if (!old) return res.status(401).json({ error: "Ticket abgelaufen" });
      const player = { sub: old.sub, name: old.name, avatar: old.avatar || "", login: old.login };
      const tier = await tierFor(player);
      return res.status(200).json({ ticket: sign("world", { ...player, tier, exp: old.exp }), exp: old.exp, tier });
    }

    let player = null;
    const match = /^Bearer\s+(.+)$/i.exec(auth);
    if (match) player = await twitchFromToken(match[1]);
    else {
      // Kick: das signierte Sitzungszeichen aus kick-auth.
      const session = verify("kicks", (req.body || {}).kickSession);
      if (session) player = { sub: session.sub, name: session.name, avatar: session.avatar || "" };
    }
    if (!player) return res.status(401).json({ error: "Nicht angemeldet" });

    const tier = await tierFor(player);
    const exp = Math.floor(Date.now() / 1000) + TICKET_SECONDS;
    return res.status(200).json({ ticket: sign("world", { ...player, tier, exp }), exp, tier });
  } catch (err) {
    return res.status(500).json({ error: "Ticket konnte nicht ausgestellt werden" });
  }
}
