-- Wochenturnier Poker für die TNT-Zockt Virtual World.
-- Im Supabase SQL Editor des Website-Projekts ausführen. Mehrfaches Ausführen schadet nicht.
--
--   * public.poker_events   Termine, geplant im Admin-Bereich (Reiter "Poker-Turnier")
--   * public.poker_entries  Anmeldungen; jede Zeile ist ein bezahltes Startgeld
--   * world_poker_*         Anmelden, Abmelden, Start, Absage und Auszahlung, nur für den Spielserver (vw_server)
--   * admin_poker_cancel    Absage aus dem Admin-Bereich, erstattet allen das Startgeld
--
-- Startgeld und Rückerstattung laufen als Buchungsart 'purchase', Preise als 'win' (Spiel 'world:poker').

begin;

-- ---------- Termine ----------
create table if not exists public.poker_events (
  id uuid primary key default gen_random_uuid(),
  starts_at timestamptz not null,
  buy_in integer not null default 500 check (buy_in between 1 and 100000),
  max_players integer not null default 27 check (max_players between 4 and 27),
  status text not null default 'scheduled' check (status in ('scheduled', 'running', 'finished', 'cancelled')),
  pool integer not null default 0,
  results jsonb,
  created_at timestamptz not null default now(),
  finished_at timestamptz
);
create index if not exists poker_events_next on public.poker_events (status, starts_at);
alter table public.poker_events enable row level security;

-- Termine darf jeder sehen (auch die Website), planen nur der Admin.
drop policy if exists "Poker-Termine lesen" on public.poker_events;
create policy "Poker-Termine lesen" on public.poker_events for select using (true);
drop policy if exists "Poker-Termine Admin" on public.poker_events;
create policy "Poker-Termine Admin" on public.poker_events for insert to authenticated
  with check (lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch' and status = 'scheduled');
drop policy if exists "Poker-Termine Admin ändern" on public.poker_events;
create policy "Poker-Termine Admin ändern" on public.poker_events for update to authenticated
  using (lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch' and status = 'scheduled')
  with check (lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch' and status = 'scheduled');
grant select, update on public.poker_events to vw_server;
drop policy if exists "Poker-Termine Spielserver" on public.poker_events;
create policy "Poker-Termine Spielserver" on public.poker_events for all to vw_server using (true) with check (true);

-- ---------- Anmeldungen ----------
create table if not exists public.poker_entries (
  event_id uuid not null references public.poker_events(id) on delete cascade,
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
alter table public.poker_entries enable row level security;
drop policy if exists "Poker-Anmeldungen Admin" on public.poker_entries;
create policy "Poker-Anmeldungen Admin" on public.poker_entries for select to authenticated
  using (lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch');
grant select on public.poker_entries to vw_server;
drop policy if exists "Poker-Anmeldungen Spielserver" on public.poker_entries;
create policy "Poker-Anmeldungen Spielserver" on public.poker_entries for select to vw_server using (true);

-- Termin löschen nur, solange niemand bezahlt hat (sonst absagen, das erstattet)
drop policy if exists "Poker-Termine Admin löschen" on public.poker_events;
create policy "Poker-Termine Admin löschen" on public.poker_events for delete to authenticated
  using (
    lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch'
    and not exists (select 1 from public.poker_entries e where e.event_id = poker_events.id and e.status in ('registered', 'playing', 'forfeit'))
  );

-- ---------- Anmelden: Startgeld abbuchen, Platz reservieren ----------
-- Rückgabe: neuer Saldo, oder null wenn das Guthaben nicht reicht.
create or replace function public.world_poker_register(p_event uuid, p_login text, p_user text, p_name text)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_event public.poker_events%rowtype;
  v_chips integer;
  v_count integer;
begin
  if p_login is null or p_login !~ '^[a-z0-9_]{1,25}$' then raise exception 'invalid_login'; end if;
  select * into v_event from public.poker_events where id = p_event for update;
  if not found or v_event.status <> 'scheduled' or v_event.starts_at <= now() then raise exception 'poker_closed'; end if;
  if exists (select 1 from public.poker_entries where event_id = p_event and twitch_login = p_login and status = 'registered') then
    raise exception 'poker_already';
  end if;
  select count(*) into v_count from public.poker_entries where event_id = p_event and status = 'registered';
  if v_count >= v_event.max_players then raise exception 'poker_full'; end if;
  v_chips := coin_lock_wallet(p_login);
  if v_chips < v_event.buy_in then return null; end if;
  v_chips := coin_book(p_login, 'purchase', -v_event.buy_in, 'world:poker', null, 'Poker-Turnier: Startgeld');
  insert into public.poker_entries (event_id, twitch_login, user_id, name, paid, status)
    values (p_event, p_login, left(p_user, 100), left(coalesce(p_name, ''), 40), v_event.buy_in, 'registered')
    on conflict (event_id, twitch_login) do update
      set user_id = excluded.user_id, name = excluded.name, paid = excluded.paid, status = 'registered', place = null, prize = 0, created_at = now();
  return v_chips;
end;
$$;

-- ---------- Abmelden bis zum Start: Startgeld zurück ----------
create or replace function public.world_poker_unregister(p_event uuid, p_login text)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_event public.poker_events%rowtype;
  v_entry public.poker_entries%rowtype;
  v_chips integer;
begin
  select * into v_event from public.poker_events where id = p_event for update;
  if not found or v_event.status <> 'scheduled' or v_event.starts_at <= now() then raise exception 'poker_closed'; end if;
  select * into v_entry from public.poker_entries where event_id = p_event and twitch_login = p_login and status = 'registered' for update;
  if not found then return null; end if;
  perform coin_lock_wallet(p_login);
  v_chips := coin_book(p_login, 'purchase', v_entry.paid, 'world:poker', null, 'Poker-Turnier: Abmeldung');
  update public.poker_entries set status = 'refunded' where event_id = p_event and twitch_login = p_login;
  return v_chips;
end;
$$;

-- ---------- Start: Anwesende spielen, Abwesende verlieren ihr Startgeld (bleibt im Pool) ----------
-- Rückgabe: Preispool (alle bezahlten Startgelder).
create or replace function public.world_poker_start(p_event uuid, p_present text[])
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_pool integer;
begin
  perform 1 from public.poker_events where id = p_event and status = 'scheduled' for update;
  if not found then raise exception 'poker_closed'; end if;
  update public.poker_entries set status = case when twitch_login = any(p_present) then 'playing' else 'forfeit' end
    where event_id = p_event and status = 'registered';
  select coalesce(sum(paid), 0) into v_pool from public.poker_entries where event_id = p_event and status in ('playing', 'forfeit');
  update public.poker_events set status = 'running', pool = v_pool where id = p_event;
  return v_pool;
end;
$$;

-- ---------- Absage: alle bekommen ihr Startgeld zurück ----------
-- Zu wenige Anwesende, Neustart mitten im Turnier oder Absage durch den Admin.
create or replace function public.poker_refund_all(p_event uuid)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_entry record;
  v_count integer := 0;
begin
  perform 1 from public.poker_events where id = p_event and status in ('scheduled', 'running') for update;
  if not found then return 0; end if;
  for v_entry in select * from public.poker_entries where event_id = p_event and status in ('registered', 'playing', 'forfeit') for update loop
    perform coin_lock_wallet(v_entry.twitch_login);
    perform coin_book(v_entry.twitch_login, 'purchase', v_entry.paid, 'world:poker', null, 'Poker-Turnier abgesagt: Rückerstattung');
    update public.poker_entries set status = 'refunded' where event_id = p_event and twitch_login = v_entry.twitch_login;
    v_count := v_count + 1;
  end loop;
  update public.poker_events set status = 'cancelled', finished_at = now() where id = p_event;
  return v_count;
end;
$$;

create or replace function public.world_poker_cancel(p_event uuid)
returns integer
language sql
security definer
set search_path = public
as $$ select public.poker_refund_all(p_event); $$;

-- ---------- Ende: Preise auszahlen ----------
-- p_results: [{ "login": "...", "place": 1, "prize": 1234 }, ...]; die Summe darf den Pool nicht übersteigen.
create or replace function public.world_poker_finish(p_event uuid, p_results jsonb)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_event public.poker_events%rowtype;
  v_item jsonb;
  v_total integer := 0;
  v_login text;
  v_prize integer;
begin
  select * into v_event from public.poker_events where id = p_event and status = 'running' for update;
  if not found then raise exception 'poker_not_running'; end if;
  select coalesce(sum((item ->> 'prize')::integer), 0) into v_total from jsonb_array_elements(p_results) as item;
  if v_total > v_event.pool then raise exception 'poker_prizes_exceed_pool'; end if;
  for v_item in select * from jsonb_array_elements(p_results) loop
    v_login := v_item ->> 'login';
    v_prize := coalesce((v_item ->> 'prize')::integer, 0);
    update public.poker_entries set status = 'finished', place = (v_item ->> 'place')::integer, prize = v_prize
      where event_id = p_event and twitch_login = v_login and status = 'playing';
    if found and v_prize > 0 then
      perform coin_lock_wallet(v_login);
      perform coin_book(v_login, 'win', v_prize, 'world:poker', null, 'Poker-Turnier: Platz ' || (v_item ->> 'place'));
    end if;
  end loop;
  update public.poker_events set status = 'finished', finished_at = now(), results = p_results where id = p_event;
  return v_total;
end;
$$;

-- ---------- Absage aus dem Admin-Bereich ----------
create or replace function public.admin_poker_cancel(p_event uuid)
returns integer
language plpgsql
security definer
set search_path = public
as $$
begin
  if lower(coalesce(auth.jwt() ->> 'email', '')) <> 'tnt-studios@gmx.ch' then raise exception 'not_admin'; end if;
  if exists (select 1 from public.poker_events where id = p_event and status = 'running') then raise exception 'poker_running'; end if;
  return public.poker_refund_all(p_event);
end;
$$;

revoke all on function public.world_poker_register(uuid, text, text, text) from public, anon, authenticated;
revoke all on function public.world_poker_unregister(uuid, text) from public, anon, authenticated;
revoke all on function public.world_poker_start(uuid, text[]) from public, anon, authenticated;
revoke all on function public.world_poker_cancel(uuid) from public, anon, authenticated;
revoke all on function public.world_poker_finish(uuid, jsonb) from public, anon, authenticated;
revoke all on function public.poker_refund_all(uuid) from public, anon, authenticated;
revoke all on function public.admin_poker_cancel(uuid) from public, anon;
grant execute on function public.world_poker_register(uuid, text, text, text) to vw_server;
grant execute on function public.world_poker_unregister(uuid, text) to vw_server;
grant execute on function public.world_poker_start(uuid, text[]) to vw_server;
grant execute on function public.world_poker_cancel(uuid) to vw_server;
grant execute on function public.world_poker_finish(uuid, jsonb) to vw_server;
grant execute on function public.admin_poker_cancel(uuid) to authenticated;

commit;
