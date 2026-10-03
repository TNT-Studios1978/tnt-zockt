// Vercel Serverless Function
// Schreibt eine Punkte-Aenderung (Gewinn oder Verlust) auf StreamElements.
// Der StreamElements JWT-Token bleibt geheim auf dem Server (Umgebungsvariable).
// amount ist ein Delta: positiv = addieren (Gewinn), negativ = abziehen (Verlust/Einsatz).
//
// Sicherheit: Der Aufrufer muss mit Twitch (Supabase Auth) eingeloggt sein und
// seinen Access Token im Header "Authorization: Bearer <token>" mitschicken.
// Der Benutzername wird NUR aus dem geprueften Token gelesen, nie aus dem
// Request-Body. So kann niemand mehr die Punkte fremder Nutzer veraendern.

// Oeffentliche Werte (stehen auch in supabase-config.js), per Umgebungsvariable ueberschreibbar.
const SUPABASE_URL = process.env.SUPABASE_URL || "https://wlvgjdzuvfbnberejthz.supabase.co";
const SUPABASE_ANON_KEY = process.env.SUPABASE_ANON_KEY || "sb_publishable_2enioy5duKB9ZHMVjimTUw_cTLlcgYO";

function getTwitchUsername(user) {
  const meta = (user && user.user_metadata) || {};
  return meta.preferred_username || meta.nickname || meta.user_name || meta.name || meta.full_name || "";
}

async function verifyUser(req) {
  const header = req.headers["authorization"] || req.headers["Authorization"] || "";
  const match = /^Bearer\s+(.+)$/i.exec(header);
  if (!match) return null;

  const userRes = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { "Authorization": `Bearer ${match[1]}`, "apikey": SUPABASE_ANON_KEY }
  });
  if (!userRes.ok) return null;
  const user = await userRes.json();
  const username = getTwitchUsername(user);
  return username ? username.toLowerCase() : null;
}

export default async function handler(req, res) {
  if (req.method !== "POST") {
    return res.status(405).json({ error: "Nur POST erlaubt" });
  }

  let username;
  try {
    username = await verifyUser(req);
  } catch (e) {
    return res.status(502).json({ error: "Anmeldung konnte nicht geprueft werden" });
  }
  if (!username) {
    return res.status(401).json({ error: "Nicht angemeldet" });
  }

  const { amount } = req.body || {};
  const roundedAmount = Math.round(Number(amount));
  if (!Number.isFinite(roundedAmount) || roundedAmount === 0) {
    return res.status(400).json({ error: "amount muss eine gueltige, von 0 verschiedene Zahl sein" });
  }

  const token = process.env.STREAMELEMENTS_JWT;
  const channelId = process.env.STREAMELEMENTS_CHANNEL_ID;

  if (!token || !channelId) {
    return res.status(500).json({ error: "Server nicht konfiguriert (Umgebungsvariablen fehlen)" });
  }

  try {
    const url = `https://api.streamelements.com/kappa/v2/points/${encodeURIComponent(channelId)}/${encodeURIComponent(username)}/${roundedAmount}`;
    const seRes = await fetch(url, {
      method: "PUT",
      headers: {
        "Authorization": `Bearer ${token}`,
        "Accept": "application/json"
      }
    });

    const rawText = await seRes.text();
    let data;
    try {
      data = JSON.parse(rawText);
    } catch (parseErr) {
      return res.status(502).json({ error: "Antwort war kein JSON", status: seRes.status });
    }

    if (!seRes.ok) {
      return res.status(seRes.status).json({ error: "StreamElements-Fehler", details: data });
    }

    return res.status(200).json(data);
  } catch (err) {
    return res.status(500).json({ error: "Fehler beim Aktualisieren der Punkte", message: err.message });
  }
}
