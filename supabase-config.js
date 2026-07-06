/* ============================================================
   TNT-ZOCKT — Supabase-Verbindung
   Diese Werte stammen aus deinem Supabase-Projekt
   (Project Settings -> API). Der anon key ist für den
   Browser gedacht und darf öffentlich im Code stehen.
   ============================================================ */

const SUPABASE_URL = "https://wlvgjdzuvfbnberejthz.supabase.co";
const SUPABASE_ANON_KEY = "sb_publishable_2enioy5duKB9ZHMVjimTUw_cTLlcgYO";

const sb = supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY);
