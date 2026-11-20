-- Sichtbarkeit der Casinos, im Admin per Häkchen steuerbar, ohne einen Eintrag zu löschen.
-- Im Supabase SQL Editor des Website-Projekts ausführen. Mehrfaches Ausführen schadet nicht.
--
-- Schweizer Casinos (casinos_ch):
--   * in_world    "In der Virtual World aufstellen"  (Standard: an, wie bisher: alle stehen im Schweizer Casino)
--   * on_website  "Auf der Website anzeigen"         (Standard: an)
-- Internationale Casinos (casinos):
--   * in_world    gibt es schon (supabase-world-casinos.sql, Standard: aus)
--   * on_website  "Auf der Website anzeigen"         (Standard: an)
-- Bestehende Einträge bleiben damit sichtbar wie bisher.

alter table public.casinos_ch add column if not exists in_world   boolean not null default true;
alter table public.casinos_ch add column if not exists on_website boolean not null default true;
alter table public.casinos    add column if not exists in_world   boolean not null default false;
alter table public.casinos    add column if not exists on_website boolean not null default true;
