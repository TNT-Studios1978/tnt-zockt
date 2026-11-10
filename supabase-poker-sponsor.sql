-- Poker-Wochenturnier: TNT-Zockt sponsert jedem Turnier 2000 Coins für den Preispool.
-- Im Supabase SQL Editor ausführen. Mehrfaches Ausführen schadet nicht.
--
-- Neue Spalte sponsor (Standard 2000) für jeden Termin, auch für bereits geplante.
-- Beim Start rechnet world_poker_start den Sponsor zum Pool der Startgelder dazu.
-- Fällt ein Turnier aus, gibt es wie bisher nur die Startgelder zurück, der Sponsor verfällt.

alter table public.poker_events add column if not exists sponsor integer not null default 2000;
alter table public.poker_events drop constraint if exists poker_events_sponsor_check;
alter table public.poker_events add constraint poker_events_sponsor_check check (sponsor between 0 and 100000);

create or replace function public.world_poker_start(p_event uuid, p_present text[])
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_pool integer;
  v_sponsor integer;
begin
  select sponsor into v_sponsor from public.poker_events where id = p_event and status = 'scheduled' for update;
  if not found then raise exception 'poker_closed'; end if;
  update public.poker_entries set status = case when twitch_login = any(p_present) then 'playing' else 'forfeit' end
    where event_id = p_event and status = 'registered';
  select coalesce(sum(paid), 0) + coalesce(v_sponsor, 0) into v_pool
    from public.poker_entries where event_id = p_event and status in ('playing', 'forfeit');
  update public.poker_events set status = 'running', pool = v_pool where id = p_event;
  return v_pool;
end;
$$;
revoke all on function public.world_poker_start(uuid, text[]) from public, anon, authenticated;
grant execute on function public.world_poker_start(uuid, text[]) to vw_server;
