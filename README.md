# Jiyū

*(English below, Czech / čeština níže)*

## English

Personal Android reader for manga/manhwa/comics/light novels, with its own
extension architecture (like Tachiyomi/Kotatsu) and a built-in AI translator.
Strictly personal use, no paid APIs or libraries.

> **Note:** the translator is tuned mainly for Czech (compression rules,
> hyphenation, glossary prompts - all written with Czech output in mind), but
> the target language is just a free-text setting, so other languages work
> too, just with less-tuned prompts.

### Running it

1. Open the `jiyu` folder in Android Studio.
2. Sync should just work - all dependencies are standard (Compose, Room,
   Hilt, Coil, OkHttp, WorkManager, ML Kit) and come from
   `google()`/`mavenCentral()`.
3. `minSdk 26` / `targetSdk 34` - runs on an emulator or a real device
   (Android 8.0+).

Tests: `./gradlew testDebugUnitTest`. Build APK: `./gradlew assembleDebug`.

### Stack

- Kotlin + Jetpack Compose, Hilt (DI), Room (local DB)
- WorkManager - background chapter downloads and periodic new-chapter checks
- OkHttp + Jsoup - HTTP and HTML parsing for sources without an official API
- Coil - images, DataStore - settings
- ML Kit Text Recognition (EN/JP/CN/KR) - on-device OCR
- Gemini / Groq / OpenRouter - LLM translation, routed through a Supabase
  Edge Function acting as a proxy (the app never holds API keys, the client
  only calls its own backend)
- Supabase - auth, cloud library sync, community features

### Features

**Sources** - 60+ `MangaSource` implementations (manga, manhwa, American
comics, light novels). MangaDex and ComicK go through official APIs, the rest
are scrapers for specific sites or the generic Madara template
(`source/madara`), where adding a new site is just a base URL.

**Reader** - horizontal and webtoon mode, reads downloaded files or streams
straight from the URL, never opens a browser.

**AI bubble translation** - OCR finds the text and the bubble's actual shape
(not just a bounding box), the LLM translates with context about the work and
a glossary of proper nouns (the glossary learns new terms on the fly from the
model's own answers), then the overlay renders the text back into the bubble
shape with shrink-to-fit sizing. If one model hits a rate limit, the chain
automatically falls back to the next one (Gemini → Groq → OpenRouter).

**Offline downloads** - a WorkManager worker downloads a whole chapter in the
background, optionally zips it into a `.cbz`. Chapters are saved under a
readable `Manga Title/0012 - Chapter Name` structure, so you can just copy
them to a PC.

**Library & tracking** - categories, reading history, reading goals,
statistics, duplicate-title detection, AniList/MyAnimeList sync, library and
settings backup/restore (JSON export/import), sharing a manga via QR code, a
home screen widget, cross-device library sync through Supabase.

### Architecture

- `source/` - the `MangaSource` interface + one implementation per source.
  A new source is just a new class, nothing else in the app needs to change.
- `data/db/` - Room (library, chapters, download state, history)
- `data/repository/` - the single place that combines sources + database
- `download/` - WorkManager worker for background downloads
- `translate/` - the whole translation pipeline: OCR → bubble shape
  detection → LLM client → glossary → layout/render overlay
- `sync/`, `anilist/`, `auth/` - cloud sync and tracker integrations
- `ui/` - Compose screens + ViewModels, one folder per screen

### What's next

- More sources as needed - cheap to add thanks to the `MangaSource` interface
- Improve OCR on harder bubbles (tight shout bubbles, heavily colored title
  pages)
- App settings - finer control over image quality on download

---

## Čeština

Osobní Android reader na manga/manhwu/komiksy/light novely s vlastní extension
architekturou (jako Tachiyomi/Kotatsu) a zabudovaným AI překladačem. Čistě pro
osobní použití, žádné placené API ani knihovny.

> **Poznámka:** překladač je laděný hlavně na češtinu (kompresní pravidla,
> dělení slov, glosářové prompty - všechno psané s ohledem na český výstup),
> ale cílový jazyk je jen textové nastavení, takže fungují i jiné jazyky,
> jen s méně doladěnými prompty.

### Jak spustit

1. Otevři složku `jiyu` v Android Studiu.
2. Sync by měl proběhnout bez zásahu - všechny závislosti jsou standardní
   (Compose, Room, Hilt, Coil, OkHttp, WorkManager, ML Kit) a táhnou se
   z `google()`/`mavenCentral()`.
3. `minSdk 26` / `targetSdk 34` - spustí se na emulátoru i fyzickém zařízení
   (Android 8.0+).

Testy: `./gradlew testDebugUnitTest`. Build APK: `./gradlew assembleDebug`.

### Stack

- Kotlin + Jetpack Compose, Hilt (DI), Room (lokální DB)
- WorkManager - stahování kapitol a periodická kontrola nových kapitol na pozadí
- OkHttp + Jsoup - HTTP a HTML parsing pro zdroje bez oficiálního API
- Coil - obrázky, DataStore - nastavení
- ML Kit Text Recognition (EN/JP/CN/KR) - OCR přímo na zařízení
- Gemini / Groq / OpenRouter - LLM překlad, přes Supabase Edge Function jako
  proxy (appka nikdy nedrží API klíče, klient jen volá vlastní backend)
- Supabase - auth, cloud sync knihovny, komunitní funkce

### Co umí

**Zdroje** - 60+ implementací `MangaSource` (manga, manhwa, americké komiksy,
light novely). Mangadex a ComicK běží přes oficiální API, zbytek jsou scrapery
na konkrétní stránky nebo generická Madara šablona (`source/madara`), kam stačí
dodat jen base URL nového webu.

**Čtečka** - horizontální i webtoon mód, čte stažené soubory nebo streamuje
přímo z URL, nikdy neotevírá browser.

**AI překlad bublin** - OCR najde text a tvar bubliny (ne jen obdélníkový box),
LLM přeloží s ohledem na kontext díla a glosář vlastních jmen (glosář se učí
za běhu z odpovědí modelu), overlay pak text vyrenderuje zpátky do tvaru
bubliny se shrink-to-fit velikostí písma. Když jeden model narazí na limit,
řetězec automaticky zkusí další (Gemini → Groq → OpenRouter).

**Offline stahování** - WorkManager worker stáhne celou kapitolu na pozadí,
volitelně zabalí do `.cbz`. Kapitoly se ukládají pod čitelnou strukturou
`Název mangy/0012 - Název kapitoly`, jde je normálně zkopírovat na PC.

**Knihovna a sledování** - kategorie, historie čtení, cíle čtení, statistiky,
detekce duplicitně přidaných titulů, sync s AniList/MyAnimeList, záloha/obnova
knihovny i nastavení (JSON export/import), sdílení mangy přes QR kód, widget
na plochu, sync knihovny mezi zařízeními přes Supabase.

### Architektura

- `source/` - rozhraní `MangaSource` + jedna implementace na zdroj. Nový zdroj
  = nová třída, nic dalšího se v appce měnit nemusí.
- `data/db/` - Room (knihovna, kapitoly, stav stažení, historie)
- `data/repository/` - jediné místo, které kombinuje zdroje + databázi
- `download/` - WorkManager worker pro stahování na pozadí
- `translate/` - celý překladový pipeline: OCR → detekce tvaru bubliny →
  LLM klient → glosář → layout/render overlay
- `sync/`, `anilist/`, `auth/` - cloud sync a tracker integrace
- `ui/` - Compose obrazovky + ViewModely, po jedné složce na obrazovku

### Co dál

- Víc zdrojů podle potřeby - přidávání je levné díky `MangaSource` rozhraní
- Doladit kvalitu OCR na těžších bublinách (kompaktní shout bubliny, sytě
  barevné title pages)
- Nastavení appky - jemnější kontrola kvality obrázků při stahování
