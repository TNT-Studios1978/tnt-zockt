// Boutique der Virtual World: Artikel, Käufe und Stripe. Dateien in api/_lib sind keine Endpunkte.
//
// Bei Vercel nötig: STRIPE_SECRET_KEY (zuerst den Test-Schlüssel sk_test_...).
// Ein Webhook-Geheimnis braucht es nicht: Der Webhook liest jedes Ereignis selbst bei Stripe nach,
// gefälschte Meldungen bewirken deshalb nichts.

import { HttpError, rest } from "./coins.js";

const STRIPE_API = "https://api.stripe.com/v1";

export function stripeConfigured() {
  return Boolean((process.env.STRIPE_SECRET_KEY || "").trim());
}

/** Stripe erwartet Formulardaten mit verschachtelten Schlüsseln, z.B. line_items[0][price_data][currency]. */
function form(data, prefix = "", out = new URLSearchParams()) {
  for (const [key, value] of Object.entries(data)) {
    if (value === undefined || value === null) continue;
    const name = prefix ? `${prefix}[${key}]` : key;
    if (typeof value === "object") form(value, name, out);
    else out.append(name, String(value));
  }
  return out;
}

export async function stripe(path, { method = "GET", body } = {}) {
  const key = (process.env.STRIPE_SECRET_KEY || "").trim();
  if (!key) throw new HttpError(503, "Boutique ist noch nicht eingerichtet");
  const r = await fetch(`${STRIPE_API}${path}`, {
    method,
    headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/x-www-form-urlencoded" },
    body: body ? form(body) : undefined
  });
  const data = await r.json().catch(() => ({}));
  if (!r.ok) throw new HttpError(502, "Stripe: " + ((data.error && data.error.message) || r.status));
  return data;
}

/** Aktive Artikel der Boutique, sortiert. */
export async function shopItems() {
  return (await rest("world_items?active=eq.true&select=id,kind,name,description,price_chf,sort_order&order=sort_order.asc")) || [];
}

/** Gekaufte, nicht erstattete Artikel eines Spielers (sub wie im Welt-Ticket). */
export async function ownedItems(sub) {
  if (typeof sub !== "string" || !/^(twitch|kick):[A-Za-z0-9_-]{1,64}$/.test(sub)) return [];
  try {
    const rows = await rest(`world_purchases?sub=eq.${encodeURIComponent(sub)}&status=eq.paid&select=item`);
    return [...new Set((rows || []).map((row) => row.item))];
  } catch (err) {
    // Ohne Tabelle (SQL noch nicht ausgeführt) oder bei Datenbankfehlern: Welt geht trotzdem, nur ohne Käufe.
    return [];
  }
}
