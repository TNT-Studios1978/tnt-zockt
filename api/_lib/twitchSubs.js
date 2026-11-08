// Twitch-Abos beim eigenen Kanal prüfen (für die Abo-Skins in der Virtual World).
// Dateien in api/_lib sind keine Endpunkte.
//
// Twitch gibt die Abo-Liste nur mit einem Zugang des Kanalinhabers heraus (Recht channel:read:subscriptions).
// Den holt /api/world-subs einmalig; Erneuerungs-Token und Ablauf liegen in public.twitch_broadcaster
// (nur mit dem Service-Schlüssel lesbar, siehe supabase-twitch-subs.sql).

import { rest } from "./coins.js";

export const BROADCASTER_LOGIN = (process.env.TWITCH_BROADCASTER_LOGIN || "tnt_studiosch").trim().toLowerCase();
export const SUBS_SCOPE = "channel:read:subscriptions";

const clientId = () => (process.env.TWITCH_CLIENT_ID || "").trim();
const clientSecret = () => (process.env.TWITCH_CLIENT_SECRET || "").trim();

export function twitchConfigured() {
  return Boolean(clientId() && clientSecret());
}

/** Code aus der Twitch-Anmeldung gegen Zugang und Erneuerungs-Token tauschen. */
export async function exchangeCode(code, redirectUri) {
  const params = new URLSearchParams({
    client_id: clientId(),
    client_secret: clientSecret(),
    code,
    grant_type: "authorization_code",
    redirect_uri: redirectUri
  });
  const r = await fetch("https://id.twitch.tv/oauth2/token", { method: "POST", body: params });
  const data = await r.json();
  if (!r.ok || !data.access_token || !data.refresh_token) throw new Error("Twitch hat den Code abgelehnt");
  return data;
}

/** Zu wem gehört ein Zugang und welche Rechte hat er? */
export async function validateToken(accessToken) {
  const r = await fetch("https://id.twitch.tv/oauth2/validate", { headers: { Authorization: `OAuth ${accessToken}` } });
  if (!r.ok) return null;
  return r.json();
}

export async function saveBroadcaster({ login, userId, accessToken, refreshToken, expiresIn }) {
  await rest("twitch_broadcaster?on_conflict=id", {
    method: "POST",
    prefer: "resolution=merge-duplicates,return=minimal",
    body: {
      id: 1,
      login,
      broadcaster_id: userId,
      access_token: accessToken,
      refresh_token: refreshToken,
      expires_at: new Date(Date.now() + Math.max(60, (expiresIn || 3600) - 120) * 1000).toISOString(),
      updated_at: new Date().toISOString()
    }
  });
}

// Pro Funktionsinstanz zwischengespeichert, damit nicht jedes Ticket die Datenbank fragt.
let cached = null;

async function broadcasterAccess() {
  if (cached && cached.expiresAt > Date.now()) return cached;
  const rows = await rest("twitch_broadcaster?id=eq.1&select=broadcaster_id,login,access_token,refresh_token,expires_at");
  const row = rows && rows[0];
  if (!row || !row.refresh_token) return null;
  if (new Date(row.expires_at).getTime() > Date.now() + 60_000) {
    cached = { id: row.broadcaster_id, token: row.access_token, expiresAt: new Date(row.expires_at).getTime() };
    return cached;
  }
  // Abgelaufen: erneuern. Twitch gibt dabei oft ein neues Erneuerungs-Token aus, das gespeichert werden muss.
  const params = new URLSearchParams({
    client_id: clientId(),
    client_secret: clientSecret(),
    grant_type: "refresh_token",
    refresh_token: row.refresh_token
  });
  const r = await fetch("https://id.twitch.tv/oauth2/token", { method: "POST", body: params });
  const data = await r.json();
  if (!r.ok || !data.access_token) {
    console.error("Twitch-Kanalzugang lässt sich nicht erneuern, bitte /api/world-subs neu verbinden");
    return null;
  }
  await saveBroadcaster({
    login: row.login,
    userId: row.broadcaster_id,
    accessToken: data.access_token,
    refreshToken: data.refresh_token || row.refresh_token,
    expiresIn: data.expires_in
  });
  cached = { id: row.broadcaster_id, token: data.access_token, expiresAt: Date.now() + Math.max(60, (data.expires_in || 3600) - 120) * 1000 };
  return cached;
}

/**
 * Abo-Stufe eines Twitch-Nutzers beim Kanal: 0 kein Abo, 1 bis 3.
 * Bei Fehlern 0: Ein Ausfall bei Twitch soll niemanden aus der Welt sperren, nur den Skin vorenthalten.
 */
export async function subTierOf(twitchUserId) {
  if (!twitchConfigured() || !/^\d{1,20}$/.test(String(twitchUserId || ""))) return 0;
  try {
    const access = await broadcasterAccess();
    if (!access) return 0;
    const url = `https://api.twitch.tv/helix/subscriptions?broadcaster_id=${encodeURIComponent(access.id)}&user_id=${encodeURIComponent(twitchUserId)}`;
    const r = await fetch(url, { headers: { "Client-Id": clientId(), Authorization: `Bearer ${access.token}` } });
    if (r.status === 401) cached = null;
    if (!r.ok) return 0;
    const data = await r.json();
    const tier = data.data && data.data[0] && data.data[0].tier;
    return tier === "3000" ? 3 : tier === "2000" ? 2 : tier === "1000" ? 1 : 0;
  } catch (err) {
    return 0;
  }
}
