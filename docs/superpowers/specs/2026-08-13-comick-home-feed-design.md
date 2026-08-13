# ComicK domovská obrazovka (Home Feed) — Design

## Cíl

Dnes appka v ComicK agregovaném režimu zobrazuje na záložce "Procházet" stejnou generickou `SourceBrowseScreen`, jakou používá všech ~180 ostatních zdrojů — jednoduchou mřížku s přepínačem Populární/Nejnovější. Skutečný web comick.io má mnohem bohatší domovskou stránku: sekce Recently Added/Completed, Popular New Comics (7d/1m/3m), Most Recent Popular (7d/1m/3m), Recent Reviews, a samostatný "Updates" feed s posledními nahranými kapitolami napříč všemi tituly (Hot/New řazení).

Tenhle dokument popisuje, jak appka nahradí generickou Procházet obrazovku v ComicK režimu touhle bohatší verzí. Vychází z rozhodnutí padlých s uživatelem v konverzaci (viz níže) a ze živě ověřeného tvaru ComicK API.

**Mimo ComicK režim (Klasický režim, ostatních ~180 zdrojů) se nic nemění** — generická `SourceBrowseScreen` zůstává beze změny.

## Klíčová rozhodnutí

1. **Vše najednou, jeden plán** — 5 domovských sekcí + Updates feed se implementují v jednom plánu rozděleném na tasky, ne jako samostatné sub-projekty.
2. **Nahrazuje generickou Procházet obrazovku** — `Routes.browseRoute(appMode)` v ComicK režimu povede na novou obrazovku místo `sourceBrowse("comick")`.
3. **"Zobrazit vše" na každé sekci** — uživatel chce plnou stránku na sekci, ne jen náhled bez pokračování.
4. **Updates feed jako přepínač na téže obrazovce** — "Domů" / "Aktualizace" nahoře, ne samostatná route.
5. **`/top` odpověď (~3 MB) se cachuje v paměti** — Home i všechny "zobrazit vše" obrazovky sdílí jedno stažení za session (stejný vzor jako `ComicKChapterResolver`'s in-memory cache), ne request na sekci.

## ComicK API (ověřeno živě)

### `GET https://api.comick.dev/top`

Jeden request vrátí data pro všech 5 domovských sekcí najednou:

```json
{
  "news": [ /* 60 položek, tvar identický s /v1.0/search */ ],
  "completions": [ /* 60 položek, stejný tvar */ ],
  "topFollowNewComics": { "7": [...60...], "30": [...60...], "90": [...60...] },
  "topFollowComics": { "7": [...240...], "30": [...240...], "90": [...240...] },
  "recentReviews": [ /* 100 položek, VLASTNÍ tvar - viz níže */ ],
  "trending": { "7": [...], "30": [...], "90": [...] },
  "rank": [...50...],
  "recentRank": [...20...],
  "follows": [...20...],
  "comicsByCurrentSeason": {...},
  "recentCustomLists": [...60...],
  "extendedNews": [...30...]
}
```

Mapování na obrazovky ze zadání uživatele:
- `news` → **Recently Added**
- `completions` → **Completed** (druhá půlka přepínače Recently Added/Completed)
- `topFollowNewComics` → **Popular New Comics** (klíče `"7"/"30"/"90"` = dny)
- `topFollowComics` → **Most Recent Popular** (stejné klíče)
- `recentReviews` → **Recent Reviews**

Pole `trending`, `rank`, `recentRank`, `follows`, `comicsByCurrentSeason`, `recentCustomLists`, `extendedNews` **nejsou v zadaných screenshotech** a jsou mimo rozsah (viz sekce níže) — v `TopFeed` se vůbec neparsují.

**Tvar položky `news`/`completions`/`topFollowNewComics`/`topFollowComics`** je identický s položkami `/v1.0/search` (`title`, `slug`, `country`, `md_covers`, …) — parsuje se stejnou privátní `parseComicList(JSONArray)`, kterou už `getGroup()` (Sub-projekt 4) znovupoužil beze změny.

**Tvar položky `recentReviews`** je jiný — recenze, ne komiks:
```json
{
  "id": 1845091,
  "title": "Only for seasoned veterans",
  "content": "This was really good...",
  "chap": "...",
  "rating": null,
  "created_at": "...",
  "identities": { "traits": { "username": "..." } },
  "md_comics": { "title": "...", "slug": "...", "md_covers": [...], ... }
}
```

### `GET https://api.comick.dev/chapter?lang={jazyk appky}&order=hot|new&page={n}`

Feed posledních nahraných kapitol napříč VŠEMI tituly (ne jeden konkrétní komiks) — přesně obsah "Updates" tabu. Ověřeno živě: `page=2` vrací jiná data než `page=1` (skutečné stránkování), `order=hot` a `order=new` vrací viditelně odlišné pořadí. **Nekešuje se jako `/top`** — každé přepnutí Hot/New nebo scroll dolů je nový request.

```json
[{
  "chap": "177", "vol": null, "hid": "7hslA3Hk",
  "created_at": "...", "up_count": 10, "comment_count": 2,
  "group_name": ["asurascans"],
  "md_chapters_groups": [{"md_groups": {"title": "Asura", "slug": "asura", ...}}],
  "md_comics": { "title": "...", "slug": "...", "md_covers": [...], ... }
}]
```

`md_chapters_groups`/`group_name` mají STEJNOU strukturu jako `chapterFromJson`/`parseGroups` už parsují u `getChapterList()` — znovupoužije se `parseGroups(json)`.

**Poznámka pro implementační plán**: `limit` parametr u `/chapter` v živém testu neomezoval počet vrácených položek spolehlivě (page=1&limit=2 vrátilo desítky položek) — přesné stránkovací chování (kolik položek na stránku doopravdy přijde) je potřeba ověřit znovu při psaní plánu/tasku, ne spoléhat na `limit` bez ověření.

## Datový model

`ComicKSource.kt`:

```kotlin
data class TopFeed(
    val recentlyAdded: List<SManga>,
    val completed: List<SManga>,
    val popularNew: Map<String, List<SManga>>,       // klíče "7","30","90"
    val mostRecentPopular: Map<String, List<SManga>>, // klíče "7","30","90"
    val recentReviews: List<ReviewItem>,
)

data class ReviewItem(
    val title: String,
    val content: String,
    val authorName: String?,
    val comic: SManga,
)

data class ChapterUpdate(
    val chapter: SChapter,   // znovupoužije existující typ (chapterNumber, name, dateUpload, groups)
    val comic: SManga,
    val upCount: Int,
    val commentCount: Int,
)
```

- `suspend fun getTop(): TopFeed` — `GET $apiBase/top`, parsuje výš popsaná pole přes `parseComicList`, cachuje výsledek in-memory (jednoduchý `private var cachedTop: TopFeed?`, žádná TTL logika navíc — cache žije po dobu běhu appky, stejně jako `ComicKChapterResolver`'s cache; invaliduje se jen novým spuštěním appky).
- `suspend fun getUpdates(order: String, page: Int): List<ChapterUpdate>` — `GET $apiBase/chapter?lang=...&order=$order&page=$page`, žádné cachování.

## UI

### Nová obrazovka `ui/comickhome/ComicKHomeScreen.kt` + `ComicKHomeViewModel.kt`

- Přepínač nahoře: **Domů** / **Aktualizace** (mění se, co se pod ním renderuje, žádná nová route).
- **Domů**: vertikálně scrollovatelná obrazovka s 5 sekcemi, každá:
  - Nadpis sekce + (u časových sekcí) přepínač 7d/1m/3m.
  - Vodorovně posuvná `LazyRow` karet (mobilní ekvivalent webové mřížky) — cca prvních 10-15 položek z už staženého `TopFeed`.
  - Tlačítko/odkaz "Zobrazit vše" na konci řady.
  - **Recent Reviews** má vlastní odlišnou kartu (text recenze + jméno autora + malý obrázek komiksu), ne standardní obálkovou kartu.
- **Aktualizace**: přepínač Hot/New + `LazyColumn` řádků (obálka, název komiksu, číslo kapitoly, čas, skupina, počet komentářů/lajků) s nekonečným scrollem (`getUpdates(order, page++)`).
- Klik na komiks kdekoliv (sekce, "zobrazit vše", Updates řádek) → `repository.openPreview(manga)` → `Routes.detail(id)`, stejný vzor jako `SourceBrowseViewModel.openManga`/`GroupViewModel.openManga`.

### Nová obrazovka `ui/comickhome/ComicKSectionScreen.kt` (+ ViewModel)

Jedna znovupoužitelná obrazovka pro "zobrazit vše" všech 4 mřížkových sekcí (ne Recent Reviews zvlášť, ta má vlastní layout ve stejné obrazovce podle typu) — nadpis + `LazyVerticalGrid`, data dostane z `ComicKSource.getTop()` (cache hit, žádný nový request), případně s parametrem, kterou sekci/časové okno zobrazit.

### Navigace

`Routes.browseRoute(appMode)` (dnes `sourceBrowse("comick")` v ComicK režimu) se přesměruje na novou `Routes.COMICK_HOME`. Nová route pro "zobrazit vše" nese parametr identifikující sekci (např. `comick_section/{section}?window={window}`).

## Mimo rozsah (záměrně)

- `/top`'s pole `trending`, `rank`, `recentRank`, `follows`, `comicsByCurrentSeason`, `recentCustomLists`, `extendedNews` — nejsou v zadaných screenshotech, neparsují se vůbec.
- Filtrace/řazení uvnitř sekcí (kromě časového okna 7d/1m/3m) — YAGNI.
- Perzistentní cache `/top` mezi spuštěními appky (Room) — jen in-memory po dobu session, stejné rozhodnutí jako u `ComicKChapterResolver`.
- Psaní/mazání vlastních recenzí, lajkování z appky — jen zobrazení.
- Sekce webu, které nejsou na poslaných screenshotech (viz `/top` pole výš).

## Testování

- `ComicKSourceTest`: parsování `getTop()` (mapování `news`→`recentlyAdded`, `completions`→`completed`, `topFollowNewComics`/`topFollowComics` s klíči 7/30/90, `recentReviews`→`ReviewItem`), test na cache (druhé volání `getTop()` nevolá znovu API — mock server dostane jen 1 request).
- `ComicKSourceTest`: parsování `getUpdates()` (`ChapterUpdate` pole, znovupoužití `parseGroups`).
- Manuální ověření na zařízení: přepnutí ComicK režimu, ověřit že se všech 5 sekcí načte a zobrazí správná data, přepnutí 7d/1m/3m mění obsah, "zobrazit vše" otevře plnou mřížku bez nového network requestu (ověřit v Network inspektoru/logu), přepnutí na Aktualizace + Hot/New + scroll dolů stránkuje.
