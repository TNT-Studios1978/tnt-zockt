// Vercel Serverless Function
// Schreibt eine Punkte-Aenderung (Gewinn oder Verlust) auf StreamElements.
// Der StreamElements JWT-Token bleibt geheim auf dem Server (Umgebungsvariable).
// amount ist ein Delta: positiv = addieren (Gewinn), negativ = abziehen (Verlust/Einsatz).

export default async function handler(req, res) {
    if (req.method !== "POST") {
          return res.status(405).json({ error: "Nur POST erlaubt" });
    }

  const { username, amount } = req.body || {};

  if (!username || typeof amount === "undefined") {
        return res.status(400).json({ error: "username oder amount fehlt" });
  }

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
                return res.status(502).json({ error: "Antwort war kein JSON", raw: rawText.slice(0, 300), status: seRes.status });
        }

      if (!seRes.ok) {
              return res.status(seRes.status).json({ error: "StreamElements-Fehler", details: data });
      }

      return res.status(200).json(data);
  } catch (err) {
        return res.status(500).json({ error: "Fehler beim Aktualisieren der Punkte", message: err.message });
  }
}
