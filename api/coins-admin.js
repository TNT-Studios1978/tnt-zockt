// Vercel Serverless Function — Admin-Funktionen für TNT-Coins.
// Nur für Supabase-Nutzer, die in der Tabelle admin_users eingetragen sind.
//
// GET  ?view=requests[&status=pending|processing|all]
// GET  ?view=player&login=<twitch>
// GET  ?view=settings
// POST {action:"approve", id}                 Punkte gutschreiben (StreamElements)
// POST {action:"reject",  id, note}           ablehnen, Coins zurück
// POST {action:"mark_done", id}               hängenden Antrag als erledigt markieren (ohne erneute Gutschrift)
// POST {action:"refund",  id, note}           hängenden Antrag zurückbuchen
// POST {action:"adjust",  login, amount, note} Coins manuell korrigieren (+/-)
// POST {action:"settings", values:{...}}      Grenzwerte / Not-Aus

import {
  HttpError, verifyUser, rpc, rest, getSettings, seAddPoints, sendError
} from "./_lib/coins.js";

const SETTING_KEYS = {
  points_to_coins: "num", coins_to_points: "num", daily_auto_limit_points: "int",
  min_account_age_days: "int", max_rtp: "num", rtp_min_bets: "int",
  max_rounds_per_10min: "int", min_payout_coins: "int", auto_payout_enabled: "bool"
};

async function verifyAdmin(req) {
  const u = await verifyUser(req);
  const rows = await rest(`admin_users?user_id=eq.${encodeURIComponent(u.id)}&select=user_id`);
  if (!rows || rows.length === 0) throw new HttpError(403, "Kein Admin-Zugriff");
  return u;
}

function cleanLogin(v) {
  const s = String(v || "").trim().toLowerCase();
  if (!/^[a-z0-9_]{2,40}$/.test(s)) throw new HttpError(400, "Ungültiger Twitch-Name");
  return s;
}

function cleanId(v) {
  const n = Number(v);
  if (!Number.isInteger(n) || n <= 0) throw new HttpError(400, "Ungültige ID");
  return n;
}

async function getRequest(id) {
  const rows = await rest(`payout_requests?id=eq.${id}&select=*`);
  if (!rows || !rows[0]) throw new HttpError(404, "Antrag nicht gefunden");
  return rows[0];
}

async function playerView(login) {
  const [wallet, ledger, rounds, requests] = await Promise.all([
    rest(`user_chips?twitch_login=eq.${encodeURIComponent(login)}&select=*`),
    rest(`coin_ledger?twitch_login=eq.${encodeURIComponent(login)}&order=id.desc&limit=300&select=*`),
    rest(`coin_rounds?twitch_login=eq.${encodeURIComponent(login)}&order=id.desc&limit=1000&select=game,stake,won,created_at`),
    rest(`payout_requests?twitch_login=eq.${encodeURIComponent(login)}&order=id.desc&limit=50&select=*`)
  ]);
  const checks = await rpc("coin_payout_checks", { p_login: login, p_points: 0 }).catch(() => []);
  const perGame = {};
  for (const r of rounds || []) {
    const g = perGame[r.game] || (perGame[r.game] = { rounds: 0, stake: 0, won: 0 });
    g.rounds++; g.stake += r.stake; g.won += r.won;
  }
  return {
    login,
    balance: wallet && wallet[0] ? wallet[0].chips : null,
    checks,
    per_game: perGame,
    ledger,
    requests
  };
}

export default async function handler(req, res) {
  res.setHeader("Cache-Control", "no-store");
  try {
    const admin = await verifyAdmin(req);
    const by = admin.email || admin.id;

    if (req.method === "GET") {
      const view = req.query.view;
      if (view === "settings") return res.status(200).json(await getSettings());
      if (view === "player") return res.status(200).json(await playerView(cleanLogin(req.query.login)));
      if (view === "requests") {
        const status = req.query.status || "open";
        const filter = status === "all" ? "" : status === "open"
          ? "&status=in.(pending,processing)" : `&status=eq.${encodeURIComponent(status)}`;
        return res.status(200).json(await rest(`payout_requests?order=id.desc&limit=200${filter}&select=*`));
      }
      return res.status(400).json({ error: "Unbekannte Ansicht" });
    }
    if (req.method !== "POST") return res.status(405).json({ error: "Methode nicht erlaubt" });

    const body = req.body || {};
    switch (body.action) {
      case "approve": {
        const id = cleanId(body.id);
        const r = await rpc("coin_payout_claim", { p_id: id }); // pending -> processing (nur einmal möglich)
        try {
          await seAddPoints(r.twitch_login, r.points);
        } catch (e) {
          // Gutschrift fehlgeschlagen -> wieder auf "offen" setzen, damit erneut versucht werden kann
          await rest(`payout_requests?id=eq.${id}&status=eq.processing`, {
            method: "PATCH", body: { status: "pending", error: String(e.message).slice(0, 300) }
          });
          throw new HttpError(502, "StreamElements-Gutschrift fehlgeschlagen, Antrag bleibt offen");
        }
        await rpc("coin_payout_finish", { p_id: id, p_status: "approved", p_by: by, p_error: null });
        return res.status(200).json({ ok: true });
      }
      case "reject": {
        const id = cleanId(body.id);
        const r = await getRequest(id);
        if (r.status !== "pending") throw new HttpError(409, "Nur offene Anträge können abgelehnt werden");
        await rpc("coin_payout_finish", { p_id: id, p_status: "rejected", p_by: by, p_error: body.note ? String(body.note).slice(0, 300) : null });
        return res.status(200).json({ ok: true });
      }
      case "mark_done": {
        const id = cleanId(body.id);
        const r = await getRequest(id);
        if (r.status !== "processing") throw new HttpError(409, "Nur hängende Anträge");
        await rpc("coin_payout_finish", { p_id: id, p_status: "approved", p_by: by, p_error: "manuell als erledigt markiert" });
        return res.status(200).json({ ok: true });
      }
      case "refund": {
        const id = cleanId(body.id);
        const r = await getRequest(id);
        if (r.status !== "processing") throw new HttpError(409, "Nur hängende Anträge");
        await rpc("coin_payout_finish", { p_id: id, p_status: "failed", p_by: by, p_error: body.note ? String(body.note).slice(0, 300) : "manuell zurückgebucht" });
        return res.status(200).json({ ok: true });
      }
      case "adjust": {
        const login = cleanLogin(body.login);
        const amount = Number(body.amount);
        if (!Number.isInteger(amount) || amount === 0 || Math.abs(amount) > 100000000) throw new HttpError(400, "Ungültiger Betrag");
        const note = "Admin (" + by + "): " + String(body.note || "Korrektur").slice(0, 200);
        const balance = await rpc("coin_admin_adjust", { p_login: login, p_amount: amount, p_note: note });
        return res.status(200).json({ balance });
      }
      case "settings": {
        const values = body.values || {};
        const patch = { updated_at: new Date().toISOString() };
        for (const [k, type] of Object.entries(SETTING_KEYS)) {
          if (!(k in values)) continue;
          const v = values[k];
          if (type === "bool") patch[k] = !!v;
          else {
            const n = Number(v);
            if (!Number.isFinite(n) || n < 0 || (type === "int" && !Number.isInteger(n))) throw new HttpError(400, "Ungültiger Wert für " + k);
            patch[k] = n;
          }
        }
        const rows = await rest("coin_settings?id=eq.1", { method: "PATCH", body: patch, prefer: "return=representation" });
        return res.status(200).json(rows && rows[0]);
      }
      default:
        return res.status(400).json({ error: "Unbekannte Aktion" });
    }
  } catch (err) {
    return sendError(res, err);
  }
}
