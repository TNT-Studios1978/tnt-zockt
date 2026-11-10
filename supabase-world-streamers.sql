-- Stream-Terminal in der TNT-Zockt Virtual World.
-- Im Supabase SQL Editor des Website-Projekts ausführen. Mehrfaches Ausführen schadet nicht.
--
-- Die bestehende Streamer-Liste der Website bekommt das Häkchen "In der Virtual World zeigen".
-- Angehakte Twitch-Kanäle können die Spieler am Terminal in der Lounge auf die grosse Wand holen,
-- sobald sie live sind. Dein Kanal tnt_studiosch ist immer dabei und wird bevorzugt.

alter table public.streamers add column if not exists in_world boolean not null default false;
