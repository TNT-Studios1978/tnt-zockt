-- Neue Boutique-Artikel für die Virtual World: eigene Kostüme, Weihnachten und schwarze Engelsflügel.
-- Die IDs müssen zu SHOP_ITEMS im Code der Welt passen (packages/shared/src/avatar.ts).
-- Preise in CHF nach Wunsch anpassen (1 bis 100). Einmal ausführen im Supabase SQL-Editor.

insert into public.world_items (id, kind, name, description, price_chf, sort_order) values
  ('skin.webrunner',   'skin',      'Netzläufer',      'Blauer Anzug mit leuchtendem Gitter',                 6.00, 8),
  ('skin.nightfalcon', 'skin',      'Nachtfalke',      'Dunkler Heldenanzug mit Umhang und Silber-Emblem',    6.00, 9),
  ('skin.stormguard',  'skin',      'Sturmwächter',    'Weiss-blauer Heldenanzug mit goldenem Umhang',        6.00, 10),
  ('skin.shadowcat',   'skin',      'Schattenkatze',   'Glänzender schwarzer Anzug mit Katzenohren',          5.00, 11),
  ('skin.elf',         'skin',      'Elfe',            'Grüne Elfentracht mit spitzen Ohren',                 5.00, 12),
  ('skin.xmaself',     'skin',      'Weihnachtself',   'Grün-rotes Elfenkostüm mit Ringelhose und Mütze',     5.00, 13),
  ('skin.santa',       'skin',      'Weihnachtsmann',  'Roter Mantel mit Pelz, Gürtel, Mütze und weissem Bart', 7.00, 14),
  ('acc.wingsdark',    'accessory', 'Schwarze Flügel', 'Schwarze Engelsflügel aus einzelnen Federn',          4.00, 15)
on conflict (id) do nothing;

-- Die bisherigen Flügel sind jetzt weisse Engelsflügel
update public.world_items
set name = 'Weisse Flügel', description = 'Weisse Engelsflügel aus einzelnen Federn'
where id = 'acc.wings';
