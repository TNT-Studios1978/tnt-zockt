-- ============================================================
-- TNT-ZOCKT — Minecraft-Shop: Verknüpfung Minecraft <-> Twitch,
-- Pakete (Kits) und Käufe mit StreamElements-Kanalpunkten.
--
-- Einmalig im Supabase SQL-Editor ausführen. Kann gefahrlos
-- mehrfach ausgeführt werden.
--
-- Zugriff nur über den Server (Vercel /api/mc mit service_role).
-- ============================================================

-- Verknüpfte Konten (ein Minecraft-Konto <-> ein Twitch-Konto)
create table if not exists mc_links (
  mc_uuid uuid primary key,
  mc_name text not null,
  twitch_login text not null unique,
  linked_at timestamptz not null default now()
);

-- Einmal-Codes für /link (10 Minuten gültig)
create table if not exists mc_link_codes (
  code text primary key,
  mc_uuid uuid not null,
  mc_name text not null,
  expires_at timestamptz not null
);
create index if not exists mc_link_codes_uuid on mc_link_codes (mc_uuid);

-- Pakete. items = JSON-Liste: [{"material":"STONE_PICKAXE","amount":1}, ...]
create table if not exists mc_kits (
  id text primary key,
  name text not null,
  description text,
  icon text not null default 'CHEST',          -- Minecraft-Material für das Menü
  price_points int not null default 0 check (price_points >= 0),
  cooldown_hours int not null default 24 check (cooldown_hours >= 0),
  once_only boolean not null default false,
  enabled boolean not null default true,
  items jsonb not null default '[]',
  sort_order int not null default 0
);

-- Käufe (Protokoll)
create table if not exists mc_purchases (
  id bigserial primary key,
  mc_uuid uuid not null,
  mc_name text not null,
  twitch_login text not null,
  kit_id text not null,
  points int not null,
  status text not null check (status in ('reserved','delivered','refunded')),
  note text,
  created_at timestamptz not null default now()
);
create index if not exists mc_purchases_user_kit on mc_purchases (mc_uuid, kit_id, created_at desc);

alter table mc_links      enable row level security;
alter table mc_link_codes enable row level security;
alter table mc_kits       enable row level security;
alter table mc_purchases  enable row level security;

-- Startpakete (nur wenn noch keine vorhanden sind)
insert into mc_kits (id, name, description, icon, price_points, cooldown_hours, once_only, items, sort_order)
select * from (values
  ('stein', 'Stein-Paket', 'Steinwerkzeuge, Lederrüstung und Essen', 'STONE_PICKAXE', 500, 24, false,
   '[{"material":"STONE_PICKAXE","amount":1},{"material":"STONE_AXE","amount":1},{"material":"STONE_SHOVEL","amount":1},{"material":"STONE_SWORD","amount":1},{"material":"LEATHER_HELMET","amount":1},{"material":"LEATHER_CHESTPLATE","amount":1},{"material":"LEATHER_LEGGINGS","amount":1},{"material":"LEATHER_BOOTS","amount":1},{"material":"COOKED_BEEF","amount":16}]'::jsonb, 1),
  ('eisen', 'Eisen-Paket', 'Eisenwerkzeuge, Eisenrüstung und ein Bett', 'IRON_PICKAXE', 2000, 24, false,
   '[{"material":"IRON_PICKAXE","amount":1},{"material":"IRON_AXE","amount":1},{"material":"IRON_SHOVEL","amount":1},{"material":"IRON_SWORD","amount":1},{"material":"IRON_HELMET","amount":1},{"material":"IRON_CHESTPLATE","amount":1},{"material":"IRON_LEGGINGS","amount":1},{"material":"IRON_BOOTS","amount":1},{"material":"RED_BED","amount":1},{"material":"COOKED_BEEF","amount":32}]'::jsonb, 2),
  ('diamant', 'Diamant-Spitzhacke', 'Eine Diamant-Spitzhacke', 'DIAMOND_PICKAXE', 10000, 72, false,
   '[{"material":"DIAMOND_PICKAXE","amount":1}]'::jsonb, 3)
) as v(id, name, description, icon, price_points, cooldown_hours, once_only, items, sort_order)
where not exists (select 1 from mc_kits);

-- ---------- Funktionen ----------

-- Code für /link erzeugen (alte Codes des Spielers werden ersetzt)
create or replace function mc_create_link_code(p_uuid uuid, p_name text)
returns text language plpgsql security definer set search_path = public as $$
declare v_code text; i int := 0;
begin
  delete from mc_link_codes where mc_uuid = p_uuid or expires_at < now();
  loop
    -- 6 Zeichen aus einem Alphabet ohne verwechselbare Zeichen (0/O, 1/I/L)
    select string_agg(substr('ABCDEFGHJKMNPQRSTUVWXYZ23456789',
             1 + (get_byte(decode(md5(gen_random_uuid()::text || n::text), 'hex'), 0) % 31), 1), '')
      into v_code from generate_series(1, 6) n;
    exit when length(v_code) = 6 and not exists (select 1 from mc_link_codes where code = v_code);
    i := i + 1;
    if i > 20 then raise exception 'code_generation_failed'; end if;
  end loop;
  insert into mc_link_codes (code, mc_uuid, mc_name, expires_at)
    values (v_code, p_uuid, p_name, now() + interval '10 minutes');
  return v_code;
end $$;

-- Code einlösen (vom eingeloggten Twitch-Nutzer auf der Website)
create or replace function mc_confirm_link(p_code text, p_login text)
returns json language plpgsql security definer set search_path = public as $$
declare c mc_link_codes%rowtype;
begin
  select * into c from mc_link_codes where code = upper(trim(p_code)) for update;
  if not found or c.expires_at < now() then raise exception 'invalid_code'; end if;
  delete from mc_link_codes where code = c.code;
  -- bestehende Verknüpfungen dieses Minecraft- oder Twitch-Kontos ersetzen
  delete from mc_links where mc_uuid = c.mc_uuid or twitch_login = p_login;
  insert into mc_links (mc_uuid, mc_name, twitch_login) values (c.mc_uuid, c.mc_name, p_login);
  return json_build_object('mc_name', c.mc_name, 'twitch_login', p_login);
end $$;

-- Prüft, ob ein Paket gekauft werden darf, und reserviert den Kauf.
-- Gibt die Kauf-ID zurück. Danach werden die Punkte abgebucht und der
-- Kauf mit mc_finish_purchase abgeschlossen.
create or replace function mc_reserve_purchase(p_uuid uuid, p_kit text)
returns json language plpgsql security definer set search_path = public as $$
declare l mc_links%rowtype; k mc_kits%rowtype; v_last timestamptz; v_id bigint;
begin
  select * into l from mc_links where mc_uuid = p_uuid;
  if not found then raise exception 'not_linked'; end if;
  select * into k from mc_kits where id = p_kit and enabled;
  if not found then raise exception 'kit_not_found'; end if;
  -- gleichzeitige Käufe desselben Spielers verhindern
  perform pg_advisory_xact_lock(hashtext(p_uuid::text));
  if k.once_only and exists (select 1 from mc_purchases where mc_uuid = p_uuid and kit_id = k.id and status <> 'refunded') then
    raise exception 'already_bought';
  end if;
  select max(created_at) into v_last from mc_purchases
    where mc_uuid = p_uuid and kit_id = k.id and status <> 'refunded';
  if v_last is not null and k.cooldown_hours > 0 and v_last > now() - make_interval(hours => k.cooldown_hours) then
    raise exception 'cooldown:%', ceil(extract(epoch from (v_last + make_interval(hours => k.cooldown_hours) - now())) / 60);
  end if;
  insert into mc_purchases (mc_uuid, mc_name, twitch_login, kit_id, points, status)
    values (p_uuid, l.mc_name, l.twitch_login, k.id, k.price_points, 'reserved') returning id into v_id;
  return json_build_object('purchase_id', v_id, 'twitch_login', l.twitch_login, 'points', k.price_points,
                           'items', k.items, 'name', k.name);
end $$;

create or replace function mc_finish_purchase(p_id bigint, p_status text, p_note text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if p_status not in ('delivered','refunded') then raise exception 'invalid_status'; end if;
  update mc_purchases set status = p_status, note = coalesce(p_note, note)
    where id = p_id and status = 'reserved';
  if not found then raise exception 'not_reserved'; end if;
end $$;

do $$
declare f text;
begin
  foreach f in array array['mc_create_link_code(uuid,text)','mc_confirm_link(text,text)',
                           'mc_reserve_purchase(uuid,text)','mc_finish_purchase(bigint,text,text)']
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
