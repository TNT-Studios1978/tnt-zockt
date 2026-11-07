// Testzugang zur TNT-Zockt Virtual World.
// Der Schnelltunnel bekommt nach einem Neustart eine neue Adresse: dann nur die URL hier anpassen.
window.VIRTUAL_WORLD = {
  url: 'https://curtis-concentration-journey-couple.trycloudflare.com/virtual',
  code: '3BZV23UY',
};

document.addEventListener('DOMContentLoaded', function () {
  var world = window.VIRTUAL_WORLD;
  var card = document.getElementById('virtual-world-card');
  var code = document.getElementById('virtual-world-code');
  var copy = document.getElementById('virtual-world-copy');
  var ticket = null;

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
        if (!kick || !kick.token) { ticket = null; updateLink(); return; }
        body.kickSession = kick.token;
      }
      var res = await fetch('/api/world-ticket', { method: 'POST', headers: headers, body: JSON.stringify(body) });
      var data = await res.json().catch(function () { return {}; });
      ticket = res.ok && data.ticket ? { value: data.ticket, exp: data.exp } : null;
    } catch (e) {
      ticket = null;
    }
    updateLink();
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
