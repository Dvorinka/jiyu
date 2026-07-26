# Audit všech zdrojů (manga/manhwa/manhua/novely) — 2026-07-26

Kompletní přehled úplně všech zdrojů, které kdy byly v appce (v `SourceManager.kt`).
Ověřeno ručně přes curl (liveness + kontrola skutečného obsahu, ne jen HTTP kódu —
spousta "živých" domén vrací jen zaparkovanou/reklamní stránku).

**Celkem 134 zdrojů** (73 na generické Madara šabloně + 61 s vlastním scraperem).

| Stav | Počet |
|---|---|
| ✅ Funguje | 85 |
| ❌ Odstraněno — mrtvé / zaparkované / malvertising / kompromitované (nejde opravit) | 45 |
| 🔧 Zbývá opravit — web žije, ale je to dražší oprava (JS-generovaný obsah / SPA / rate-limiting) | 3 |
| ❓ Nejisté, zatím ponecháno — potřeba ověřit z mobilu/reálné appky | 1 |

Ve `SourceManager.kt` u každého odstraněného zdroje zůstal komentář s důvodem
(datum 2026-07-26), takže se dá kdykoliv dohledat, co a proč zmizelo.

**Update (tentýž den, druhá fáze):** napsal jsem 7 nových `MangaSource` tříd pro
zdroje, které se v prvním kole auditu jevily jako "opravitelné" — comizy.io
(náhrada za MangaBuddy), hivetoons.org (náhrada za Hive Scans), mangaworld.mx
(náhrada za MangaWorld IT), voidscans.net, hostednovel.com, manhuabuddy.com a
woopread.com. Všechny mají unit testy a jsou zapojené v appce. Při hledání
náhrad se navíc odhalily 3 další nebezpečné/mrtvé případy (creativenovels,
xcalibrscans, mangatoto) — viz sekce 2b.

---

## 1) ✅ FUNGUJE (85)

### 1a. Nově napsané vlastní scrapery (7) — druhá fáze auditu

| ID | Název | URL | Poznámka |
|---|---|---|---|
| comizy | Comizy | comizy.io | náhrada za MangaBuddy; parsuje `__NEXT_DATA__` JSON |
| hivetoons | HiveToons | hivetoons.org | náhrada za Hive Scans; schema.org `itemProp` mikrodata |
| mangaworld | MangaWorld (IT) | www.mangaworld.mx | vlastní Laravel frontend, nikdy nebyla Madara |
| voidscans | Void Scans | voidscans.net | malý statický Hugo web |
| hostednovel | HostedNovel | hostednovel.com | vlastní Laravel/Vue frontend, NOVEL |
| manhuabuddy | ManhuaBuddy | manhuabuddy.com | kapitoly ze schema.org JSON-LD `ItemList` |
| woopread | WoopRead | woopread.com | Next.js App Router, NOVEL; kapitoly regexem z RSC payloadu |



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
| comick | ComicK | api.comick.dev | API (root vrací 404, reálné endpointy fungují) |
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

### Madara — DNS mrtvé / timeout (14)
astrascan, cosmicscans, isekaiscan, magicscans, mangaeffects, mangafuture (IT),
mangakiss, mangapt, mangarosie, manhuaonline, manhuarock, okumangas (TR),
trillerscans, tempestmanga

### Madara — zaparkované / prodané domény (6)
- **azuremanga** (azuremanga.com) — vrací 404 na hlavní stránce
- **mangayo** (mangayo.com) — prodáváno přes Sedo domain parking
- **mangatube** (mangatube.net, DE) — JS redirect na `/lander` reklamní stránku
- **zeroscans** (zeroscans.com) — meta-refresh na zscans.com, ale i ten je teď mrtvý/blokovaný
- **mangamotto** (mangamoto.com) — Sedo parking + malvertising redirect řetězec
- **manhwade** (manhwa.de) — "No advertisers available" ad-network placeholder

### Madara — živá doména, ale malvertising/anti-adblock "Redirecting..." stránka (10)
zinmanga, manhuaes, manhuascan, drakescans, realmscans, infernalvoid, manga68,
mm-scans, nightscans, topmanhua

### Madara — živá doména, ale JS bot-gate / fingerprint gate (7)
disasterscans, freakscans, leviathanscans, manhuacat, suryascans, mangatx,
reaperscanseu (reapercomics.com)

### Vlastní scraper — mrtvé (5)
- **reaperscans** (api.reaperscans.com) — API i hlavní web (reaperscans.com) vrací
  502/521, celý web je dole (reálně: Reaper Scans měli v minulosti právní problémy
  a ukončili činnost)
- **mangalek** (mangalek.com) — 404 na hlavní stránce (potvrzeno i s Cloudflare cache)
- **mangafreak** (mangafreak.net i www variant) — vrací prázdnou HTML kostru
  (pravděpodobně blokuje non-browser požadavky)
- **mangaleer** (mangaleer.com) — doména expirovala, přesměrovává na
  expireddomains.com marketplace nabídku
- **unionmangas** (unionmangas.xyz) — zaparkovaná doména, JS redirect na `/lander`

### 2b. Doplňkově odhalené při psaní náhrad ve skupině 3 (3)
- **creativenovels** (creativenovels.com) — **kompromitovaný web**: listing
  stránky (`/browse-new/`, `/latest-releases/`) servírují gambling spam
  (title "PANENTOTO"/"EMON777") místo obsahu, i když jednotlivé
  `/novel/{slug}/` stránky ještě fungují. Nepoužívat, dokud si to vlastník
  webu nevyčistí.
- **xcalibrscans** (xcalibrscans.com) — stejný "Redirecting..." malvertising
  vzor jako sekce výše (Windows Defender obsah karanténoval, včetně vlastního
  bufferu nástroje použitého k auditu).
- **mangatoto** (mangatoto.com) — doména vypršela a byla zabrána spekulantem;
  teď je to obecný thajský WordPress SEO blog (`lang="th"`, AIOSEO plugin,
  jen `/blog/`, `/category/`, `/gallery/`) bez jakéhokoliv manga obsahu.

---

## 3) 🔧 ZBÝVÁ OPRAVIT — web žije, ale je to dražší oprava (3)

Tyhle weby mají reálný obsah, ale nejde o prostou výměnu CSS selektorů —
chybí buď JS-generovaný obsah ve statickém HTML, nebo je zdroj nespolehlivě
dostupný (rate-limiting). Nižší priorita, případně přes reverse-engineering
interního API.

- **mangadenizi** (mangadenizi.net, TR) — čistě klientsky renderované Nuxt
  SPA, žádná data v syrovém HTML (`window.__NUXT__` prázdný/chybí), navíc
  agresivní rate-limiting (HTTP 429 i po pár sekundách mezi requesty).
- **animesama** (anime-sama.fr) — doména mrtvá, nová **anime-sama.to** žije
  a je funkční, ALE web byl kompletně přepsán (Tailwind redesign) a **seznam
  kapitol se generuje přes JS `document.write()`**, není ve statickém HTML.
  Nejde o výměnu URL/selektoru — potřeba buď reverse-engineering JS dat, nebo
  najít interní JSON API.
- **tmo** (lectortmo.com) — doména mrtvá (DNS). **lectortmo.net** existuje a
  vrací 200, ale je to čistě klientský SPA shell (Vite bundle, prázdné HTML) —
  nejde bezpečně potvrdit, že je to vůbec TMO, natož ho scrapovat bez API.

### Úspěšně opravené zdroje z této skupiny (7) — viz sekce 1a
Zbytek původní skupiny 3 (11 Madara-redesignů + 0 dalších) se povedlo opravit
vlastním `MangaSource`: voidscans, hostednovel, manhuabuddy, woopread (beze
změny domény) a mangabuddy → **comizy.io**, hivecomic → **hivetoons.org**,
mangaworld → **mangaworld.mx** (přebrandováno).

---

## 4) ❓ Nejisté — ponecháno v appce, potřeba ověřit (1)

- **batoto** (bato.to) — z tohoto stroje konzistentně `curl: (28) Connection
  timed out`. Bato.to je velký populární web, může jít o blokaci datacenter IP
  adres (běžné u anti-scraping ochran), ne o skutečně mrtvý web. **Nechal jsem
  ho v appce** — bylo by riziko odstranit fungující populární zdroj kvůli
  nejednoznačnému testu z vývojářského stroje. Zkus prosím v appce na telefonu,
  jestli Bato.to funguje — pokud ne, dej vědět a odstraním ho taky.

---

## 5) Návrhy, jak zvýšit počet funkčních zdrojů

1. ~~Dopsat scrapery pro skupinu 3~~ — **hotovo**, 7 z 11 Madara-redesignů má
   teď vlastní `MangaSource` (comizy, hivetoons, mangaworld, voidscans,
   hostednovel, manhuabuddy, woopread), všechny s unit testy.
2. **mangadenizi, animesama a tmo jsou dražší oprava** (JS-generovaný obsah /
   SPA / rate-limiting) — nízká priorita, případně zkusit najít jejich interní
   JSON API přes Chrome DevTools Network tab (rychlejší než parsovat JS).
3. **Ověřit, že `CloudflareChallengeHost` opravdu řeší všech 9 Cloudflare-gated
   zdrojů** (aquareader, foxaholic, immortalupdates, kunmanga, manhuafast,
   manhuaus, webtoonxyz, evilmanga, scribblehub) přímo v appce — curl audit
   je nemůže ověřit (JS challenge), takže je teoreticky možné, že některé z
   nich reálně nefungují ani přes interceptor.
4. **Přidat pravidelnou automatickou kontrolu zdrojů** (např. GitHub Actions
   jednou za měsíc/kvartál pouští podobný curl audit jako dnes) — ať se mrtvé
   domény odhalí dřív, než se o tom appka dozví od uživatelů.
5. **batoto ověřit z mobilní sítě** (viz sekce 4) — pokud je fakt jen
   blokovaný z devel. stroje, není co řešit.
