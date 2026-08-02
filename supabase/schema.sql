-- Jiyu Cloud Schema
-- Spusť v: Supabase Dashboard → SQL Editor

-- ── profiles ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS profiles (
  id           UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  username     TEXT UNIQUE,
  display_name TEXT,
  avatar_url   TEXT,
  public_library BOOLEAN DEFAULT FALSE,
  created_at   TIMESTAMPTZ DEFAULT NOW(),
  updated_at   TIMESTAMPTZ DEFAULT NOW()
);

-- Auto-vytvoření profilu při registraci
CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO profiles (id, display_name, avatar_url)
  VALUES (
    NEW.id,
    NEW.raw_user_meta_data->>'full_name',
    NEW.raw_user_meta_data->>'avatar_url'
  );
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION handle_new_user();

ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can view own profile" ON profiles FOR SELECT USING (auth.uid() = id);
CREATE POLICY "Users can update own profile" ON profiles FOR UPDATE USING (auth.uid() = id);

-- ── manga_sync ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS manga_sync (
  id          TEXT NOT NULL,
  user_id     UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  source_id   TEXT NOT NULL,
  url         TEXT NOT NULL,
  title       TEXT NOT NULL,
  cover_url   TEXT,
  in_library  BOOLEAN NOT NULL DEFAULT TRUE,
  last_read_chapter_id TEXT,
  last_read_at BIGINT DEFAULT 0,
  updated_at  BIGINT NOT NULL,
  PRIMARY KEY (id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_manga_sync_user ON manga_sync(user_id);

ALTER TABLE manga_sync ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage own manga_sync" ON manga_sync
  USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- ── chapter_sync ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS chapter_sync (
  id              TEXT NOT NULL,
  user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  manga_id        TEXT NOT NULL,
  read            BOOLEAN NOT NULL DEFAULT FALSE,
  last_page_read  INTEGER NOT NULL DEFAULT 0,
  updated_at      BIGINT NOT NULL,
  PRIMARY KEY (id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_chapter_sync_user ON chapter_sync(user_id);
CREATE INDEX IF NOT EXISTS idx_chapter_sync_manga ON chapter_sync(manga_id);

ALTER TABLE chapter_sync ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can manage own chapter_sync" ON chapter_sync
  USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- ── translate_usage ──────────────────────────────────────────────────────────
-- Denní strop pro překladovou proxy (viz supabase/functions/translate-proxy/index.ts).
-- Tahle tabulka i funkce níž dlouho existovaly JEN v živém projektu a chyběly ve
-- verzování - kdyby se projekt obnovoval, překlad by se tiše rozbil na chybějící RPC.
--
-- POZOR: strop je ZÁMĚRNĚ globální (jedna řádka na den za celý projekt), ne per-uživatel.
-- Appka je osobní a proxy běží s verify_jwt=false, takže tu není koho identifikovat.
-- Důsledek: kdokoli, kdo z APK vytáhne URL + anon key, může strop vyčerpat. Pokud by
-- appku někdy používal někdo další, tohle je první místo, které je potřeba předělat
-- (přidat identifikátor volajícího do klíče a do PRIMARY KEY).
CREATE TABLE IF NOT EXISTS translate_usage (
  day           DATE PRIMARY KEY,
  request_count INTEGER NOT NULL DEFAULT 0,
  char_count    BIGINT  NOT NULL DEFAULT 0
);

-- RLS zapnuté ZÁMĚRNĚ bez jediné policy: k tabulce se dostane pouze service role
-- (edge funkce), nikdy klient s anon klíčem. Prázdný seznam policies tady tedy není
-- opomenutí - je to ta nejpřísnější varianta.
ALTER TABLE translate_usage ENABLE ROW LEVEL SECURITY;

-- Atomicky započítá jeden požadavek a vrátí, jestli se ještě vejde do denního stropu.
-- Limity chodí jako parametry (ne konstanty v SQL), aby se daly měnit v jednom místě -
-- v index.ts, kde se o nich rozhoduje.
CREATE OR REPLACE FUNCTION public.increment_translate_usage(
  p_chars                INTEGER,
  p_daily_char_limit     BIGINT,
  p_daily_request_limit  INTEGER
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
declare
  v_count integer;
  v_chars bigint;
begin
  insert into public.translate_usage (day, request_count, char_count)
  values (current_date, 1, p_chars)
  on conflict (day) do update
    set request_count = translate_usage.request_count + 1,
        char_count = translate_usage.char_count + excluded.char_count
  returning request_count, char_count into v_count, v_chars;

  return v_count <= p_daily_request_limit and v_chars <= p_daily_char_limit;
end;
$function$;

-- Vrátí do denního ZNAKOVÉHO stropu znaky za pokus, při kterém upstream vůbec nic
-- nevygeneroval (HTTP chyba nebo rate limit) - viz refundQuota v index.ts.
--
-- POČET POŽADAVKŮ SE ZÁMĚRNĚ NEVRACÍ. Ta hodnota není měřítko práce, ale pojistka proti
-- rozjeté smyčce, a rozjetá smyčka se skládá právě z NEÚSPĚŠNÝCH pokusů. Kdyby se
-- request_count vracel, provider, který odpovídá pořád 429, by šlo volat donekonečna a
-- na denní strop by se nikdy nenarazilo - tedy přesně to, před čím má strop chránit.
--
-- Znaky se naopak vracejí, protože znakový limit má odhadovat SKUTEČNĚ přeložený objem;
-- fallback řetězec appky (Gemini -> Groq -> OpenRouter -> ...) si jinak za jednu dávku
-- ukrojí znaky tolikrát, kolikrát selhal, i když se nakonec přeložila jen jednou.
--
-- `greatest(0, ...)` je pojistka pro případ, kdy pokus začne před půlnocí a vrátí se až po
-- ní: refund pak trefí ŘÁDKU NOVÉHO DNE, kde ty znaky nikdy připsané nebyly. Rozdíl je
-- zanedbatelný, ale počítadlo nesmí spadnout pod nulu.
CREATE OR REPLACE FUNCTION public.refund_translate_usage(p_chars INTEGER)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
begin
  update public.translate_usage
     set char_count = greatest(0, char_count - p_chars)
   where day = current_date;
end;
$function$;
