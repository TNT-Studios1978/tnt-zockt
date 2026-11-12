-- Black-Jack-Turnier (KO) für die TNT-Zockt Virtual World.
-- Im Supabase SQL Editor des Website-Projekts ausführen. Mehrfaches Ausführen schadet nicht.
--
-- Eine Serie hat 5 Spieltage: 4 Vorrunden (je eine pro Woche) und das Finale.
--   * Vorrunde: Anmeldung an der Kasse der Welt mit Startgeld in TNT-Coins, jede Woche neu. Kein Nachkaufen.
--     25 % der Startgelder gehen an die Plätze 1–3 der Woche (50/30/20), 75 % in den grossen Topf der Serie.
--     Die besten 3 der Woche, die noch nicht qualifiziert sind, kommen ins Finale.
--   * Finale: gratis für die Qualifizierten. Topf der Serie plus Sponsor, verteilt 50/30/20.
--
--   * public.bj_series      Serien, geplant im Admin-Bereich (Reiter "Black-Jack-Turnier")
--   * public.bj_events      die 5 Spieltage einer Serie (round 1–4 Vorrunde, 5 Finale)
--   * public.bj_entries     Anmeldungen bzw. Finalteilnehmer
--   * public.bj_qualified   Finalisten einer Serie
--   * world_bj_*            nur für den Spielserver (vw_server)
--   * admin_bj_cancel       Absage einer Vorrunde aus dem Admin-Bereich, erstattet allen das Startgeld
--
-- Startgeld und Rückerstattung laufen als Buchungsart 'purchase', Preise als 'win' (Spiel 'world:blackjack-tour').

begin;

-- ---------- Serien ----------
create table if not exists public.bj_series (
  id uuid primary key default gen_random_uuid(),
  name text not null default 'Black-Jack-Turnier' check (char_length(name) between 1 and 60),
  sponsor integer not null default 0 check (sponsor between 0 and 100000),
  pot integer not null default 0,
  status text not null default 'open' check (status in ('open', 'finished', 'cancelled')),
  created_at timestamptz not null default now()
);
alter table public.bj_series enable row level security;

-- ---------- Spieltage ----------
create table if not exists public.bj_events (
  id uuid primary key default gen_random_uuid(),
  series_id uuid not null references public.bj_series(id) on delete cascade,
  round integer not null check (round between 1 and 5),
  starts_at timestamptz not null,
  buy_in integer not null default 0 check (buy_in between 0 and 100000),
  max_players integer not null default 30 check (max_players between 4 and 30),
  status text not null default 'scheduled' check (status in ('scheduled', 'running', 'finished', 'cancelled')),
  pool integer not null default 0,
  results jsonb,
  created_at timestamptz not null default now(),
  finished_at timestamptz,
  unique (series_id, round)
);
create index if not exists bj_events_next on public.bj_events (status, starts_at);
alter table public.bj_events enable row level security;

-- ---------- Anmeldungen ----------
create table if not exists public.bj_entries (
  event_id uuid not null references public.bj_events(id) on delete cascade,
  twitch_login text not null,
  user_id text not null,
  name text not null,
  paid integer not null check (paid >= 0),
  status text not null default 'registered' check (status in ('registered', 'playing', 'forfeit', 'refunded', 'finished')),
  place integer,
  prize integer not null default 0,
  created_at timestamptz not null default now(),
  primary key (event_id, twitch_login)
);
alter table public.bj_entries enable row level security;

-- ---------- Finalisten ----------
create table if not exists public.bj_qualified (
  series_id uuid not null references public.bj_series(id) on delete cascade,
  twitch_login text not null,
  user_id text not null,
  name text not null,
  round integer not null,
  place integer not null,
  created_at timestamptz not null default now(),
  primary key (series_id, twitch_login)
);
alter table public.bj_qualified enable row level security;

-- ---------- Rechte ----------
-- Serien und Spieltage darf jeder sehen, Anmeldungen und Finalisten nur der Admin und der Spielserver.
drop policy if exists "BJ-Serien lesen" on public.bj_series;
create policy "BJ-Serien lesen" on public.bj_series for select using (true);
drop policy if exists "BJ-Serien Admin" on public.bj_series;
create policy "BJ-Serien Admin" on public.bj_series for insert to authenticated
  with check (lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch' and status = 'open' and pot = 0);
drop policy if exists "BJ-Serien Admin löschen" on public.bj_series;
create policy "BJ-Serien Admin löschen" on public.bj_series for delete to authenticated
  using (
    lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch'
    and pot = 0
    and not exists (select 1 from public.bj_events e where e.series_id = bj_series.id and e.status <> 'scheduled')
    and not exists (select 1 from public.bj_entries x join public.bj_events e on e.id = x.event_id where e.series_id = bj_series.id and x.status <> 'refunded')
  );

drop policy if exists "BJ-Spieltage lesen" on public.bj_events;
create policy "BJ-Spieltage lesen" on public.bj_events for select using (true);
drop policy if exists "BJ-Spieltage Admin" on public.bj_events;
create policy "BJ-Spieltage Admin" on public.bj_events for insert to authenticated
  with check (lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch' and status = 'scheduled');
-- Termin verschieben, solange der Spieltag geplant ist (z. B. neues Datum für ein ausgefallenes Finale: siehe admin_bj_reschedule)
drop policy if exists "BJ-Spieltage Admin ändern" on public.bj_events;
create policy "BJ-Spieltage Admin ändern" on public.bj_events for update to authenticated
  using (lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch' and status = 'scheduled')
  with check (lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch' and status = 'scheduled');

drop policy if exists "BJ-Anmeldungen Admin" on public.bj_entries;
create policy "BJ-Anmeldungen Admin" on public.bj_entries for select to authenticated
  using (lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch');
drop policy if exists "BJ-Finalisten lesen" on public.bj_qualified;
create policy "BJ-Finalisten lesen" on public.bj_qualified for select using (true);

grant select on public.bj_series, public.bj_events, public.bj_entries, public.bj_qualified to vw_server;
drop policy if exists "BJ-Serien Spielserver" on public.bj_series;
create policy "BJ-Serien Spielserver" on public.bj_series for select to vw_server using (true);
drop policy if exists "BJ-Spieltage Spielserver" on public.bj_events;
create policy "BJ-Spieltage Spielserver" on public.bj_events for select to vw_server using (true);
drop policy if exists "BJ-Anmeldungen Spielserver" on public.bj_entries;
create policy "BJ-Anmeldungen Spielserver" on public.bj_entries for select to vw_server using (true);
drop policy if exists "BJ-Finalisten Spielserver" on public.bj_qualified;
create policy "BJ-Finalisten Spielserver" on public.bj_qualified for select to vw_server using (true);

-- ---------- Anmelden zur Vorrunde: Startgeld abbuchen ----------
-- Rückgabe: neuer Saldo, oder null wenn das Guthaben nicht reicht.
create or replace function public.world_bj_register(p_event uuid, p_login text, p_user text, p_name text)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_event public.bj_events%rowtype;
  v_chips integer;
  v_count integer;
begin
  if p_login is null or p_login !~ '^[a-z0-9_]{1,25}$' then raise exception 'invalid_login'; end if;
  select * into v_event from public.bj_events where id = p_event for update;
  if not found or v_event.status <> 'scheduled' or v_event.starts_at <= now() then raise exception 'bj_closed'; end if;
  if v_event.round = 5 then raise exception 'bj_final'; end if;
  if exists (select 1 from public.bj_entries where event_id = p_event and twitch_login = p_login and status = 'registered') then
    raise exception 'bj_already';
  end if;
  select count(*) into v_count from public.bj_entries where event_id = p_event and status = 'registered';
  if v_count >= v_event.max_players then raise exception 'bj_full'; end if;
  v_chips := coin_lock_wallet(p_login);
  if v_chips < v_event.buy_in then return null; end if;
  if v_event.buy_in > 0 then
    v_chips := coin_book(p_login, 'purchase', -v_event.buy_in, 'world:blackjack-tour', null, 'Black-Jack-Turnier: Startgeld Vorrunde ' || v_event.round);
  end if;
  insert into public.bj_entries (event_id, twitch_login, user_id, name, paid, status)
    values (p_event, p_login, left(p_user, 100), left(coalesce(p_name, ''), 40), v_event.buy_in, 'registered')
    on conflict (event_id, twitch_login) do update
      set user_id = excluded.user_id, name = excluded.name, paid = excluded.paid, status = 'registered', place = null, prize = 0, created_at = now();
  return v_chips;
end;
$$;

-- ---------- Abmelden bis zum Start: Startgeld zurück ----------
create or replace function public.world_bj_unregister(p_event uuid, p_login text)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_event public.bj_events%rowtype;
  v_entry public.bj_entries%rowtype;
  v_chips integer;
begin
  select * into v_event from public.bj_events where id = p_event for update;
  if not found or v_event.status <> 'scheduled' or v_event.starts_at <= now() then raise exception 'bj_closed'; end if;
  select * into v_entry from public.bj_entries where event_id = p_event and twitch_login = p_login and status = 'registered' for update;
  if not found then return null; end if;
  v_chips := coin_lock_wallet(p_login);
  if v_entry.paid > 0 then
    v_chips := coin_book(p_login, 'purchase', v_entry.paid, 'world:blackjack-tour', null, 'Black-Jack-Turnier: Abmeldung');
  end if;
  update public.bj_entries set status = 'refunded' where event_id = p_event and twitch_login = p_login;
  return v_chips;
end;
$$;

-- ---------- Start ----------
-- Vorrunde: Anwesende spielen, Abwesende verlieren ihr Startgeld (bleibt im Pool der Woche).
-- Finale: die Finalisten der Serie werden eingetragen; Pool = Topf der Serie + Sponsor.
-- Rückgabe: Pool dieses Spieltags.
create or replace function public.world_bj_start(p_event uuid, p_present text[])
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_event public.bj_events%rowtype;
  v_series public.bj_series%rowtype;
  v_pool integer;
begin
  select * into v_event from public.bj_events where id = p_event and status = 'scheduled' for update;
  if not found then raise exception 'bj_closed'; end if;
  select * into v_series from public.bj_series where id = v_event.series_id for update;
  if v_event.round = 5 then
    insert into public.bj_entries (event_id, twitch_login, user_id, name, paid, status)
      select p_event, q.twitch_login, q.user_id, q.name, 0, case when q.twitch_login = any(p_present) then 'playing' else 'forfeit' end
      from public.bj_qualified q where q.series_id = v_event.series_id
      on conflict (event_id, twitch_login) do update set status = excluded.status, place = null, prize = 0;
    v_pool := v_series.pot + v_series.sponsor;
  else
    update public.bj_entries set status = case when twitch_login = any(p_present) then 'playing' else 'forfeit' end
      where event_id = p_event and status = 'registered';
    select coalesce(sum(paid), 0) into v_pool from public.bj_entries where event_id = p_event and status in ('playing', 'forfeit');
  end if;
  update public.bj_events set status = 'running', pool = v_pool where id = p_event;
  return v_pool;
end;
$$;

-- ---------- Absage ----------
-- Vorrunde: alle bekommen ihr Startgeld zurück. Finale: niemand hat bezahlt, der Topf bleibt in der Serie
-- (der Admin plant ein neues Datum mit admin_bj_reschedule).
create or replace function public.bj_refund_all(p_event uuid)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_entry record;
  v_count integer := 0;
begin
  perform 1 from public.bj_events where id = p_event and status in ('scheduled', 'running') for update;
  if not found then return 0; end if;
  for v_entry in select * from public.bj_entries where event_id = p_event and status in ('registered', 'playing', 'forfeit') for update loop
    if v_entry.paid > 0 then
      perform coin_lock_wallet(v_entry.twitch_login);
      perform coin_book(v_entry.twitch_login, 'purchase', v_entry.paid, 'world:blackjack-tour', null, 'Black-Jack-Turnier abgesagt: Rückerstattung');
      v_count := v_count + 1;
    end if;
    update public.bj_entries set status = 'refunded' where event_id = p_event and twitch_login = v_entry.twitch_login;
  end loop;
  update public.bj_events set status = 'cancelled', finished_at = now(), pool = 0 where id = p_event;
  return v_count;
end;
$$;

create or replace function public.world_bj_cancel(p_event uuid)
returns integer
language sql
security definer
set search_path = public
as $$ select public.bj_refund_all(p_event); $$;

-- ---------- Ende ----------
-- p_results: [{ "login": "...", "place": 1, "prize": 123 }, ...]; die Summe darf den Pool nicht übersteigen.
-- Vorrunde: Rest des Pools in den Topf der Serie, p_qualify (Logins) kommen ins Finale.
-- Finale: Topf geleert, Serie beendet.
create or replace function public.world_bj_finish(p_event uuid, p_results jsonb, p_qualify text[])
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_event public.bj_events%rowtype;
  v_item jsonb;
  v_total integer := 0;
  v_login text;
  v_prize integer;
  v_place integer;
begin
  select * into v_event from public.bj_events where id = p_event and status = 'running' for update;
  if not found then raise exception 'bj_not_running'; end if;
  perform 1 from public.bj_series where id = v_event.series_id for update;
  select coalesce(sum((item ->> 'prize')::integer), 0) into v_total from jsonb_array_elements(p_results) as item;
  if v_total > v_event.pool then raise exception 'bj_prizes_exceed_pool'; end if;
  for v_item in select * from jsonb_array_elements(p_results) loop
    v_login := v_item ->> 'login';
    v_prize := coalesce((v_item ->> 'prize')::integer, 0);
    v_place := (v_item ->> 'place')::integer;
    update public.bj_entries set status = 'finished', place = v_place, prize = v_prize
      where event_id = p_event and twitch_login = v_login and status = 'playing';
    if found and v_prize > 0 then
      perform coin_lock_wallet(v_login);
      perform coin_book(v_login, 'win', v_prize, 'world:blackjack-tour', null,
        case when v_event.round = 5 then 'Black-Jack-Turnier Finale: Platz ' else 'Black-Jack-Turnier Vorrunde ' || v_event.round || ': Platz ' end || v_place);
    end if;
  end loop;
  if v_event.round = 5 then
    update public.bj_series set pot = 0, status = 'finished' where id = v_event.series_id;
  else
    update public.bj_series set pot = pot + (v_event.pool - v_total) where id = v_event.series_id;
    insert into public.bj_qualified (series_id, twitch_login, user_id, name, round, place)
      select v_event.series_id, x.twitch_login, x.user_id, x.name, v_event.round, x.place
      from public.bj_entries x
      where x.event_id = p_event and x.twitch_login = any(coalesce(p_qualify, '{}'))
      on conflict (series_id, twitch_login) do nothing;
  end if;
  update public.bj_events set status = 'finished', finished_at = now(), results = p_results where id = p_event;
  return v_total;
end;
$$;

-- ---------- Admin: Vorrunde absagen ----------
create or replace function public.admin_bj_cancel(p_event uuid)
returns integer
language plpgsql
security definer
set search_path = public
as $$
begin
  if lower(coalesce(auth.jwt() ->> 'email', '')) <> 'tnt-studios@gmx.ch' then raise exception 'not_admin'; end if;
  if exists (select 1 from public.bj_events where id = p_event and status = 'running') then raise exception 'bj_running'; end if;
  return public.bj_refund_all(p_event);
end;
$$;

-- ---------- Admin: abgesagten Spieltag neu ansetzen (z. B. Finale) ----------
create or replace function public.admin_bj_reschedule(p_event uuid, p_starts timestamptz)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if lower(coalesce(auth.jwt() ->> 'email', '')) <> 'tnt-studios@gmx.ch' then raise exception 'not_admin'; end if;
  if p_starts < now() + interval '5 minutes' then raise exception 'bj_too_soon'; end if;
  update public.bj_events set status = 'scheduled', starts_at = p_starts, finished_at = null, pool = 0, results = null
    where id = p_event and status = 'cancelled'
      and exists (select 1 from public.bj_series s where s.id = bj_events.series_id and s.status = 'open');
  if not found then raise exception 'bj_not_cancelled'; end if;
  delete from public.bj_entries where event_id = p_event and status = 'refunded';
end;
$$;

revoke all on function public.world_bj_register(uuid, text, text, text) from public, anon, authenticated;
revoke all on function public.world_bj_unregister(uuid, text) from public, anon, authenticated;
revoke all on function public.world_bj_start(uuid, text[]) from public, anon, authenticated;
revoke all on function public.world_bj_cancel(uuid) from public, anon, authenticated;
revoke all on function public.world_bj_finish(uuid, jsonb, text[]) from public, anon, authenticated;
revoke all on function public.bj_refund_all(uuid) from public, anon, authenticated;
revoke all on function public.admin_bj_cancel(uuid) from public, anon;
revoke all on function public.admin_bj_reschedule(uuid, timestamptz) from public, anon;
grant execute on function public.world_bj_register(uuid, text, text, text) to vw_server;
grant execute on function public.world_bj_unregister(uuid, text) to vw_server;
grant execute on function public.world_bj_start(uuid, text[]) to vw_server;
grant execute on function public.world_bj_cancel(uuid) to vw_server;
grant execute on function public.world_bj_finish(uuid, jsonb, text[]) to vw_server;
grant execute on function public.admin_bj_cancel(uuid) to authenticated;
grant execute on function public.admin_bj_reschedule(uuid, timestamptz) to authenticated;

commit;
