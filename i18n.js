/* ============================================================
   TNT-ZOCKT — Sprachsystem (DE / FR / EN)
   Übersetzt alle festen Seiten-Texte. Admin-Inhalte (Casino-/
   Streamer-Einträge) bleiben einsprachig, wie im Admin-Panel
   eingegeben.
   ============================================================ */

const TNT_TRANSLATIONS = {

  /* ---------- Navigation (gemeinsam) ---------- */
  'nav.angebote':      { de: 'Angebote',        fr: 'Offres',            en: 'Offers' },
  'nav.angebote.de':   { de: 'Angebote DE',      fr: 'Offres DE',         en: 'Offers DE' },
  'nav.angebote.ch':   { de: 'Angebote CH',      fr: 'Offres CH',         en: 'Offers CH' },
  'nav.streamer':       { de: 'Streamer',        fr: 'Streamers',         en: 'Streamers' },
  'nav.spiele':          { de: 'Spiele',           fr: 'Jeux',              en: 'Games' },
  'nav.shop':             { de: 'Shop',              fr: 'Boutique',          en: 'Shop' },
  'nav.tournierbaum': { de: 'Tournierbaum', fr: 'Tournoi',           en: 'Bracket' },
  'nav.verantwortung': { de: 'Verantwortung', fr: 'Jeu responsable',  en: 'Responsibility' },
  'nav.topbonus':       { de: 'Top Bonus sichern', fr: 'Profiter du bonus', en: 'Claim top bonus' },
  'nav.login.twitch':  { de: 'Twitch',          fr: 'Twitch',            en: 'Twitch' },
  'nav.login.kick':     { de: 'Kick',              fr: 'Kick',              en: 'Kick' },
  'nav.logout':         { de: 'Logout',            fr: 'Déconnexion',      en: 'Logout' },
  'nav.points.soon':   { de: '⭐ Punkte: bald verfügbar', fr: '⭐ Points : bientôt disponible', en: '⭐ Points: coming soon' },
  'nav.points.loading':{ de: 'Lädt…',              fr: 'Chargement…',       en: 'Loading…' },
  'bottom.angebote':   { de: 'Angebote',        fr: 'Offres',            en: 'Offers' },
  'bottom.streamer':    { de: 'Streamer',        fr: 'Streamers',         en: 'Streamers' },
  'bottom.spiele':       { de: 'Spiele',           fr: 'Jeux',              en: 'Games' },
  'bottom.shop':          { de: 'Shop',              fr: 'Boutique',          en: 'Shop' },
  'bottom.schutz':        { de: 'Schutz',            fr: 'Protection',        en: 'Safety' },
  'bottom.turnier':       { de: 'Turnier',           fr: 'Tournoi',           en: 'Tournament' },

  /* ---------- Startseite: Hero ---------- */
  'hero.eyebrow':      { de: 'Live · Casino Bonusse & Streams', fr: 'En direct · Bonus casino & streams', en: 'Live · Casino bonuses & streams' },
  'hero.title.pre':    { de: 'Zünde dein', fr: 'Enflamme ta', en: 'Ignite your' },
  'hero.title.flame':  { de: 'Glück', fr: 'chance', en: 'luck' },
  'hero.title.post':   { de: 'mit TNT-Zockt', fr: 'avec TNT-Zockt', en: 'with TNT-Zockt' },
  'hero.desc':         { de: 'Geprüfte Online-Casinos, exklusive Boni und die besten Twitch- & Kick-Streamer an einem Ort. Kurze Zündschnur, große Wirkung.', fr: 'Casinos en ligne vérifiés, bonus exclusifs et les meilleurs streamers Twitch & Kick au même endroit. Mèche courte, grand effet.', en: 'Verified online casinos, exclusive bonuses, and the best Twitch & Kick streamers in one place. Short fuse, big impact.' },
  'hero.btn.casinos':  { de: 'Top Casinos ansehen', fr: 'Voir les meilleurs casinos', en: 'View top casinos' },
  'hero.btn.streams':  { de: 'Live-Streams', fr: 'Streams en direct', en: 'Live streams' },
  'hero.stat.casinos': { de: 'Casino-Angebote', fr: 'Offres casino', en: 'Casino offers' },
  'hero.stat.social':  { de: 'Social-Kanäle', fr: 'Canaux sociaux', en: 'Social channels' },
  'hero.stat.responsible': { de: 'Verantwortung', fr: 'Responsabilité', en: 'Responsibility' },

  /* ---------- Startseite: Casinos ---------- */
  'casinos.tag':   { de: 'Top Auswahl', fr: 'Meilleure sélection', en: 'Top selection' },
  'casinos.title': { de: 'Unsere Casino-Empfehlungen (Deutschland)', fr: 'Nos recommandations de casinos (Allemagne)', en: 'Our casino recommendations (Germany)' },
  'casinos.desc':  { de: 'Geprüfte Casino-Partner mit exklusiven Boni für unsere Community. 🇨🇭 Schweizer Zuschauer finden passende Angebote unter "Angebote CH".', fr: 'Partenaires de casino vérifiés avec des bonus exclusifs pour notre communauté. 🇨🇭 Les spectateurs suisses trouveront des offres adaptées sous "Offres CH".', en: 'Verified casino partners with exclusive bonuses for our community. 🇨🇭 Swiss viewers can find suitable offers under "Offers CH".' },
  'casinos.play':  { de: 'Jetzt spielen →', fr: 'Jouer maintenant →', en: 'Play now →' },
  'casinos.fine':  { de: '18+ · AGB gelten · verantwortungsvoll spielen', fr: '18+ · CGU applicables · jouez de manière responsable', en: '18+ · T&Cs apply · play responsibly' },

  /* ---------- Startseite: Streamer ---------- */
  'streams.tag':   { de: 'Community', fr: 'Communauté', en: 'Community' },
  'streams.title': { de: 'Unsere Streamer', fr: 'Nos streamers', en: 'Our streamers' },
  'streams.desc':  { de: 'Unser offizieller Kanal und befreundete Streamer auf Twitch & Kick.', fr: 'Notre chaîne officielle et des streamers partenaires sur Twitch & Kick.', en: 'Our official channel and partner streamers on Twitch & Kick.' },
  'streams.watch': { de: 'Kanal ansehen', fr: 'Voir la chaîne', en: 'View channel' },

  /* ---------- Startseite: Verantwortung ---------- */
  'responsible.title': { de: 'Verantwortungsvolles Spielen', fr: 'Jeu responsable', en: 'Responsible gaming' },
  'responsible.desc':  {
    de: 'Glücksspiel ist Unterhaltung, kein Weg zum Geldverdienen und kann süchtig machen. Spiele nur mit Geld, dessen Verlust du dir leisten kannst. Hilfe und Beratung: <a href="tel:080013727700">BZgA-Hotline 0800 1 37 27 00</a> (kostenlos, anonym) oder <a href="https://www.check-dein-spiel.de" target="_blank" rel="noopener">check-dein-spiel.de</a>. Zugang ab 18 Jahren.',
    fr: 'Les jeux d\'argent sont un divertissement, pas un moyen de gagner de l\'argent, et peuvent créer une dépendance. Ne jouez qu\'avec de l\'argent dont vous pouvez vous permettre de perdre. Aide : <a href="tel:080013727700">ligne d\'assistance BZgA 0800 1 37 27 00</a> (gratuit, anonyme) ou <a href="https://www.check-dein-spiel.de" target="_blank" rel="noopener">check-dein-spiel.de</a>. Accès à partir de 18 ans.',
    en: 'Gambling is entertainment, not a way to make money, and can be addictive. Only play with money you can afford to lose. Help & advice: <a href="tel:080013727700">BZgA hotline 0800 1 37 27 00</a> (free, anonymous) or <a href="https://www.check-dein-spiel.de" target="_blank" rel="noopener">check-dein-spiel.de</a>. Access from age 18.'
  },
  'casinos.details': { de: 'Details & Infos ▾', fr: 'Détails & infos ▾', en: 'Details & info ▾' },

  /* ---------- Footer ---------- */
  'footer.legal': {
    de: '© 2026 TNT-Zockt | Ein Projekt von TNT-Studios.ch. Einige Links sind Affiliate-Links — beim Abschluss können wir eine Provision erhalten, das hat keinen Einfluss auf deine Kosten. Alle Angebote richten sich ausschließlich an volljährige Personen (18+). Glücksspiel kann süchtig machen — spiele verantwortungsvoll.',
    fr: '© 2026 TNT-Zockt | Un projet de TNT-Studios.ch. Certains liens sont des liens d\'affiliation — nous pouvons recevoir une commission sans frais supplémentaires pour vous. Toutes les offres s\'adressent exclusivement aux personnes majeures (18+). Les jeux d\'argent peuvent créer une dépendance — jouez de manière responsable.',
    en: '© 2026 TNT-Zockt | A project by TNT-Studios.ch. Some links are affiliate links — we may receive a commission at no extra cost to you. All offers are intended exclusively for adults (18+). Gambling can be addictive — play responsibly.'
  },
  'footer.admin': { de: '🔧 Admin-Bereich (Inhalte bearbeiten)', fr: '🔧 Espace admin (modifier le contenu)', en: '🔧 Admin area (edit content)' },

  /* ---------- Age Gate ---------- */
  'gate.title':  { de: 'Altersverifikation', fr: 'Vérification de l\'âge', en: 'Age verification' },
  'gate.desc':   { de: 'Diese Seite enthält Inhalte zu Glücksspiel mit Echtgeld-Einsatz. Der Zutritt ist nur Personen ab 18 Jahren gestattet.', fr: 'Ce site contient des contenus liés aux jeux d\'argent réel. L\'accès est réservé aux personnes de 18 ans et plus.', en: 'This site contains content related to real-money gambling. Access is only permitted for persons aged 18 and over.' },
  'gate.confirm':{ de: 'Ich bin 18 Jahre oder älter', fr: 'J\'ai 18 ans ou plus', en: 'I am 18 years or older' },
  'gate.leave':  { de: 'Seite verlassen', fr: 'Quitter le site', en: 'Leave site' },
  'gate.legal':  { de: 'Glücksspiel kann süchtig machen. Spielerschutz: BZgA-Hotline 0800 1 37 27 00.', fr: 'Les jeux d\'argent peuvent créer une dépendance. Protection des joueurs : ligne BZgA 0800 1 37 27 00.', en: 'Gambling can be addictive. Player protection: BZgA hotline 0800 1 37 27 00.' },

  /* ---------- Shop ---------- */
  'shop.hero.eyebrow': { de: 'Merch · Bald verfügbar', fr: 'Merch · Bientôt disponible', en: 'Merch · Coming soon' },
  'shop.hero.desc': { de: 'Shirts, Caps und mehr im TNT-Zockt Look. Der Shop ist gerade im Aufbau — die ersten Drops kommen bald.', fr: 'T-shirts, casquettes et plus encore dans le style TNT-Zockt. La boutique est en cours de construction — les premiers drops arrivent bientôt.', en: 'Shirts, caps, and more in TNT-Zockt style. The shop is currently under construction — the first drops are coming soon.' },
  'shop.gate.title': { de: '🔒 Nur für Mitglieder', fr: '🔒 Réservé aux membres', en: '🔒 Members only' },
  'shop.gate.desc':  { de: 'Logg dich mit Twitch oder Kick ein, um den Shop zu sehen.', fr: 'Connecte-toi avec Twitch ou Kick pour voir la boutique.', en: 'Log in with Twitch or Kick to view the shop.' },
  'shop.gate.twitch':{ de: 'Mit Twitch anmelden', fr: 'Se connecter avec Twitch', en: 'Sign in with Twitch' },
  'shop.gate.kick':  { de: 'Mit Kick anmelden', fr: 'Se connecter avec Kick', en: 'Sign in with Kick' },
  'shop.notify.title':{ de: 'Als Erste(r) informiert werden', fr: 'Sois informé(e) en premier', en: 'Be the first to know' },
  'shop.notify.desc':{ de: 'Trag dich ein und erfahre sofort, wenn der TNT-Zockt Merch-Drop live geht.', fr: 'Inscris-toi et sois averti(e) dès que le drop de merch TNT-Zockt est en ligne.', en: 'Sign up and get notified the moment the TNT-Zockt merch drop goes live.' },
  'shop.notify.placeholder': { de: 'Deine E-Mail-Adresse', fr: 'Ton adresse e-mail', en: 'Your email address' },
  'shop.notify.btn': { de: 'Benachrichtigen', fr: 'M\'avertir', en: 'Notify me' },
  'shop.badge.soon': { de: 'Bald da', fr: 'Bientôt disponible', en: 'Coming soon' },
  'shop.soon.tag': { de: 'Bald verfügbar', fr: 'Bientôt disponible', en: 'Coming soon' },
  'shop.product.image': { de: '[Produktbild]', fr: '[Image du produit]', en: '[Product image]' },

  /* FAQ Shop */
  'faq.shop.1.q': { de: 'Wann kommt echter Merch?', fr: 'Quand arrivera le vrai merch ?', en: 'When will real merch arrive?' },
  'faq.shop.1.a': { de: 'Der Shop ist aktuell im Aufbau — sobald echte Produkte verfügbar sind, ersetzen wir die Platzhalter. Trag dich gerne für Updates ein!', fr: 'La boutique est en cours de construction — dès que de vrais produits seront disponibles, nous remplacerons les espaces réservés. N\'hésite pas à t\'inscrire pour les mises à jour !', en: 'The shop is currently under construction — once real products are available, we\'ll replace the placeholders. Feel free to sign up for updates!' },
  'faq.shop.2.q': { de: 'Warum muss ich mich einloggen?', fr: 'Pourquoi dois-je me connecter ?', en: 'Why do I need to log in?' },
  'faq.shop.2.a': { de: 'Der Shop ist aktuell nur für eingeloggte Mitglieder sichtbar (Twitch oder Kick).', fr: 'La boutique n\'est actuellement visible que pour les membres connectés (Twitch ou Kick).', en: 'The shop is currently only visible to logged-in members (Twitch or Kick).' },
  'faq.shop.3.q': { de: 'Was sind TNT-Chips?', fr: 'Que sont les TNT-Chips ?', en: 'What are TNT-Chips?' },
  'faq.shop.3.a': { de: 'Eine rein virtuelle Spielwährung für unsere TNT-Spiele-Seite — kein Echtgeld. Jeder neue Spieler startet mit 1\'000 Gratis-Chips.', fr: 'Une monnaie de jeu purement virtuelle pour notre page TNT-Jeux — pas d\'argent réel. Chaque nouveau joueur commence avec 1 000 jetons gratuits.', en: 'A purely virtual game currency for our TNT-Games page — no real money. Every new player starts with 1,000 free chips.' },
  'faq.shop.4.q': { de: 'Ich hab ein Problem/Frage', fr: 'J\'ai un problème/une question', en: 'I have a problem/question' },
  'faq.shop.4.a': { de: 'Am schnellsten erreichst du uns über unsere Social-Media-Kanäle (Twitch, YouTube, Facebook).', fr: 'Le plus rapide est de nous contacter via nos réseaux sociaux (Twitch, YouTube, Facebook).', en: 'The fastest way to reach us is via our social media channels (Twitch, YouTube, Facebook).' },

  /* ---------- Tournierbaum ---------- */
  'bracket.eyebrow': { de: 'Community · Live', fr: 'Communauté · En direct', en: 'Community · Live' },
  'bracket.desc': { de: 'Der aktuelle Turnierbaum unserer Community-Events — live eingebettet, kein separater Tab nötig.', fr: 'Le tableau de tournoi actuel de nos événements communautaires — intégré en direct, pas besoin d\'onglet séparé.', en: 'The current bracket for our community events — embedded live, no separate tab needed.' },
  'bracket.fallback': { de: 'Wird der Turnierbaum nicht angezeigt? <a href="https://tnt-studios1978.github.io/Tournamentbaum/" target="_blank" rel="noopener">Direkt öffnen →</a>', fr: 'Le tableau ne s\'affiche pas ? <a href="https://tnt-studios1978.github.io/Tournamentbaum/" target="_blank" rel="noopener">Ouvrir directement →</a>', en: 'Bracket not showing? <a href="https://tnt-studios1978.github.io/Tournamentbaum/" target="_blank" rel="noopener">Open directly →</a>' },

  /* FAQ Tournierbaum */
  'faq.bracket.1.q': { de: 'Wie nehme ich an einem Turnier teil?', fr: 'Comment participer à un tournoi ?', en: 'How do I join a tournament?' },
  'faq.bracket.1.a': { de: 'Ankündigungen zu Turnieren findest du auf unseren Social-Media-Kanälen und im Stream-Chat — dort erfährst du, wie du dich anmeldest.', fr: 'Les annonces de tournois se trouvent sur nos réseaux sociaux et dans le chat du stream — tu y trouveras comment t\'inscrire.', en: 'Tournament announcements are posted on our social media channels and in the stream chat — that\'s where you\'ll find out how to sign up.' },
  'faq.bracket.2.q': { de: 'Der Turnierbaum lädt nicht richtig', fr: 'Le tableau ne se charge pas correctement', en: 'The bracket isn\'t loading properly' },
  'faq.bracket.2.a': { de: 'Versuch die Seite neu zu laden. Falls es weiterhin nicht klappt, kannst du den Turnierbaum auch direkt über den Link unter der Einbettung öffnen.', fr: 'Essaie de recharger la page. Si le problème persiste, tu peux aussi ouvrir le tableau directement via le lien sous l\'intégration.', en: 'Try reloading the page. If it still doesn\'t work, you can also open the bracket directly via the link below the embed.' },
  'faq.bracket.3.q': { de: 'Wo finde ich die Ergebnisse?', fr: 'Où puis-je trouver les résultats ?', en: 'Where can I find the results?' },
  'faq.bracket.3.a': { de: 'Der Turnierbaum aktualisiert sich live während des Events — die Ergebnisse siehst du direkt hier auf dieser Seite.', fr: 'Le tableau se met à jour en direct pendant l\'événement — tu peux voir les résultats directement sur cette page.', en: 'The bracket updates live during the event — you can see the results directly on this page.' },

  /* ---------- Angebote Schweiz ---------- */
  'ch.eyebrow': { de: '🇨🇭 Für Schweizer Zuschauer', fr: '🇨🇭 Pour les spectateurs suisses', en: '🇨🇭 For Swiss viewers' },
  'ch.desc': { de: 'Casino-Partner, die speziell für Zuschauer aus der Schweiz geeignet sind.', fr: 'Partenaires de casino spécialement adaptés aux spectateurs suisses.', en: 'Casino partners specifically suited for viewers from Switzerland.' },

  /* FAQ Angebote Schweiz */
  'faq.ch.1.q': { de: 'Sind diese Angebote für die Schweiz legal?', fr: 'Ces offres sont-elles légales en Suisse ?', en: 'Are these offers legal in Switzerland?' },
  'faq.ch.1.a': { de: 'Wir listen nur Casino-Partner, die wir selbst geprüft haben. Bitte informiere dich vor der Anmeldung trotzdem immer selbst über die aktuelle Rechtslage in der Schweiz.', fr: 'Nous ne listons que des partenaires de casino que nous avons vérifiés nous-mêmes. Renseigne-toi néanmoins toujours toi-même sur la situation légale actuelle en Suisse avant de t\'inscrire.', en: 'We only list casino partners we\'ve personally vetted. Still, always check the current legal situation in Switzerland yourself before signing up.' },
  'faq.ch.2.q': { de: 'Warum eine eigene Seite für die Schweiz?', fr: 'Pourquoi une page dédiée à la Suisse ?', en: 'Why a dedicated page for Switzerland?' },
  'faq.ch.2.a': { de: 'Die Rechtslage und die passenden Anbieter unterscheiden sich zwischen Deutschland und der Schweiz — deshalb pflegen wir zwei getrennte Listen.', fr: 'La situation légale et les prestataires adaptés diffèrent entre l\'Allemagne et la Suisse — c\'est pourquoi nous tenons deux listes séparées.', en: 'The legal situation and suitable providers differ between Germany and Switzerland — that\'s why we maintain two separate lists.' },
  'faq.ch.3.q': { de: 'Ich hab ein Problem/Frage', fr: 'J\'ai un problème/une question', en: 'I have a problem/question' },
  'faq.ch.3.a': { de: 'Am schnellsten erreichst du uns über unsere Social-Media-Kanäle (Twitch, YouTube, Facebook).', fr: 'Le plus rapide est de nous contacter via nos réseaux sociaux (Twitch, YouTube, Facebook).', en: 'The fastest way to reach us is via our social media channels (Twitch, YouTube, Facebook).' },

  /* FAQ Spiele */
  'faq.games.1.q': { de: 'Wie bekomme ich TNT-Chips?', fr: 'Comment obtenir des TNT-Chips ?', en: 'How do I get TNT-Chips?' },
  'faq.games.1.a': { de: 'Jeder neue Spieler startet mit 1\'000 Gratis-Chips, sobald du dich mit Twitch oder Kick einloggst. Weitere Chips gibt es aktuell nur durchs Gewinnen in den Spielen.', fr: 'Chaque nouveau joueur commence avec 1 000 jetons gratuits dès que tu te connectes avec Twitch ou Kick. D\'autres jetons ne s\'obtiennent actuellement qu\'en gagnant dans les jeux.', en: 'Every new player starts with 1,000 free chips as soon as you log in with Twitch or Kick. Additional chips are currently only earned by winning in the games.' },
  'faq.games.2.q': { de: 'Ist das echtes Geld?', fr: 'Est-ce de l\'argent réel ?', en: 'Is this real money?' },
  'faq.games.2.a': { de: 'Nein! TNT-Chips sind eine rein virtuelle Spielwährung ohne Geldwert. Es findet an keiner Stelle Echtgeld-Glücksspiel statt.', fr: 'Non ! Les TNT-Chips sont une monnaie de jeu purement virtuelle sans valeur monétaire. Aucun jeu d\'argent réel n\'a lieu ici.', en: 'No! TNT-Chips are a purely virtual game currency with no monetary value. No real-money gambling takes place here.' },
  'faq.games.3.q': { de: 'Wie funktioniert Minen?', fr: 'Comment fonctionne Mines ?', en: 'How does Mines work?' },
  'faq.games.3.a': { de: 'Du wählst einen Einsatz und die Anzahl Minen. Dann deckst du Felder auf — jedes sichere Feld erhöht deinen Multiplikator. Du kannst jederzeit auszahlen, bevor du eine Mine triffst!', fr: 'Tu choisis une mise et le nombre de mines. Ensuite, tu révèles des cases — chaque case sûre augmente ton multiplicateur. Tu peux encaisser à tout moment avant de toucher une mine !', en: 'You choose a bet and the number of mines. Then you reveal tiles — each safe tile increases your multiplier. You can cash out anytime before hitting a mine!' },
  'faq.games.4.q': { de: 'Wie funktioniert Münzwurf?', fr: 'Comment fonctionne Pile ou face ?', en: 'How does Coinflip work?' },
  'faq.games.4.a': { de: 'Du setzt auf Kopf oder Zahl und wirfst die Münze. Bei richtiger Wahl gewinnst du das 1.94-fache deines Einsatzes.', fr: 'Tu paries sur pile ou face et tu lances la pièce. Si ton choix est correct, tu gagnes 1,94 fois ta mise.', en: 'You bet on heads or tails and flip the coin. If your choice is correct, you win 1.94 times your bet.' },
  'faq.games.5.q': { de: 'Wie funktioniert Absturz?', fr: 'Comment fonctionne Crash ?', en: 'How does Crash work?' },
  'faq.games.5.a': { de: 'Der Multiplikator steigt immer weiter — du kannst jederzeit auszahlen. Wartest du zu lange, stürzt die Rakete ab und du verlierst deinen Einsatz.', fr: 'Le multiplicateur ne cesse d\'augmenter — tu peux encaisser à tout moment. Si tu attends trop longtemps, la fusée s\'écrase et tu perds ta mise.', en: 'The multiplier keeps rising — you can cash out anytime. If you wait too long, the rocket crashes and you lose your bet.' },
  'faq.games.6.q': { de: 'Wie funktioniert Plinko?', fr: 'Comment fonctionne Plinko ?', en: 'How does Plinko work?' },
  'faq.games.6.a': { de: 'Du lässt einen Ball durch ein Nagel-Feld fallen. Je nachdem, wo er landet, gewinnst du unterschiedliche Multiplikatoren — höheres Risiko bedeutet größere mögliche Gewinne, aber auch größere Verluste.', fr: 'Tu laisses tomber une bille à travers un champ de clous. Selon l\'endroit où elle atterrit, tu gagnes différents multiplicateurs — un risque plus élevé signifie des gains potentiels plus importants, mais aussi des pertes plus importantes.', en: 'You drop a ball through a field of pegs. Depending on where it lands, you win different multipliers — higher risk means bigger potential wins, but also bigger losses.' },
  'faq.games.7.q': { de: 'Meine Chips sind alle weg, was jetzt?', fr: 'Mes jetons sont tous partis, que faire ?', en: 'My chips are all gone, what now?' },
  'faq.games.7.a': { de: 'Kein Problem — die Chips sind rein virtuell. Kontaktiere uns über unsere Social-Media-Kanäle, falls du einen Neustart brauchst.', fr: 'Pas de problème — les jetons sont purement virtuels. Contacte-nous via nos réseaux sociaux si tu as besoin d\'un redémarrage.', en: 'No problem — the chips are purely virtual. Contact us via our social media channels if you need a reset.' },
  'shop.footer.legal': {
    de: '© 2026 TNT-Zockt | Ein Projekt von TNT-Studios.ch. Der Shop-Bereich befindet sich im Aufbau, alle gezeigten Produkte, Preise und Bilder sind Platzhalter.',
    fr: '© 2026 TNT-Zockt | Un projet de TNT-Studios.ch. La boutique est en cours de construction, tous les produits, prix et images affichés sont des espaces réservés.',
    en: '© 2026 TNT-Zockt | A project by TNT-Studios.ch. The shop is under construction — all products, prices, and images shown are placeholders.'
  },

  /* ---------- Spiele: Hero & Wallet ---------- */
  'games.gate.title': { de: '🔒 Nur für Mitglieder', fr: '🔒 Réservé aux membres', en: '🔒 Members only' },
  'games.gate.desc':  { de: 'Logg dich mit Twitch oder Kick ein, um mit deinen TNT-Chips zu spielen. Jeder neue Spieler startet mit 1\'000 Gratis-Chips.', fr: 'Connecte-toi avec Twitch ou Kick pour jouer avec tes TNT-Chips. Chaque nouveau joueur commence avec 1 000 jetons gratuits.', en: 'Log in with Twitch or Kick to play with your TNT-Chips. Every new player starts with 1,000 free chips.' },
  'games.eyebrow':    { de: 'TNT Originals', fr: 'TNT Originals', en: 'TNT Originals' },
  'games.title.pre':  { de: 'TNT', fr: 'TNT', en: 'TNT' },
  'games.title.post': { de: 'Spiele', fr: 'Jeux', en: 'Games' },
  'games.desc':       { de: 'Spiel mit deinen TNT-Chips um virtuelle Gewinne — nur zum Spaß, kein Echtgeld.', fr: 'Joue avec tes TNT-Chips pour des gains virtuels — juste pour le plaisir, sans argent réel.', en: 'Play with your TNT-Chips for virtual winnings — just for fun, no real money.' },
  'games.disclaimer': { de: '🛡️ Es wird ausschließlich mit virtuellen TNT-Chips gespielt, nie mit echtem Geld.', fr: '🛡️ On ne joue qu\'avec des TNT-Chips virtuels, jamais avec de l\'argent réel.', en: '🛡️ Play is exclusively with virtual TNT-Chips, never real money.' },
  'games.chips.label':{ de: 'TNT-Chips', fr: 'TNT-Chips', en: 'TNT-Chips' },
  'games.back':        { de: '← Zurück zur Übersicht', fr: '← Retour à l\'aperçu', en: '← Back to overview' },
  'games.original':    { de: 'Original', fr: 'Original', en: 'Original' },
  'games.play':         { de: 'Jetzt spielen →', fr: 'Jouer maintenant →', en: 'Play now →' },

  /* Game names */
  'game.mines':      { de: 'Minen', fr: 'Mines', en: 'Mines' },
  'game.coinflip':    { de: 'Münzwurf', fr: 'Pile ou face', en: 'Coinflip' },
  'game.crash':        { de: 'Absturz', fr: 'Crash', en: 'Crash' },
  'game.plinko':       { de: 'Plinko', fr: 'Plinko', en: 'Plinko' },
  'game.roulette':     { de: 'Roulette', fr: 'Roulette', en: 'Roulette' },
  'game.blackjack':    { de: 'Blackjack', fr: 'Blackjack', en: 'Blackjack' },

  /* ---------- Spiele: Support Chat / FAQ ---------- */
  'support.title':   { de: '💬 Support', fr: '💬 Assistance', en: '💬 Support' },
  'support.intro':   { de: 'Hey! Ich bin der TNT-Zockt Support-Bot. Wähl unten eine Frage aus:', fr: 'Salut ! Je suis le bot d\'assistance TNT-Zockt. Choisis une question ci-dessous :', en: 'Hey! I\'m the TNT-Zockt support bot. Pick a question below:' },

  /* FAQ Startseite */
  'faq.index.1.q': { de: 'Sind die Casino-Angebote seriös?', fr: 'Les offres de casino sont-elles sérieuses ?', en: 'Are the casino offers legitimate?' },
  'faq.index.1.a': { de: 'Wir listen nur Casinos, die wir selbst geprüft haben. Trotzdem: Bitte prüfe vor der Anmeldung immer selbst die Lizenzierung und Rechtslage in deinem Land.', fr: 'Nous ne listons que des casinos que nous avons vérifiés nous-mêmes. Néanmoins : vérifie toujours toi-même la licence et la situation légale dans ton pays avant de t\'inscrire.', en: 'We only list casinos we\'ve personally vetted. Still, always check the licensing and legal situation in your country yourself before signing up.' },
  'faq.index.2.q': { de: 'Was sind TNT-Chips?', fr: 'Que sont les TNT-Chips ?', en: 'What are TNT-Chips?' },
  'faq.index.2.a': { de: 'Eine rein virtuelle Spielwährung für unsere TNT-Spiele-Seite — kein Echtgeld, keine Auszahlung. Jeder neue Spieler startet mit 1\'000 Gratis-Chips nach dem Login.', fr: 'Une monnaie de jeu purement virtuelle pour notre page TNT-Jeux — pas d\'argent réel, pas de paiement. Chaque nouveau joueur commence avec 1 000 jetons gratuits après connexion.', en: 'A purely virtual game currency for our TNT-Games page — no real money, no payout. Every new player starts with 1,000 free chips after logging in.' },
  'faq.index.3.q': { de: 'Wie logge ich mich ein?', fr: 'Comment me connecter ?', en: 'How do I log in?' },
  'faq.index.3.a': { de: 'Oben rechts kannst du dich mit deinem Twitch- oder Kick-Konto einloggen — das schaltet den Shop und die Spiele-Seite frei.', fr: 'En haut à droite, tu peux te connecter avec ton compte Twitch ou Kick — cela débloque la boutique et la page des jeux.', en: 'In the top right, you can log in with your Twitch or Kick account — this unlocks the shop and games page.' },
  'faq.index.4.q': { de: 'Wo finde ich eure Streams?', fr: 'Où puis-je trouver vos streams ?', en: 'Where can I find your streams?' },
  'faq.index.4.a': { de: 'Unter "Streamer" auf dieser Seite findest du unsere offiziellen Kanäle auf Twitch und YouTube.', fr: 'Sous "Streamers" sur cette page, tu trouveras nos chaînes officielles sur Twitch et YouTube.', en: 'Under "Streamers" on this page, you\'ll find our official channels on Twitch and YouTube.' },
  'faq.index.5.q': { de: 'Ich hab ein Problem/Frage', fr: 'J\'ai un problème/une question', en: 'I have a problem/question' },
  'faq.index.5.a': { de: 'Am schnellsten erreichst du uns über unsere Social-Media-Kanäle (Twitch, YouTube, Facebook) unten im Footer.', fr: 'Le plus rapide est de nous contacter via nos réseaux sociaux (Twitch, YouTube, Facebook) en bas de page.', en: 'The fastest way to reach us is via our social media channels (Twitch, YouTube, Facebook) in the footer below.' },
};

function tntGetLang(){
  return localStorage.getItem('tnt_lang') || 'de';
}

function tntSetLang(lang){
  localStorage.setItem('tnt_lang', lang);
  tntApplyTranslations();
  tntUpdateLangSwitcher();
  if(typeof onLanguageChanged === 'function') onLanguageChanged(lang);
}

function t(key){
  const lang = tntGetLang();
  const entry = TNT_TRANSLATIONS[key];
  if(!entry) return key;
  return entry[lang] || entry.de || key;
}

function tntApplyTranslations(){
  document.querySelectorAll('[data-i18n]').forEach(el => {
    el.textContent = t(el.dataset.i18n);
  });
  document.querySelectorAll('[data-i18n-html]').forEach(el => {
    el.innerHTML = t(el.dataset.i18nHtml);
  });
  document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
    el.placeholder = t(el.dataset.i18nPlaceholder);
  });
  document.documentElement.lang = tntGetLang();
}

function tntUpdateLangSwitcher(){
  document.querySelectorAll('.lang-btn').forEach(b => {
    b.classList.toggle('active', b.dataset.lang === tntGetLang());
  });
}

function tntLangSwitcherHTML(){
  return `
    <div class="lang-switcher">
      <button class="lang-btn" data-lang="de" onclick="tntSetLang('de')">DE</button>
      <button class="lang-btn" data-lang="fr" onclick="tntSetLang('fr')">FR</button>
      <button class="lang-btn" data-lang="en" onclick="tntSetLang('en')">EN</button>
    </div>
  `;
}

/* ============================================================
   Geo-Sichtbarkeit: Elemente mit der Klasse "geo-hide-ch"
   werden für Besucher aus der Schweiz automatisch ausgeblendet
   (z. B. die deutschen Casino-Angebote). Läuft über Vercels
   eigene IP-Standorterkennung, keine Cookies nötig.
   ============================================================ */
async function tntCheckGeoRestriction(){
  try{
    const res = await fetch('/api/geo');
    const data = await res.json();
    if(data && data.country === 'CH'){
      document.querySelectorAll('.geo-hide-ch').forEach(el => el.style.display = 'none');
    }
  }catch(e){
    // Bei Fehler: sicherheitshalber alles wie gewohnt anzeigen
    console.error('Geo-Check fehlgeschlagen:', e);
  }
}

document.addEventListener('DOMContentLoaded', () => {
  tntApplyTranslations();
  tntUpdateLangSwitcher();
  tntCheckGeoRestriction();
});
