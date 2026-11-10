// Vercel Serverless Function
// Inhalt für die grosse Wand in der TNT-Zockt Virtual World:
// ob der Twitch-Kanal live ist, und sonst die Shop-Artikel für die Werbung.
// Dazu die Streamer fürs Stream-Terminal: alle im Admin mit "In der Virtual World zeigen" angehakten
// Twitch-Kanäle und welche davon gerade live sind. Der eigene Kanal steht immer zuerst.
// Öffentlich lesbar (CORS *), enthält nur Daten, die ohnehin auf der Website stehen.

import { SUPABASE_URL, SUPABASE_ANON_KEY } from "./_lib/coins.js";

const CHANNEL = "tnt_studiosch";

let cachedToken = null;
let cachedTokenExpiry = 0;

async function twitchToken() {
  if (cachedToken && Date.now() < cachedTokenExpiry) return cachedToken;
  const params = new URLSearchParams({
    client_id: process.env.TWITCH_CLIENT_ID,
    client_secret: process.env.TWITCH_CLIENT_SECRET,
    grant_type: "client_credentials"
  });
  const res = await fetch(`https://id.twitch.tv/oauth2/token?${params}`, { method: "POST" });
  const data = await res.json();
  if (!res.ok) throw new Error("Twitch-Token-Fehler");
  cachedToken = data.access_token;
  cachedTokenExpiry = Date.now() + (data.expires_in - 60) * 1000;
  return cachedToken;
}

function streamInfo(stream, name, own) {
  return {
    channel: String(stream.user_login || "").toLowerCase(),
    name: String(name || stream.user_name || stream.user_login || "").slice(0, 40),
    title: String(stream.title || "").slice(0, 140),
    game: String(stream.game_name || "").slice(0, 60),
    viewers: Number(stream.viewer_count) || 0,
    startedAt: stream.started_at || null,
    own
  };
}

/** Angehakte Twitch-Kanäle aus der Streamer-Liste. Fehlt die Spalte in_world noch, gibt es nur den eigenen Kanal. */
async function worldStreamers() {
  try {
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/streamers?select=name,link,platform,sort_order&in_world=eq.true&platform=eq.twitch&order=sort_order.asc&limit=60`,
      { headers: { apikey: SUPABASE_ANON_KEY } }
    );
    if (!res.ok) return [];
    const seen = new Set([CHANNEL]);
    const list = [];
    for (const row of await res.json()) {
      const match = /twitch\.tv\/([A-Za-z0-9_]{3,25})/.exec(String(row?.link || ""));
      const login = match ? match[1].toLowerCase() : "";
      if (!login || seen.has(login)) continue;
      seen.add(login);
      list.push({ channel: login, name: String(row.name || login).slice(0, 40) });
    }
    return list;
  } catch (e) {
    return [];
  }
}

/** Live-Status für den eigenen Kanal und alle Streamer in einer Abfrage (Twitch erlaubt bis 100 Kanäle). */
async function liveStreams(streamers) {
  if (!process.env.TWITCH_CLIENT_ID || !process.env.TWITCH_CLIENT_SECRET) return [];
  try {
    const logins = [CHANNEL, ...streamers.map((s) => s.channel)].slice(0, 100);
    const query = logins.map((login) => `user_login=${encodeURIComponent(login)}`).join("&");
    const res = await fetch(`https://api.twitch.tv/helix/streams?first=100&${query}`, {
      headers: { "Client-Id": process.env.TWITCH_CLIENT_ID, Authorization: `Bearer ${await twitchToken()}` }
    });
    if (!res.ok) return [];
    const names = new Map(streamers.map((s) => [s.channel, s.name]));
    return ((await res.json()).data || [])
      .filter((stream) => stream && stream.type === "live")
      .map((stream) => {
        const login = String(stream.user_login || "").toLowerCase();
        return streamInfo(stream, login === CHANNEL ? "TNT-Zockt" : names.get(login), login === CHANNEL);
      })
      .sort((a, b) => Number(b.own) - Number(a.own) || b.viewers - a.viewers);
  } catch (e) {
    return [];
  }
}

async function products() {
  try {
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/products?select=id,name,price,image_url,category,is_hit,sort_order&order=sort_order.asc&limit=24`,
      { headers: { apikey: SUPABASE_ANON_KEY } }
    );
    if (!res.ok) return [];
    const rows = await res.json();
    return rows
      .filter((p) => p && p.name)
      .map((p) => ({
        id: String(p.id),
        name: String(p.name).slice(0, 60),
        price: String(p.price || "").slice(0, 20),
        category: String(p.category || "").slice(0, 30),
        hit: Boolean(p.is_hit),
        // Nur Bilder aus dem eigenen Supabase-Speicher: die erlauben das Laden in die 3D-Welt (CORS).
        image: typeof p.image_url === "string" && p.image_url.startsWith(`${SUPABASE_URL}/storage/`) ? p.image_url : ""
      }));
  } catch (e) {
    return [];
  }
}

export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "GET") return res.status(405).json({ error: "Nur GET erlaubt" });

  const [streamers, items] = await Promise.all([worldStreamers(), products()]);
  const streams = await liveStreams(streamers);
  const own = streams.find((stream) => stream.own) || null;
  const live = own ? { channel: own.channel, title: own.title, game: own.game, viewers: own.viewers, startedAt: own.startedAt } : null;
  // Eine Minute zwischenspeichern, damit nicht jeder Besucher Twitch und die Datenbank abfragt.
  res.setHeader("Cache-Control", "public, s-maxage=60, stale-while-revalidate=120");
  return res.status(200).json({
    live,
    streams,
    streamers: [{ channel: CHANNEL, name: "TNT-Zockt", own: true }, ...streamers.map((s) => ({ ...s, own: false }))],
    products: items,
    shopUrl: "https://www.tnt-zockt.ch/shop.html"
  });
}
