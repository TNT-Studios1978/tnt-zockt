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
  // Der Code reist im Teil nach # mit und wird in der Welt automatisch eingetragen.
  if (card) card.href = world.url + '#code=' + encodeURIComponent(world.code);
  if (code) code.textContent = world.code;
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
