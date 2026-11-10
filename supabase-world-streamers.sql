-- Stream-Terminal in der TNT-Zockt Virtual World.
-- Im Supabase SQL Editor des Website-Projekts ausführen. Mehrfaches Ausführen schadet nicht.
--
-- Die bestehende Streamer-Liste der Website bekommt zwei Häkchen:
--   * on_website  "Auf der Website zeigen"       (bisherige Einträge bleiben auf der Website sichtbar)
--   * in_world    "In der Virtual World zeigen"  (angehakte Twitch-Kanäle erscheinen am Stream-Terminal)
-- So lassen sich Streamer nur für die Welt eintragen, ohne dass sie auf der Website erscheinen.
-- Dein Kanal tnt_studiosch ist im Terminal immer dabei und wird bevorzugt.

alter table public.streamers add column if not exists on_website boolean not null default true;
alter table public.streamers add column if not exists in_world boolean not null default false;
