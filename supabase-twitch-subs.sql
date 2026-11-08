-- Abo-Skins in der Virtual World: Zugang des Twitch-Kanals zur Abo-Liste.
-- Einmal im Supabase SQL Editor ausführen. Lesen und schreiben darf nur der Service-Schlüssel der Website
-- (api/world-subs.js und api/_lib/twitchSubs.js). Für Besucher ist die Tabelle unsichtbar.

create table if not exists public.twitch_broadcaster (
  id int primary key default 1 check (id = 1),
  login text not null,
  broadcaster_id text not null,
  access_token text not null,
  refresh_token text not null,
  expires_at timestamptz not null,
  updated_at timestamptz not null default now()
);

-- Zeilenschutz an, keine Richtlinien: anon und angemeldete Nutzer sehen nichts.
alter table public.twitch_broadcaster enable row level security;
revoke all on public.twitch_broadcaster from anon, authenticated;
