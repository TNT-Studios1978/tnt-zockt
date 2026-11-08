-- Jukebox für die TNT-Zockt Virtual World.
-- Im Supabase SQL Editor des Website-Projekts ausführen. Mehrfaches Ausführen schadet nicht.
--
--   * public.jukebox_tracks      Lieder, gepflegt im Admin-Bereich (Reiter "Jukebox")
--   * Speicher "jukebox"          die Musikdateien, öffentlich lesbar, schreiben nur der Admin
--   * virtual_world.jukebox_queue Warteschlange der Welt; jeder Eintrag ist ein bezahltes Lied
--   * coin_ledger                 neue Buchungsart 'purchase' (Kauf mit TNT-Coins, kein Spiel)
--   * world_jukebox_buy/_refund   Kauf und Rückerstattung, nur für den Spielserver (vw_server)

begin;

-- ---------- Lieder ----------
create table if not exists public.jukebox_tracks (
  id uuid primary key default gen_random_uuid(),
  title text not null check (length(title) between 1 and 80),
  artist text not null default '' check (length(artist) <= 80),
  file_url text not null,
  file_path text not null,
  duration_sec integer not null check (duration_sec between 5 and 1200),
  active boolean not null default true,
  sort_order integer not null default 0,
  created_at timestamptz not null default now()
);
alter table public.jukebox_tracks enable row level security;

drop policy if exists "Jukebox lesen" on public.jukebox_tracks;
create policy "Jukebox lesen" on public.jukebox_tracks for select
  using (active or lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch');
drop policy if exists "Jukebox Admin" on public.jukebox_tracks;
create policy "Jukebox Admin" on public.jukebox_tracks for all to authenticated
  using (lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch')
  with check (lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch');

grant select on public.jukebox_tracks to vw_server;
drop policy if exists "Jukebox Spielserver" on public.jukebox_tracks;
create policy "Jukebox Spielserver" on public.jukebox_tracks for select to vw_server using (active);

-- ---------- Speicher für die Musikdateien (max. 25 MB je Datei) ----------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('jukebox', 'jukebox', true, 26214400,
        array['audio/mpeg', 'audio/mp3', 'audio/mp4', 'audio/x-m4a', 'audio/aac', 'audio/ogg', 'audio/wav', 'audio/x-wav', 'audio/webm'])
on conflict (id) do update
  set public = true, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Jukebox Dateien lesen" on storage.objects;
create policy "Jukebox Dateien lesen" on storage.objects for select to authenticated
  using (bucket_id = 'jukebox' and lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch');
drop policy if exists "Jukebox Dateien hochladen" on storage.objects;
create policy "Jukebox Dateien hochladen" on storage.objects for insert to authenticated
  with check (bucket_id = 'jukebox' and lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch');
drop policy if exists "Jukebox Dateien ändern" on storage.objects;
create policy "Jukebox Dateien ändern" on storage.objects for update to authenticated
  using (bucket_id = 'jukebox' and lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch');
drop policy if exists "Jukebox Dateien löschen" on storage.objects;
create policy "Jukebox Dateien löschen" on storage.objects for delete to authenticated
  using (bucket_id = 'jukebox' and lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch');

-- ---------- Neue Buchungsart 'purchase' im Coin-Verlauf ----------
-- Übernimmt alle bisher erlaubten Arten aus der bestehenden Regel und ergänzt nur 'purchase'.
do $$
declare
  v_name text;
  v_def text;
  v_kinds text;
begin
  select conname, pg_get_constraintdef(oid) into v_name, v_def
    from pg_constraint
    where conrelid = 'public.coin_ledger'::regclass and contype = 'c' and pg_get_constraintdef(oid) ilike '%kind%'
    limit 1;
  if v_def is not null and v_def like '%''purchase''%' then return; end if;
  select string_agg(quote_literal(k), ', ') into v_kinds from (
    select distinct m[1] as k from regexp_matches(coalesce(v_def, ''), '''([a-z_]+)''', 'g') as m
    union select 'purchase'
  ) kinds;
  if v_name is not null then execute format('alter table public.coin_ledger drop constraint %I', v_name); end if;
  execute format('alter table public.coin_ledger add constraint coin_ledger_kind_check check (kind in (%s))', v_kinds);
end $$;

-- ---------- Warteschlange ----------
create table if not exists virtual_world.jukebox_queue (
  key text primary key,
  twitch_login text not null,
  user_id text not null,
  name text not null,
  track_id uuid not null,
  amount integer not null check (amount > 0),
  status text not null default 'queued' check (status in ('queued', 'playing', 'played', 'refunded')),
  created_at timestamptz not null default now(),
  started_at timestamptz
);
create index if not exists jukebox_queue_open on virtual_world.jukebox_queue (status, created_at);
alter table virtual_world.jukebox_queue enable row level security;
grant select, update on virtual_world.jukebox_queue to vw_server;
drop policy if exists server_jukebox on virtual_world.jukebox_queue;
create policy server_jukebox on virtual_world.jukebox_queue for all to vw_server using (true) with check (true);

-- Kauf: bucht die Coins ab und stellt das Lied in die Warteschlange.
-- Rückgabe: neuer Saldo, oder null wenn das Guthaben nicht reicht. Gleicher Schlüssel bucht nicht doppelt.
create or replace function public.world_jukebox_buy(p_login text, p_user text, p_name text, p_key text, p_track uuid, p_amount integer)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_chips integer;
  v_title text;
begin
  if p_login is null or p_login !~ '^[a-z0-9_]{1,25}$' then raise exception 'invalid_login'; end if;
  if p_key is null or length(p_key) not between 1 and 200 then raise exception 'invalid_key'; end if;
  if p_amount is null or p_amount not between 1 and 10000 then raise exception 'invalid_amount'; end if;

  v_chips := coin_lock_wallet(p_login);
  if exists (select 1 from virtual_world.jukebox_queue where key = p_key) then return v_chips; end if;

  select title into v_title from public.jukebox_tracks where id = p_track and active;
  if v_title is null then raise exception 'jukebox_track'; end if;
  if (select count(*) from virtual_world.jukebox_queue where user_id = p_user and status = 'queued') >= 5 then
    raise exception 'jukebox_limit';
  end if;
  if v_chips < p_amount then return null; end if;

  v_chips := coin_book(p_login, 'purchase', -p_amount, 'world:jukebox', null, left('Jukebox: ' || v_title, 120));
  insert into virtual_world.jukebox_queue (key, twitch_login, user_id, name, track_id, amount)
    values (p_key, p_login, left(p_user, 100), left(coalesce(p_name, ''), 40), p_track, p_amount);
  return v_chips;
end;
$$;

-- Rückerstattung eines noch nicht gespielten Lieds (abgebrochen oder Lied nicht mehr verfügbar).
-- Rückgabe: neuer Saldo, oder null wenn es nichts zu erstatten gibt.
create or replace function public.world_jukebox_refund(p_key text)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_row virtual_world.jukebox_queue%rowtype;
  v_chips integer;
begin
  select * into v_row from virtual_world.jukebox_queue where key = p_key and status = 'queued' for update;
  if not found then return null; end if;
  perform coin_lock_wallet(v_row.twitch_login);
  v_chips := coin_book(v_row.twitch_login, 'purchase', v_row.amount, 'world:jukebox', null, 'Jukebox: Rückerstattung');
  update virtual_world.jukebox_queue set status = 'refunded' where key = p_key;
  return v_chips;
end;
$$;

revoke all on function public.world_jukebox_buy(text, text, text, text, uuid, integer) from public, anon, authenticated;
revoke all on function public.world_jukebox_refund(text) from public, anon, authenticated;
grant execute on function public.world_jukebox_buy(text, text, text, text, uuid, integer) to vw_server;
grant execute on function public.world_jukebox_refund(text) to vw_server;

commit;
