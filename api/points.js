// Vercel Serverless Function
// Ruft Streamlabs-Loyalty-Punkte für einen Twitch-Benutzernamen ab.
// Der Streamlabs-Zugangstoken bleibt geheim auf dem Server (Umgebungsvariable),
// wird NIE an den Browser/Client gesendet.

export default async function handler(req, res) {
  const { username } = req.query;

  if (!username) {
    return res.status(400).json({ error: "username fehlt" });
  }

  const token = process.env.STREAMLABS_ACCESS_TOKEN;
  const channel = process.env.STREAMLABS_CHANNEL;

  if (!token || !channel) {
    return res.status(500).json({ error: "Server nicht konfiguriert (Umgebungsvariablen fehlen)" });
  }

  try {
    const url = `https://streamlabs.com/api/v2.0/points?username=${encodeURIComponent(username)}&channel=${encodeURIComponent(channel)}`;
    const streamlabsRes = await fetch(url, {
      headers: {
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json",
        "X-Requested-With": "XMLHttpRequest"
      }
    });

    const rawText = await streamlabsRes.text();
    let data;
    try {
      data = JSON.parse(rawText);
    } catch (parseErr) {
      return res.status(502).json({ error: "Antwort war kein JSON", raw: rawText.slice(0, 300), status: streamlabsRes.status });
    }

    if (!streamlabsRes.ok) {
      return res.status(streamlabsRes.status).json({ error: "Streamlabs-Fehler", details: data });
    }

    return res.status(200).json(data);
  } catch (err) {
    return res.status(500).json({ error: "Fehler beim Abrufen der Punkte", message: err.message });
  }
}
