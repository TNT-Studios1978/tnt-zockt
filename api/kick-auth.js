// Vercel Serverless Function
// Tauscht den Kick-OAuth-Code gegen einen Access Token und holt die
// Nutzerdaten ab. Der Client Secret bleibt server-seitig geheim.

export default async function handler(req, res) {
  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Nur POST erlaubt' });
  }

  const { code, code_verifier, redirect_uri } = req.body || {};

  if (!code || !code_verifier || !redirect_uri) {
    return res.status(400).json({ error: 'code, code_verifier oder redirect_uri fehlt' });
  }

  const clientId = process.env.KICK_CLIENT_ID;
  const clientSecret = process.env.KICK_CLIENT_SECRET;

  if (!clientId || !clientSecret) {
    return res.status(500).json({ error: 'Server nicht konfiguriert (Umgebungsvariablen fehlen)' });
  }

  try {
    // Schritt 1: Code gegen Access Token tauschen
    const tokenRes = await fetch('https://id.kick.com/oauth/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'authorization_code',
        client_id: clientId,
        client_secret: clientSecret,
        redirect_uri,
        code_verifier,
        code
      })
    });
    const tokenData = await tokenRes.json();

    if (!tokenRes.ok) {
      return res.status(tokenRes.status).json({ error: 'Kick-Token-Fehler', details: tokenData });
    }

    // Schritt 2: Nutzerdaten abrufen
    const userRes = await fetch('https://api.kick.com/public/v1/users', {
      headers: { Authorization: `Bearer ${tokenData.access_token}` }
    });
    const userData = await userRes.json();

    if (!userRes.ok) {
      return res.status(userRes.status).json({ error: 'Kick-Nutzer-Fehler', details: userData });
    }

    const user = (userData.data && userData.data[0]) || null;
    if (!user) {
      return res.status(404).json({ error: 'Kick-Nutzer nicht gefunden' });
    }

    return res.status(200).json({
      username: user.name || user.username || user.slug,
      avatar: user.profile_picture || user.profile_pic || null,
      user_id: user.user_id || user.id
    });
  } catch (err) {
    return res.status(500).json({ error: 'Fehler bei Kick-Anmeldung', message: err.message });
  }
}
