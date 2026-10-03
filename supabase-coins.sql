-- ============================================================
-- TNT-ZOCKT — TNT-Coins (Spielwährung) mit Spielverlauf und
-- kontrollierter Rückbuchung in StreamElements-Kanalpunkte.
--
-- Einmalig im Supabase SQL-Editor ausführen:
-- Dashboard -> SQL Editor -> New query -> einfügen -> Run
--
-- Grundprinzip:
--   * Alle Coin-Buchungen laufen über Serverfunktionen (Vercel /api/coins)
--     mit dem geheimen service_role-Schlüssel. Der Browser kann Coins
--     NICHT mehr direkt in der Datenbank verändern.
--   * Jede Buchung landet im Spielverlauf (coin_ledger). Der Saldo in
--     user_chips muss immer der Summe des Verlaufs entsprechen.
--   * Bestehende Guthaben aus user_chips werden übernommen und beim
--     ersten Kontakt als "opening_balance" im Verlauf eingetragen.
-- ============================================================

-- ---------- Bestehende Tabelle absichern ----------
create table if not exists user_chips (
  twitch_login text primary key,
  chips integer not null default 1000,
  updated_at timestamptz not null default now()
);
alter table user_chips enable row level security;
drop policy if exists "Public write chips" on user_chips;   -- niemand mehr direkt schreiben
drop policy if exists "Public read chips" on user_chips;
create policy "Public read chips" on user_chips for select using (true);

-- ---------- Admins ----------
-- Wer hier eingetragen ist, darf im Admin-Bereich Auszahlungen freigeben.
-- Weitere Admins eintragen (E-Mail anpassen):
--   insert into admin_users (user_id) select id from auth.users where email = 'weitere@email.ch';
create table if not exists admin_users (
  user_id uuid primary key,
  created_at timestamptz not null default now()
);
-- Admin-Konto aus admin.html (ADMIN_EMAIL) eintragen:
do $$
begin
  if exists (select 1 from information_schema.tables where table_schema = 'auth' and table_name = 'users') then
    insert into admin_users (user_id)
      select id from auth.users where lower(email) = 'tnt-studios@gmx.ch'
      on conflict (user_id) do nothing;
  end if;
end $$;

-- ---------- Einstellungen (eine Zeile) ----------
create table if not exists coin_settings (
  id int primary key default 1 check (id = 1),
  points_to_coins numeric not null default 1,      -- 1 Kanalpunkt = x Coins
  coins_to_points numeric not null default 1,      -- 1 Coin = x Kanalpunkte
  daily_auto_limit_points int not null default 5000,
  min_account_age_days int not null default 7,
  max_rtp numeric not null default 1.25,           -- Auszahlungsquote, ab der markiert wird
  rtp_min_bets int not null default 2000,          -- erst ab so viel Einsatz bewerten
  max_rounds_per_10min int not null default 150,   -- Hinweis auf Skripte
  min_payout_coins int not null default 100,
  auto_payout_enabled boolean not null default true,   -- Not-Aus
  updated_at timestamptz not null default now()
);
insert into coin_settings (id) values (1) on conflict (id) do nothing;

-- ---------- Spielrunden ----------
create table if not exists coin_rounds (
  id bigserial primary key,
  twitch_login text not null,
  game text not null,
  stake int not null default 0,
  won int not null default 0,
  created_at timestamptz not null default now()
);
create index if not exists coin_rounds_user_game on coin_rounds (twitch_login, game, id desc);
create index if not exists coin_rounds_user_time on coin_rounds (twitch_login, created_at);

-- ---------- Spielverlauf (jede Buchung) ----------
create table if not exists coin_ledger (
  id bigserial primary key,
  twitch_login text not null,
  kind text not null check (kind in (
    'start','opening_balance','exchange_in','bet','win',
    'payout_reserved','payout_refund','admin_adjust')),
  amount int not null,            -- Veränderung der Coins (+/-)
  balance_after int not null,
  game text,
  round_id bigint references coin_rounds(id),
  note text,
  created_at timestamptz not null default now()
);
create index if not exists coin_ledger_user_time on coin_ledger (twitch_login, created_at desc);

-- ---------- Auszahlungsanträge ----------
create table if not exists payout_requests (
  id bigserial primary key,
  twitch_login text not null,
  coins int not null,
  points int not null,
  status text not null check (status in ('pending','processing','approved','approved_auto','rejected','failed')),
  reasons text[] not null default '{}',
  error text,
  decided_by text,
  created_at timestamptz not null default now(),
  decided_at timestamptz
);
create index if not exists payout_requests_status on payout_requests (status, created_at);
create index if not exists payout_requests_user on payout_requests (twitch_login, created_at desc);

-- Neue Tabellen: RLS an, KEINE öffentlichen Policies -> nur der Server (service_role) hat Zugriff.
alter table admin_users     enable row level security;
alter table coin_settings   enable row level security;
alter table coin_rounds     enable row level security;
alter table coin_ledger     enable row level security;
alter table payout_requests enable row level security;

-- ============================================================
-- Funktionen (laufen als Besitzer, nur für service_role ausführbar)
-- ============================================================

-- Konto sicherstellen und sperren. Gibt den aktuellen Saldo zurück.
create or replace function coin_lock_wallet(p_login text)
returns int language plpgsql security definer set search_path = public as $$
declare v_chips int; v_has_ledger boolean;
begin
  select chips into v_chips from user_chips where twitch_login = p_login for update;
  if not found then
    insert into user_chips (twitch_login, chips) values (p_login, 1000)
      on conflict (twitch_login) do nothing;
    select chips into v_chips from user_chips where twitch_login = p_login for update;
    insert into coin_ledger (twitch_login, kind, amount, balance_after, note)
      values (p_login, 'start', v_chips, v_chips, 'Startguthaben');
    return v_chips;
  end if;
  select exists(select 1 from coin_ledger where twitch_login = p_login) into v_has_ledger;
  if not v_has_ledger then
    insert into coin_ledger (twitch_login, kind, amount, balance_after, note)
      values (p_login, 'opening_balance', v_chips, v_chips, 'Übernommenes Guthaben aus user_chips');
  end if;
  return v_chips;
end $$;

-- Interne Buchung auf gesperrtem Konto.
create or replace function coin_book(p_login text, p_kind text, p_amount int, p_game text, p_round bigint, p_note text)
returns int language plpgsql security definer set search_path = public as $$
declare v_chips int;
begin
  update user_chips set chips = chips + p_amount, updated_at = now()
    where twitch_login = p_login returning chips into v_chips;
  if v_chips < 0 then raise exception 'insufficient_balance'; end if;
  insert into coin_ledger (twitch_login, kind, amount, balance_after, game, round_id, note)
    values (p_login, p_kind, p_amount, v_chips, p_game, p_round, p_note);
  return v_chips;
end $$;

-- Saldo abfragen (legt Konto bei Bedarf an).
create or replace function coin_balance(p_login text)
returns int language plpgsql security definer set search_path = public as $$
begin
  return coin_lock_wallet(p_login);
end $$;

-- Einsatz. p_raise = true erhöht den Einsatz der laufenden Runde (z. B. Double Down).
create or replace function coin_bet(p_login text, p_game text, p_amount int, p_raise boolean)
returns json language plpgsql security definer set search_path = public as $$
declare v_chips int; v_round bigint;
begin
  if p_amount is null or p_amount <= 0 then raise exception 'invalid_amount'; end if;
  v_chips := coin_lock_wallet(p_login);
  if v_chips < p_amount then raise exception 'insufficient_balance'; end if;
  if p_raise then
    select id into v_round from coin_rounds
      where twitch_login = p_login and game = p_game and created_at > now() - interval '30 minutes'
      order by id desc limit 1;
    if v_round is null then raise exception 'no_open_round'; end if;
    update coin_rounds set stake = stake + p_amount where id = v_round;
  else
    insert into coin_rounds (twitch_login, game, stake) values (p_login, p_game, p_amount)
      returning id into v_round;
  end if;
  v_chips := coin_book(p_login, 'bet', -p_amount, p_game, v_round, null);
  return json_build_object('balance', v_chips, 'round_id', v_round);
end $$;

-- Gewinn auf die laufende Runde. Gesamtgewinn darf Einsatz × Höchstmultiplikator nicht übersteigen.
create or replace function coin_win(p_login text, p_game text, p_amount int, p_max_mult numeric)
returns json language plpgsql security definer set search_path = public as $$
declare v_chips int; v_round coin_rounds%rowtype;
begin
  if p_amount is null or p_amount <= 0 then raise exception 'invalid_amount'; end if;
  perform coin_lock_wallet(p_login);
  select * into v_round from coin_rounds
    where twitch_login = p_login and game = p_game and created_at > now() - interval '30 minutes'
    order by id desc limit 1 for update;
  if not found then raise exception 'no_open_round'; end if;
  if v_round.won + p_amount > ceil(v_round.stake * p_max_mult) then raise exception 'win_exceeds_limit'; end if;
  update coin_rounds set won = won + p_amount where id = v_round.id;
  v_chips := coin_book(p_login, 'win', p_amount, p_game, v_round.id, null);
  return json_build_object('balance', v_chips, 'round_id', v_round.id);
end $$;

-- Gutschrift nach erfolgreichem Abzug der Kanalpunkte.
create or replace function coin_exchange_in(p_login text, p_coins int, p_points int)
returns int language plpgsql security definer set search_path = public as $$
begin
  if p_coins <= 0 then raise exception 'invalid_amount'; end if;
  perform coin_lock_wallet(p_login);
  return coin_book(p_login, 'exchange_in', p_coins, null, null, p_points || ' Kanalpunkte umgetauscht');
end $$;

-- Automatische Prüfungen. Gibt eine Liste von Gründen zurück (leer = alles ok).
create or replace function coin_payout_checks(p_login text, p_points int)
returns text[] language plpgsql security definer set search_path = public as $$
declare
  s coin_settings%rowtype;
  v_reasons text[] := '{}';
  v_chips int; v_sum bigint; v_first timestamptz;
  v_bets bigint; v_wins bigint; v_today bigint; v_burst int;
begin
  select * into s from coin_settings where id = 1;

  -- 1. Saldo-Abgleich
  select chips into v_chips from user_chips where twitch_login = p_login;
  select coalesce(sum(amount),0), min(created_at) into v_sum, v_first from coin_ledger where twitch_login = p_login;
  if v_chips is distinct from v_sum then
    v_reasons := v_reasons || format('Saldo passt nicht zum Spielverlauf (%s statt %s)', v_chips, v_sum);
  end if;

  -- 2. Kontoalter
  if v_first is null or v_first > now() - make_interval(days => s.min_account_age_days) then
    v_reasons := v_reasons || format('Konto jünger als %s Tage', s.min_account_age_days);
  end if;

  -- 3. Gewinnquote (letzte 30 Tage)
  select coalesce(sum(-amount) filter (where kind = 'bet'),0),
         coalesce(sum(amount)  filter (where kind = 'win'),0)
    into v_bets, v_wins
    from coin_ledger where twitch_login = p_login and created_at > now() - interval '30 days';
  if v_bets >= s.rtp_min_bets and v_wins::numeric / v_bets > s.max_rtp then
    v_reasons := v_reasons || format('Auffällig hohe Gewinnquote: %s %% (Grenze %s %%)',
      round(v_wins::numeric * 100 / v_bets), round(s.max_rtp * 100));
  end if;

  -- 4. Tageslimit (heute bereits ausbezahlt oder in Bearbeitung + dieser Antrag)
  select coalesce(sum(points),0) into v_today from payout_requests
    where twitch_login = p_login and status in ('approved','approved_auto','processing')
      and created_at > date_trunc('day', now());
  if v_today + p_points > s.daily_auto_limit_points then
    v_reasons := v_reasons || format('Tageslimit überschritten (%s + %s > %s Punkte)', v_today, p_points, s.daily_auto_limit_points);
  end if;

  -- 5. Tempo: höchste Rundenzahl in einem 10-Minuten-Fenster der letzten 24 h
  select coalesce(max(cnt),0) into v_burst from (
    select count(*) as cnt from coin_rounds
      where twitch_login = p_login and created_at > now() - interval '24 hours'
      group by date_trunc('hour', created_at), floor(extract(minute from created_at) / 10)
  ) t;
  if v_burst > s.max_rounds_per_10min then
    v_reasons := v_reasons || format('Unmenschlich viele Runden (%s in 10 Minuten)', v_burst);
  end if;

  -- 6. Offene Anträge
  if exists (select 1 from payout_requests where twitch_login = p_login and status = 'pending') then
    v_reasons := v_reasons || 'Es gibt bereits einen offenen Antrag'::text;
  end if;

  return v_reasons;
end $$;

-- Antrag anlegen und Coins reservieren (abziehen).
create or replace function coin_payout_request(p_login text, p_coins int, p_points int, p_status text, p_reasons text[])
returns bigint language plpgsql security definer set search_path = public as $$
declare v_id bigint;
begin
  if p_coins <= 0 or p_points <= 0 then raise exception 'invalid_amount'; end if;
  perform coin_lock_wallet(p_login);
  insert into payout_requests (twitch_login, coins, points, status, reasons)
    values (p_login, p_coins, p_points, p_status, p_reasons) returning id into v_id;
  perform coin_book(p_login, 'payout_reserved', -p_coins, null, null, 'Auszahlungsantrag #' || v_id);
  return v_id;
end $$;

-- Offenen Antrag für die Bearbeitung sperren (verhindert doppelte Freigabe).
create or replace function coin_payout_claim(p_id bigint)
returns json language plpgsql security definer set search_path = public as $$
declare r payout_requests%rowtype;
begin
  update payout_requests set status = 'processing'
    where id = p_id and status = 'pending' returning * into r;
  if not found then raise exception 'not_pending'; end if;
  return row_to_json(r);
end $$;

-- Antrag abschliessen. Bei Ablehnung oder Fehler werden die Coins zurückgebucht.
create or replace function coin_payout_finish(p_id bigint, p_status text, p_by text, p_error text)
returns void language plpgsql security definer set search_path = public as $$
declare r payout_requests%rowtype;
begin
  if p_status not in ('approved','approved_auto','rejected','failed') then raise exception 'invalid_status'; end if;
  select * into r from payout_requests where id = p_id for update;
  if not found then raise exception 'not_found'; end if;
  if r.status not in ('processing','pending') then raise exception 'already_finished'; end if;
  update payout_requests set status = p_status, decided_by = p_by, decided_at = now(), error = p_error where id = p_id;
  if p_status in ('rejected','failed') then
    perform coin_lock_wallet(r.twitch_login);
    perform coin_book(r.twitch_login, 'payout_refund', r.coins, null, null, 'Rückbuchung Antrag #' || p_id);
  end if;
end $$;

-- Manuelle Korrektur durch Admin.
create or replace function coin_admin_adjust(p_login text, p_amount int, p_note text)
returns int language plpgsql security definer set search_path = public as $$
begin
  if p_amount = 0 then raise exception 'invalid_amount'; end if;
  perform coin_lock_wallet(p_login);
  return coin_book(p_login, 'admin_adjust', p_amount, null, null, p_note);
end $$;

-- Nur der Server darf die Funktionen ausführen.
do $$
declare f text;
begin
  foreach f in array array[
    'coin_lock_wallet(text)','coin_book(text,text,int,text,bigint,text)','coin_balance(text)',
    'coin_bet(text,text,int,boolean)','coin_win(text,text,int,numeric)','coin_exchange_in(text,int,int)',
    'coin_payout_checks(text,int)','coin_payout_request(text,int,int,text,text[])','coin_payout_claim(bigint)',
    'coin_payout_finish(bigint,text,text,text)','coin_admin_adjust(text,int,text)']
  loop
    execute format('revoke all on function %s from public', f);
    if exists (select 1 from pg_roles where rolname = 'anon') then
      execute format('revoke all on function %s from anon, authenticated', f);
    end if;
    if exists (select 1 from pg_roles where rolname = 'service_role') then
      execute format('grant execute on function %s to service_role', f);
    end if;
  end loop;
end $$;
