-- ============================================================
-- TNT-ZOCKT — Datei-Speicher "logos" absichern
-- Im Supabase SQL Editor des Website-Projekts ausführen. Mehrfaches Ausführen schadet nicht.
-- Am besten nach supabase-admin-rechte.sql (die Prüffunktion wird hier aber auch angelegt).
--
-- Problem: Die Policy "public upload logos" erlaubt jeder Person ohne Login, beliebige Dateien
-- in den Speicher "logos" hochzuladen. Die Dateien sind danach öffentlich abrufbar
-- (Missbrauch als Gratis-Hosting, z. B. für Phishing-Seiten oder Schadsoftware unter deiner Supabase-Adresse).
--
-- Nach diesem Skript:
--   * Lesen:                     alle (wie bisher, Logos und Produktbilder auf der Website)
--   * Hochladen, ersetzen, löschen: nur dein Admin-Konto (admin_users)
-- Der Speicher "jukebox" bleibt unverändert.
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

drop policy if exists "public upload logos" on storage.objects;
drop policy if exists "Logos Admin hochladen" on storage.objects;
drop policy if exists "Logos Admin ändern" on storage.objects;
drop policy if exists "Logos Admin löschen" on storage.objects;

create policy "Logos Admin hochladen" on storage.objects for insert to authenticated
  with check (bucket_id = 'logos' and public.is_site_admin());

-- Der Admin lädt mit upsert hoch (gleicher Dateiname ersetzt die alte Datei), dafür braucht es "ändern".
create policy "Logos Admin ändern" on storage.objects for update to authenticated
  using (bucket_id = 'logos' and public.is_site_admin())
  with check (bucket_id = 'logos' and public.is_site_admin());

create policy "Logos Admin löschen" on storage.objects for delete to authenticated
  using (bucket_id = 'logos' and public.is_site_admin());

commit;

-- Kontrolle (nur lesen): "public read logos" bleibt, dazu die drei neuen "Logos Admin …".
select policyname, cmd, roles, qual, with_check
from pg_policies
where schemaname = 'storage' and tablename = 'objects'
order by policyname;

-- Optional (nur lesen): Wurden schon fremde Dateien hochgeladen? Liste der letzten 50 Dateien.
-- select name, created_at, metadata->>'mimetype' as typ, metadata->>'size' as bytes
-- from storage.objects where bucket_id = 'logos' order by created_at desc limit 50;
