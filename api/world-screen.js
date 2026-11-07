// Vercel Serverless Function
// Inhalt für die grosse Wand in der TNT-Zockt Virtual World:
// ob der Twitch-Kanal live ist, und sonst die Shop-Artikel für die Werbung.
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

async function liveStatus() {
  if (!process.env.TWITCH_CLIENT_ID || !process.env.TWITCH_CLIENT_SECRET) return null;
  try {
    const res = await fetch(`https://api.twitch.tv/helix/streams?user_login=${CHANNEL}`, {
      headers: { "Client-Id": process.env.TWITCH_CLIENT_ID, Authorization: `Bearer ${await twitchToken()}` }
    });
    if (!res.ok) return null;
    const stream = ((await res.json()).data || [])[0];
    if (!stream || stream.type !== "live") return null;
    return {
      channel: CHANNEL,
      title: String(stream.title || "").slice(0, 140),
      game: String(stream.game_name || "").slice(0, 60),
      viewers: Number(stream.viewer_count) || 0,
      startedAt: stream.started_at || null
    };
  } catch (e) {
    return null;
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

  const [live, items] = await Promise.all([liveStatus(), products()]);
  // Eine Minute zwischenspeichern, damit nicht jeder Besucher Twitch und die Datenbank abfragt.
  res.setHeader("Cache-Control", "public, s-maxage=60, stale-while-revalidate=120");
  return res.status(200).json({ live, products: items, shopUrl: "https://www.tnt-zockt.ch/shop.html" });
}
