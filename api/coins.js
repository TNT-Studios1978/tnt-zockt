// Vercel Serverless Function — TNT-Coins für eingeloggte Twitch-Nutzer.
//
// GET  /api/coins                       -> Saldo, Einstellungen, letzte Buchungen, Anträge
// POST /api/coins {action:"bet",   game, amount}          Einsatz (neue Runde)
// POST /api/coins {action:"raise", game, amount}          Einsatz der laufenden Runde erhöhen
// POST /api/coins {action:"win",   game, amount}          Gewinn auf die laufende Runde
// POST /api/coins {action:"exchange", points}             Kanalpunkte -> Coins
// POST /api/coins {action:"payout", coins}                Coins -> Kanalpunkte (mit Prüfung)

import {
  MAX_MULT, HttpError, verifyTwitchUser, rpc, rest, getSettings,
  seGetPoints, seAddPoints, sendError, intAmount
} from "./_lib/coins.js";

function checkGame(game) {
  if (!Object.prototype.hasOwnProperty.call(MAX_MULT, game)) throw new HttpError(400, "Unbekanntes Spiel");
  return game;
}

async function overview(login) {
  const [balance, settings, ledger, requests] = await Promise.all([
    rpc("coin_balance", { p_login: login }),
    getSettings(),
    rest(`coin_ledger?twitch_login=eq.${encodeURIComponent(login)}&order=id.desc&limit=20&select=kind,amount,balance_after,game,note,created_at`),
    rest(`payout_requests?twitch_login=eq.${encodeURIComponent(login)}&order=id.desc&limit=10&select=id,coins,points,status,created_at,decided_at`)
  ]);
  return {
    balance,
    rates: { points_to_coins: Number(settings.points_to_coins), coins_to_points: Number(settings.coins_to_points) },
    min_payout_coins: settings.min_payout_coins,
    auto_payout_enabled: settings.auto_payout_enabled,
    ledger,
    requests
  };
}

async function exchange(login, body) {
  const points = intAmount(body.points);
  const settings = await getSettings();
  const coins = Math.floor(points * Number(settings.points_to_coins));
  if (coins <= 0) throw new HttpError(400, "Betrag zu klein");

  const current = await seGetPoints(login);
  if (current < points) throw new HttpError(400, "Nicht genug Kanalpunkte");

  const newAmount = await seAddPoints(login, -points);
  if (newAmount !== null && newAmount < 0) {
    await seAddPoints(login, points); // gleichzeitiger Umtausch -> zurückbuchen
    throw new HttpError(409, "Nicht genug Kanalpunkte");
  }
  try {
    const balance = await rpc("coin_exchange_in", { p_login: login, p_coins: coins, p_points: points });
    return { balance, coins, points };
  } catch (e) {
    await seAddPoints(login, points); // Coins konnten nicht gebucht werden -> Punkte zurück
    throw e;
  }
}

async function payout(login, body) {
  const coins = intAmount(body.coins);
  const settings = await getSettings();
  if (coins < settings.min_payout_coins) {
    throw new HttpError(400, `Mindestens ${settings.min_payout_coins} Coins`);
  }
  const points = Math.floor(coins * Number(settings.coins_to_points));
  if (points <= 0) throw new HttpError(400, "Betrag zu klein");

  const reasons = await rpc("coin_payout_checks", { p_login: login, p_points: points }) || [];
  if (!settings.auto_payout_enabled) reasons.push("Automatische Auszahlung pausiert");
  const auto = reasons.length === 0;

  const id = await rpc("coin_payout_request", {
    p_login: login, p_coins: coins, p_points: points,
    p_status: auto ? "processing" : "pending", p_reasons: reasons
  });

  if (!auto) {
    return { id, status: "pending", message: "Antrag wird geprüft", reasons };
  }
  try {
    await seAddPoints(login, points);
  } catch (e) {
    // Punkte wurden nicht gutgeschrieben -> Coins zurück
    await rpc("coin_payout_finish", { p_id: id, p_status: "failed", p_by: "auto", p_error: String(e.message).slice(0, 300) });
    throw new HttpError(502, "Auszahlung fehlgeschlagen, Coins wurden zurückgebucht");
  }
  // Punkte sind gutgeschrieben. Schlägt nur der Abschluss fehl, bleibt der Antrag
  // auf "processing" und erscheint im Admin-Bereich – keine Rückbuchung.
  try {
    await rpc("coin_payout_finish", { p_id: id, p_status: "approved_auto", p_by: "auto", p_error: null });
  } catch (e) { /* bewusst ignoriert */ }
  return { id, status: "approved_auto", points };
}

export default async function handler(req, res) {
  res.setHeader("Cache-Control", "no-store");
  try {
    const user = await verifyTwitchUser(req);
    const login = user.login;

    if (req.method === "GET") {
      return res.status(200).json(await overview(login));
    }
    if (req.method !== "POST") return res.status(405).json({ error: "Methode nicht erlaubt" });

    const body = req.body || {};
    switch (body.action) {
      case "bet":
      case "raise": {
        const r = await rpc("coin_bet", {
          p_login: login, p_game: checkGame(body.game), p_amount: intAmount(body.amount), p_raise: body.action === "raise"
        });
        return res.status(200).json(r);
      }
      case "win": {
        const game = checkGame(body.game);
        const r = await rpc("coin_win", {
          p_login: login, p_game: game, p_amount: intAmount(body.amount), p_max_mult: MAX_MULT[game]
        });
        return res.status(200).json(r);
      }
      case "exchange":
        return res.status(200).json(await exchange(login, body));
      case "payout":
        return res.status(200).json(await payout(login, body));
      default:
        return res.status(400).json({ error: "Unbekannte Aktion" });
    }
  } catch (err) {
    return sendError(res, err);
  }
}
