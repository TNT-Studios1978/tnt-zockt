-- Casino-Räume der TNT-Zockt Virtual World: Bestätigung des Hinweises vor dem ersten Spiel.
-- Im Supabase SQL Editor des Website-Projekts ausführen. Mehrfaches Ausführen schadet nicht.
--
-- Wer an einem Portal-Automaten den Hinweis bestätigt (externes lizenziertes Casino, Spiel auf eigenes Risiko,
-- Rechtslage beim Casino-Betreiber, keine Haftung von TNT-Zockt, ab 18, SOS Spielsucht), wird hier gespeichert:
-- Konto (twitch:… oder kick:…), Name zum Zeitpunkt, Raum, Textversion und Zeit. Keine IP-Adresse, kein Gerät.
-- Nur mit dem Server-Schlüssel lesbar und beschreibbar (RLS an, keine Regeln für Besucher).

create table if not exists public.world_casino_consents (
  id bigint generated always as identity primary key,
  sub text not null,
  name text not null default '',
  room text not null check (room in ('casino-ch', 'casino-intl')),
  version int not null check (version > 0),
  accepted_at timestamptz not null default now(),
  unique (sub, version, room)
);

alter table public.world_casino_consents enable row level security;

notify pgrst, 'reload schema';
