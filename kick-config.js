/* ============================================================
   TNT-ZOCKT — Kick-Login Konfiguration
   Die Client ID ist öffentlich (wird im Browser verwendet) —
   das Client Secret bleibt server-seitig in Vercel und wird
   hier NICHT eingetragen.
   ============================================================ */
 
const KICK_CLIENT_ID = "01KX301T7E1KV1CVJ4J3YY24X3";
 
function base64URLEncode(buffer){
  let str = '';
  const bytes = new Uint8Array(buffer);
  for(let i=0; i<bytes.length; i++) str += String.fromCharCode(bytes[i]);
  return btoa(str).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
}
 
function generateRandomString(len){
  const arr = new Uint8Array(len);
  crypto.getRandomValues(arr);
  return base64URLEncode(arr.buffer).slice(0, len);
}
 
async function generateCodeChallenge(verifier){
  const data = new TextEncoder().encode(verifier);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return base64URLEncode(digest);
}
 
async function loginWithKick(){
  const verifier = generateRandomString(64);
  const state = generateRandomString(24);
  sessionStorage.setItem('kick_code_verifier', verifier);
  sessionStorage.setItem('kick_oauth_state', state);
  sessionStorage.setItem('kick_redirect_page', location.pathname);
 
  const challenge = await generateCodeChallenge(verifier);
  const redirectUri = window.location.origin + '/kick-callback.html';
 
  const authUrl = 'https://id.kick.com/oauth/authorize'
    + '?client_id=' + encodeURIComponent(KICK_CLIENT_ID)
    + '&redirect_uri=' + encodeURIComponent(redirectUri)
    + '&response_type=code'
    + '&scope=user:read'
    + '&code_challenge=' + challenge
    + '&code_challenge_method=S256'
    + '&state=' + state;
 
  window.location.href = authUrl;
}
 
function getKickSession(){
  try{
    const raw = localStorage.getItem('kick_session');
    return raw ? JSON.parse(raw) : null;
  }catch(e){ return null; }
}
 
function logoutKick(){
  localStorage.removeItem('kick_session');
  location.reload();
}