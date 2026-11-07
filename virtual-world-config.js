// Testzugang zur TNT-Zockt Virtual World.
// Der Schnelltunnel bekommt nach einem Neustart eine neue Adresse: dann nur die URL hier anpassen.
window.VIRTUAL_WORLD = {
  url: 'https://curtis-concentration-journey-couple.trycloudflare.com/virtual',
  code: '3BZV23UY',
};

document.addEventListener('DOMContentLoaded', function () {
  var card = document.getElementById('virtual-world-card');
  var code = document.getElementById('virtual-world-code');
  if (card) card.href = window.VIRTUAL_WORLD.url;
  if (code) code.textContent = window.VIRTUAL_WORLD.code;
});
