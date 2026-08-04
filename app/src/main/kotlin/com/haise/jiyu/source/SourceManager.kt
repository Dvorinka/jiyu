package com.haise.jiyu.source

import com.haise.jiyu.data.db.CustomSourceDao
import com.haise.jiyu.source.comizy.ComizySource
import com.haise.jiyu.source.hivetoons.HiveToonsSource
import com.haise.jiyu.source.mangaworld.MangaWorldSource
import com.haise.jiyu.source.voidscans.VoidScansSource
import com.haise.jiyu.source.hostednovel.HostedNovelSource
import com.haise.jiyu.source.mangadenizi.MangaDeniziSource
import com.haise.jiyu.source.manhuabuddy.ManhuaBuddySource
import com.haise.jiyu.source.woopread.WoopReadSource
import com.haise.jiyu.source.dynasty.DynastySource
import com.haise.jiyu.source.hitomi.HitomiSource
import com.haise.jiyu.source.mangapark.MangaParkSource
import com.haise.jiyu.source.mangago.MangagoSource
import com.haise.jiyu.source.asurascans.AsuraScansSource
import com.haise.jiyu.source.flamecomics.FlameComicsSource
import com.haise.jiyu.source.comic.ComicBookPlusSource
import com.haise.jiyu.source.comic.ReadFreeComicsOnlineSource
import com.haise.jiyu.source.comicskingdom.ComicsKingdomSource
import com.haise.jiyu.source.novelfull.NovelFullSource
import com.haise.jiyu.source.freewebnovel.FreeWebNovelSource
import com.haise.jiyu.source.nhentai.NhentaiSource
import com.haise.jiyu.source.madara.MadaraSelectors
import com.haise.jiyu.source.madara.MadaraSource
import com.haise.jiyu.source.mangadex.MangaDexSource
import com.haise.jiyu.source.mangaplus.MangaPlusSource
import com.haise.jiyu.source.webtoon.WebtoonSource
import com.haise.jiyu.source.royalroad.RoyalRoadSource
import com.haise.jiyu.source.weebcentral.WeebCentralSource
import com.haise.jiyu.source.vortexscans.VortexScansSource
import com.haise.jiyu.source.mangak.MangaKSource
import com.haise.jiyu.source.i18n.JapscanSource
import com.haise.jiyu.source.i18n.AnimeSamaSource
import com.haise.jiyu.source.i18n.ScanVFSource
import com.haise.jiyu.source.mangadotnet.MangaDotNetSource
import com.haise.jiyu.source.kaliscan.KaliScanSource
import com.haise.jiyu.source.mangacloud.MangaCloudSource
import com.haise.jiyu.source.galaxymanga.GalaxyMangaSource
import com.haise.jiyu.source.kuramanga.KuraMangaSource
import com.haise.jiyu.source.lightnovelworld.LightNovelWorldSource
import com.haise.jiyu.source.novelfire.NovelFireSource
import com.haise.jiyu.source.wuxiabox.WuxiaBoxSource
import com.haise.jiyu.source.ranobes.RanobesSource
import com.haise.jiyu.source.novelcool.NovelCoolSource
import com.haise.jiyu.source.novelhall.NovelHallSource
import com.haise.jiyu.source.mangakatana.MangaKatanaSource
import com.haise.jiyu.source.baozimanhua.BaoziManhuaSource
import com.haise.jiyu.source.mangapill.MangapillSource
import com.haise.jiyu.source.mangatown.MangaTownSource
import com.haise.jiyu.source.novelbuddy.NovelBuddySource
import com.haise.jiyu.source.mangahome.MangaHomeSource
import com.haise.jiyu.source.nihonkuni.NihonKuniSource
import com.haise.jiyu.source.hachirumi.HachirumiSource
import com.haise.jiyu.source.kingofshojo.KingofshojoSource
import com.haise.jiyu.source.manga18fx.Manga18fxSource
import com.haise.jiyu.source.hentai20.Hentai20Source
import com.haise.jiyu.source.demonicscans.DemonicScansSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centrální registr zdrojů. Statické zdroje (MangaDex, MANGA Plus, ComicK)
 * jsou pevně dané; k nim se přidávají uživatelem nakonfigurované generické
 * Madara zdroje z `CustomSourceDao` - proto je seznam reaktivní (Flow),
 * ne statický snapshot.
 */
@Singleton
class SourceManager @Inject constructor(
    mangaDexSource: MangaDexSource,
    mangaPlusSource: MangaPlusSource,
    hitomiSource: HitomiSource,
    nhentaiSource: NhentaiSource,
    webtoonSource: WebtoonSource,
    dynastySource: DynastySource,
    mangaParkSource: MangaParkSource,
    novelFullSource: NovelFullSource,
    freeWebNovelSource: FreeWebNovelSource,
    mangagoSource: MangagoSource,
    asuraScansSource: AsuraScansSource,
    flameComicsSource: FlameComicsSource,
    comicBookPlusSource: ComicBookPlusSource,
    readFreeComicsOnlineSource: ReadFreeComicsOnlineSource,
    comicsKingdomSource: ComicsKingdomSource,
    royalRoadSource: RoyalRoadSource,
    weebCentralSource: WeebCentralSource,
    vortexScansSource: VortexScansSource,
    mangaKSource: MangaKSource,
    japscanSource: JapscanSource,
    animeSamaSource: AnimeSamaSource,
    scanVFSource: ScanVFSource,
    mangaDotNetSource: MangaDotNetSource,
    kaliScanSource: KaliScanSource,
    mangaCloudSource: MangaCloudSource,
    galaxyMangaSource: GalaxyMangaSource,
    kuraMangaSource: KuraMangaSource,
    lightNovelWorldSource: LightNovelWorldSource,
    novelFireSource: NovelFireSource,
    wuxiaBoxSource: WuxiaBoxSource,
    ranobesSource: RanobesSource,
    novelCoolSource: NovelCoolSource,
    novelHallSource: NovelHallSource,
    mangaKatanaSource: MangaKatanaSource,
    baoziManhuaSource: BaoziManhuaSource,
    mangapillSource: MangapillSource,
    mangaTownSource: MangaTownSource,
    novelBuddySource: NovelBuddySource,
    mangaHomeSource: MangaHomeSource,
    nihonKuniSource: NihonKuniSource,
    hachirumiSource: HachirumiSource,
    kingofshojoSource: KingofshojoSource,
    manga18fxSource: Manga18fxSource,
    hentai20Source: Hentai20Source,
    demonicScansSource: DemonicScansSource,
    comizySource: ComizySource,
    hiveToonsSource: HiveToonsSource,
    mangaWorldSource: MangaWorldSource,
    voidScansSource: VoidScansSource,
    hostedNovelSource: HostedNovelSource,
    manhuaBuddySource: ManhuaBuddySource,
    woopReadSource: WoopReadSource,
    mangaDeniziSource: MangaDeniziSource,
    private val customSourceDao: CustomSourceDao,
    private val client: OkHttpClient,
    private val settings: com.haise.jiyu.settings.SettingsRepository,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _cache = MutableStateFlow<List<MangaSource>>(emptyList())

    private val staticSources: List<MangaSource> = listOf(
        mangaDexSource,
        mangaPlusSource,
        // ComicK (api.comick.dev) odstraněno 2026-07-27 - web i API teď fungují jen jako
        // "tracker" (odkazuje na oficiální licencované platformy jako Tappytoon/MangaPlus),
        // reálné stránky kapitol (md_images) API nevrací a ani samotný web comick.dev je
        // v čtečce nezobrazí (ověřeno naživo v prohlížeči) - žádné reálné obrázky ke stažení.
        // Metadata/seznam kapitol by šly, ale appka bez čitelných stránek by byla zavádějící.
        // Viz ComicKSource.kt / ComicKSourceTest.kt (ponecháno pro případ, že by se to vrátilo).
        hitomiSource,
        nhentaiSource,
        // MangaFire odstraněno 2026-07-27 (čtvrté kolo) - API (mangafire.to/api/titles)
        // je od pohledu bez autentizace, ale vrací {"message":"Missing token."} (403).
        // Analýza staženého JS bundlu (main-tit729-*.js) odhalila vzor axios
        // withXSRFToken - klient musí poslat hlavičku X-XSRF-TOKEN podle hodnoty
        // cookie XSRF-TOKEN, tu ale server nikdy nenastavuje přes Set-Cookie;
        // v bundlu je i odkaz na "turnstile" - cookie se zjevně nastavuje až
        // klientským JS po vyřešení neviditelné Cloudflare Turnstile výzvy, stejný
        // architektonický limit jako u evilmanga/kunmanga/atd. (viz sekce 8b v
        // docs/source-audit-2026-07-26.md). Viz MangaFireSource.kt (ponecháno pro
        // případ, že by appka v budoucnu směrovala requesty přes WebView).
        // Bato.to odstraněno 2026-07-27 - z vývojářského stroje šlo jen o "connection
        // timed out" (možná blokace datacenter IP), ale uživatel potvrdil, že appka na
        // reálném telefonu Bato.to taky nenačte. Viz BatoToSource.kt (ponecháno pro
        // případ, že by se to v budoucnu vrátilo).
        webtoonSource,
        dynastySource,
        mangaParkSource,
        novelFullSource,
        freeWebNovelSource,
        mangagoSource,
        asuraScansSource,
        flameComicsSource,
        comicBookPlusSource,
        readFreeComicsOnlineSource,
        comicsKingdomSource,
        royalRoadSource,
        weebCentralSource,
        vortexScansSource,
        mangaKSource,
        // ── Manhua (čínské komiksy) ──────────────────────────────────────────
        // manhuafast/manhuaus: uživatel 2026-07-27 upozornil, že oba weby v appce
        // Kotatsu fungují bez problémů, a Kotatsu parser je (na rozdíl od
        // ImmortalUpdates) neoznačuje jako @Broken. Kvůli tomu byly dočasně
        // vráceny zpět a přetestovány ŽIVĚ v appce na emulátoru (ne jen curlem):
        // - manhuafast: GET archiv (`/manga/page/1/?m_orderby=`) → 403 i po
        //   úspěšném interaktivním vyřešení Cloudflare Turnstile výzvy (stejný
        //   TLS/HTTP-otisk mismatch jako u evilmanga). Zkusen i Kotatsu přístup -
        //   AJAX POST na `/wp-admin/admin-ajax.php` (`action=madara_load_more`,
        //   stejný endpoint, který appka používá pro getChapterList) - výsledek
        //   byl HTTP 525 (Cloudflare SSL handshake failed), reprodukováno 3x.
        // - manhuaus: stejný AJAX POST přístup → HTTP 403 rovnou, bez zobrazení
        //   Turnstile dialogu (tvrdé WAF pravidlo, ne jen chybějící cookie).
        // Obě selhání potvrzena PŘÍMO V APPCE (ne curlem), takže nejde o
        // TLS/JA3 fingerprint artefakt tohoto Windows stroje - jde o reálnou
        // ochranu, kterou appka nemá jak obejít. Kotatsu zjevně používá jiný
        // mechanismus (pravděpodobně routuje celé requesty přes WebView/systémový
        // prohlížeč, ne jen řešení výzvy + OkHttp replay) - jeho @Broken flag
        // pro tyhle dva zjevně jen není aktuální. Znovu odstraněno, viz
        // docs/source-audit-2026-07-26.md sekce 9. AJAX-archiv mechanismus v
        // MadaraSource.kt byl po tomto zjištění zase odstraněn (nepoužitá
        // komplexita, nikam jinam se nehodí).
        MadaraSource("manhuaplus",    "Manhuaplus",         "https://manhuaplus.com",       client, contentTypeOverride = "MANHUA"),
        // ── Manhwa scanlation skupiny ────────────────────────────────────────
        MadaraSource("manhwatop",     "Manhwatop",          "https://manhwatop.com",        client, contentTypeOverride = "MANHWA"),
        // wuxiaworldsite: audit 2026-07-27 zjistil, ze vychozi "/manga/page/N/"
        // archiv vraci 404 - web ma vlastni taxonomy slug pro novely.
        MadaraSource(
            "wuxiaworldsite", "Wuxiaworld.site", "https://wuxiaworld.site", client,
            contentTypeOverride = "NOVEL",
            popularUrl = { root, page, orderby -> "$root/novels-list/page/$page/?m_orderby=$orderby" },
        ),
        // "demonscans" (demonscans.net) odstraneno 2026-07-24 - domena uplne prestala
        // existovat (DNS nerozresolvuje), nahrazeno DemonicScansSource (jiny tym/branding,
        // vlastni sablona - viz komentar ve tride).
        //
        // Audit 2026-07-26: 48 z 73 Madara zdrojů odstraněno po plošné kontrole (curl +
        // ruční ověření obsahu). Kategorie:
        //  1) DNS/timeout mrtvé domény: astrascan, cosmicscans, isekaiscan, magicscans,
        //     mangaeffects, mangafuture, mangakiss, mangapt, mangarosie, manhuaonline,
        //     manhuarock, okumangas, trillerscans, tempestmanga
        //  2) Zaparkované domény / affiliate redirect (Sedo parking, ad-lander, meta-refresh
        //     na mrtvý cíl): azuremanga, mangayo, mangatube, zeroscans, mangamotto, manhwade
        //  3) Doména žije, ale vrací malvertising/anti-adblock "Redirecting..." nebo JS
        //     bot-gate stránku místo Madara obsahu (proto appka hlásila chybu při otevření):
        //     zinmanga, manhuaes, manhuascan, drakescans, realmscans, leviathanscans,
        //     infernalvoid, manga68, topmanhua, disasterscans, freakscans, mm-scans,
        //     reaperscanseu, suryascans, mangatx, manhuacat
        //  4) Web žije a má reálný obsah, ale přestal používat Madara šablonu (redesign na
        //     vlastní frontend) - generické Madara selektory proto nic nenašly ("tu nic
        //     není"). Vyřešeno vlastními MangaSource třídami: mangabuddy -> comizy.io,
        //     hivecomic -> hivetoons.org, mangaworld -> mangaworld.mx, voidscans,
        //     hostednovel, manhuabuddy, woopread a mangadenizi (Nuxt SPA bez dat v HTML,
        //     ale s plně funkčním interním REST API - reverzováno z JS bundlu), viz
        //     ComizySource / HiveToonsSource / MangaWorldSource / VoidScansSource /
        //     HostedNovelSource / ManhuaBuddySource / WoopReadSource / MangaDeniziSource.
        //  5) Doplňkový audit 2026-07-26 při hledání náhrad ke skupině 4 odhalil další
        //     nebezpečné/mrtvé případy, odstraněny bez náhrady:
        //     creativenovels (creativenovels.com) - kompromitovaný web: listing
        //     stránky (/browse-new/, /latest-releases/) servírují gambling spam
        //     (title "PANENTOTO"/"EMON777") místo obsahu, i když jednotlivé
        //     /novel/{slug}/ stránky ještě fungují.
        //     xcalibrscans (xcalibrscans.com) - stejný "Redirecting..." malvertising
        //     vzor jako skupina 3 výše (Windows Defender obsah karanténoval).
        //     mangatoto (mangatoto.com) - doména vypršela a byla zabrána
        //     spekulantem, teď je to obecný thajský WordPress SEO blog bez
        //     jakéhokoliv manga obsahu.
        MadaraSource("manhuahot",     "Manhua Hot",         "https://manhuahot.com",        client, contentTypeOverride = "MANHUA"),
        // manhuarm (manhuarmtl.com) odstraněno 2026-08-04 - web žije a vrací plnou
        // stránku (ne parking/blok), ale katalog je prázdný: žádná karta na "/manga/",
        // "/manga/?m_orderby=views" ani "/listing-big-thumbnail/". Ne chyba selektoru -
        // tam prostě není co najít.
        // ── Manga — další populární weby ─────────────────────────────────────
        // toonily/mangagg: audit 2026-07-27 zjistil, ze vychozi "/manga/page/N/"
        // archiv vraci 404 - vlastni taxonomy slug ("/webtoons/", "/comic/").
        MadaraSource(
            "toonily", "Toonily", "https://toonily.com", client,
            contentTypeOverride = "MANHWA",
            popularUrl = { root, page, orderby -> "$root/webtoons/page/$page/?m_orderby=$orderby" },
        ),
        MadaraSource("mangazin",      "Mangazin",           "https://mangazin.org",         client, contentTypeOverride = "MANHUA"),
        MadaraSource("cocomic",       "Cocomic",            "https://cocomic.co",           client, contentTypeOverride = "MANHWA"),
        MadaraSource(
            "mangagg", "MangaGG", "https://mangagg.com", client,
            contentTypeOverride = "MANHUA",
            popularUrl = { root, page, orderby -> "$root/comic/page/$page/?m_orderby=$orderby" },
        ),
        MadaraSource("mangaread",     "MangaRead",          "https://www.mangaread.org",    client, contentTypeOverride = "MANGA"),
        MadaraSource("coffeemanga",   "CoffeManga",         "https://coffeemanga.ink",      client, contentTypeOverride = "MANGA"),
        MadaraSource("mangasushi",    "Mangasushi",         "https://mangasushi.org",       client, contentTypeOverride = "MANGA"),
        MadaraSource("manhwatoon",    "Manhwatoon",         "https://www.manhwatoon.me",    client, contentTypeOverride = "MANHWA"),
        // mangalink.site vraci Cloudflare 522 (origin nedostupny) - mrtvy web, nepridavat.
        // pawmanga (pawmanga.com) odstraněno 2026-08-04 - doména je zaparkovaná
        // (FingerprintJS tracking/redirect skript, žádný manga obsah).
        // LikeManga (mgread.io) NENÍ Madara - "madara207" v HTML je jen jméno
        // uploadera, web běží na jiném WP pluginu (wp-theme-init-manga).
        // Vyžadovalo by vlastní MangaSource, viz project_jiyu_american_comics_audit
        // / manga source audit poznámky - zatím nepřidáno.
        // manhwaz.com pouziva vlastni permalinky ("/webtoon/{slug}" misto
        // "/manga/{slug}", "/genre/manga?page=N" pro archiv, "/search?s=..."
        // pro hledani) - proto vlastni popularUrl/searchUrl misto vychozich.
        MadaraSource(
            "manhwaz", "Manhwaz", "https://manhwaz.com", client,
            contentTypeOverride = "MANHWA",
            popularUrl = { root, page, _ -> "$root/genre/manga?page=$page" },
            searchUrl = { root, query, page -> "$root/search?s=$query&page=$page" },
        ),
        // ── Francouzské zdroje 🇫🇷 ──────────────────────────────────────────
        japscanSource,
        // animesama: doména anime-sama.fr mrtvá, přesunuto na anime-sama.to.
        // Web byl kompletně přepsán a seznam kapitol/stránek se negeneruje
        // ve statickém HTML - AnimeSamaSource proto místo Jsoup selektorů
        // volá interní JSON API webu (/s2/scans/get_nb_chap_et_img.php),
        // kterou používá jeho vlastní JS reader. Viz komentář u třídy.
        animeSamaSource,
        scanVFSource,
        // ── Španělské a portugalské zdroje 🇪🇸🇧🇷 ──────────────────────────
        // tmo (lectortmo.com) odstraněno 2026-07-26 - doména mrtvá (DNS), nová
        // lectortmo.net existuje ale je to čistě klientský SPA shell (Vite bundle) -
        // obsah by šel získat jen přes JS/API, ne přes Jsoup HTML parsing.
        // mangaleer (mangaleer.com) odstraněno 2026-07-26 - doména expirovala,
        // přesměrovává na expireddomains.com marketplace nabídku.
        // unionmangas (unionmangas.xyz) odstraněno 2026-07-26 - zaparkovaná doména,
        // reklamní JS redirect na "/lander".
        // inmanga (inmanga.com) odstraněno 2026-07-27 - archiv je čistě JS/AJAX
        // renderovaný (AngularJS "Factory/Controller" SPA), reálný POST endpoint
        // "/manga/GetMangasConsultResult" existuje, ale přesný JSON tvar
        // "filterSettings" parametru se nepodařilo v rozumném čase zjistit
        // (needs bigger investigation). Viz InMangaSource.kt (ponecháno).
        // ── Noví kandidáti (jednoduchý vlastní scraping) ─────────────────────
        mangaDotNetSource,
        kaliScanSource,
        mangaCloudSource,
        galaxyMangaSource,
        kuraMangaSource,
        // ── Novely (nový vlastní scraping) ───────────────────────────────────
        lightNovelWorldSource,
        novelFireSource,
        wuxiaBoxSource,
        ranobesSource,
        novelCoolSource,
        // Adult zdroj, pridano na vyslovne prani uzivatele (viz konverzace 2026-07-18)
        novelHallSource,
        mangaKatanaSource,
        baoziManhuaSource,
        mangapillSource,
        mangaTownSource,
        novelBuddySource,
        mangaHomeSource,
        nihonKuniSource,
        hachirumiSource,
        kingofshojoSource,
        // Adult davka #2, pridano na vyslovne prani uzivatele (viz konverzace 2026-07-19).
        // Nezavisle overeno pres Chrome pred implementaci - zadny malvertising redirect
        // na chapter readeru (na rozdil od trvale zamitnutych ComicLand/VyManga).
        manga18fxSource,
        hentai20Source,
        demonicScansSource,
        // MangaBuddy (mangabuddy.com) prebrandovano 2026-07-26 na comizy.io -
        // kompletni Next.js redesign, viz ComizySource (parsuje __NEXT_DATA__
        // JSON misto HTML selektoru, viz docs/source-audit-2026-07-26.md).
        comizySource,
        // Hive Scans (hivescans.com) prebrandovano 2026-07-26 na hivetoons.org -
        // kompletni redesign (Astro + schema.org microdata), viz HiveToonsSource.
        hiveToonsSource,
        // MangaWorld (IT, mangaworld.ac -> mangaworld.mx) nikdy nebylo Madara -
        // vlastni Laravel frontend, viz MangaWorldSource.
        mangaWorldSource,
        // Void Scans (voidscans.net) - maly staticky Hugo web, nikdy nebyl Madara.
        voidScansSource,
        // HostedNovel (hostednovel.com) - vlastni Laravel/Vue frontend, nikdy Madara.
        hostedNovelSource,
        // ManhuaBuddy (manhuabuddy.com) - vlastni PHP frontend, nikdy nebylo Madara.
        manhuaBuddySource,
        // WoopRead (woopread.com) - textovy light-novel web, vlastni Next.js
        // App Router frontend, nikdy nebyl Madara.
        woopReadSource,
        // MangaDenizi (mangadenizi.net, TR) - nikdy nebylo Madara. Nuxt SPA bez
        // dat ve statickem HTML, ale ma plne funkcni interni REST API (viz
        // komentar u tridy) - vcetne rozskladani zamichanych "tiled-v1"
        // dlazdic, viz TileScramble/TileScrambleBitmap.
        mangaDeniziSource,
        // 2026-07-27 (čtvrté kolo auditu) - hromadné odstranění zdrojů se skutečnou,
        // architektonicky neřešitelnou Cloudflare Turnstile ochranou. Živě v appce
        // ověřeno na evilmanga: tichý WebView solve i viditelný interaktivní dialog
        // (CloudflareChallengeBridge) OBA úspěšně získají platný cf_clearance cookie,
        // ale OkHttp replay s tímto cookie je Cloudflare ORIGIN serverem STEJNĚ
        // odmítnut (403, "Just a moment...") - jde o mismatch TLS/HTTP otisku mezi
        // WebView (Chromium engine, řeší výzvu) a OkHttp (jiný klient, replayuje
        // cookie), na což je Turnstile navržen reagovat blokací. Bez zásadní
        // přestavby (routovat VŠECHNY requesty přes WebView síťovou vrstvu) appka
        // tyhle weby nemůže nikdy přečíst, i když prohlížení/hledání může naživo
        // vypadat funkčně. curl 2026-07-27 reconfirmed real "Just a moment..." (403)
        // na všech níže - stejná kategorie ochrany jako evilmanga/kunmanga:
        // webtoonxyz, aquareader, foxaholic, immortalupdates, scribblehub,
        // manganato (natomanga.com - nově chráněno, dřív fungovalo bez ochrany),
        // manhuafast, manhuaus (viz komentář u sekce "Manhua" výše - dočasně
        // vráceny 2026-07-27 na uživatelův popud, ale živý test v appce/emulátoru
        // potvrdil stejné selhání jako u evilmanga i s Kotatsu-style AJAX
        // archivem, takže odstraněny znovu).
        // Viz *Source.kt třídy jednotlivých zdrojů (ponechány pro případ, že by
        // appka v budoucnu routovala requesty přes WebView).
        //
        // madaradex: NEODSTRANĚN jako "CF-gated" web, ale CDN subdoména
        // (cdn.madaradex.org) sama vrací 403 i se správným Refererem (vlastní WAF
        // pravidlo na obrázcích) - archiv/hledání by fungovalo, ale žádná kapitola
        // by se nedala přečíst, proto odstraněno taky. Viz MadaraDex v sekci 6d.
        //
        // mangahub: OVĚŘENO ŽIVĚ V APPCE (ne jen curl) - GraphQL API
        // (api.mghubcdn.com/graphql) vrací HTTP 200, ale tělo je anti-adblock/bot
        // "Redirecting..." JS interstitial (ne Cloudflare - jiný, nebrandovaný bot
        // gate), takže JSONObject parsing tiše selže a getPopular() vrátí prázdno.
        // Appka nemá infrastrukturu pro řešení tohoto typu JS gate. Viz MangaHubSource.kt.
        //
        // rawkuma: web se přestěhoval na rawkuma.net s kompletně jinou strukturou
        // (WordPress + htmx, archiv karty se dohrávají přes skrytý JS lazyload
        // mechanismus, ne standardní hx-get), navíc opakované curl requesty
        // narazily na skutečný Cloudflare "you have been blocked" hard-block -
        // vyžadovalo by kompletní přepis srovnatelný s mangadenizi. Viz RawKumaSource.kt.
        //
        // mangaboomers: manga-boomers.cz je Vue SPA, seznam titulů jde přes
        // "/api/mangalist" (funguje), ale detail/kapitoly ("/api/mangaInfo",
        // "/api/loadChapters") vyžadují neznámý tvar POST parametru - vyzkoušeny
        // běžné varianty (id, mangaId, manga_id, JSON body, cookie session), žádná
        // nefunguje. Bez čitelné kapitoly by appka jen "prohlížela", proto odstraněno.
        // Viz MangaBoomersSource.kt.
        //
        // mangablaze: web běží na hluboce přetémovaném/bespoke Madara (vlastní
        // a.acard/.ac-t karty, detail/kapitola nesedí na žádný výchozí Madara
        // selektor) - vyžadovalo by vlastní MangaSource třídu srovnatelnou
        // náročností s mangadenizi, nestihnuto v tomto kole (byl to jen
        // MadaraSource("mangablaze", ...) s výchozími selektory, ne vlastní třída).
        //
        // ranovel: NOVEL oprava (6e) zůstává v MadaraSource.kt (prospěje případným
        // budoucím Madara NOVEL zdrojům), ale samotný ranovel odstraněn - stránky
        // KAPITOL (ne archiv/detail) jsou za stejnou neřešitelnou Cloudflare Turnstile
        // ochranou jako výše, čtení by tedy stejně nikdy nefungovalo.
    )

    init {
        scope.launch {
            customSourceDao.observeAll().collect { customs ->
                _cache.value = staticSources + customs.map { custom ->
                    val defaults = MadaraSelectors.DEFAULT
                    MadaraSource(
                        id = "madara:${custom.id}",
                        name = custom.name,
                        baseUrl = custom.baseUrl,
                        client = client,
                        selectors = MadaraSelectors(
                            listItem = custom.listItemSelector?.ifBlank { null } ?: defaults.listItem,
                            titleLink = custom.titleLinkSelector?.ifBlank { null } ?: defaults.titleLink,
                            description = custom.descriptionSelector?.ifBlank { null } ?: defaults.description,
                            status = custom.statusSelector?.ifBlank { null } ?: defaults.status,
                            chapterList = custom.chapterListSelector?.ifBlank { null } ?: defaults.chapterList,
                            pageImage = custom.pageImageSelector?.ifBlank { null } ?: defaults.pageImage,
                        ),
                        contentTypeOverride = custom.contentType,
                    )
                }
            }
        }
    }

    /** Čeká na první NEprázdnou emisi cache (start appky, než se static+custom zdroje slijí do jedné množiny) - beze změny obsahu, jen "chvíli počkej". */
    private suspend fun rawSources(): List<MangaSource> = _cache.filter { it.isNotEmpty() }.first()

    /**
     * Zdroje pro OBJEVOVÁNÍ (Procházet mřížka, GlobalSearch) - respektuje
     * [SettingsRepository.showAdultSources]. Záměrně NEPOUŽÍVÁ [getById] (ten zůstává
     * nefiltrovaný), aby vypnutí adult zdrojů neschovalo/nerozbilo mangu, kterou uživatel
     * už má v knihovně z dřívějška - jen ji skryje z NOVÉHO objevování.
     */
    fun observeAll(): Flow<List<MangaSource>> = combine(_cache, settings.showAdultSources) { all, showAdult ->
        if (showAdult) all else all.filterNot { it.isAdult }
    }

    suspend fun getAll(): List<MangaSource> {
        val all = rawSources()
        return if (settings.showAdultSources.first()) all else all.filterNot { it.isAdult }
    }

    /** Nefiltrované podle isAdult - viz komentář u [observeAll]. */
    suspend fun getById(id: String): MangaSource? = rawSources().find { it.id == id }
}
