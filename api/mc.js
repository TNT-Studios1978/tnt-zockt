// Vercel Serverless Function — Minecraft-Shop.
//
// 1) Minecraft-Server (Plugin "TNT-Shop"), Header "X-MC-Secret: <MC_SERVER_SECRET>":
//    POST {action:"link_code", uuid, name}  -> Code für /link
//    POST {action:"status",    uuid}        -> Verknüpfung + Kanalpunkte
//    POST {action:"kits"}                    -> aktive Pakete
//    POST {action:"buy",       uuid, kit_id} -> Punkte abbuchen, Items zurückgeben
//
// 2) Spieler auf der Website (Twitch-Login):
//    GET  ?view=kits                         -> aktive Pakete (öffentlich)
//    GET  ?view=me                           -> eigene Verknüpfung + Käufe
//    POST {action:"confirm_link", code}
//    POST {action:"unlink"}
//
// 3) Admin (Supabase-Login, in admin_users eingetragen):
//    GET  ?view=admin                        -> alle Pakete, letzte Käufe, Verknüpfungen
//    POST {action:"admin_save_kit", kit}
//    POST {action:"admin_delete_kit", id}

import crypto from "crypto";
import {
  HttpError, verifyUser, verifyTwitchUser, rpc, rest, seGetPoints, seAddPoints, sendError
} from "./_lib/coins.js";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const NAME_RE = /^[A-Za-z0-9_]{1,16}$/;
const KIT_RE = /^[a-z0-9_-]{1,40}$/;
const MATERIAL_RE = /^[A-Z0-9_]{2,60}$/;

function checkServerSecret(req) {
  const expected = (process.env.MC_SERVER_SECRET || "").trim();
  const given = String(req.headers["x-mc-secret"] || "");
  if (expected.length < 24) throw new HttpError(500, "MC_SERVER_SECRET fehlt oder ist zu kurz (mind. 24 Zeichen)");
  const a = Buffer.from(given), b = Buffer.from(expected);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) throw new HttpError(401, "Ungültiges Server-Geheimnis");
}

function uuid(v) {
  const s = String(v || "");
  if (!UUID_RE.test(s)) throw new HttpError(400, "Ungültige UUID");
  return s.toLowerCase();
}

async function activeKits() {
  return rest("mc_kits?enabled=eq.true&order=sort_order.asc&select=id,name,description,icon,price_points,cooldown_hours,once_only,items");
}

async function verifyAdmin(req) {
  const u = await verifyUser(req);
  const rows = await rest(`admin_users?user_id=eq.${encodeURIComponent(u.id)}&select=user_id`);
  if (!rows || rows.length === 0) throw new HttpError(403, "Kein Admin-Zugriff");
  return u;
}

// ---------- Server-Aktionen ----------
async function serverAction(body) {
  switch (body.action) {
    case "link_code": {
      const name = String(body.name || "");
      if (!NAME_RE.test(name)) throw new HttpError(400, "Ungültiger Spielername");
      const code = await rpc("mc_create_link_code", { p_uuid: uuid(body.uuid), p_name: name });
      return { code, expires_minutes: 10 };
    }
    case "status": {
      const rows = await rest(`mc_links?mc_uuid=eq.${uuid(body.uuid)}&select=twitch_login,mc_name,linked_at`);
      if (!rows || !rows[0]) return { linked: false };
      const points = await seGetPoints(rows[0].twitch_login);
      return { linked: true, twitch_login: rows[0].twitch_login, points };
    }
    case "kits":
      return { kits: await activeKits() };
    case "buy": {
      const kitId = String(body.kit_id || "");
      if (!KIT_RE.test(kitId)) throw new HttpError(400, "Ungültiges Paket");
      const r = await rpc("mc_reserve_purchase", { p_uuid: uuid(body.uuid), p_kit: kitId });
      if (r.points > 0) {
        try {
          const current = await seGetPoints(r.twitch_login);
          if (current < r.points) throw new HttpError(400, `Nicht genug Kanalpunkte (${current} von ${r.points})`);
          const newAmount = await seAddPoints(r.twitch_login, -r.points);
          if (newAmount !== null && newAmount < 0) {
            await seAddPoints(r.twitch_login, r.points);
            throw new HttpError(409, "Nicht genug Kanalpunkte");
          }
        } catch (e) {
          await rpc("mc_finish_purchase", { p_id: r.purchase_id, p_status: "refunded", p_note: String(e.message).slice(0, 200) });
          throw e;
        }
      }
      await rpc("mc_finish_purchase", { p_id: r.purchase_id, p_status: "delivered", p_note: null });
      const points = await seGetPoints(r.twitch_login).catch(() => null);
      return { purchase_id: r.purchase_id, name: r.name, items: r.items, paid: r.points, points };
    }
    default:
      throw new HttpError(400, "Unbekannte Aktion");
  }
}

// ---------- Admin ----------
function cleanKit(k) {
  if (!k || typeof k !== "object") throw new HttpError(400, "Paket fehlt");
  const id = String(k.id || "").trim().toLowerCase();
  if (!KIT_RE.test(id)) throw new HttpError(400, "ID: nur a-z, 0-9, - und _");
  const name = String(k.name || "").trim();
  if (!name || name.length > 60) throw new HttpError(400, "Name fehlt oder ist zu lang");
  let items = k.items;
  if (typeof items === "string") {
    try { items = JSON.parse(items); } catch (e) { throw new HttpError(400, "Items: ungültiges JSON"); }
  }
  if (!Array.isArray(items) || items.length === 0 || items.length > 36) throw new HttpError(400, "Items: 1 bis 36 Einträge");
  items = items.map(it => {
    const material = String((it && it.material) || "").toUpperCase();
    const amount = Number(it && it.amount);
    if (!MATERIAL_RE.test(material)) throw new HttpError(400, "Ungültiges Material: " + material);
    if (!Number.isInteger(amount) || amount < 1 || amount > 64) throw new HttpError(400, "Menge 1–64 bei " + material);
    return { material, amount };
  });
  const int = (v, min, max, label) => {
    const n = Number(v);
    if (!Number.isInteger(n) || n < min || n > max) throw new HttpError(400, label + " ungültig");
    return n;
  };
  const icon = String(k.icon || "CHEST").toUpperCase();
  if (!MATERIAL_RE.test(icon)) throw new HttpError(400, "Ungültiges Symbol");
  return {
    id, name,
    description: String(k.description || "").slice(0, 200),
    icon,
    price_points: int(k.price_points, 0, 100000000, "Preis"),
    cooldown_hours: int(k.cooldown_hours, 0, 8760, "Abklingzeit"),
    once_only: !!k.once_only,
    enabled: k.enabled !== false,
    items,
    sort_order: int(k.sort_order || 0, -1000, 1000, "Reihenfolge")
  };
}

export default async function handler(req, res) {
  res.setHeader("Cache-Control", "no-store");
  try {
    // Aufruf vom Minecraft-Server
    if (req.headers["x-mc-secret"] !== undefined) {
      if (req.method !== "POST") return res.status(405).json({ error: "Nur POST" });
      checkServerSecret(req);
      return res.status(200).json(await serverAction(req.body || {}));
    }

    if (req.method === "GET") {
      const view = req.query.view;
      if (view === "kits") return res.status(200).json({ kits: await activeKits() });
      if (view === "me") {
        const u = await verifyTwitchUser(req);
        const [links, purchases] = await Promise.all([
          rest(`mc_links?twitch_login=eq.${encodeURIComponent(u.login)}&select=mc_name,linked_at`),
          rest(`mc_purchases?twitch_login=eq.${encodeURIComponent(u.login)}&order=id.desc&limit=20&select=kit_id,points,status,created_at`)
        ]);
        return res.status(200).json({ twitch_login: u.login, link: links && links[0] || null, purchases });
      }
      if (view === "admin") {
        await verifyAdmin(req);
        const [kits, purchases, links] = await Promise.all([
          rest("mc_kits?order=sort_order.asc&select=*"),
          rest("mc_purchases?order=id.desc&limit=100&select=*"),
          rest("mc_links?order=linked_at.desc&limit=200&select=*")
        ]);
        return res.status(200).json({ kits, purchases, links });
      }
      return res.status(400).json({ error: "Unbekannte Ansicht" });
    }
    if (req.method !== "POST") return res.status(405).json({ error: "Methode nicht erlaubt" });

    const body = req.body || {};
    switch (body.action) {
      case "confirm_link": {
        const u = await verifyTwitchUser(req);
        const code = String(body.code || "").trim().toUpperCase();
        if (!/^[A-Z0-9]{6}$/.test(code)) throw new HttpError(400, "Code ungültig oder abgelaufen");
        return res.status(200).json(await rpc("mc_confirm_link", { p_code: code, p_login: u.login }));
      }
      case "unlink": {
        const u = await verifyTwitchUser(req);
        await rest(`mc_links?twitch_login=eq.${encodeURIComponent(u.login)}`, { method: "DELETE" });
        return res.status(200).json({ ok: true });
      }
      case "admin_save_kit": {
        await verifyAdmin(req);
        const kit = cleanKit(body.kit);
        const rows = await rest("mc_kits?on_conflict=id", {
          method: "POST", body: kit, prefer: "resolution=merge-duplicates,return=representation"
        });
        return res.status(200).json(rows && rows[0]);
      }
      case "admin_delete_kit": {
        await verifyAdmin(req);
        const id = String(body.id || "");
        if (!KIT_RE.test(id)) throw new HttpError(400, "Ungültige ID");
        await rest(`mc_kits?id=eq.${encodeURIComponent(id)}`, { method: "DELETE" });
        return res.status(200).json({ ok: true });
      }
      default:
        return res.status(400).json({ error: "Unbekannte Aktion" });
    }
  } catch (err) {
    return sendError(res, err);
  }
}
