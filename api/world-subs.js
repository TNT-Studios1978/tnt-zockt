// Vercel Serverless Function
// Verbindet den Twitch-Kanal einmalig mit der Website, damit die Virtual World Abo-Skins vergeben kann.
//
// Ablauf: Der Kanalinhaber öffnet /api/world-subs im Browser, meldet sich bei Twitch an und stimmt
// dem Lesen der Abo-Liste zu. Twitch schickt ihn hierher zurück; gespeichert wird nur ein Zugang
// des Kanals BROADCASTER_LOGIN. Jeder andere Twitch-Nutzer wird abgewiesen.
//
// In der Twitch-Entwicklerkonsole muss diese Adresse als OAuth Redirect URL eingetragen sein:
//   https://<domain>/api/world-subs

import { sign, ticketsConfigured, verify } from "./_lib/ticket.js";
import { BROADCASTER_LOGIN, exchangeCode, saveBroadcaster, SUBS_SCOPE, twitchConfigured, validateToken } from "./_lib/twitchSubs.js";

function page(res, status, title, text) {
  res.setHeader("Content-Type", "text/html; charset=utf-8");
  res.setHeader("Cache-Control", "no-store");
  const escape = (value) => String(value).replace(/[&<>"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[char]);
  res.status(status).send(`<!doctype html><html lang="de"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${escape(title)}</title>
<body style="font-family:system-ui,sans-serif;background:#05060f;color:#fff;display:grid;place-items:center;min-height:100vh;margin:0">
<main style="max-width:32rem;padding:2rem;border:1px solid #1e9bff66;border-radius:12px;background:#0b0f22">
<h1 style="margin-top:0">${escape(title)}</h1><p style="color:#c7d2e8;line-height:1.5">${escape(text)}</p>
<p><a style="color:#16e0d0" href="/">Zurück zu TNT-Zockt</a></p></main></body></html>`);
}

export default async function handler(req, res) {
  if (req.method !== "GET") return res.status(405).json({ error: "Nur GET erlaubt" });
  if (!twitchConfigured() || !ticketsConfigured()) return page(res, 503, "Nicht eingerichtet", "TWITCH_CLIENT_ID, TWITCH_CLIENT_SECRET oder WORLD_TICKET_SECRET fehlen bei Vercel.");

  const host = req.headers["x-forwarded-host"] || req.headers.host;
  const redirectUri = `https://${host}/api/world-subs`;
  const { code, state, error } = req.query || {};

  if (error) return page(res, 400, "Abgebrochen", "Die Verbindung mit Twitch wurde nicht erlaubt.");

  // Schritt 1: zur Twitch-Anmeldung
  if (!code) {
    const params = new URLSearchParams({
      client_id: process.env.TWITCH_CLIENT_ID.trim(),
      redirect_uri: redirectUri,
      response_type: "code",
      scope: SUBS_SCOPE,
      force_verify: "true",
      state: sign("subs", { exp: Math.floor(Date.now() / 1000) + 600 })
    });
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("Location", `https://id.twitch.tv/oauth2/authorize?${params.toString()}`);
    return res.status(302).end();
  }

  // Schritt 2: Rückkehr von Twitch
  if (!verify("subs", state)) return page(res, 400, "Abgelaufen", "Der Link ist abgelaufen. Bitte /api/world-subs neu öffnen.");
  try {
    const tokens = await exchangeCode(String(code), redirectUri);
    const info = await validateToken(tokens.access_token);
    if (!info || String(info.login || "").toLowerCase() !== BROADCASTER_LOGIN) {
      return page(res, 403, "Falsches Konto", `Bitte mit dem Twitch-Konto ${BROADCASTER_LOGIN} anmelden. Nur der Kanal selbst darf die Abo-Liste freigeben.`);
    }
    if (!(info.scopes || []).includes(SUBS_SCOPE)) return page(res, 400, "Recht fehlt", "Twitch hat das Lesen der Abos nicht freigegeben.");
    await saveBroadcaster({
      login: info.login,
      userId: info.user_id,
      accessToken: tokens.access_token,
      refreshToken: tokens.refresh_token,
      expiresIn: tokens.expires_in
    });
    return page(res, 200, "Verbunden", `Der Kanal ${info.login} ist verbunden. Abonnenten bekommen ihren Skin in der Virtual World, sobald sie die Welt neu betreten oder am Abo-Stand ihr Abo prüfen.`);
  } catch (err) {
    return page(res, 500, "Fehler", "Die Verbindung mit Twitch hat nicht geklappt. Bitte später erneut versuchen.");
  }
}
