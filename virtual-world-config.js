// Testzugang zur TNT-Zockt Virtual World.
// Läuft auf dem Oracle-Server (feste Adresse mit HTTPS). Bei einem Domainwechsel nur die URL hier anpassen.
window.VIRTUAL_WORLD = {
  url: 'https://152-67-91-74.sslip.io/virtual',
  code: '3BZV23UY',
};

document.addEventListener('DOMContentLoaded', function () {
  var world = window.VIRTUAL_WORLD;
  var card = document.getElementById('virtual-world-card');
  var code = document.getElementById('virtual-world-code');
  var copy = document.getElementById('virtual-world-copy');
  var ticket = null;

  /* Anmeldung aus der Welt: …/games.html?goto=world&login=twitch|kick. Nach dem Login (auch über den Umweg
     zu Twitch/Kick) geht es mit dem Ticket automatisch zurück in die Welt. Ziel ist immer die feste Welt-Adresse. */
  var GOTO_KEY = 'tnt_goto_world';
  var GOTO_MAX_MS = 10 * 60 * 1000;
  var params = new URLSearchParams(location.search);
  var wantedLogin = null;
  if (params.get('goto') === 'world') {
    try { sessionStorage.setItem(GOTO_KEY, String(Date.now())); } catch (e) {}
    var login = params.get('login');
    if (login === 'twitch' || login === 'kick') wantedLogin = login;
    history.replaceState(null, '', location.pathname);
  }
  function goingToWorld() {
    try {
      var since = Number(sessionStorage.getItem(GOTO_KEY) || 0);
      return since > 0 && Date.now() - since < GOTO_MAX_MS;
    } catch (e) { return false; }
  }
  function backToWorld() {
    if (!ticket || !goingToWorld() || !card) return false;
    try { sessionStorage.removeItem(GOTO_KEY); } catch (e) {}
    location.replace(card.href);
    return true;
  }
  function startWantedLogin() {
    if (!wantedLogin) return;
    var provider = wantedLogin;
    wantedLogin = null;
    if (provider === 'twitch' && typeof loginWithTwitch === 'function') loginWithTwitch();
    if (provider === 'kick' && typeof loginWithKick === 'function') loginWithKick();
  }

  // Der Code (und wenn möglich ein Welt-Ticket) reist im Teil nach # mit und wird dort automatisch übernommen.
  function updateLink() {
    if (!card) return;
    var hash = 'code=' + encodeURIComponent(world.code);
    if (ticket) hash += '&ticket=' + encodeURIComponent(ticket.value);
    card.href = world.url + '#' + hash;
  }

  /* Ticket mit Name und Bild aus dem Website-Login (Twitch über Supabase, Kick über das Sitzungszeichen). */
  async function loadTicket() {
    try {
      var headers = { 'Content-Type': 'application/json' };
      var body = {};
      var session = (typeof sb !== 'undefined') ? (await sb.auth.getSession()).data.session : null;
      if (session) headers.Authorization = 'Bearer ' + session.access_token;
      else {
        var kick = (typeof getKickSession === 'function') ? getKickSession() : null;
        if (!kick || !kick.token) {
          ticket = null;
          updateLink();
          // Aus der Welt mit Wunsch-Anbieter gekommen, aber noch nicht angemeldet: Anmeldung starten
          startWantedLogin();
          return;
        }
        body.kickSession = kick.token;
      }
      var res = await fetch('/api/world-ticket', { method: 'POST', headers: headers, body: JSON.stringify(body) });
      var data = await res.json().catch(function () { return {}; });
      ticket = res.ok && data.ticket ? { value: data.ticket, exp: data.exp } : null;
    } catch (e) {
      ticket = null;
    }
    updateLink();
    if (backToWorld()) return;
    // Noch nicht angemeldet, aber aus der Welt mit Wunsch-Anbieter gekommen: Anmeldung starten
    if (!ticket) startWantedLogin();
  }

  updateLink();
  loadTicket();
  if (typeof sb !== 'undefined') sb.auth.onAuthStateChange(function () { loadTicket(); });
  if (card) card.addEventListener('click', function () {
    // Ein abgelaufenes Ticket beim nächsten Mal erneuern.
    if (ticket && ticket.exp * 1000 < Date.now() + 60000) loadTicket();
  });

  if (!copy) return;
  function done(ok) {
    var label = copy.textContent;
    copy.textContent = ok ? '✓' : world.code;
    setTimeout(function () { copy.textContent = label; }, 1500);
  }
  function copyCode(event) {
    // Kopieren statt den Link der Kachel zu öffnen.
    event.preventDefault();
    event.stopPropagation();
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(world.code).then(function () { done(true); }, function () { done(false); });
    } else {
      done(false);
    }
  }
  copy.addEventListener('click', copyCode);
  copy.addEventListener('keydown', function (event) {
    if (event.key === 'Enter' || event.key === ' ') copyCode(event);
  });
});
