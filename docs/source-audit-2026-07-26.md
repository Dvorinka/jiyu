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

**Stav po čtvrtém kole (2026-07-27, aktuální/finální):** **69 aktivně
registrovaných zdrojů** v `SourceManager.kt` (54 vlastních `MangaSource`
tříd + 15 inline `MadaraSource` instancí, mimo dynamicky přidané uživatelské
Madara zdroje). Od třetího kola dál: **+3 opraveno** (flamecomics, scanvf,
wuxiabox), **-17 odstraněno** (viz sekce 8). Čísla v tabulce výše i v
sekcích 1-7 odpovídají stavu PŘED čtvrtým kolem — sekce 8 je autoritativní
pro finální/aktuální stav.

**Update 2026-07-27 (druhé kolo, na výslovné přání uživatele):** curl-only audit dal u
ComicK a Bato.to falešně pozitivní výsledek (metadata/HTTP kódy vypadaly OK, ale appka
je reálně nemohla použít ke čtení). Proto teď probíhá druhé kolo ověřování **přímo v
appce na emulátoru** - u každého zdroje se zkouší otevřít konkrétní titul a kapitolu,
ne jen zkontrolovat HTTP odpověď. Průběžné výsledky viz sekce 6 na konci dokumentu.

**Update 2026-07-27 (třetí kolo):** doopraveny/prošetřeny všechny zdroje označené
druhým kolem jako "needs bigger investigation" (sekce 6c-6f). Shrnutí: **9 skutečně
opraveno** (6× hotlink referer v 6d, 2× parsovací selektor - MadaraSource NOVEL
větev + NovelFire, 1× zastaralá doména - Japscan), **6 se ukázalo jako falešný
poplach** z minulého kola (fungují beze změny na reálném titulu/kapitole), **~20
zůstává jako needs bigger investigation** (buď skutečná Cloudflare JS výzva čekající
na živý test, nebo web mezitím kompletně změnil platformu/strukturu a vyžaduje
srovnatelné úsilí jako dřívější mangadenizi přepis). Detaily viz sekce 6c-6f.

**Update 2026-07-27 (čtvrté kolo — finální rozhodnutí, live debug test):**
dořešeny všechny zbývající "needs bigger investigation" položky ze sekcí
6c-6f. **3 zdroje opraveny** (flamecomics, scanvf, wuxiabox — kompletní
přepis/oprava selektorů). **17 zdrojů odstraněno** jako definitivně
nefunkční — mj. živý debug test přímo v appce (dočasné logování v
`CloudflareInterceptor`/`EvilMangaSource`, viz sekce 8) odhalil, že
`CloudflareInterceptor` sice u reálné Turnstile výzvy získá platný
`cf_clearance` cookie (tichý i interaktivní WebView solve), ale OkHttp
replay s tímhle cookie je origin serverem STEJNĚ odmítnut — jde o
architektonický TLS/HTTP-otisk mismatch mezi WebView a OkHttp klientem,
ne o chybějící kód. Detaily a úplný seznam viz **sekce 8**.

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

### 6c. 🌐 Cloudflare-gated (9) - třetí kolo 2026-07-27, evilmanga doladěno, zbytek needs bigger investigation

Appka má `CloudflareInterceptor` (tichý WebView pokus + interaktivní dialog
pro uživatele). Naživo v emulátoru dotestováno:
- **evilmanga**: znovu naživo otestováno po opravě - `getPopular()` používal
  `$base/?page=N` (jen stránkování WP blogu/homepage), zatímco CSS selektory
  v kódu (`.page-item-detail` aj.) prozrazují, že web běží na Madara šabloně
  se standardním archivem. Opraveno na `$base/manga/page/N/?m_orderby=`
  (`EvilMangaSource.kt`). **Přesto i po opravě URL appka pořád vrací "Sin
  resultados"** - tentokrát se navíc při live testu vůbec nezobrazil žádný
  Cloudflare interaktivní dialog (na rozdíl od dřívějšího pozorování), takže
  buď `CloudflareInterceptor` požadavek řeší tiše a neúspěšně, nebo appka
  dostává jinou chybu, kterou tichý try/catch v `getPopular()` polyká beze
  stopy v logcatu. Oprava URL zůstává v kódu (je správná bez ohledu na CF),
  ale **zdroj zůstává nefunkční - needs bigger investigation** (potřeba
  dočasně odstranit try/catch a zjistit skutečnou výjimku/HTTP kód).
- **kunmanga**: nedotestováno znovu tento kruh (viz předchozí kolo - CF výzva
  se zdánlivě vyřeší, ale `cf_clearance` cookie z WebView je neplatná, 403
  přetrvává i po retry). **needs bigger investigation** (vyžadovalo by opravu
  přímo v `CloudflareInterceptor`/WebView cookie handlingu).
- **webtoonxyz**: reálná URL (`www.webtoon.xyz/read/page/1/?m_orderby=`,
  ne uhodnutá `webtoonxyz.com`) reconfirmed jako Cloudflare "Just a moment"
  (403) i s běžnou prohlížečovou hlavičkou - živě nedotestováno.
- **aquareader, foxaholic, immortalupdates, manhuafast, manhuaus,
  scribblehub**: reconfirmed curlem 2026-07-27 - všech 6 pořád vrací skutečnou
  Cloudflare JS výzvu (`<title>Just a moment...</title>`, 403) i s běžnou
  prohlížečovou hlavičkou, nejde tedy o odumřelé domény. **Živé UI otestování
  v appce nestihnuto** z časových důvodů (každý test = několik minut
  navigace přes uiautomator) - zůstává jako TODO, viz sekce 7.

### 6d. 🔧 Obrázky kapitoly vrací chybu — druhé kolo oprav 2026-07-27

Appka má `HotlinkRefererInterceptor` s mapou domén, které potřebují specifický
`Referer` (viz `AppModule.kt`). U 6 z 10 zdrojů šlo o skutečně chybějící/zastaralý
záznam v mapě - **opraveno**:

| ID | CDN doména obrázků | Zjištěno / oprava | Stav |
|---|---|---|---|
| manhwaz | `cdn.manhwaz.com` | chybějící přesný host, přidáno do `hotlinkReferers` | **opraveno** |
| toonily | `data.tnlycdn.com` | chybějící přesný host, přidáno do `hotlinkReferers` | **opraveno** |
| kuramanga | `shadowabyss.com` | chybějící přesný host, přidáno do `hotlinkReferers` | **opraveno** |
| manhuabuddy | `cdn.manhuabuddy.com` | 520 byl ve skutečnosti hotlink-block CDN, ne výpadek originu; navíc `ManhuaBuddySource.kt` mělo druhou, závažnější chybu - `div.visual a` teď vrací **relativní** href (dřív absolutní, web prošel redesignem), což shazovalo `getMangaDetails`/`getChapterList` přes `IllegalArgumentException` v OkHttp (chyceno tichým try/catch). Opraveno absolutizací URL + referer mapou. | **opraveno** |
| comizy | `x{N}.cmzcdn.org` (subdoména se mění chapter od chapteru) | přidáno do `hotlinkRefererSuffixes` (suffix `cmzcdn.org`) | **opraveno** |
| mangak | `rx.{náhodné-slovo}.org` (CELÁ 2. úroveň domény rotuje - `rx.resmk.org` v mapě byl zastaralý a prakticky nikdy nesedí) | přidán nový mechanismus `hotlinkRefererPrefixes` (match podle prefixu hostu `rx.`), starý přesný záznam smazán | **opraveno** |
| weebcentral | `hot.planeptune.us` | naživo funkčně i bez Refereru, přesto přidáno defenzivně (levné, CDN hotlink pravidla bývají nekonzistentní) | **opraveno (defenzivně)** |
| cocomic | - | znovu otestováno - obrázky (`img.cocomic.co`) se teď stahují normálně bez úpravy, dřívější "766 bajtů" pozorování bylo zřejmě jednorázové/dočasné | **funguje beze změny** |
| mangaworld | - | znovu otestováno - `cdn.mangaworld.mx/.../{n}.jpg` vrací 200 pro všechny stránky testované kapitoly bez Refereru, 404 se nereprodukoval | **funguje beze změny** |
| madaradex | `cdn.madaradex.org` | **NEOPRAVENO** - vrací 403 i s Refereru (vlastní stránka "MadaraDex • 403", `server: cloudflare`), tzn. jde o skutečné Cloudflare WAF pravidlo na CDN subdoméně, ne jen chybějící hlavičku. Web/archiv/hledání dál funguje, jen čtení konkrétní kapitoly ne. Řešilo by se jedině rozšířením `CloudflareInterceptor` i na CDN subdomény obrázků (výzva/cookie tam může být jiná než na hlavní doméně) - **needs bigger investigation**, ponecháno v appce (browse/search funguje). | **needs bigger investigation** |

Kód: `app/src/main/kotlin/com/haise/jiyu/di/AppModule.kt` (referer mapy +
nový `hotlinkRefererPrefixes`), `app/src/main/kotlin/com/haise/jiyu/source/manhuabuddy/ManhuaBuddySource.kt`
(absolutizace URL).

### 6e. ❌ Prázdný/chybný seznam kapitol nebo stránek

**Update 2026-07-27 (třetí kolo):** 5 z 8 se ukázalo jako falešný poplach -
ruční ověření konkrétního reálného titulu/kapitoly (ne náhodně vybraného,
který mohl mít 0 kapitol nebo být uzamčený) prošlo bez problémů, beze změny
kódu. Skutečná chyba se potvrdila jen u 2 (wuxiaworldsite/ranovel - společná
příčina, opravena) + wuxiabox zůstává nevyřešen:

| ID | Chyba | Výsledek |
|---|---|---|
| dynasty | seznam kapitol prázdný | **re-test OK** - `.chapter-list dd a[href*='/chapters/']` funguje, prázdno bylo jen u konkrétního titulu bez nahraných kapitol |
| wuxiabox | seznam kapitol prázdný | **potvrzeno, needs bigger investigation** - AJAX endpointy `/e/extend/fy.php` i `/e/extend/fy1.php?wjm={slug}` vrací prázdný `<ul class="chapter-list">`; detail stránka neobsahuje žádnou stopu po správném volání (pravděpodobně jinačí endpoint/parametr skrytý v minifikovaném `app.min.js`) |
| mangahome | seznam kapitol prázdný | **re-test OK** - `ul.detail-chlist li` i `img.image[src]` fungují na reálném titulu |
| ranobes | seznam stránek prázdný (50 kapitol nalezeno) | **re-test OK** - `#arrticle` (skutečný, ne překlep) obsahuje plný text na reálné kapitole |
| novelhall | seznam stránek prázdný (762 kapitol nalezeno) | **re-test OK** - `div.book-catalog li a` i `div#htmlContent` fungují na reálné kapitole |
| voidscans | seznam stránek prázdný (6 kapitol nalezeno) | **re-test OK** - `img[data-elem=pinchzoomer]` funguje, jen `div.card.shadow-sm` třída má navíc `h-100` (CSS multi-class selektor to nevadí) |
| wuxiaworldsite | seznam stránek prázdný (288 kapitol nalezeno, po opravě 6b) | **opraveno** - skutečná příčina: `MadaraSource.getPageList()` uměl vytáhnout jen `<img>` (obrázky), ale Madara-šablonové NOVEL weby (`contentTypeOverride = "NOVEL"`) mají obsah kapitoly jako text v `<p>` uvnitř `.reading-content` - appka tak u KAŽDÉHO Madara NOVEL zdroje vždy vracela 0 stránek. Přidána větev v `MadaraSource.kt` - když `contentTypeOverride == "NOVEL"`, vytáhne se text z nového `selectors.novelContent` (`div.reading-content p`) a vrátí se jako `Page("novel://text")` sentinel (stejná konvence jako `NovelHallSource`/`WuxiaBoxSource`). Přidán test `getPageList extracts paragraph text for NOVEL content type`. |
| ranovel | stránka kapitoly 403 (471 kapitol nalezeno, po opravě 6b) | **stejná NOVEL oprava aplikována**, ale zdroj navíc má vlastní problém - konkrétně stránky KAPITOL (ne archiv/detail) jsou za skutečnou Cloudflare JS výzvou (`Cf-Mitigated: challenge`, `Just a moment...`) - archiv/detail prochází bez problémů. Spoléhá na existující `CloudflareInterceptor`, živě netestováno tento kruh (stejná kategorie jako 6c) - **needs bigger investigation / live test**. |

### 6f. ❌ Prázdný seznam populárních titulů - potvrzeno jako REÁLNÝ problém (ne Cloudflare/blokace)

U všech níže ověřeno přes curl s běžnou prohlížečovou hlavičkou, že hlavní
stránka vrací **HTTP 200 a normální obsah** (ne Cloudflare, ne DNS mrtvý) -
appka i tak vrátí prázdný seznam, čili jde o skutečnou chybu v parsování
nebo API endpointu:

**Update 2026-07-27 (třetí kolo) - root cause zjištěn/doopraven u všech 13:**

- **mangafire** - reconfirmed: API stále vrací `{"message":"Missing token."}`
  (HTTP 403) - vyžaduje auth/session token, který appka nezískává. **needs
  bigger investigation** (potřeba zjistit zdroj tokenu, možná cookie po
  načtení hlavní stránky).
- **mangaboomers** - reconfirmed: `manga-boomers.cz` je pořád čistě klientsky
  renderované SPA (7,9 kB HTML) - **needs bigger investigation** (potřeba
  najít interní API jako dřív u mangadenizi/mangafire).
- **flamecomics** - **nová příčina zjištěna**: web přešel z Madara na
  kompletně přepsanou Next.js aplikaci (`/_next/static/...`, archiv na
  `/browse`, detail na `/series/{id}`, `__NEXT_DATA__` s `series_id`) -
  `FlameComicsSource.kt` cílí na starou Madara `/manga/?...` URL, která už
  vůbec neexistuje. **needs bigger investigation** (kompletní přepis
  zdrojové třídy na nové Next.js API, srovnatelné úsilí jako mangadenizi).
- **rawkuma** - **nová příčina zjištěna**: web se přestěhoval na novou
  doménu `rawkuma.net` (z `.com`) s úplně jiným archivem
  (`/library/?the_page=N&the_genre=...`, ne Madara `/manga/page/N/`).
  **needs bigger investigation** (kompletní přepis na novou strukturu).
- **manganato** (`natomanga.com`) - **nová příčina zjištěna**: doména je teď
  za skutečnou Cloudflare JS výzvou (`Cf-Mitigated: challenge`) - dřív
  fungovala bez ochrany. Spoléhá na existující `CloudflareInterceptor`,
  živě netestováno - stejná kategorie jako 6c, **needs bigger investigation
  / live test**.
- **mangahub** - **nelze ověřit z tohoto prostředí**: GraphQL API
  (`api.mghubcdn.com/graphql`) při curlu na Windows spadne do
  schannel TLS re-negotiation smyčky (nesouvisí nutně se skutečnou
  dostupností - může to být jen Windows-curl kvirk, ne realny problem
  appky) - doporučeno ověřit přímo v appce/emulátoru.
- **scribblehub** - Cloudflare-gated, viz 6c.
- **japscan** - **opraveno (částečně)**: `japscan.lol` přesměrovává na
  novou doménu `japscan.foo` ("Nous avons déménagé"/"přestěhovali jsme se").
  `JapscanSource.kt` aktualizován na novou doménu, ale ta je navíc za
  Cloudflare JS výzvou - spoléhá na `CloudflareInterceptor`, živě
  netestováno.
- **scanvf** - **needs bigger investigation**: archivní URL vrací HTTP 200
  s reálným obsahem stránky, ale žádná z očekávaných karet titulů
  (`.manga-poster`/`.bsx`/`.novel-item`) ani žádný `/manga/` odkaz se v HTML
  vůbec nevyskytuje - buď se listing teď generuje přes JS/AJAX, nebo
  `sort=views` je neplatný parametr, který tiše vrátí 0 výsledků.
- **inmanga** - **needs bigger investigation**: archivní URL
  `/ver/manga/lista` vrací 404 (web přešel na `/manga/consult`), ale ani
  nová cesta neobsahuje žádné z očekávaných tříd (`.manga-card`/`.thumbnail`)
  ani `/ver/manga/` odkazy - vypadá to na JS/AJAX-driven listing.
- **novelfire** - **opraveno**: web přejmenoval `h2.title a` na
  `a:has(h4.novel-title)` (redesign karty titulu, přibyl druhý odkaz na
  poslední kapitolu uvnitř stejného bloku, na který starý selektor
  omylem mohl narazit). Opraven selektor v `NovelFireSource.kt` + fixture
  v testu aktualizována na reálnou strukturu.
- **manhuarm** - **falešný poplach, funguje bez úprav**: `/manga/page/1/`
  vrací 301 → 200 (redirect) - curl bez `-L` i pravděpodobně předchozí
  test bez sledování redirectů to vyhodnotily jako selhání, appka
  (OkHttp) redirecty sleduje automaticky.
- **mangablaze** - **needs bigger investigation**: web běží na hluboce
  přetémovaném Madara (vlastní `a.acard`/`.ac-t` karty na archivu, a na
  detailu/kapitole žádný z výchozích Madara selektorů vůbec nesedí -
  celý template vypadá jako bespoke, ne jen upravené CSS třídy).
  Oprava jen archivní karty by appku nedostala k reálně čitelné kapitole,
  proto neaplikováno - vyžadovalo by kompletní vlastní `MangaSource`
  třídu srovnatelnou s náročností mangadenizi.

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

Aktualizace: viz sekce 8 — všechny čtyři body výše byly v následujícím kole
dořešeny (dořešeno = fix nebo definitivní odstranění, nic nezůstalo viset).

---

## 8) Čtvrté kolo — 2026-07-27, live debug test + finální rozhodnutí (fix vs. remove)

Cíl tohoto kola: u každé zbývající "needs bigger investigation" položky ze
sekcí 6c-6f buď najít skutečnou opravu, nebo definitivně rozhodnout o
odstranění — žádná položka nezůstává trvale v limbu.

### 8a. ✅ Opraveno (3)

| ID | Příčina | Oprava |
|---|---|---|
| flamecomics | web přešel z Madara na Next.js (`/browse`, `/series/{id}`, `/series/{id}/{token}`) | Kompletní přepis `FlameComicsSource.kt` na parsování `__NEXT_DATA__` JSON (`props.pageProps.series`/`chapters`/`chapter.images`) místo mrtvých Madara HTML selektorů. Obrázky na `cdn.flamecomics.xyz/uploads/images/series/{id}/{token}/{name}`. Testy přepsány na JSON fixtures. |
| scanvf (scan-vf.net) | web prošel redesignem na Bootstrap "media" karty — staré selektory (`.manga-poster`/`.bsx`/`.novel-item`, `.chapter-list li a`) v HTML vůbec neexistují | Nové selektory: archiv `div.media > .media-heading a.chart-title`, detail `dt`/`dd` páry (`Auteur(s)`/`Statut`) přes `:matchesOwn`, kapitoly `h5.chapter-title-rtl a`, čtečka `img.img-responsive[data-src]`. |
| wuxiabox | `getPopular()` scrapoval `/updates/{page}.html`, což jsou karty JEDNOTLIVÝCH KAPITOL (`/novel/{slug}_{číslo}.html`), ne katalog titulů — `manga.url` pak ukazoval na kapitolu místo na detail, takže `getChapterList()` posílalo špatný `wjm=` slug do `fy.php` a vždy dostalo prázdno | Přepnuto na skutečný katalog `/list/all/all-onclick-{page-1}.html` (0-indexováno, řazeno podle počtu prokliků), který odkazuje přímo na `/novel/{slug}.html`. `fy.php` endpoint samotný byl vždy funkční — chyba byla jen ve zdrojové URL. |

Všechny tři mají aktualizované/přepsané unit testy, `:app:testDebugUnitTest`
prochází. Commit `1536a6f`.

### 8b. 🔬 Klíčový nález — architektonický limit `CloudflareInterceptor` (evilmanga)

Živý debug test přímo v appce na emulátoru (`jiyu_test` AVD), s dočasným
`android.util.Log.e` instrumentováním `CloudflareInterceptor.kt` a
`EvilMangaSource.kt` (odstraněno po testu, žádná trvalá změna v kódu):

1. Request na `evil-manga.eu/manga/page/1/?m_orderby=` → `403`,
   `Cf-Mitigated: challenge` → `isCloudflareBlocked()` = `true`, cooldown
   `false` → interceptor spustí solve flow.
2. Tichý WebView solve (`solveCloudflareSynchronously`) **uspěje** (~12 s) —
   `silentCookies=true`, `cookiesLen=674`, i přes `blockNetworkImage=true`.
3. Retry originálního requestu s tímhle cookie (`request.withClearance`) →
   **`retriedCode=403 retriedBlocked=true`** — origin server cookie
   odmítne, i když je z pohledu WebView "platná" (obsahuje `cf_clearance`).

Závěr: Cloudflare Turnstile váže `cf_clearance` na TLS/HTTP otisk klienta,
který výzvu vyřešil (WebView/Chromium engine) — když stejný cookie
"přehraje" úplně jiný HTTP klient (OkHttp), Cloudflare to vyhodnotí jako
podezřelé a blokuje dál. Tohle **není bug v appce** ani chybějící
try/catch — je to zásadní architektonické omezení současného přístupu
(WebView řeší výzvu, OkHttp dělá reálné requesty). Oprava by vyžadovala
směrovat VŠECHNY requesty na takhle chráněné domény přes WebView síťovou
vrstvu (zásadní přestavba, ne "quick fix").

Stejná kategorie (curl 2026-07-27 reconfirmed skutečnou `Just a moment...`
403 Turnstile výzvu na všech): **kunmanga, webtoonxyz, aquareader,
foxaholic, immortalupdates, manhuafast, manhuaus, scribblehub, manganato
(natomanga.com), ranovel** (stránky kapitol, ne archiv/detail).

### 8c. ❌ Odstraněno (17)

| ID | Kategorie | Důvod |
|---|---|---|
| evilmanga | Cloudflare Turnstile (viz 8b) | živě potvrzený TLS-otisk mismatch, needs bigger investigation → definitivně unfixable v současné architektuře |
| kunmanga | Cloudflare Turnstile | stejná kategorie jako evilmanga (potvrzeno už ve 3. kole — čerstvá clearance stejně 403) |
| webtoonxyz | Cloudflare Turnstile | curl reconfirmed `Just a moment...` 403 |
| aquareader | Cloudflare Turnstile | curl reconfirmed `Just a moment...` 403 |
| foxaholic | Cloudflare Turnstile | curl reconfirmed `Just a moment...` 403 |
| immortalupdates | Cloudflare Turnstile | curl reconfirmed `Just a moment...` 403 |
| manhuafast | Cloudflare Turnstile | curl reconfirmed `Just a moment...` 403 |
| manhuaus | Cloudflare Turnstile | curl reconfirmed `Just a moment...` 403 |
| scribblehub | Cloudflare Turnstile | curl reconfirmed `Just a moment...` 403 |
| manganato (natomanga.com) | Cloudflare Turnstile | nově chráněno (dřív fungovalo bez ochrany) |
| ranovel | Cloudflare Turnstile na stránkách kapitol | archiv/detail OK, ale čtení kapitoly nikdy nepůjde — NOVEL oprava z 6e zůstává v `MadaraSource.kt` pro budoucí zdroje |
| madaradex | CDN WAF blok (ne Turnstile) | `cdn.madaradex.org` vrací 403 i se správným Refererem (viz 6d) — archiv by fungoval, čtení kapitoly ne |
| mangahub | anti-adblock/bot JS gate (NE Cloudflare) | **ověřeno živě v appce**: GraphQL API vrací HTTP 200, ale tělo je "Redirecting..." JS interstitial, ne JSON — `JSONObject` parsing tiše selže, appka nemá infrastrukturu na řešení tohohle typu gate |
| rawkuma | přesun na novou doménu/strukturu + CF hard-block | `rawkuma.net` (WordPress+htmx) má skrytý JS lazyload mechanismus pro archiv karty (ne standardní `hx-get`); opakované curl requesty navíc narazily na skutečný Cloudflare "you have been blocked" hard-block |
| inmanga | JS/AJAX-driven archiv | AngularJS "Factory" SPA, reálný endpoint `/manga/GetMangasConsultResult` nalezen, ale přesný JSON tvar `filterSettings` parametru se nepodařilo v rozumném čase uhodnout |
| mangaboomers | JS/AJAX-driven detail/kapitoly | seznam (`/api/mangalist`) funguje, ale `/api/mangaInfo`/`/api/loadChapters` vyžadují neznámý tvar POST parametru (vyzkoušeny: `id`, `mangaId`, `manga_id`, `mangaID`, JSON body, cookie session — žádná varianta nefunguje) |
| mangablaze | bespoke Madara varianta | vlastní `a.acard`/`.ac-t` karty, žádný výchozí Madara selektor nesedí — vyžadovalo by vlastní `MangaSource` třídu srovnatelnou s mangadenizi |

Všechny `.kt` třídy a testy odstraněných zdrojů **zůstávají na disku**
(mrtvý kód, stejná konvence jako u předchozích odstranění) pro případ
budoucí opravy — jen odebrány z `SourceManager.kt` (import, konstruktor,
instance v `staticSources`). Komentáře s důvodem viz přímo v
`SourceManager.kt` u místa, kde zdroj dřív byl.

**Nezkoumáno dál v tomto kole** (mimo scope, nebyly součástí 6c-6f seznamu
"needs bigger investigation"): mangafire (chybí auth token — 6f).

Commit `faff271`.
