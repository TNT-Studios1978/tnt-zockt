// Vercel Serverless Function
// Ruft StreamElements-Loyalty-Punkte für einen Twitch-Benutzernamen ab.
// Der StreamElements JWT-Token bleibt geheim auf dem Server (Umgebungsvariable),
// wird NIE an den Browser/Client gesendet.
//
// GET  /api/points?username=…   Punkte abfragen (wie bisher, ohne Speichern)
// POST /api/points              Punkte abfragen UND den Login in user_logins vermerken.
//      Nur mit geprüfter Anmeldung: Twitch per "Authorization: Bearer <Supabase-Token>",
//      Kick per { kickSession: <signiertes Sitzungszeichen aus /api/kick-auth> } im Body.
//      Der Browser darf user_logins selbst nicht mehr beschreiben (siehe supabase-user-logins.sql).

import { HttpError, rest, sendError, verifyUser } from "./_lib/coins.js";
import { verify } from "./_lib/ticket.js";

async function fetchPoints(username) {
  const token = process.env.STREAMELEMENTS_JWT;
  const channelId = process.env.STREAMELEMENTS_CHANNEL_ID;
  if (!token || !channelId) {
    return { status: 500, body: { error: "Server nicht konfiguriert (Umgebungsvariablen fehlen)" } };
  }
  const url = `https://api.streamelements.com/kappa/v2/points/${encodeURIComponent(channelId)}/${encodeURIComponent(username)}`;
  const seRes = await fetch(url, {
    headers: { Authorization: `Bearer ${token}`, Accept: "application/json" }
  });
  if (seRes.status === 404) {
    return { status: 200, body: { points: 0, pointsAlltime: 0, watchtime: 0, rank: null } };
  }
  const rawText = await seRes.text();
  let data;
  try {
    data = JSON.parse(rawText);
  } catch (parseErr) {
    return { status: 502, body: { error: "Antwort war kein JSON", raw: rawText.slice(0, 300), status: seRes.status } };
  }
  if (!seRes.ok) {
    return { status: seRes.status, body: { error: "StreamElements-Fehler", details: data } };
  }
  return { status: 200, body: data };
}

// Wer ist angemeldet? Liefert { key, platform, name, avatar } oder wirft 401.
async function identify(req) {
  if (/^Bearer\s+/i.test(req.headers["authorization"] || "")) {
    const user = await verifyUser(req);
    if (!user.login) throw new HttpError(403, "Nur mit Twitch-Login möglich");
    return { key: user.login, platform: "twitch", name: user.name || user.login, avatar: user.avatar };
  }
  const session = verify("kicks", (req.body || {}).kickSession);
  if (session && session.name) {
    return { key: "kick:" + session.name, platform: "kick", name: session.name, avatar: session.avatar || null };
  }
  throw new HttpError(401, "Nicht angemeldet");
}

function cleanUrl(value) {
  return typeof value === "string" && /^https:\/\//.test(value) ? value.slice(0, 500) : null;
}

export default async function handler(req, res) {
  if (req.method === "POST") {
    try {
      const who = await identify(req);
      const result = await fetchPoints(who.name).catch(() => ({ status: 502, body: { error: "Fehler beim Abrufen der Punkte" } }));
      const points = typeof result.body.points === "number" ? result.body.points : null;
      const pointsAlltime = typeof result.body.pointsAlltime === "number" ? result.body.pointsAlltime : null;
      try {
        await rest("user_logins?on_conflict=login", {
          method: "POST",
          prefer: "resolution=merge-duplicates,return=minimal",
          body: {
            login: who.key,
            platform: who.platform,
            display_name: String(who.name).slice(0, 40),
            avatar_url: cleanUrl(who.avatar),
            points,
            points_alltime: pointsAlltime,
            last_seen: new Date().toISOString()
          }
        });
      } catch (e) {
        console.error("Login konnte nicht gespeichert werden:", e.message);
      }
      return res.status(result.status).json(result.body);
    } catch (err) {
      return sendError(res, err);
    }
  }

  if (req.method !== "GET") {
    return res.status(405).json({ error: "Nur GET oder POST" });
  }

  const { username } = req.query;
  if (!username) {
    return res.status(400).json({ error: "username fehlt" });
  }
  try {
    const result = await fetchPoints(username);
    return res.status(result.status).json(result.body);
  } catch (err) {
    return res.status(500).json({ error: "Fehler beim Abrufen der Punkte", message: err.message });
  }
}
