-- ============================================================
-- TNT-ZOCKT — Supabase Datenbank-Setup
-- Führe dieses komplette Skript im Supabase SQL-Editor aus:
-- Dashboard -> SQL Editor -> New query -> einfügen -> Run
-- ============================================================

-- ============================================================
-- Migration: Logo-URL-Spalte hinzufügen (falls Tabellen bereits bestehen)
-- Kannst du separat ausführen, falls du das Schema schon einmal
-- komplett ausgeführt hattest.
-- ============================================================
alter table casinos add column if not exists logo_url text;

-- Tabellen anlegen
create table if not exists casinos (
  id text primary key,
  name text not null,
  badge text,
  logo_url text,
  stars int default 3,
  bonus text,
  link text,
  sort_order int default 0
);

create table if not exists streamers (
  id text primary key,
  name text not null,
  platform text,
  link text,
  sort_order int default 0
);

create table if not exists products (
  id text primary key,
  name text not null,
  description text,
  price text,
  icon text,
  sort_order int default 0
);

-- Row Level Security aktivieren
alter table casinos enable row level security;
alter table streamers enable row level security;
alter table products enable row level security;

-- Bestehende Policies entfernen (falls Skript schon einmal gelaufen ist)
drop policy if exists "Public read casinos" on casinos;
drop policy if exists "Public write casinos" on casinos;
drop policy if exists "Public read streamers" on streamers;
drop policy if exists "Public write streamers" on streamers;
drop policy if exists "Public read products" on products;
drop policy if exists "Public write products" on products;

-- Öffentlicher Lese- UND Schreibzugriff (siehe Sicherheitshinweis im Chat)
create policy "Public read casinos" on casinos for select using (true);
create policy "Public write casinos" on casinos for all using (true) with check (true);

create policy "Public read streamers" on streamers for select using (true);
create policy "Public write streamers" on streamers for all using (true) with check (true);

create policy "Public read products" on products for select using (true);
create policy "Public write products" on products for all using (true) with check (true);

-- Startdaten einfügen (deine aktuellen Inhalte)
insert into casinos (id, name, badge, stars, bonus, link, sort_order) values
('fugu',     'Fugu Casino',     'FG', 5, '250% auf 1. Einzahlung · bis zu 500 FS',         'https://fuguway68.com/c36a788da', 1),
('beef',     'Beef Casino',     'BF', 5, '250% auf 1. Einzahlung · bis zu 600 FS',         'https://beefway66.com/c25d35ccd', 2),
('martin',   'MARTIN Casino',   'MC', 4, '225% auf 1. Einzahlung · bis zu 600 FS',         'https://martinway62.com/cefa654e9', 3),
('bitkingz', 'Bitkingz Casino', 'BK', 4, 'Willkommenspaket · 150% Bonus & 150 FS',         'https://www.bitkingzmedia.com/awfzlngxa', 4),
('24casino', '24Casino',        '24', 4, 'Willkommenspaket · 100% Bonus & bis zu 350 FS', 'https://www.24casino.live/juq9lp4lr', 5),
('posido',   'POSIDO Casino',   'PS', 3, 'Willkommenspaket · Bonus & Freispiele',          'https://psdcur.lynmonkel.com/?mid=378693_2197251', 6),
('drgnn',    'Drgnn Casino',    'DR', 3, 'Willkommenspaket · Bonus & Freispiele',          'https://drgnn.lynmonkel.com/?mid=378693_2197249', 7)
on conflict (id) do nothing;

insert into streamers (id, name, platform, link, sort_order) values
('twitch-tnt',     'TNT-Studios.ch',    'twitch',  'https://twitch.tv/tnt_studiosch', 1),
('youtube-tnt',    'SuperTNTstudios',   'youtube', 'https://www.youtube.com/@SuperTNTstudios', 2),
('twitch-guest-1', '[Gast-Streamer 1]', 'twitch',  '#', 3),
('kick-guest-1',   '[Gast-Streamer 2]', 'kick',    '#', 4)
on conflict (id) do nothing;

insert into products (id, name, description, price, icon, sort_order) values
('p1', '[T-Shirt Name]',    '[Kurzbeschreibung: Material, Passform, Print]',        '[--,-- CHF]', 'shirt',  1),
('p2', '[Cap Name]',        '[Kurzbeschreibung: Material, Verstellbar, Stickerei]', '[--,-- CHF]', 'cap',    2),
('p3', '[Hoodie Name]',     '[Kurzbeschreibung: Material, Passform, Print]',        '[--,-- CHF]', 'hoodie', 3),
('p4', '[Accessoire Name]', '[z. B. Tasse, Sticker-Set, Lanyard]',                  '[--,-- CHF]', 'mug',    4)
on conflict (id) do nothing;
