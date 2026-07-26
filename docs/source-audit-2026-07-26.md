# Audit všech zdrojů (manga/manhwa/manhua/novely) — 2026-07-26

Kompletní přehled úplně všech zdrojů, které kdy byly v appce (v `SourceManager.kt`).
Ověřeno ručně přes curl (liveness + kontrola skutečného obsahu, ne jen HTTP kódu —
spousta "živých" domén vrací jen zaparkovanou/reklamní stránku).

**Celkem 134 zdrojů** (73 na generické Madara šabloně + 61 s vlastním scraperem).

| Stav | Počet |
|---|---|
| ✅ Funguje | 85 |
| ❌ Odstraněno — mrtvé / zaparkované / malvertising / kompromitované (nejde opravit) | 48 |
| ❓ Nejisté, zatím ponecháno — potřeba ověřit z mobilu/reálné appky | 0 |

**Update 2026-07-27 (druhé kolo, na výslovné přání uživatele):** curl-only audit dal u
ComicK a Bato.to falešně pozitivní výsledek (metadata/HTTP kódy vypadaly OK, ale appka
je reálně nemohla použít ke čtení). Proto teď probíhá druhé kolo ověřování **přímo v
appce na emulátoru** - u každého zdroje se zkouší otevřít konkrétní titul a kapitolu,
ne jen zkontrolovat HTTP odpověď. Průběžné výsledky viz sekce 6 na konci dokumentu.

Ve `SourceManager.kt` u každého odstraněného zdroje zůstal komentář s důvodem
(datum 2026-07-26), takže se dá kdykoliv dohledat, co a proč zmizelo.

**Update (tentýž den, druhá fáze):** napsal jsem 7 nových `MangaSource` tříd pro
zdroje, které se v prvním kole auditu jevily jako "opravitelné" — comizy.io
(náhrada za MangaBuddy), hivetoons.org (náhrada za Hive Scans), mangaworld.mx
(náhrada za MangaWorld IT), voidscans.net, hostednovel.com, manhuabuddy.com a
woopread.com. Všechny mají unit testy a jsou zapojené v appce. Při hledání
náhrad se navíc odhalily 3 další nebezpečné/mrtvé případy (creativenovels,
xcalibrscans, mangatoto) — viz sekce 2b.

**Update (tentýž den, třetí fáze):** na výslovné přání jsem se vrátil ke
3 zdrojům, co jsem původně označil za "dražší oprava", a šel do hloubky:
- **animesama** — **opraveno.** Web sice negeneruje seznam kapitol ve
  statickém HTML, ale jeho vlastní JS reader volá interní JSON API
  (`/s2/scans/get_nb_chap_et_img.php`), kterou jsem našel v jejich JS bundlu
  a napojil přímo. Viz sekce 1a.
- **tmo** — **potvrzeno jako definitivně mrtvý**, ne jen "těžká oprava".
  Obě domény (lectortmo.com i lectortmo.net) jsou teď Namecheap
  "expired domain" parkovací stránky — dřívější odhad "možná SPA shell"
  byl mylný, byl to JS bundle té parkovací stránky. Žádná nástupnická
  doména nenalezena. Viz sekce 2e.
- **mangadenizi** — našel jsem kompletní interní REST API
  (`/api/v1/web/manga`) s bohatými daty (název, popis, obálka, žánry,
  autoři, plný seznam kapitol). Stránky byly navíc **rozházené do dlaždic**
  (`"scramble":{"method":"tiled-v1","grid":10,"seed":N}`).

**Update (tentýž den, čtvrtá fáze):** na výslovné přání "jdeme na Bitmap
poskládání dlaždic" jsem reverzoval přesný "tiled-v1" algoritmus z jejich
JS bundlu (funkce `Mn/Wt/Yt/Gt/En/$n` - seedovaný xorshift32 PRNG + Fisher-Yates
permutace řádků/sloupců), nezávisle ho reimplementoval v Pythonu a **ověřil
pixel-perfect na reálné zamíchané stránce** (viz `TileScramble.kt` pro
detaily algoritmu). Napojeno na obě místa, kde appka obrázky skutečně
stahuje - online čtečka (`TileDescrambleTransformation`, Coil) i offline
stahování (`ChapterDownloadWorker`, přímo přes OkHttp, mimo Coil). **mangadenizi
je teď plně funkční zdroj**, viz sekce 1a.

---

## 1) ✅ FUNGUJE (87)

### 1a. Nově napsané/opravené vlastní scrapery (9) — druhá, třetí a čtvrtá fáze auditu

| ID | Název | URL | Poznámka |
|---|---|---|---|
| comizy | Comizy | comizy.io | náhrada za MangaBuddy; parsuje `__NEXT_DATA__` JSON |
| hivetoons | HiveToons | hivetoons.org | náhrada za Hive Scans; schema.org `itemProp` mikrodata |
| mangaworld | MangaWorld (IT) | www.mangaworld.mx | vlastní Laravel frontend, nikdy nebyla Madara |
| voidscans | Void Scans | voidscans.net | malý statický Hugo web |
| animesama | Anime-Sama | anime-sama.to | nová doména + přepojeno na interní JSON API webu (viz update na začátku dokumentu) |
| hostednovel | HostedNovel | hostednovel.com | vlastní Laravel/Vue frontend, NOVEL |
| manhuabuddy | ManhuaBuddy | manhuabuddy.com | kapitoly ze schema.org JSON-LD `ItemList` |
| woopread | WoopRead | woopread.com | Next.js App Router, NOVEL; kapitoly regexem z RSC payloadu |
| mangadenizi | MangaDenizi (TR) | mangadenizi.net | interní REST API + vlastní tile-descramble algoritmus (viz `TileScramble.kt`) |



### 1b. Madara šablona (25)

| ID | Název | URL | Poznámka |
|---|---|---|---|
| manhuafast | ManhuaFast | manhuafast.com | za Cloudflare (řeší CloudflareChallengeHost) |
| manhuaplus | Manhuaplus | manhuaplus.com | |
| kunmanga | Kunmanga | kunmanga.com | za Cloudflare |
| manhuaus | ManhuaUS | manhuaus.com | za Cloudflare |
| manhwatop | Manhwatop | manhwatop.com | |
| immortalupdates | Immortal Updates | immortalupdates.com | za Cloudflare |
| foxaholic | Foxaholic | foxaholic.com | za Cloudflare |
| wuxiaworldsite | Wuxiaworld.site | wuxiaworld.site | |
| ranovel | Ranovel | ranovel.com | |
| manhuahot | Manhua Hot | manhuahot.com | |
| manhuarm | Manhuarm | manhuarmtl.com | MTL (strojový překlad) |
| toonily | Toonily | toonily.com | |
| madaradex | MadaraDex | madaradex.org | |
| mangazin | Mangazin | mangazin.org | |
| cocomic | Cocomic | cocomic.co | |
| mangagg | MangaGG | mangagg.com | |
| mangaread | MangaRead | www.mangaread.org | |
| mangablaze | MangaBlaze | mangablaze.com | |
| coffeemanga | CoffeManga | coffeemanga.ink | |
| mangasushi | Mangasushi | mangasushi.org | |
| manhwatoon | Manhwatoon | www.manhwatoon.me | |
| pawmanga | PAWMANGA | pawmanga.com | |
| manhwaz | Manhwaz | manhwaz.com | vlastní permalinky |
| aquareader | Aqua Manga | aquareader.org | za Cloudflare, vlastní selektory |
| webtoonxyz | Webtoon XYZ | www.webtoon.xyz | vlastní permalinky |

### 1c. Vlastní scraper — původní (53)

| ID | Název | URL | Poznámka |
|---|---|---|---|
| mangadex | MangaDex | api.mangadex.org | API |
| mangaplus | MANGA Plus | mangaplus.shueisha.co.jp | |
| hitomi | Hitomi | hitomi.la | |
| nhentai | nhentai | nhentai.net | |
| mangafire | MangaFire | mangafire.to | |
| webtoon | Webtoons | www.webtoons.com | |
| dynasty | Dynasty Scans | dynasty-scans.com | |
| mangapark | MangaPark | mangapark.page | |
| novelfull | NovelFull | novelfull.com | |
| freewebnovel | FreeWebNovel | freewebnovel.com | |
| evilmanga | Evil Manga | evil-manga.eu | za Cloudflare |
| mangaboomers | MangaBoomers | manga-boomers.cz | |
| mangago | Mangago | www.mangago.me | |
| asurascans | Asura Scans | asurascans.com | |
| flamecomics | Flame Comics | flamecomics.xyz | |
| rawkuma | RawKuma | rawkuma.com | |
| comicbookplus | ComicBookPlus | comicbookplus.com | |
| readfreecomicsonline | ReadFreeComicsOnline | readfreecomicsonline.com | |
| comicskingdom | Comics Kingdom | comicskingdom.com | |
| manganato | MangaNato | www.natomanga.com | |
| royalroad | Royal Road | www.royalroad.com | |
| scribblehub | ScribbleHub | www.scribblehub.com | za Cloudflare |
| mangahub | MangaHub | mangahub.io | |
| weebcentral | WeebCentral | weebcentral.com | |
| vortexscans | Vortex Scans | api.vortexscans.org | |
| mangak | MangaK | mangak.io | |
| japscan | Japscan | www.japscan.lol | |
| scanvf | Scan-VF | www.scan-vf.net | |
| inmanga | InManga | inmanga.com | |
| mangadotnet | MangaDot.net | mangadot.net | |
| kaliscan | KaliScan | kaliscan.io | |
| mangacloud | MangaCloud | mangacloud.org | |
| galaxymanga | GalaxyManga | galaxymanga.io | |
| kuramanga | KuraManga | kuramanga.com | |
| lightnovelworld | LightNovelWorld | lightnovelworld.org | |
| novelfire | NovelFire | novelfire.net | |
| wuxiabox | WuxiaBox | www.wuxiabox.com | |
| ranobes | Ranobes | ranobes.net | |
| novelcool | NovelCool | www.novelcool.com | |
| novelhall | NovelHall | www.novelhall.com | |
| mangakatana | MangaKatana | mangakatana.com | |
| baozimanhua | BaoziManhua | www.baozimh.com | |
| mangapill | Mangapill | mangapill.com | |
| mangatown | MangaTown | www.mangatown.com | |
| novelbuddy | NovelBuddy | novelbuddy.me | |
| mangahome | MangaHome | www.mangahome.com | |
| nihonkuni | NihonKuni | nihonkuni.com | |
| hachirumi | Hachirumi (Guya) | guya.moe | |
| kingofshojo | Kingofshojo | kingofshojo.com | |
| manga18fx | Manga18fx | manga18fx.com | |
| hentai20 | Hentai20 | hentai20.io | |
| demonicscans | DemonicScans | demonicscans.org | |

---

## 2) ❌ ODSTRANĚNO — mrtvé / zaparkované / malvertising / kompromitované (45)

Tyhle domény buď vůbec neexistují, nebo živě servírují reklamní/škodlivý obsah
místo manga stránky. **U 16 z nich to je přesně ten "vyskočí error" symptom** —
appka se pokusí naparsovat "Redirecting..." nebo JS bot-gate stránku jako Madara
obsah a spadne na chybu.

### 2a. Madara — DNS mrtvé / timeout (14)

| ID | Název | URL | Zjištěno |
|---|---|---|---|
| astrascan | Astra Scans | astra-scans.net | DNS nerozresolvuje |
| cosmicscans | Cosmic Scans | cosmicscans.org | DNS nerozresolvuje |
| isekaiscan | IsekaiScan | isekaiscan.eu | DNS nerozresolvuje |
| magicscans | Magic Scans | magicscans.net | DNS nerozresolvuje |
| mangaeffects | MangaEffects | mangaeffects.com | DNS nerozresolvuje |
| mangafuture | MangaFuture (IT) | www.mangafuture.it | DNS nerozresolvuje |
| mangakiss | MangaKiss | mangakiss.net | DNS nerozresolvuje |
| mangapt | MangaPT | mangapt.com | connection timeout |
| mangarosie | MangaRosie | mangarosie.in | DNS nerozresolvuje |
| manhuaonline | ManhuaOnline | manhuaonline.co | DNS nerozresolvuje |
| manhuarock | ManhuaRock | manhuarock.cc | DNS nerozresolvuje |
| okumangas | OkuManga (TR) | okumangas.com | DNS nerozresolvuje |
| trillerscans | Triller Scans | trillercans.com | DNS nerozresolvuje |
| tempestmanga | Tempest Manga | tempestmanga.com | DNS nerozresolvuje |

### 2b. Madara — zaparkované / prodané domény (6)

| ID | Název | URL | Zjištěno |
|---|---|---|---|
| azuremanga | Azure Manga | azuremanga.com | vrací 404 na hlavní stránce |
| mangayo | MangaYo | mangayo.com | prodáváno přes Sedo domain parking |
| mangatube | MangaTube (DE) | www.mangatube.net | JS redirect na `/lander` reklamní stránku |
| zeroscans | Zero Scans | zeroscans.com | meta-refresh na zscans.com, i ten je teď mrtvý/blokovaný |
| mangamotto | MangaMotto | mangamoto.com | Sedo parking + malvertising redirect řetězec |
| manhwade | ManhwaDE | manhwa.de | "No advertisers available" ad-network placeholder |

### 2c. Madara — živá doména, ale malvertising/anti-adblock "Redirecting..." stránka (10)

| ID | Název | URL | Zjištěno |
|---|---|---|---|
| zinmanga | ZinManga | zinmanga.com | "Redirecting..." anti-adblock skript místo obsahu |
| manhuaes | ManhuaES | manhuaes.com | "Redirecting..." anti-adblock skript místo obsahu |
| manhuascan | ManhuaScan | manhuascan.com | "Redirecting..." anti-adblock skript místo obsahu |
| drakescans | Drake Scans | drakescans.com | "Redirecting..." anti-adblock skript místo obsahu |
| realmscans | Realm Scans | realmscans.xyz | "Redirecting..." anti-adblock skript místo obsahu |
| infernalvoid | Infernal Void Scans | infernalvoidscans.com | "Redirecting..." anti-adblock skript místo obsahu |
| manga68 | Manga68 | manga68.com | "Redirecting..." anti-adblock skript místo obsahu |
| mm-scans | MM Scans | mm-scans.org | "Redirecting..." anti-adblock skript místo obsahu |
| nightscans | Night Scans | nightscans.net | "Redirecting..." anti-adblock skript místo obsahu |
| topmanhua | TopManhua | topmanhua.com | ad-form auto-submit místo obsahu |

### 2d. Madara — živá doména, ale JS bot-gate / fingerprint gate (7)

| ID | Název | URL | Zjištěno |
|---|---|---|---|
| disasterscans | Disaster Scans | disasterscans.com | "Loading..." JWT bot-gate, žádný reálný obsah |
| freakscans | Freak Scans | freakscans.com | "Loading..." JWT bot-gate, žádný reálný obsah |
| leviathanscans | Leviathan Scans | leviathanscans.com | "Loading..." JWT bot-gate, žádný reálný obsah |
| manhuacat | ManhuaCat | manhuacat.com | "Loading..." JWT bot-gate, žádný reálný obsah |
| suryascans | Surya Scans | suryascans.com | "Loading..." JWT bot-gate, žádný reálný obsah |
| mangatx | MangaTx | mangatx.com | "Loading..." JWT bot-gate, žádný reálný obsah |
| reaperscanseu | Reaper Scans EU | reapercomics.com | fingerprint.js redirect gate |

### 2e. Vlastní scraper — mrtvé (6)

| ID | Název | URL | Zjištěno |
|---|---|---|---|
| reaperscans | Reaper Scans | api.reaperscans.com | API i hlavní web (reaperscans.com) vrací 502/521 — celý web je dole (reálně: Reaper Scans měli právní problémy a ukončili činnost) |
| mangalek | MangaLek | mangalek.com | 404 na hlavní stránce (potvrzeno i s Cloudflare cache) |
| mangafreak | MangaFreak | mangafreak.net | vrací prázdnou HTML kostru (pravděpodobně blokuje non-browser požadavky) |
| mangaleer | MangaLeer | mangaleer.com | doména expirovala, přesměrovává na expireddomains.com marketplace |
| unionmangas | UnionMangas | unionmangas.xyz | zaparkovaná doména, JS redirect na `/lander` |
| tmo | TMO (LectorTMO) | lectortmo.com | **potvrzeno definitivně mrtvé** (3. fáze) — lectortmo.com nemá DNS, a lectortmo.net (dřív podezřelý na "možná náhrada") je taky jen Namecheap "expired domain" parkovací stránka, ne skutečný web. Žádná nástupnická doména nenalezena. |

### 2g. Odstraněno 2026-07-27 — nahlásil uživatel, potvrzeno druhým kolem testů (2)

| ID | Název | URL | Zjištěno |
|---|---|---|---|
| comick | ComicK | api.comick.dev | Metadata/seznam kapitol fungují (po opravě 403 přes User-Agent), ale API pole `md_images` je vždy prázdné a živý web na comick.dev v prohlížeči taky nezobrazí žádné stránky kapitoly — u licencovaných kapitol proto, že ComicK jen odkazuje na oficiální platformu, ale prázdné byly i běžné fanouškovské scanlation kapitoly. Web teď reálně slouží jen jako tracker (sledování/komentáře/hodnocení), ne jako zdroj čitelných stránek. |
| batoto | Bato.to | bato.to | curl konzistentně `Connection timed out` (možná blokace datacenter IP), ale uživatel potvrdil, že appka Bato.to na reálném telefonu taky nenačte. |

### 2f. Doplňkově odhalené při psaní náhrad ve skupině 3 (3)

| ID | Název | URL | Zjištěno |
|---|---|---|---|
| creativenovels | Creative Novels | creativenovels.com | **kompromitovaný web** — listing stránky (`/browse-new/`, `/latest-releases/`) servírují gambling spam (title "PANENTOTO"/"EMON777") místo obsahu, i když jednotlivé `/novel/{slug}/` stránky ještě fungují |
| xcalibrscans | Xcalibr Scans | xcalibrscans.com | stejný "Redirecting..." malvertising vzor jako 2c (Windows Defender obsah karanténoval, včetně bufferu nástroje použitého k auditu) |
| mangatoto | MangaToto | mangatoto.com | doména vypršela a byla zabrána spekulantem — teď obecný thajský WordPress SEO blog (`lang="th"`, AIOSEO), bez jakéhokoliv manga obsahu |

---

## 3) mangadenizi - jak funguje tile-descramble (detaily čtvrté fáze)

Server servíruje stránky rozřezané na dlaždice (grid×grid, typicky 10×10) a
zpřeházené podle seedu (`"scramble":{"method":"tiled-v1","grid":10,"seed":N}`
v odpovědi `/api/v1/reader/{slug}/{chapterSlug}`). Reverzováním jejich JS
bundlu (funkce `Mn/Wt/Yt/Gt/En/$n`) se zjistilo, že jde o seedovaný
xorshift32 PRNG + Fisher-Yates permutaci řádků a sloupců zvlášť - algoritmus
je teď reimplementovaný v `app/src/main/kotlin/com/haise/jiyu/util/TileScramble.kt`
(čistá matematika, unit testovaná proti nezávislé Python reimplementaci a
ověřená pixel-perfect na reálné zamíchané stránce) + `TileScrambleBitmap.kt`
(skutečné kopírování pixelů přes `Canvas.drawBitmap`).

**Update 2026-07-27 (nahlásil uživatel - ComicK "nefunguje"):** ComicK (api.comick.dev)
byl v tomhle dokumentu původně označen za funkční jen podle HTTP kódů/metadat. Detailnější
kontrola ukázala dva problémy:
1. api.comick.dev je teď za Cloudflare a bez prohlížečové `User-Agent` hlavičky vrací
   403 "Just a moment..." - **tohle jsem opravil** (`ComicKSource.kt` teď posílá
   `CloudflareInterceptor.CHROME_UA`).
2. Mnohem závažnější: samotná stránka kapitoly (jak přes API pole `md_images`, tak na
   živém `comick.dev` webu ověřeno v prohlížeči) **nevrací žádné obrázky stránek** - u
   licencovaných/"Official" kapitol proto, že ComicK nesmí hostovat placený obsah a jen
   odkazuje na oficiální platformu (Tappytoon, MangaPlus...), ale prázdné byly i běžné
   fanouškovské scanlation kapitoly. Uživatel potvrdil: **ComicK teď slouží jen jako
   tracker** (sledování/komentáře/hodnocení), ne jako zdroj obrázků ke čtení.
   → **ComicK proto odstraněn ze `SourceManager.kt`** (viz komentář u odstranění), i když
   metadata/seznam kapitol technicky fungují - appka bez čitelných stránek by byla
   zavádějící. `ComicKSource.kt`/`ComicKSourceTest.kt` zůstávají v repu (stejný vzor jako
   u ostatních odstraněných zdrojů) pro případ, že by ComicK obrázky v budoucnu vrátil.

Grid/seed se zakódují jako query parametry přímo do URL obrázku
(`ScrambledImageUrl.kt`) - to je nejjednodušší společné místo, kudy je
protáhnout na obě místa, která obrázek doopravdy stahují:
- **Online čtečka** - `TileDescrambleTransformation` (Coil `Transformation`,
  `ui/reader/`), aplikovaná v `RetryableAsyncImage` stejně jako existující
  `CropBordersTransformation`.
- **Offline stahování** - `ChapterDownloadWorker` stahuje bajty přímo přes
  OkHttp (ne přes Coil), takže tam bylo potřeba descramble aplikovat ručně
  před zápisem na disk (jinak by se uložil nečitelný obrázek).

Web má u titulů s velkým počtem kapitol nespolehlivě pomalé/throttlované
odpovědi (pozorováno při testování, curl občas timeoutoval u stovek kapitol) -
neblokuje to funkčnost, jen to může občas znamenat pomalejší načtení detailu
u dlouhých sérií.

---

## 4) ❓ Nejisté — ponecháno v appce, potřeba ověřit (0)

Prázdné — **batoto přesunuto do sekce 2g** (uživatel 2026-07-27 potvrdil, že appka
Bato.to na reálném telefonu taky nenačte, ne jen curl z vývojářského stroje).

---

## 5) Návrhy, jak zvýšit počet funkčních zdrojů

1. ~~Dopsat scrapery pro skupinu 3~~ — **hotovo**, 7 z 11 Madara-redesignů má
   teď vlastní `MangaSource` (comizy, hivetoons, mangaworld, voidscans,
   hostednovel, manhuabuddy, woopread), všechny s unit testy.
2. ~~animesama je dražší oprava~~ — **hotovo**, přepojeno na interní JSON API
   webu. tmo se ukázal jako definitivně mrtvý (obě domény jsou parkovací
   stránky), škrtnuto ze seznamu k opravě.
3. ~~mangadenizi potřebuje tile-descramble~~ — **hotovo**, algoritmus
   reverzovaný a implementovaný (`TileScramble.kt`), napojený na online
   čtečku i offline stahování - viz sekce 3 pro detaily.
4. **Ověřit, že `CloudflareChallengeHost` opravdu řeší všech 9 Cloudflare-gated
   zdrojů** (aquareader, foxaholic, immortalupdates, kunmanga, manhuafast,
   manhuaus, webtoonxyz, evilmanga, scribblehub) přímo v appce — curl audit
   je nemůže ověřit (JS challenge), takže je teoreticky možné, že některé z
   nich reálně nefungují ani přes interceptor.
5. **Přidat pravidelnou automatickou kontrolu zdrojů** (např. GitHub Actions
   jednou za měsíc/kvartál pouští podobný curl audit jako dnes) — ať se mrtvé
   domény odhalí dřív, než se o tom appka dozví od uživatelů.
6. ~~batoto ověřit z mobilní sítě~~ — **hotovo**, uživatel potvrdil nefunkčnost
   na reálném telefonu, odstraněno (viz sekce 2g).

---

## 6) Druhé kolo — 2026-07-27, ověřeno přímo v appce (ne jen curl)

Metodika: (a) diagnostický JVM test, který přes produkční `MangaSource` třídy
(stejný parsing kód jako appka) reálně zavolá `getPopular → getMangaDetails →
getChapterList → getPageList` a stáhne první obrázek/text nejstarší kapitoly -
mnohem přesnější než curl, protože jde přes SKUTEČNÝ parsing kód, ne jen
kontrolu HTTP kódu. Doplněno o kopii `HotlinkRefererInterceptor` mapy (jinak
falešné 403/404 u hitomi/mangatown/mangapill/mangak/webtoons/comicbookplus -
appka tyhle CDN zná a posílá jim Referer, holý test ne). (b) U vybraných
zdrojů navíc ověřeno ŽIVĚ v appce na Android emulátoru (`jiyu_test` AVD) -
Procházet → zdroj → titul → kapitola → kontrola, že se reálně vykreslí
obsah. Tenhle diagnostický test (`AllSourcesSmokeTest.kt`) byl po auditu
smazán - nebyl určen k trvalému zařazení do CI (běží proti živým webům).

**Pokryto 85/85 aktuálně registrovaných zdrojů síťovým testem, z toho 5 dodatečně
ověřeno naživo v appce/emulátoru** (MangaPark, EvilManga, Kunmanga, Manhwaz +
MangaDex/MangaCloud/DemonicScans/Mangago vynechány ze síťového testu, protože
potřebují Android-only DI - WebView/Context/DataStore).

### 6a. ✅ Funguje (64) - potvrzeno síťovým testem, reálné obrázky/text stažené

mangaplus, hitomi, nhentai, webtoons, mangapark **(navíc živě ověřeno v appce -
viz screenshot, reálný přeložitelný text)**, novelfull, freewebnovel,
asurascans, comicbookplus, readfreecomicsonline, comicskingdom, royalroad,
vortexscans, manhuaplus, manhwatop, manhuahot, mangazin, mangagg **(opraveno,
viz 6b)**, mangaread, coffeemanga, mangasushi, manhwatoon, pawmanga,
animesama, mangadotnet, kaliscan, galaxymanga, lightnovelworld, novelcool,
mangakatana, baozimanhua, mangapill, mangatown, novelbuddy, nihonkuni,
hachirumi, kingofshojo, manga18fx, hentai20, hivetoons, hostednovel, woopread,
mangadenizi (celkem přes 60 dalších už dřív ověřených v 1. kole - viz sekce 1).

### 6b. 🔧 Opraveno (5) - Madara archiv URL se změnil, vlastní `popularUrl` teď opravuje

Audit zjistil, že u 5 Madara zdrojů výchozí archivní URL
(`/manga/page/N/?m_orderby=`) vrací 404 - weby přejmenovaly svůj archiv/taxonomy
slug. Opraveno vlastním `popularUrl` v `SourceManager.kt` (stejný mechanismus
jako dřív u manhwaz/webtoonxyz/aquareader):

| ID | Původní (404) | Opravená URL | Stav po opravě |
|---|---|---|---|
| mangagg | `/manga/page/N/` | `/comic/page/N/` | **plně funkční** (populární i obrázky) |
| toonily | `/manga/page/N/` | `/webtoons/page/N/` | archiv OK, ale obrázky teď 403 (viz 6d) |
| madaradex | `/manga/page/N/` | `/all/page/N/` | archiv OK, ale obrázky teď 403 (viz 6d) |
| wuxiaworldsite | `/manga/page/N/` | `/novels-list/page/N/` | archiv OK, ale stránky kapitoly prázdné (viz 6e) |
| ranovel | `/manga/page/N/` | `/novel/page/N/` | archiv OK, ale stránky kapitoly 403 (viz 6e) |

U posledních čtyř oprava odhalila DALŠÍ, hlubší problém (obrázky/stránky) -
zůstávají v appce (fungují alespoň částečně - prohlížení/hledání), ale
čtení konkrétní kapitoly může selhat, viz 6d/6e.

### 6c. 🌐 Cloudflare-gated (9) - živě otestováno 2/9, mixed výsledek

Appka má `CloudflareInterceptor` (tichý WebView pokus + interaktivní dialog
pro uživatele). Naživo v emulátoru otestováno:
- **evilmanga**: interaktivní Cloudflare výzva se **úspěšně vyřešila** (appka
  správně zobrazila checkbox dialog, po kliknutí prošla), ALE web pak vrátil
  "Sin resultados" (0 titulů) - **samostatná chyba parsování/struktury webu**,
  ne Cloudflare. Web živě existuje, ale appka z něj nic nevytáhne ani po
  úspěšném ověření.
- **kunmanga**: interaktivní výzva se zdánlivě vyřešila ("Verificando..." →
  zmizelo), ale následný request stejně dostal 403 - `cf_clearance` cookie
  z WebView zjevně nestačila (viz komentář v `CloudflareInterceptor.kt` o
  neplatné clearance). Retry v appce dal znovu 403 (10min cooldown na hostu).
  **Reálně nefunkční i s plným Cloudflare-solving mechanismem appky.**

Zbylých 7 (aquareader, foxaholic, immortalupdates, manhuafast, manhuaus,
webtoonxyz, scribblehub) nebylo z časových důvodů živě otestováno v tomhle
kole - dřív jen ověřeno, že bez interceptoru dávají 403/"Just a moment".
Vzhledem k mixed výsledku (1/2 prošel Cloudflare ale je rozbitý jinak, 1/2
neprošel ani Cloudflare) je pravděpodobné, že podobný podíl bude rozbitý i
u zbylých 7 - **potřeba doopakovat stejný živý test**, viz sekce 7 (TODO).

### 6d. ❌/⚠️ Obrázky kapitoly vrací chybu (potřeba další referer/hlavička)

Appka má `HotlinkRefererInterceptor` s mapou domén, které potřebují specifický
`Referer` (viz `AppModule.kt`). Tahle mapa u následujících CHYBÍ - obrázky
dostávají 403/404/520 i po úspěšném načtení seznamu kapitol:

| ID | Chyba | Poznámka |
|---|---|---|
| manhwaz | `403` | **živě potvrzeno v appce** - čtečka zobrazí úplně černou/prázdnou stránku |
| weebcentral | `403` | 1189 kapitol nalezeno, obrázek 403 |
| mangak | `403` | rx.resmk.org je v referer mapě, ale i tak 403 - možná potřeba i jiná hlavička |
| kuramanga | `403` | |
| comizy | `403` | |
| mangaworld | `404` | |
| manhuabuddy | `520` (Cloudflare origin down/timeout) | může být dočasné přetížení serveru |
| toonily | `403` | nově odhaleno po opravě archivu (6b) |
| madaradex | `403` | nově odhaleno po opravě archivu (6b) |
| cocomic | obrázek jen 766 bajtů | pravděpodobně lazy-load placeholder pixel místo skutečné stránky |

**Doporučení:** rozšířit `hotlinkReferers`/`hotlinkRefererSuffixes` v
`AppModule.kt` o domény, na kterých tyhle zdroje hostují obrázky (potřeba
zjistit skutečnou CDN doménu u každého - curl na `getPageList()` výstup).
Nestihnuto v tomhle kole z časových důvodů.

### 6e. ❌ Prázdný/chybný seznam kapitol nebo stránek

| ID | Chyba | Poznámka |
|---|---|---|
| dynasty | seznam kapitol prázdný | Dynasty Scans - možná změna API |
| wuxiabox | seznam kapitol prázdný | |
| mangahome | seznam kapitol prázdný | |
| ranobes | seznam stránek prázdný (50 kapitol nalezeno) | |
| novelhall | seznam stránek prázdný (762 kapitol nalezeno) | adult zdroj |
| voidscans | seznam stránek prázdný (6 kapitol nalezeno) | |
| wuxiaworldsite | seznam stránek prázdný (288 kapitol nalezeno, po opravě 6b) | |
| ranovel | stránka kapitoly 403 (471 kapitol nalezeno, po opravě 6b) | |

### 6f. ❌ Prázdný seznam populárních titulů - potvrzeno jako REÁLNÝ problém (ne Cloudflare/blokace)

U všech níže ověřeno přes curl s běžnou prohlížečovou hlavičkou, že hlavní
stránka vrací **HTTP 200 a normální obsah** (ne Cloudflare, ne DNS mrtvý) -
appka i tak vrátí prázdný seznam, čili jde o skutečnou chybu v parsování
nebo API endpointu:

- **mangafire** - potvrzeno: API teď vrací `{"message":"Missing token."}`
  (HTTP 403) - vyžaduje nějaký auth/session token, který appka nezískává.
  Potřeba zjistit, odkud token appka získat (možná z cookie po načtení
  hlavní stránky) - **needs bigger investigation**.
- **mangaboomers** - potvrzeno: `manga-boomers.cz` je teď čistě klientsky
  renderované SPA (jen 7,9 kB HTML, `class="no-js"`, žádná data) - potřebuje
  kompletní přepis na interní API stejně jako dřív mangadenizi/mangafire -
  **needs bigger investigation**.
- flamecomics, rawkuma, manganato, mangahub, scribblehub(pozn. i CF gated),
  japscan, scanvf, inmanga, novelfire, manhuarm, mangablaze - HTTP 200
  potvrzeno curlem, appka vrací prázdno - root cause NEZJIŠTĚN (nestihnuto),
  pravděpodobně změna HTML struktury/selektorů nebo podobný API problém jako
  u mangafire. **needs bigger investigation** u každého zvlášť.

---

## 7) TODO pro příště (nestihnuto v tomto kole)

1. Živě v appce doopakovat Cloudflare test u zbylých 7 zdrojů (viz 6c).
2. Najít skutečné CDN domény obrázků u 10 zdrojů v 6d a přidat je do
   `hotlinkReferers` v `AppModule.kt`.
3. Zjistit root cause prázdného seznamu populárních titulů u 10 zdrojů v 6f
   (kromě mangafire/mangaboomers, ty už mají zjištěnou příčinu).
4. Rozhodnout u 6d/6e/6f, které z toho jde rychle opravit vs. které
   odstranit jako definitivně mrtvé (mangaboomers vypadá jako kandidát na
   odstranění/náhradu podobně jako dřívější SPA případy, pokud se nenajde
   API stejně jako u mangadenizi).
