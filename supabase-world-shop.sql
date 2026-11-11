-- Boutique in der TNT-Zockt Virtual World: kosmetische Artikel für Echtgeld (Stripe).
-- Einmal im Supabase SQL Editor des Website-Projekts ausführen. Mehrfaches Ausführen schadet nicht.
--
-- Wichtig: Die Artikel sind rein kosmetisch. Sie geben keine TNT-Coins, keine Kanalpunkte und
-- keinen Vorteil in Spielen. So bleibt die Boutique klar getrennt von den Spielen mit Coins.
--
-- world_items      Artikel mit Preis. Lesen darf jeder (nur aktive), ändern nur der Admin.
-- world_purchases  Käufe. Schreibt nur die Website (api/shop.js mit Service-Schlüssel), lesen nur der Admin.

create table if not exists public.world_items (
  id text primary key,                         -- gleiche ID wie im Code der Welt (SHOP_ITEMS), z.B. acc.wings
  kind text not null check (kind in ('accessory', 'skin')),
  name text not null,
  description text not null default '',
  price_chf numeric(6,2) not null check (price_chf >= 1 and price_chf <= 100),
  active boolean not null default true,
  sort_order int not null default 0,
  created_at timestamptz not null default now()
);

create table if not exists public.world_purchases (
  id bigint generated always as identity primary key,
  sub text not null,                           -- Spieler wie im Welt-Ticket: twitch:<id> oder kick:<id>
  name text not null default '',
  item text not null references public.world_items(id),
  amount_chf numeric(6,2) not null,
  stripe_session text not null unique,
  payment_intent text,
  status text not null default 'paid' check (status in ('paid', 'refunded')),
  created_at timestamptz not null default now()
);
create index if not exists world_purchases_sub on public.world_purchases (sub) where status = 'paid';
create index if not exists world_purchases_intent on public.world_purchases (payment_intent);

alter table public.world_items enable row level security;
alter table public.world_purchases enable row level security;

drop policy if exists "Boutique lesen" on public.world_items;
create policy "Boutique lesen" on public.world_items for select
  using (active or lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch');

drop policy if exists "Boutique Admin" on public.world_items;
create policy "Boutique Admin" on public.world_items for all to authenticated
  using (lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch')
  with check (lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch');

drop policy if exists "Boutique Käufe Admin" on public.world_purchases;
create policy "Boutique Käufe Admin" on public.world_purchases for select to authenticated
  using (lower(coalesce(auth.jwt() ->> 'email', '')) = 'tnt-studios@gmx.ch');

revoke insert, update, delete on public.world_purchases from anon, authenticated;

-- Startsortiment. Preise und Sichtbarkeit lassen sich danach im Admin unter "Boutique" ändern.
insert into public.world_items (id, kind, name, description, price_chf, sort_order) values
  ('acc.tophat',  'accessory', 'Zylinder',      'Schwarzer Zylinder mit Leuchtband',          2.00, 1),
  ('acc.catears', 'accessory', 'Katzenohren',   'Leuchtende Katzenohren',                     2.00, 2),
  ('acc.horns',   'accessory', 'Teufelshörner', 'Glühende Hörner',                            3.00, 3),
  ('acc.wings',   'accessory', 'Flügel',        'Leuchtende Flügel auf dem Rücken',           4.00, 4),
  ('skin.neon',   'skin',      'Neon-Outfit',   'Schwarzer Anzug mit pinkem Neon',            4.00, 5),
  ('skin.lava',   'skin',      'Lava-Outfit',   'Glühender Lava-Anzug mit Feuerring',         5.00, 6),
  ('skin.galaxy', 'skin',      'Galaxie-Outfit','Sternenhimmel-Anzug mit kreisenden Sternen', 6.00, 7)
on conflict (id) do nothing;
