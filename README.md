# TNT-Zockt

Casino-Affiliate-Website mit Supabase-Backend und Admin-Panel.

## Setup (einmalig)

1. **Supabase-Datenbank einrichten:**
   Öffne dein Supabase-Projekt → SQL Editor → New query → Inhalt von `supabase-schema.sql` einfügen → Run.
   Das legt die Tabellen `casinos`, `streamers`, `products` an und befüllt sie mit den aktuellen Inhalten.

2. **Supabase-Zugangsdaten prüfen:**
   In `supabase-config.js` stehen `SUPABASE_URL` und `SUPABASE_ANON_KEY` bereits eingetragen (aus deinem Projekt).

## Deployment über GitHub + Vercel

1. Neues GitHub-Repository erstellen (z. B. `tnt-zockt`)
2. Diese Dateien in das Repo pushen:
   ```
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/DEIN-USERNAME/tnt-zockt.git
   git push -u origin main
   ```
3. Auf [vercel.com](https://vercel.com) → "Add New Project" → GitHub-Repo auswählen
4. Framework Preset: **Other** (reines statisches HTML, kein Build-Schritt nötig)
5. Deploy klicken — fertig, Vercel gibt dir eine Live-URL

Bei jedem weiteren `git push` deployt Vercel automatisch die neue Version.

## Struktur

```
tnt-zockt/
├── index.html            Startseite (Casinos, Streamer)
├── shop.html              Merch-Shop (Platzhalter-Produkte)
├── admin.html              Admin-Panel zum Bearbeiten der Inhalte (PIN: 1234)
├── supabase-config.js      Supabase-Verbindungsdaten
├── supabase-schema.sql     Datenbank-Setup-Skript (einmalig ausführen)
└── assets/
    └── logo.jpg            Website-Logo
```

## Admin-Panel

Unter `/admin.html` erreichbar. PIN-Schutz (Standard: `1234`, änderbar in `admin.html` → Variable `ADMIN_PIN`).
Änderungen werden **direkt in Supabase gespeichert** und sind sofort auf der Live-Website sichtbar.

⚠️ **Sicherheitshinweis:** Der PIN schützt nur die Oberfläche. Da es sich um eine rein statische Website ohne eigenen Server handelt, verwendet der Admin-Bereich den öffentlichen Supabase "anon key". Für zusätzliche Sicherheit könnte später echter Login-Schutz via Supabase Auth ergänzt werden.
