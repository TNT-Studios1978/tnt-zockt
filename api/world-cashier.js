// Vercel Serverless Function
// Kasse in der TNT-Zockt Virtual World: Kanalpunkte direkt in der Welt in TNT-Coins tauschen.
// Anmeldung mit dem Welt-Ticket (/api/world-ticket), das der Spieler beim Betreten der Welt bekommt.
//
// GET  /api/world-cashier            -> { points, balance, rate }
// POST /api/world-cashier {points}   -> { balance, coins, points }

import { HttpError, rpc, getSettings, seGetPoints, sendError, exchangePoints } from "./_lib/coins.js";
import { verify } from "./_lib/ticket.js";

// Nur die Welt darf die Kasse aus dem Browser aufrufen.
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
    res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
    res.setHeader("Access-Control-Max-Age", "600");
  }
}

/** Twitch-Login aus dem Welt-Ticket. Kick-Spieler haben keine TNT-Coins. */
function playerLogin(req) {
  const match = /^World\s+(\S+)$/i.exec(req.headers["authorization"] || "");
  const ticket = match ? verify("world", match[1]) : null;
  if (!ticket) throw new HttpError(401, "Ticket abgelaufen");
  const login = typeof ticket.login === "string" ? ticket.login : "";
  if (!String(ticket.sub || "").startsWith("twitch:") || !/^[a-z0-9_]{1,25}$/.test(login)) {
    throw new HttpError(403, "Nur mit Twitch-Login möglich");
  }
  return login;
}

export default async function handler(req, res) {
  cors(req, res);
  res.setHeader("Cache-Control", "no-store");
  if (req.method === "OPTIONS") return res.status(204).end();

  try {
    const login = playerLogin(req);
    if (req.method === "GET") {
      const [points, balance, settings] = await Promise.all([seGetPoints(login), rpc("coin_balance", { p_login: login }), getSettings()]);
      return res.status(200).json({ points, balance, rate: Number(settings.points_to_coins) });
    }
    if (req.method === "POST") {
      return res.status(200).json(await exchangePoints(login, (req.body || {}).points));
    }
    return res.status(405).json({ error: "Methode nicht erlaubt" });
  } catch (err) {
    return sendError(res, err);
  }
}
