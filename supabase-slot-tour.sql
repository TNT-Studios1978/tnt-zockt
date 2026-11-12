-- Slot-Turnier (KO) für die TNT-Zockt Virtual World, gleiche Serien wie das Black-Jack-Turnier.
-- Im Supabase SQL Editor ausführen, NACH supabase-blackjack-tour.sql. Mehrfaches Ausführen schadet nicht.
--
-- Serien bekommen eine Spielart (game: blackjack oder slots) und eine Heat-Dauer in Minuten.
-- Spieltage, Anmeldungen, Topf und Finalisten laufen über dieselben Tabellen (bj_*) und Funktionen (world_bj_*);
-- Buchungstexte und Spiel-Kennung richten sich nach der Spielart (world:slot-tour bzw. world:blackjack-tour).

begin;

alter table public.bj_series add column if not exists game text not null default 'blackjack';
alter table public.bj_series drop constraint if exists bj_series_game_check;
alter table public.bj_series add constraint bj_series_game_check check (game in ('blackjack', 'slots'));
alter table public.bj_series add column if not exists heat_minutes integer not null default 5;
alter table public.bj_series drop constraint if exists bj_series_heat_minutes_check;
alter table public.bj_series add constraint bj_series_heat_minutes_check check (heat_minutes between 3 and 15);

-- Slot-Spieltage: bis 40 Spieler (Heats à 5 laufen nacheinander)
alter table public.bj_events drop constraint if exists bj_events_max_players_check;
alter table public.bj_events add constraint bj_events_max_players_check check (max_players between 4 and 40);

-- Name und Spiel-Kennung für die Buchungen
create or replace function public.bj_label(p_event uuid)
returns text
language sql
stable
security definer
set search_path = public
as $$ select case when s.game = 'slots' then 'Slot-Turnier' else 'Black-Jack-Turnier' end
      from public.bj_events e join public.bj_series s on s.id = e.series_id where e.id = p_event $$;

create or replace function public.bj_game_key(p_event uuid)
returns text
language sql
stable
security definer
set search_path = public
as $$ select case when s.game = 'slots' then 'world:slot-tour' else 'world:blackjack-tour' end
      from public.bj_events e join public.bj_series s on s.id = e.series_id where e.id = p_event $$;
revoke all on function public.bj_label(uuid) from public, anon, authenticated;
revoke all on function public.bj_game_key(uuid) from public, anon, authenticated;

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
    v_chips := coin_book(p_login, 'purchase', -v_event.buy_in, public.bj_game_key(p_event), null, public.bj_label(p_event) || ': Startgeld Vorrunde ' || v_event.round);
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
    v_chips := coin_book(p_login, 'purchase', v_entry.paid, public.bj_game_key(p_event), null, public.bj_label(p_event) || ': Abmeldung');
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
      perform coin_book(v_entry.twitch_login, 'purchase', v_entry.paid, public.bj_game_key(p_event), null, public.bj_label(p_event) || ' abgesagt: Rückerstattung');
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
      perform coin_book(v_login, 'win', v_prize, public.bj_game_key(p_event), null,
        case when v_event.round = 5 then public.bj_label(p_event) || ' Finale: Platz ' else public.bj_label(p_event) || ' Vorrunde ' || v_event.round || ': Platz ' end || v_place);
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
