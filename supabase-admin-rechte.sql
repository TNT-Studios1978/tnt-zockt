-- ============================================================
-- TNT-ZOCKT — Schreibrechte absichern
-- Im Supabase SQL Editor des Website-Projekts ausführen. Mehrfaches Ausführen schadet nicht.
--
-- Problem: supabase-schema.sql hat für casinos, casinos_ch, streamers und products die Policy
-- "Public write" mit using (true) angelegt. Damit kann jede Person mit dem öffentlichen
-- Schlüssel aus supabase-config.js Casino-Links, Streamer und Produkte ändern oder löschen.
--
-- Nach diesem Skript gilt für casinos, casinos_ch, streamers, products und partner_offers:
--   * Lesen:     alle (wie bisher, die Website braucht das)
--   * Schreiben: nur eingeloggte Konten, die in admin_users stehen (dein Admin-Konto)
-- Alle bisherigen Policies dieser fünf Tabellen werden entfernt und neu angelegt.
--
-- Die Website schreibt in diese Tabellen nur aus admin.html (eingeloggt). Die Vercel-Funktionen
-- lesen nur. Andere Tabellen (Coins, Turniere, Boutique, visits, user_logins) bleiben unverändert.
--
-- Läuft als ein Block: Wenn etwas schiefgeht, wird nichts geändert.
-- ============================================================

begin;

-- 1) Admin-Konto sicherstellen. Ohne Eintrag in admin_users könnte danach niemand mehr speichern.
create table if not exists public.admin_users (
  user_id uuid primary key,
  created_at timestamptz not null default now()
);
alter table public.admin_users enable row level security;

insert into public.admin_users (user_id)
  select id from auth.users where lower(email) = 'tnt-studios@gmx.ch'
  on conflict (user_id) do nothing;

do $$
begin
  if not exists (select 1 from public.admin_users a join auth.users u on u.id = a.user_id) then
    raise exception 'Kein Admin-Konto in admin_users gefunden. Abgebrochen, nichts wurde geändert.';
  end if;
end $$;

-- 2) Prüffunktion: Ist das eingeloggte Konto ein Admin?
--    security definer, damit sie admin_users lesen darf (die Tabelle hat keine öffentliche Policy).
create or replace function public.is_site_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (select 1 from public.admin_users where user_id = auth.uid());
$$;
revoke all on function public.is_site_admin() from public;
grant execute on function public.is_site_admin() to anon, authenticated;

-- 3) Policies der fünf Tabellen ersetzen
do $$
declare
  t text;
  p record;
begin
  foreach t in array array['casinos', 'casinos_ch', 'streamers', 'products', 'partner_offers'] loop
    if to_regclass('public.' || t) is null then
      raise notice 'Tabelle % gibt es nicht, übersprungen.', t;
      continue;
    end if;

    for p in select policyname from pg_policies where schemaname = 'public' and tablename = t loop
      execute format('drop policy %I on public.%I', p.policyname, t);
    end loop;

    execute format('alter table public.%I enable row level security', t);
    execute format('create policy %I on public.%I for select using (true)', 'Lesen ' || t, t);
    execute format(
      'create policy %I on public.%I for all to authenticated using (public.is_site_admin()) with check (public.is_site_admin())',
      'Admin schreiben ' || t, t);
  end loop;
end $$;

commit;

-- ============================================================
-- Kontrolle (danach ausführen, nur lesen):
-- Pro Tabelle sollten genau zwei Zeilen erscheinen: "Lesen …" (SELECT, true)
-- und "Admin schreiben …" (ALL, is_site_admin()).
-- ============================================================
select tablename, policyname, cmd, roles, qual
from pg_policies
where schemaname = 'public'
  and tablename in ('casinos', 'casinos_ch', 'streamers', 'products', 'partner_offers')
order by tablename, policyname;

-- Zusätzlich anschauen (nur lesen): Wer darf Dateien im Speicher "logos" hochladen oder löschen?
-- Steht dort eine Policy für anon oder ohne Bedingung, ist das die nächste Lücke.
-- select policyname, cmd, roles, qual, with_check from pg_policies
-- where schemaname = 'storage' and tablename = 'objects';
