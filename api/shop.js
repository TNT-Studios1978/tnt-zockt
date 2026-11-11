// Vercel Serverless Function
// Boutique der TNT-Zockt Virtual World: kosmetische Artikel für Echtgeld über Stripe.
//
//   GET  /api/shop            aktive Artikel mit Preis (öffentlich)
//   POST /api/shop            Kauf starten. Header "Authorization: World <ticket>", Body { item }.
//                             Antwort { url } zur Bezahlseite von Stripe.
//   POST /api/shop?webhook=1  Meldungen von Stripe (im Stripe-Dashboard als Webhook eintragen,
//                             Ereignisse: checkout.session.completed, checkout.session.async_payment_succeeded,
//                             charge.refunded, charge.dispute.created).
//
// Freigeschaltet wird über das Welt-Ticket (api/world-ticket.js liest die Käufe). Die Artikel geben
// keine Coins und keinen Vorteil in Spielen und lassen sich nicht in Coins oder Kanalpunkte tauschen.

import { HttpError, rest } from "./_lib/coins.js";
import { verify } from "./_lib/ticket.js";
import { ownedItems, shopItems, stripe, stripeConfigured } from "./_lib/shop.js";

const ORIGINS = ["https://144-24-253-88.sslip.io", "https://virtual.tnt-zockt.ch"]
  .concat((process.env.WORLD_ORIGINS || "").split(","))
  .map((value) => value.trim())
  .filter(Boolean);

function cors(req, res) {
  const origin = req.headers.origin;
  if (origin && ORIGINS.includes(origin)) {
    res.setHeader("Access-Control-Allow-Origin", origin);
    res.setHeader("Vary", "Origin");
    res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
    res.setHeader("Access-Control-Max-Age", "600");
  } else if (req.method === "GET") {
    res.setHeader("Access-Control-Allow-Origin", "*");
  }
}

/** Nach Stripe landet man auf einer kurzen Seite dieser Website, nicht in einer zweiten Welt. */
function doneUrl(req) {
  const host = req.headers["x-forwarded-host"] || req.headers.host;
  return `https://${host}/api/shop?done=`;
}

function donePage(res, ok) {
  res.setHeader("Content-Type", "text/html; charset=utf-8");
  res.setHeader("Cache-Control", "no-store");
  const title = ok ? "Danke für deinen Kauf!" : "Kauf abgebrochen";
  const text = ok
    ? "Die Zahlung ist angekommen. Schliess diesen Tab, geh zurück in die Virtual World und tippe in der Boutique auf «Käufe prüfen». Danach findest du den Artikel im Menü unter Avatar."
    : "Es wurde nichts bezahlt. Du kannst diesen Tab schliessen und in der Boutique jederzeit neu wählen.";
  res.status(200).send(`<!doctype html><html lang="de"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${title}</title>
<body style="font-family:system-ui,sans-serif;background:#05060f;color:#fff;display:grid;place-items:center;min-height:100vh;margin:0">
<main style="max-width:32rem;padding:2rem;border:1px solid #ff2fb366;border-radius:12px;background:#0b0f22">
<h1 style="margin-top:0">${ok ? "🛍️ " : ""}${title}</h1><p style="color:#c7d2e8;line-height:1.5">${text}</p>
<p><a style="color:#16e0d0" href="javascript:window.close()">Tab schliessen</a></p></main></body></html>`);
}

async function startCheckout(req, res) {
  const match = /^World\s+(\S+)$/i.exec(req.headers["authorization"] || "");
  const player = match ? verify("world", match[1]) : null;
  if (!player || typeof player.sub !== "string") throw new HttpError(401, "Bitte die Welt über tnt-zockt.ch betreten");

  const wanted = String((req.body || {}).item || "");
  const item = (await shopItems()).find((entry) => entry.id === wanted);
  if (!item) throw new HttpError(404, "Artikel nicht gefunden");
  if ((await ownedItems(player.sub)).includes(item.id)) throw new HttpError(409, "Diesen Artikel hast du schon");

  const back = doneUrl(req);
  const name = String(player.name || "").slice(0, 40);
  const session = await stripe("/checkout/sessions", {
    method: "POST",
    body: {
      mode: "payment",
      locale: "de",
      client_reference_id: player.sub,
      line_items: [
        {
          quantity: 1,
          price_data: {
            currency: "chf",
            unit_amount: Math.round(Number(item.price_chf) * 100),
            product_data: { name: `Virtual World: ${item.name}`, description: item.description || "Kosmetischer Artikel für deine Figur" }
          }
        }
      ],
      metadata: { sub: player.sub, item: item.id, name },
      payment_intent_data: { description: `TNT-Zockt Virtual World: ${item.name}`, metadata: { sub: player.sub, item: item.id } },
      custom_text: { submit: { message: "Rein kosmetischer Artikel für deine Figur in der TNT-Zockt Virtual World. Kein Spielguthaben, kein Umtausch in Coins oder Kanalpunkte." } },
      success_url: `${back}ok`,
      cancel_url: `${back}cancel`
    }
  });
  return res.status(200).json({ url: session.url });
}

async function recordPurchase(session) {
  const meta = session.metadata || {};
  if (session.payment_status !== "paid" || !meta.sub || !meta.item) return;
  await rest("world_purchases?on_conflict=stripe_session", {
    method: "POST",
    prefer: "resolution=ignore-duplicates,return=minimal",
    body: {
      sub: meta.sub,
      name: meta.name || "",
      item: meta.item,
      amount_chf: (session.amount_total || 0) / 100,
      stripe_session: session.id,
      payment_intent: typeof session.payment_intent === "string" ? session.payment_intent : null,
      status: "paid"
    }
  });
}

async function revoke(paymentIntent) {
  if (typeof paymentIntent !== "string" || !paymentIntent) return;
  await rest(`world_purchases?payment_intent=eq.${encodeURIComponent(paymentIntent)}`, {
    method: "PATCH",
    prefer: "return=minimal",
    body: { status: "refunded" }
  });
}

/** Stripe meldet ein Ereignis. Es zählt nur, was Stripe beim Nachfragen selbst bestätigt. */
async function webhook(req, res) {
  const id = String((req.body || {}).id || "");
  if (!/^evt_[A-Za-z0-9]+$/.test(id)) return res.status(400).json({ error: "Kein Ereignis" });
  const event = await stripe(`/events/${id}`);
  const object = (event.data && event.data.object) || {};
  if (event.type === "checkout.session.completed" || event.type === "checkout.session.async_payment_succeeded") {
    await recordPurchase(object);
  } else if (event.type === "charge.refunded" && object.refunded) {
    await revoke(object.payment_intent);
  } else if (event.type === "charge.dispute.created") {
    await revoke(object.payment_intent);
  }
  return res.status(200).json({ received: true });
}

export default async function handler(req, res) {
  cors(req, res);
  if (req.method === "OPTIONS") return res.status(204).end();
  res.setHeader("Cache-Control", "no-store");
  try {
    if (req.method === "GET" && (req.query || {}).done) return donePage(res, req.query.done === "ok");
    if (req.method === "GET") {
      const items = await shopItems().catch(() => []);
      res.setHeader("Cache-Control", "public, max-age=60");
      return res.status(200).json({ ready: stripeConfigured(), items });
    }
    if (req.method !== "POST") return res.status(405).json({ error: "Nur GET oder POST" });
    if ((req.query || {}).webhook) return await webhook(req, res);
    return await startCheckout(req, res);
  } catch (err) {
    const status = err instanceof HttpError ? err.status : 500;
    return res.status(status).json({ error: status === 500 ? "Boutique gerade nicht erreichbar" : err.message });
  }
}
