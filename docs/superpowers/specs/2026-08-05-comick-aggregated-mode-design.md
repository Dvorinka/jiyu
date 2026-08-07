# ComicK agregovaný režim — Design

## Cíl

Dnes appka pracuje se ~180 nezávislými zdroji: uživatel si sám vybere zdroj, v něm hledá a z něj čte. Nový **ComicK agregovaný režim** (přepínatelný v Nastavení) tohle otočí: ComicK se stane jediným vstupním katalogem appky. Uživatel v něm hledá a prohlíží tituly se všemi metadaty přesně jako na comick.io (obálka, popis, autoři, žánry, stav, kapitoly, překladatelské skupiny u každé kapitoly) — ale nikdy z ComicK nečte, protože ComicK už reálné obrázky stránek neposkytuje (viz `SourceManager.kt:143-148`, proto byl jako čtecí zdroj v minulosti odstraněný).

Místo toho appka na kliknutí "Číst" (nebo na konkrétní kapitolu) najde mezi skutečnými ~180 zdroji ty, které danou kapitolu mají, ukáže je s mírou úplnosti vůči ComicK a nechá uživatele vybrat. Po výběru se zbytek appky (knihovna, stahování, historie, pokračování ve čtení) chová přesně jako dnes — ComicK do toho dál nezasahuje.

**Klasický režim (dnešní chování) zůstává výchozí a plně dostupný přepnutím zpět.**

## Klíčová rozhodnutí (platí napříč celou funkcí)

Tahle rozhodnutí padla v konverzaci s uživatelem a platí pro všechny sub-projekty níž:

1. **Po vyřešení zdroje appka pracuje úplně normálně** — žádný trvalý "přišlo z ComicK" stav. ComicK je čistě vrstva na objevování/katalog, ne trvalá součást chování titulu.
2. **Křížové hledání zdroje se zužuje podle typu obsahu** — ComicK titul má svůj typ (manga/manhwa/manhua, odvozeno z `country`: jp→MANGA, kr→MANHWA, cn→MANHUA). Appka prohledá jen zdroje se stejným `contentType`, ne všech ~180 (typicky ~10-20 kandidátů místo 180). Novelové a komiksové zdroje se nikdy neprohledávají u ComicK titulu (ComicK sám je jen manga/manhwa/manhua tracker).
3. **Appka nikdy neotevře zdroj potichu bez potvrzení** — i když vyjde jen jeden kandidát, ukáže se seznam s mírou úplnosti (`X/Y kapitol` vůči ComicK), uživatel potvrdí kliknutím. Řeší to obavu, že by appka otevřela zdroj, který přestal překládat/chybí mu úvodní kapitoly.
4. **Úplnost se počítá porovnáním počtu kapitol** kandidátního zdroje s celkovým počtem na ComicK (ne jen datem poslední aktualizace — to by neodhalilo zdroj, co odpadl uprostřed).
5. **Preferované zdroje** = znovupoužije se existující "Oblíbené zdroje" (`SettingsKeys.FAVORITE_SOURCE_IDS`) — pokud je mezi kandidáty oblíbený zdroj, zvýrazní/předvybere se nahoře seznamu (ale pořád vyžaduje potvrzení dle bodu 3).
6. **Cache výsledků na úrovni titulu** — jakmile appka jednou najde kandidátní zdroje pro titul, při dalších kapitolách STEJNÉHO titulu znovu neprohledává od nuly (drahé), jen kontroluje, jestli už objevené zdroje mají i tu novou kapitolu.

## Rozdělení na sub-projekty

Příliš velké na jeden plán, dělá se postupně, každý má vlastní commit/testy:

1. **Přepínač režimu + navigace** (tenhle dokument, detailně níž) — nastavení, přesměrování "Procházet".
2. **ComicK detail obohacený o skupiny** — `SChapter.groups`, oprava `contentType` detekce (country→typ), zobrazení skupiny u kapitoly.
3. **Motor pro křížové vyhledání zdroje** — zúžení podle typu, paralelní hledání, stažení seznamů kapitol kandidátů, výpočet úplnosti, chooser dialog, cache.
4. **Stránka skupiny** (volitelné) — klik na skupinu ukáže její další tituly na ComicK (`GET /group/{slug}/`).

---

## Sub-projekt 1: Přepínač režimu + navigace

### Datový model

`SettingsRepository.kt`:
- Nový klíč `SettingsKeys.APP_MODE = stringPreferencesKey("app_mode")`.
- Nový enum `AppMode { SOURCES, COMICK }` (vzor jako `ReaderTheme`/`ThemeOption` objekty ve stejném souboru), `SOURCES` je default/fallback při chybějící/neplatné hodnotě.
- `val appMode: Flow<AppMode>` + `suspend fun setAppMode(mode: AppMode)`.

### UI

- Nový přepínač v Nastavení (`SettingsScreen.kt` nebo vhodná podsekce) — jednoduchý switch se stručným vysvětlením, co se změní (vzor doc-komentářů u `IS_ADULT`/`CRASH_REPORTING`).

### Navigace

**Cesty (routes) se NEMĚNÍ** — záložka "Procházet" ve spodní liště pozná svou aktivitu přesnou shodou `route == Routes.BROWSE` (`MainScreen.kt:96`, `tabs.forEach`); kdyby se v ComicK režimu navigovalo na jinou cestu (např. `Routes.sourceBrowse("comick")`), záložka by se přestala zvýrazňovat jako aktivní a rozbilo by se `saveState`/`restoreState` mezi záložkami.

Místo toho se **cesta, na kterou se naviguje, počítá dynamicky podle režimu** — sdílená funkce `Routes.browseRoute(appMode)` vrací `Routes.BROWSE` v Klasickém režimu nebo `Routes.sourceBrowse("comick")` v ComicK režimu, použitá konzistentně na všech 3 místech (bottom nav tab, prázdný stav Knihovny, prázdný stav Seznamu). Tab samotný (`NavTab.route`) tak v ComicK režimu nese rovnou cílovou cestu, takže zvýraznění aktivní záložky (`it.route == tab.route`) i `saveState`/`restoreState` zůstávají konzistentní. (Přesné technické detaily viz implementační plán `docs/superpowers/plans/2026-08-05-comick-mode-toggle-and-navigation.md`.)

`SourceBrowseScreen` už dnes umí zobrazit jeden konkrétní zdroj (parametr `sourceId`) — žádná úprava té obrazovky není potřeba pro tenhle krok.

### Mimo rozsah (záměrně)

- `GlobalSearchScreen` — dnes už prohledává živě všechny zdroje najednou (`GlobalSearchViewModel.search()` → `sourceManager.getAll()`), nezávislý účel, zůstává beze změny bez ohledu na režim.
- Nastavení → Zdroje / katalog zdrojů — zůstává dostupné pro pokročilé ruční úpravy bez ohledu na režim.
- ComicK musí zůstat zaregistrovaný v `SourceManager.kt` (odkomentovat/upravit — dnes je vypnutý kvůli nefunkčnímu čtení, což řeší sub-projekt 3 tím, že se `getPageList()` z ComicK nikdy nevolá v běžném toku).

### Testování

- Unit test `SettingsRepository`: `setAppMode`/`appMode` round-trip, default `SOURCES` při prázdném/neplatném uloženém řetězci.
- `composable(Routes.BROWSE)` větvení není snadné pokrýt čistým unit testem (závisí na Compose navigaci) — ověří se manuálně po implementaci (přepnout režim, zkontrolovat, že záložka "Procházet" vede na správnou obrazovku a zůstává zvýrazněná jako aktivní).

---

## Sub-projekt 2: ComicK detail obohacený o skupiny

Sub-projekt 1 znovu-zaregistroval `ComicKSource` pro browse/metadata (viz výš). Ruční test na zařízení po jeho nasazení (2026-08-07) odhalil, že samotný `ComicKSource` má dva samostatné bugy a chybí mu klíčová data pro agregovaný katalog — oba se opravují tady, protože se dotýkají stejných řádků jako nová funkcionalita níž.

### Zjištěné bugy (opravují se jako součást tohoto sub-projektu)

- **„Vol.null Ch.X – null" v názvu kapitoly.** `ComicKSource.chapterFromJson()` používá `json.optString("vol")` / `optString("title")`. ComicK API ale tahle pole vrací jako JSON `null` (ne jako chybějící klíč) — ověřeno živým dotazem na `/comic/{hid}/chapters` (`"title":null,"vol":null` v reálné odpovědi). Android `org.json` u `optString()` na hodnotě `JSONObject.NULL` vrací doslovný text `"null"`, ne `""` — `.ifBlank { null }` to nezachytí. Oprava: `if (json.isNull("vol")) null else json.optString("vol").ifBlank { null }` (stejně pro `title`), ne string-hack porovnávající s `"null"`.
- **`contentType` se u ComicK nikdy nenastavuje.** `parseComicList()` staví `SManga(...)` bez `contentType`, takže vždy defaultuje na `"MANGA"` (past popsaná v `project_jiyu_contenttype_default` memory). ComicK API vrací pole `country` jak ve výsledcích search/popular, tak v `/comic/{slug}` detailu — ověřeno živě (`"country":"kr"` u Solo Levelingu). Mapování: `jp→MANGA, kr→MANHWA, cn→MANHUA`, cokoliv jiného → `MANGA` (fallback). Nastavuje se v `parseComicList()` (seznam) i znovu v `getMangaDetails()` (refresh).

### Datový model

`MangaSource.kt`:
- Nová datová třída `data class SGroup(val name: String, val slug: String? = null)`.
- `SChapter` získá nové pole `val groups: List<SGroup> = emptyList()`. Existující `scanlationGroup: String?` zůstává beze změny — zpětná kompatibilita s ostatními ~180 zdroji, které ho nastavují jako jeden řetězec.

`ComicKSource.chapterFromJson()`:
- Skupiny čteme z `group_name: string[]` — tohle pole je autoritativní zdroj počtu/pořadí skupin u kapitoly. `md_chapters_groups[].md_groups.{title,slug}` doplňuje slug (a hezčí zobrazovací jméno), ale **není spolehlivě stejně dlouhé ani vždy přítomné** — ověřeno živě: u jedné kapitoly bylo `group_name` neprázdné a `md_chapters_groups` úplně `[]`, u jiné se `group_name[0]` ("asurascans") lišilo od `md_chapters_groups[0].md_groups.title` ("Asura", hezčí tvar). Proto: iterovat přes `group_name` podle indexu `i`, k němu přes `md_chapters_groups.getOrNull(i)?.md_groups` dohledat `slug` a přednostně použít jeho `title` jako zobrazované jméno; když `md_chapters_groups` pro daný index chybí, použít rovnou string z `group_name[i]` a `slug = null`.
- `scanlationGroup` (starý řetězec) se pro zpětnou kompatibilitu s dnešní UI naplní jako `groups.joinToString(", ") { it.name }`.

`ChapterEntity.kt`:
- Nový sloupec `val groupsJson: String? = null` — JSON pole `[{"name":...,"slug":...}]`, serializace přes `org.json` (stejná knihovna jako v `ComicKSource.kt`, žádná nová závislost).
- `AppDatabase.kt`: `MIGRATION_29_30` (`version = 30`), `ALTER TABLE chapter ADD COLUMN groupsJson TEXT` — stejný vzor jako `MIGRATION_27_28`. Zaregistrovat v `AppModule.kt`'s `addMigrations(...)`.
- `MangaRepository.kt`: mapping `SChapter → ChapterEntity` uloží `groupsJson` vedle stávajícího `scanlationGroup`.
- `groupsJson` se v tomhle sub-projektu nikde nečte zpátky do UI (žádná klikací komponenta) — je to jen příprava dat pro Sub-projekt 4. Zobrazení beze změny používá stávající `chapter.scanlationGroup` text (`MangaDetailScreen.kt:900-902`), který teď konečně nebude prázdný u ComicK titulů.

### UI

- **Stahovací ikonka** (`GlassChapterRow`, `MangaDetailScreen.kt:869-936`): podmínka `if (chapter.sourceId != "comick")` kolem celého `when (chapter.downloadStatus) { ... }` bloku (řádky 909-915) — jinak se nevykreslí vůbec nic (zcela schovat, ne jen prázdné místo).
- **Klik na kapitolu u ComicK zdroje**: v místě, kde `onOpenChapter(chapter.id)` skutečně naviguje do čtečky, se před navigací zkontroluje `chapter.sourceId == "comick"` — pokud ano, zobrazí se Snackbar/Toast „Čtení přes ComicK ještě nefunguje — hledání skutečného zdroje přidáme v příští aktualizaci" a k navigaci vůbec nedojde (ušetří i zbytečné volání ComicK API, které stejně selže). Přesné místo zásahu (navigační lambda vs. ViewModel) určí implementační plán.

### Mimo rozsah (záměrně)

- Klikací UI na jednotlivou skupinu (otevře její další tituly na ComicK) — to je Sub-projekt 4, `groupsJson` pole se pro něj teď jen připravuje.
- Skutečné vyřešení „Číst" na reálný zdroj — Sub-projekt 3.

### Testování

- `ComicKSourceTest`: test pro `isNull` větev (`"vol": null` v JSONu → `chapters[0].volume == null`, ne řetězec `"null"`); test pro `contentType` mapování (`country: "kr"` → `MANHWA` atd., 3 varianty + fallback); test pro `groups` parsování z `group_name` + `md_chapters_groups`; mutation-test ověření (dočasně vrátit bug, potvrdit že nový test spadne).
- `AppDatabaseMigrationTest`: rozšířit o `MIGRATION_29_30` (stejný vzor jako existující testy pro předchozí migrace).
- Manuální ověření na zařízení: otevřít titul v ComicK režimu, zkontrolovat že se u kapitol zobrazují jména skupin a datum bez "null", že chybí stahovací ikonka, a že klik na kapitolu ukáže srozumitelnou hlášku místo pádu do čtečky.

---

## Sub-projekt 3: Motor pro křížové vyhledání zdroje

Tohle je jádro celé funkce — nahrazuje dočasnou hlášku „Čtení přes ComicK ještě nefunguje" (Sub-projekt 2) skutečným vyřešením na reálný, čitelný zdroj.

### Kde se to spouští

Nahrazuje DVĚ místa ze Sub-projektu 2, obě dnes jen zobrazují snackbar a nic dalšího nedělají:

1. `MangaDetailScreen.kt`'s `openChapter(chapter, incognito)` helper (Task 7) — klik na konkrétní kapitolu/tlačítko "Číst" na detailu ComicK titulu.
2. `ReaderViewModel.loadChapter()` (Task 8) — jediné místo, kterým prochází VŠECH 6 navigačních cest do čtečky (Knihovna, Seznam, Aktualizace, historie, detail titulu, inkognito) — ponechává se jako poslední pojistka i pro cesty, které Sub-projekt 3 explicitně neřeší (např. by teoreticky šlo obejít UI a nastartovat čtečku přímo).

Obě místa místo snackbaru spustí resolveci popsanou níž a v případě úspěchu naviguji do nové obrazovky "Vyber zdroj" (viz UI), ne rovnou do čtečky — i kdyby vyšel jen jeden kandidát (klíčové rozhodnutí 3 z úvodu dokumentu).

### Motor vyhledání

1. **Zúžení podle typu obsahu** — `SourceManager` (Sub-projekt 1) už umí filtrovat zdroje podle `contentType`. Použije se stejný filtr, aby se u ComicK titulu s `contentType = MANHWA` prohledávaly jen manga/manhwa/manhua zdroje (~10-20 kandidátů), nikdy novelové/komiksové.
2. **Paralelní živé hledání** — pro každého kandidáta zavolá jeho `search(query = comicKTitle, ...)` (stejná metoda, jakou dnes používá `GlobalSearchViewModel`), paralelně (`async`/`awaitAll`, stejný vzor jako `ChapterUpdateWorker`'s `Semaphore(5)` — omezit současný počet requestů, aby se neplácly desítky paralelních volání najednou).
3. **Shoda názvu** — použije se existující `normalizeMangaTitle()` (dnes slouží k odhalení duplicit v knihovně, `MangaRepository.findLibraryMatchesByTitle`) k porovnání normalizovaného ComicK názvu s normalizovanými názvy výsledků hledání u každého kandidáta. Zdroj bez shody se zahodí.
4. **Úplnost** — u každého zbylého kandidáta se stáhne celý seznam kapitol (`getChapterList`) a porovná jejich počet s celkovým počtem na ComicK (pole `comic.chapter_count` z `/comic/{slug}` — ComicK ho už posílá, žádné nové API volání navíc oproti tomu, co appka dělá při obohacení detailu). Zobrazí se jako `X/Y kapitol`.
5. **Konkrétní kapitola** — pokud uživatel klikal na konkrétní kapitolu (ne obecné "Číst"), u každého kandidáta se navíc zkontroluje, jestli má kapitolu se stejným (nebo nejbližším) číslem — `chapterNumber` porovnání s tolerancí pro desetinná čísla (ComicK i zdroje používají `Float`).
6. **Cache na úrovni titulu** — výsledek kroku 1-4 (seznam kandidátů + jejich staženétabulky kapitol) se uloží do in-memory cache (mapa `comicKMangaId -> List<CandidateSource>`), platná jen po dobu běhu appky (žádná Room tabulka). Další kliknutí na JINOU kapitolu STEJNÉHO titulu ve stejné session jen zkontroluje, jestli cachovaní kandidáti mají i tuhle novou kapitolu (bez nového `search()`/plného `getChapterList()`).

### UI

**Nová obrazovka "Vyber zdroj"** (`Routes.SOURCE_RESOLVER` nebo podobně, přesný název určí plán):
- Loading stav během kroku 1-4 výše (může trvat pár sekund kvůli paralelnímu síťovému hledání).
- Seznam nalezených kandidátů: název zdroje, `X/Y kapitol` vůči ComicK, hvězdička/zvýraznění pokud je zdroj v "Oblíbených zdrojích" (`SettingsKeys.FAVORITE_SOURCE_IDS`, klíčové rozhodnutí 5), případně indikátor že zdroj konkrétní požadovanou kapitolu má/nemá.
- Tlačítko **"Hledat ručně"** — vždy dostupné (i když kandidáti vyšli), otevře normální `GlobalSearchScreen` s předvyplněným dotazem (název ComicK titulu) pro případ, že automatické hledání zdroj nenajde kvůli jinému názvu na zdroji, nebo že žádný z nalezených kandidátů není ve skutečnosti správný.
- 0 kandidátů: obrazovka ukáže "Žádný zdroj tenhle titul nemá" + stejné tlačítko "Hledat ručně".

### Chování po výběru

Klik na kandidáta v seznamu: appka zavolá `repository.openPreview(resolvedSManga)` (stejná cesta jako dnešní klik na výsledek v Procházet/GlobalSearch — vytvoří/znovupoužije NE-knihovní `MangaEntity` pro ten reálný zdroj), synchronizuje jeho kapitoly (`refreshChapters`, kandidát už je má stažené z kroku 4 výše, není nutné je stahovat znovu) a rovnou naviguje do čtečky na `ChapterEntity`, jehož `chapterNumber` odpovídá požadované ComicK kapitole.

Od tohohle okamžiku appka pracuje úplně normálně (klíčové rozhodnutí 1) — žádný trvalý "přišlo z ComicK" stav, žádné zvláštní chování v historii/pokračování ve čtení. Pokud si uživatel titul chce trvale sledovat přes tenhle reálný zdroj místo ComicK, může ho normálně přidat do knihovny tlačítkem na jeho detailu (stejně jako dnes u jakéhokoliv jiného zdroje) — to je oddělené rozhodnutí od ComicK knihovnní položky, obě mohou existovat vedle sebe.

### Mimo rozsah (záměrně)

- Perzistentní cache (Room) — jen in-memory, viz "Cache rozsah" rozhodnutí výše.
- Automatické sloučení ComicK knihovní položky se zjištěným reálným zdrojem do jedné entity — zůstávají oddělené (klíčové rozhodnutí 1).
- Klikací stránka skupiny (Sub-projekt 4).

### Testování

- Unit testy pro zúžení podle `contentType` (mock `SourceManager` s několika zdroji různých typů, ověřit že se prohledají jen odpovídající).
- Unit testy pro `normalizeMangaTitle`-based shodu (existující i nová edge-case pokrytí — diakritika, velká/malá písmena, různé oddělovače).
- Unit testy pro výpočet úplnosti (`X/Y`) a pro nalezení nejbližší odpovídající kapitoly podle čísla.
- Unit test pro in-memory cache (druhé volání pro stejný titul nevolá znovu `search()`/plný `getChapterList()` u již objevených kandidátů).
- Manuální ověření na zařízení: ComicK titul s jasnou shodou (např. Solo Leveling) → ověřit že se najdou správní kandidáti se správným poměrem kapitol; titul co žádný zdroj nemá → ověřit "Hledat ručně" cestu; klik na konkrétní starší kapitolu → ověřit že appka skočí rovnou na odpovídající kapitolu ve vybraném zdroji, ne na první.
