// Gemeinsame Hilfsfunktionen für die TNT-Coins.
// Dateien in api/_lib werden von Vercel NICHT als eigene Endpunkte veröffentlicht.

export const SUPABASE_URL = process.env.SUPABASE_URL || "https://wlvgjdzuvfbnberejthz.supabase.co";
export const SUPABASE_ANON_KEY = process.env.SUPABASE_ANON_KEY || "sb_publishable_2enioy5duKB9ZHMVjimTUw_cTLlcgYO";
const SERVICE_KEY = (process.env.SUPABASE_SERVICE_ROLE_KEY || "").trim();

// Neue Supabase-Schlüssel (sb_secret_...) sind keine JWTs und gehören nur in den
// apikey-Header. Alte service_role-JWTs werden zusätzlich als Bearer mitgeschickt.
function serviceHeaders(extra) {
  const h = { apikey: SERVICE_KEY, "Content-Type": "application/json", ...(extra || {}) };
  if (!SERVICE_KEY.startsWith("sb_")) h.Authorization = `Bearer ${SERVICE_KEY}`;
  return h;
}

// Höchstmultiplikator je Spiel (Gewinn pro Runde <= Einsatz × Wert).
export const MAX_MULT = {
  coinflip: 1.94,
  roulette: 36,
  blackjack: 101,   // inkl. Side Bets (Suited Trips 101×)
  crash: 100,
  plinko: 18,
  mines: 10000
};

export class HttpError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

function getTwitchUsername(user) {
  const meta = (user && user.user_metadata) || {};
  return meta.preferred_username || meta.nickname || meta.user_name || meta.name || meta.full_name || "";
}

// Prüft den Supabase-Login aus dem Header "Authorization: Bearer <token>".
export async function verifyUser(req) {
  const header = req.headers["authorization"] || "";
  const match = /^Bearer\s+(.+)$/i.exec(header);
  if (!match) throw new HttpError(401, "Nicht angemeldet");
  const r = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { Authorization: `Bearer ${match[1]}`, apikey: SUPABASE_ANON_KEY }
  });
  if (!r.ok) throw new HttpError(401, "Nicht angemeldet");
  const user = await r.json();
  const login = getTwitchUsername(user).toLowerCase();
  return { id: user.id, email: user.email || null, login };
}

export async function verifyTwitchUser(req) {
  const u = await verifyUser(req);
  if (!u.login) throw new HttpError(403, "Nur mit Twitch-Login möglich");
  return u;
}

// Aufruf einer Datenbankfunktion mit dem geheimen Server-Schlüssel.
export async function rpc(fn, args) {
  if (!SERVICE_KEY) throw new HttpError(500, "Server nicht konfiguriert (SUPABASE_SERVICE_ROLE_KEY fehlt)");
  const r = await fetch(`${SUPABASE_URL}/rest/v1/rpc/${fn}`, {
    method: "POST",
    headers: serviceHeaders(),
    body: JSON.stringify(args || {})
  });
  const text = await r.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (e) { data = text; }
  if (!r.ok) {
    const msg = (data && data.message) || String(text).slice(0, 200);
    const known = {
      insufficient_balance: [400, "Nicht genug Coins"],
      invalid_amount: [400, "Ungültiger Betrag"],
      no_open_round: [400, "Keine laufende Runde"],
      win_exceeds_limit: [400, "Gewinn unplausibel hoch"],
      not_pending: [409, "Antrag ist nicht mehr offen"],
      already_finished: [409, "Antrag ist bereits abgeschlossen"],
      not_found: [404, "Nicht gefunden"],
      not_linked: [400, "Minecraft-Konto ist nicht verknüpft"],
      kit_not_found: [404, "Paket nicht gefunden"],
      already_bought: [409, "Dieses Paket kann nur einmal gekauft werden"],
      invalid_code: [400, "Code ungültig oder abgelaufen"],
      not_reserved: [409, "Kauf ist bereits abgeschlossen"]
    };
    const cd = /cooldown:(\d+)/.exec(msg);
    if (cd) {
      const min = Number(cd[1]);
      const text = min >= 60 ? `${Math.ceil(min / 60)} Stunden` : `${min} Minuten`;
      throw new HttpError(429, `Noch nicht wieder verfügbar (in ${text})`);
    }
    for (const [code, [status, text2]] of Object.entries(known)) {
      if (msg.includes(code)) throw new HttpError(status, text2);
    }
    throw new HttpError(500, "Datenbankfehler: " + msg);
  }
  return data;
}

// Tabellen lesen/schreiben (nur Server).
export async function rest(path, { method = "GET", body, prefer } = {}) {
  if (!SERVICE_KEY) throw new HttpError(500, "Server nicht konfiguriert (SUPABASE_SERVICE_ROLE_KEY fehlt)");
  const headers = serviceHeaders(prefer ? { Prefer: prefer } : null);
  const r = await fetch(`${SUPABASE_URL}/rest/v1/${path}`, {
    method, headers, body: body ? JSON.stringify(body) : undefined
  });
  const text = await r.text();
  if (!r.ok) throw new HttpError(500, "Datenbankfehler: " + text.slice(0, 200));
  return text ? JSON.parse(text) : null;
}

export async function getSettings() {
  const rows = await rest("coin_settings?id=eq.1&select=*");
  return rows && rows[0];
}

// ---------- StreamElements ----------
function seConfig() {
  const token = process.env.STREAMELEMENTS_JWT;
  const channelId = process.env.STREAMELEMENTS_CHANNEL_ID;
  if (!token || !channelId) throw new HttpError(500, "StreamElements nicht konfiguriert");
  return { token, channelId };
}

export async function seGetPoints(username) {
  const { token, channelId } = seConfig();
  const r = await fetch(`https://api.streamelements.com/kappa/v2/points/${encodeURIComponent(channelId)}/${encodeURIComponent(username)}`, {
    headers: { Authorization: `Bearer ${token}`, Accept: "application/json" }
  });
  if (r.status === 404) return 0;
  if (!r.ok) throw new HttpError(502, "StreamElements nicht erreichbar");
  const d = await r.json();
  return typeof d.points === "number" ? d.points : 0;
}

// Ändert die Punkte um delta. Gibt den neuen Stand zurück.
export async function seAddPoints(username, delta) {
  const { token, channelId } = seConfig();
  const r = await fetch(`https://api.streamelements.com/kappa/v2/points/${encodeURIComponent(channelId)}/${encodeURIComponent(username)}/${Math.round(delta)}`, {
    method: "PUT",
    headers: { Authorization: `Bearer ${token}`, Accept: "application/json" }
  });
  const text = await r.text();
  if (!r.ok) throw new HttpError(502, "StreamElements-Fehler: " + text.slice(0, 150));
  let d = {};
  try { d = JSON.parse(text); } catch (e) {}
  return typeof d.newAmount === "number" ? d.newAmount : null;
}

export function sendError(res, err) {
  const status = err instanceof HttpError ? err.status : 500;
  return res.status(status).json({ error: err.message || "Unbekannter Fehler" });
}

export function intAmount(v) {
  const n = Number(v);
  if (!Number.isFinite(n) || !Number.isInteger(n) || n <= 0 || n > 100000000) {
    throw new HttpError(400, "Ungültiger Betrag");
  }
  return n;
}
