-- Casino-Räume der TNT-Zockt Virtual World: Startseite je Casino.
-- Im Supabase SQL Editor des Website-Projekts ausführen. Mehrfaches Ausführen schadet nicht.
--
-- home_url: normale Startseite des Casinos (ohne Partner-Link), z.B. https://www.jackpots.ch/de/
-- Genutzt im Raum «Schweizer Casinos». Im internationalen Raum zeigt das Display direkt den Affiliate-Link.
-- Der Bildschirm des Automaten zeigt diese Seite (nur Demo-Spiele). Registrieren und Anmelden laufen
-- über die Knöpfe darunter mit dem Affiliate-Link, damit die Registrierung dir zugeordnet wird.
-- Leer: der Bildschirm zeigt wie bisher den Affiliate-Link.

alter table public.casinos_ch add column if not exists home_url text not null default '';
alter table public.casinos add column if not exists home_url text not null default '';

notify pgrst, 'reload schema';
