// Vercel Serverless Function
// Ruft das Twitch-Profilbild eines Nutzers ab (server-seitig, damit
// Client ID/Secret niemals im Browser-Code sichtbar sind).

let cachedAppToken = null;
let cachedAppTokenExpiry = 0;

async function getAppAccessToken() {
  const now = Date.now();
  if (cachedAppToken && now < cachedAppTokenExpiry) {
    return cachedAppToken;
  }

  const clientId = process.env.TWITCH_CLIENT_ID;
  const clientSecret = process.env.TWITCH_CLIENT_SECRET;

  const params = new URLSearchParams({
    client_id: clientId,
    client_secret: clientSecret,
    grant_type: "client_credentials"
  });

  const res = await fetch(`https://id.twitch.tv/oauth2/token?${params.toString()}`, {
    method: "POST"
  });
  const data = await res.json();

  if (!res.ok) {
    throw new Error("Twitch-Token-Fehler: " + JSON.stringify(data));
  }

  cachedAppToken = data.access_token;
  cachedAppTokenExpiry = now + (data.expires_in - 60) * 1000;
  return cachedAppToken;
}

export default async function handler(req, res) {
  const { login } = req.query;

  if (!login) {
    return res.status(400).json({ error: "login fehlt" });
  }

  const clientId = process.env.TWITCH_CLIENT_ID;
  if (!clientId || !process.env.TWITCH_CLIENT_SECRET) {
    return res.status(500).json({ error: "Server nicht konfiguriert (Umgebungsvariablen fehlen)" });
  }

  try {
    const token = await getAppAccessToken();

    const userRes = await fetch(`https://api.twitch.tv/helix/users?login=${encodeURIComponent(login)}`, {
      headers: {
        "Client-Id": clientId,
        "Authorization": `Bearer ${token}`
      }
    });
    const userData = await userRes.json();

    if (!userRes.ok) {
      return res.status(userRes.status).json({ error: "Twitch-API-Fehler", details: userData });
    }

    const user = userData.data && userData.data[0];
    if (!user) {
      return res.status(404).json({ error: "Nutzer nicht gefunden" });
    }

    return res.status(200).json({ avatar: user.profile_image_url });
  } catch (err) {
    return res.status(500).json({ error: "Fehler beim Abrufen des Profilbilds", message: err.message });
  }
}