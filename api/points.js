// Vercel Serverless Function
// Ruft StreamElements-Loyalty-Punkte für einen Twitch-Benutzernamen ab.
// Der StreamElements JWT-Token bleibt geheim auf dem Server (Umgebungsvariable),
// wird NIE an den Browser/Client gesendet.

export default async function handler(req, res) {
    const { username } = req.query;

  if (!username) {
        return res.status(400).json({ error: "username fehlt" });
  }

  const token = process.env.STREAMELEMENTS_JWT;
    const channelId = process.env.STREAMELEMENTS_CHANNEL_ID;

  if (!token || !channelId) {
        return res.status(500).json({ error: "Server nicht konfiguriert (Umgebungsvariablen fehlen)" });
  }

  try {
        const url = `https://api.streamelements.com/kappa/v2/points/${encodeURIComponent(channelId)}/${encodeURIComponent(username)}`;
        const seRes = await fetch(url, {
                headers: {
                          "Authorization": `Bearer ${token}`,
                          "Accept": "application/json"
                }
        });

      if (seRes.status === 404) {
              return res.status(200).json({ points: 0, pointsAlltime: 0, watchtime: 0, rank: null });
      }

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
        return res.status(500).json({ error: "Fehler beim Abrufen der Punkte", message: err.message });
  }
}
