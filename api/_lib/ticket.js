// Signierte Kurztexte für die Virtual World. Dateien in api/_lib sind keine Endpunkte.
// Aufbau: <art>.<base64url(JSON)>.<base64url(HMAC-SHA256)>
// Das Geheimnis WORLD_TICKET_SECRET steht gleich bei Vercel und auf dem Spielserver.

import { createHmac, timingSafeEqual } from "node:crypto";

function secret() {
  const value = (process.env.WORLD_TICKET_SECRET || "").trim();
  return value.length >= 32 ? value : null;
}

export function ticketsConfigured() {
  return secret() !== null;
}

function signature(kind, body) {
  return createHmac("sha256", secret()).update(`${kind}.${body}`).digest("base64url");
}

export function sign(kind, payload) {
  const body = Buffer.from(JSON.stringify(payload)).toString("base64url");
  return `${kind}.${body}.${signature(kind, body)}`;
}

/** Liefert den Inhalt oder null, wenn Art, Signatur oder Ablauf nicht stimmen. */
export function verify(kind, token) {
  if (!secret() || typeof token !== "string") return null;
  const [given, body, sig] = token.split(".");
  if (given !== kind || !body || !sig) return null;
  const expected = Buffer.from(signature(kind, body));
  const actual = Buffer.from(sig);
  if (expected.length !== actual.length || !timingSafeEqual(expected, actual)) return null;
  try {
    const payload = JSON.parse(Buffer.from(body, "base64url").toString());
    if (typeof payload.exp !== "number" || payload.exp * 1000 < Date.now()) return null;
    return payload;
  } catch (e) {
    return null;
  }
}
