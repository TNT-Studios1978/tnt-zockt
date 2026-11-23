-- ============================================================
-- TNT-ZOCKT — Tabelle user_logins absichern
-- Im Supabase SQL Editor des Website-Projekts ausführen. Mehrfaches Ausführen schadet nicht.
--
-- WICHTIG: Erst ausführen, NACHDEM die neue Website-Version live ist (api/points.js speichert
-- den Login jetzt auf dem Server). Vorher würde die alte Startseite beim Speichern scheitern.
-- Die Startseite zeigt dann trotzdem alles an, nur der Eintrag in user_logins fehlt.
--
-- Problem: "Public read user_logins" und "Public write user_logins" erlauben jeder Person,
-- die Liste aller Twitch-/Kick-Namen mit Punkteständen zu lesen, zu ändern und zu löschen.
--
-- Nach diesem Skript:
--   * Lesen:     nur dein Admin-Konto (Admin-Bereich "Nutzer")
--   * Schreiben: nur noch der Server (api/points.js mit dem geheimen Schlüssel, umgeht RLS)
-- ============================================================

begin;

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

do $$
declare
  p record;
begin
  for p in select policyname from pg_policies where schemaname = 'public' and tablename = 'user_logins' loop
    execute format('drop policy %I on public.user_logins', p.policyname);
  end loop;
end $$;

alter table public.user_logins enable row level security;

create policy "Admin liest user_logins" on public.user_logins for select to authenticated
  using (public.is_site_admin());

revoke insert, update, delete on public.user_logins from anon, authenticated;

commit;

-- Kontrolle (nur lesen): genau eine Policy "Admin liest user_logins".
select policyname, cmd, roles, qual
from pg_policies
where schemaname = 'public' and tablename = 'user_logins';
