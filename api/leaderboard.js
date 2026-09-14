// Vercel Serverless Function
// Ruft die komplette StreamElements-Loyalty-Bestenliste ab (alle Zuschauer mit Punkten),
// nicht nur einen einzelnen Nutzer. Der JWT-Token bleibt geheim auf dem Server.

export default async function handler(req, res) {
const token = process.env.STREAMELEMENTS_JWT;
const channelId = process.env.STREAMELEMENTS_CHANNEL_ID;

if (!token || !channelId) {
return res.status(500).json({ error: "Server nicht konfiguriert (Umgebungsvariablen fehlen)" });
}

const limit = req.query.limit || 100;

try {
const url = `https://api.streamelements.com/kappa/v2/points/${encodeURIComponent(channelId)}/top?limit=${encodeURIComponent(limit)}`;
const seRes = await fetch(url, {
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
return res.status(500).json({ error: "Fehler beim Abrufen der Bestenliste", message: err.message });
}
}
