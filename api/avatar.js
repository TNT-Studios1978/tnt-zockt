// Vercel Serverless Function
// Ruft das Profilbild eines Streamers ab (Twitch, YouTube, Kick).
// Zugangsschlüssel bleiben server-seitig geheim.

let cachedTwitchToken = null;
let cachedTwitchTokenExpiry = 0;

async function getTwitchAppToken() {
  const now = Date.now();
  if (cachedTwitchToken && now < cachedTwitchTokenExpiry) {
    return cachedTwitchToken;
  }
  const params = new URLSearchParams({
    client_id: process.env.TWITCH_CLIENT_ID,
    client_secret: process.env.TWITCH_CLIENT_SECRET,
    grant_type: "client_credentials"
  });
  const res = await fetch(`https://id.twitch.tv/oauth2/token?${params.toString()}`, { method: "POST" });
  const data = await res.json();
  if (!res.ok) throw new Error("Twitch-Token-Fehler: " + JSON.stringify(data));
  cachedTwitchToken = data.access_token;
  cachedTwitchTokenExpiry = now + (data.expires_in - 60) * 1000;
  return cachedTwitchToken;
}

async function getTwitchAvatar(login) {
  const clientId = process.env.TWITCH_CLIENT_ID;
  if (!clientId || !process.env.TWITCH_CLIENT_SECRET) {
    throw { status: 500, message: "Twitch-Umgebungsvariablen fehlen" };
  }
  const token = await getTwitchAppToken();
  const userRes = await fetch(`https://api.twitch.tv/helix/users?login=${encodeURIComponent(login)}`, {
    headers: { "Client-Id": clientId, "Authorization": `Bearer ${token}` }
  });
  const userData = await userRes.json();
  if (!userRes.ok) throw { status: userRes.status, message: "Twitch-API-Fehler", details: userData };
  const user = userData.data && userData.data[0];
  if (!user) throw { status: 404, message: "Twitch-Nutzer nicht gefunden" };
  return user.profile_image_url;
}

async function getYoutubeAvatar(handle) {
  const apiKey = process.env.YOUTUBE_API_KEY;
  if (!apiKey) throw { status: 500, message: "YouTube-Umgebungsvariable fehlt" };

  const cleanHandle = handle.replace(/^@/, "");
  const url = `https://www.googleapis.com/youtube/v3/channels?part=snippet&forHandle=${encodeURIComponent(cleanHandle)}&key=${apiKey}`;
  const ytRes = await fetch(url);
  const ytData = await ytRes.json();
  if (!ytRes.ok) throw { status: ytRes.status, message: "YouTube-API-Fehler", details: ytData };
  const channel = ytData.items && ytData.items[0];
  if (!channel) throw { status: 404, message: "YouTube-Kanal nicht gefunden" };
  return channel.snippet.thumbnails.default.url;
}

async function getKickAvatar(slug) {
  // Inoffizielle öffentliche Kick-API - kann gelegentlich durch
  // Bot-Schutz blockiert werden (kein garantierter Zugriff).
  const res = await fetch(`https://kick.com/api/v2/channels/${encodeURIComponent(slug)}`, {
    headers: { "User-Agent": "Mozilla/5.0 (compatible; TNT-Zockt/1.0)" }
  });
  if (!res.ok) throw { status: res.status, message: "Kick-API nicht erreichbar oder blockiert" };
  const data = await res.json();
  const pic = data && data.user && data.user.profile_pic;
  if (!pic) throw { status: 404, message: "Kick-Profilbild nicht gefunden" };
  return pic;
}

export default async function handler(req, res) {
  const { login, platform } = req.query;

  if (!login || !platform) {
    return res.status(400).json({ error: "login oder platform fehlt" });
  }

  try {
    let avatar;
    if (platform === "twitch") {
      avatar = await getTwitchAvatar(login);
    } else if (platform === "youtube") {
      avatar = await getYoutubeAvatar(login);
    } else if (platform === "kick") {
      avatar = await getKickAvatar(login);
    } else {
      return res.status(400).json({ error: "Unbekannte Plattform" });
    }
    return res.status(200).json({ avatar });
  } catch (err) {
    const status = err.status || 500;
    return res.status(status).json({ error: err.message || "Unbekannter Fehler", details: err.details });
  }
}