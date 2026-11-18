-- Casino-Räume der TNT-Zockt Virtual World.
-- Im Supabase SQL Editor des Website-Projekts ausführen. Mehrfaches Ausführen schadet nicht.
--
-- Die internationalen Casinos (Tabelle casinos, im Admin "Casinos DE") bekommen ein Häkchen:
--   * in_world  "Im Internationalen Casino der Virtual World aufstellen"  (Standard: aus)
-- Nur angehakte Anbieter erscheinen dort. Der Raum ist für Besucher aus der Schweiz gesperrt.
-- Die Schweizer Casinos (casinos_ch) kommen alle in den Raum "Schweizer Casinos", ohne Häkchen.

alter table public.casinos add column if not exists in_world boolean not null default false;
