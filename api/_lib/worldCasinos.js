// Casino-Räume der Virtual World. Dateien in api/_lib sind keine eigenen Endpunkte; aufgerufen über
// /api/world-casinos und /api/world-casino-consent (Umleitung auf /api/world-ticket, siehe vercel.json).
//
// - Liste: Partner-Casinos für einen Raum. Schweiz: aus casinos_ch, ausser im Admin abgehakte (in_world). International: nur im Admin angehakte
//   (casinos.in_world) und nie für Besucher aus der Schweiz (Land aus der Geolocation von Vercel).
// - Bestätigung: Wer den Hinweis vor dem ersten Spiel bestätigt, wird mit Konto, Textversion, Raum und Zeit
//   gespeichert (Tabelle world_casino_consents, nur mit dem Server-Schlüssel lesbar). Keine IP, kein Gerät.

import { rest } from "./coins.js";
import { verify } from "./ticket.js";

const ROOMS = new Set(["casino-ch", "casino-intl"]);

function countryOf(req) {
  const value = String(req.headers["x-vercel-ip-country"] || "").toUpperCase();
  return /^[A-Z]{2}$/.test(value) ? value : "";
}

/** Spieler aus "Authorization: World <ticket>", sonst null. */
function playerOf(req) {
  const match = /^World\s+(\S+)$/i.exec(req.headers["authorization"] || "");
  return match ? verify("world", match[1]) : null;
}

const clean = (value, max) => String(value || "").slice(0, max);

async function casinoList(req, res, room) {
  if (room === "casino-intl") {
    const country = countryOf(req);
    if (!country || country === "CH") return res.status(403).json({ error: "Nicht aus der Schweiz verfügbar" });
  }
  const rows =
    room === "casino-ch"
      ? await rest("casinos_ch?select=*&order=sort_order.asc")
      : await rest("casinos?select=id,name,logo_url,bonus,link,home_url,sort_order&in_world=eq.true&order=sort_order.asc");
  const casinos = (rows || [])
    // Schweiz: im Admin abgehakte nicht aufstellen (fehlt die Spalte noch, stehen alle)
    .filter((row) => row.in_world !== false)
    // Schweiz: Casinos ohne Affiliate-Link öffnen ihre Startseite (Anzeige und Knöpfe)
    .map((row) => (room === "casino-ch" && !/^https:\/\//i.test(row.link || "") ? { ...row, link: row.home_url } : row))
    .filter((row) => /^https:\/\//i.test(row.link || ""))
    .map((row) => ({ id: clean(row.id, 64), name: clean(row.name, 40), logo: clean(row.logo_url, 400), bonus: clean(row.bonus, 120), link: clean(row.link, 600), home: /^https:\/\//i.test(row.home_url || "") ? clean(row.home_url, 600) : "" }));
  res.setHeader("Cache-Control", "private, no-store");
  return res.status(200).json({ room, casinos });
}

async function consent(req, res, player, room) {
  const version = Number((req.method === "POST" ? (req.body || {}).version : req.query.version) || 0);
  if (!Number.isInteger(version) || version < 1 || version > 1000) return res.status(400).json({ error: "Version fehlt" });
  if (req.method === "GET") {
    const rows = await rest(`world_casino_consents?select=accepted_at&sub=eq.${encodeURIComponent(player.sub)}&version=eq.${version}&room=eq.${room}&limit=1`);
    return res.status(200).json({ accepted: Boolean(rows && rows.length), at: rows && rows[0] ? rows[0].accepted_at : null });
  }
  await rest("world_casino_consents?on_conflict=sub,version,room", {
    method: "POST",
    prefer: "resolution=ignore-duplicates,return=minimal",
    body: { sub: player.sub, name: clean(player.name, 40), room, version }
  });
  return res.status(200).json({ accepted: true });
}

/** Behandelt die Casino-Anfragen, falls es eine ist (sonst false). */
export async function handleWorldCasinos(req, res) {
  const which = req.query.casinos ? "list" : req.query.consent ? "consent" : null;
  if (!which) return false;
  res.setHeader("Cache-Control", "no-store");
  try {
    const room = String(req.query.room || (req.body || {}).room || "");
    if (!ROOMS.has(room)) return res.status(400).json({ error: "Raum unbekannt" }), true;
    const player = playerOf(req);
    if (!player) return res.status(401).json({ error: "Nicht angemeldet" }), true;
    if (which === "list") {
      if (req.method !== "GET") return res.status(405).json({ error: "Nur GET" }), true;
      await casinoList(req, res, room);
      return true;
    }
    if (req.method !== "GET" && req.method !== "POST") return res.status(405).json({ error: "Nur GET oder POST" }), true;
    await consent(req, res, player, room);
    return true;
  } catch (err) {
    res.status(500).json({ error: "Casino-Daten nicht verfügbar" });
    return true;
  }
}
