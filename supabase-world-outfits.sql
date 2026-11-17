-- Boutique der Virtual World: echte Kleider (Modelle von Quaternius, CC0) als Kostüme.
-- Die IDs müssen zu SHOP_ITEMS im Code der Welt passen (packages/shared/src/avatar.ts).
-- Preise in CHF nach Wunsch anpassen (1 bis 100). Einmal ausführen im Supabase SQL-Editor.

insert into public.world_items (id, kind, name, description, price_chf, sort_order) values
  ('skin.peasant', 'skin', 'Bauer',      'Leinenhemd, Weste, Hose und Lederstiefel',                       4.00, 16),
  ('skin.ranger',  'skin', 'Waldläufer', 'Kapuze, Lederrüstung mit Schulterschutz, Armschienen und Stiefel', 6.00, 17)
on conflict (id) do nothing;
