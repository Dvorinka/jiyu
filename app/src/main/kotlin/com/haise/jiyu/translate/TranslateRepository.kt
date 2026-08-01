package com.haise.jiyu.translate

import com.haise.jiyu.data.db.MangaDao
import com.haise.jiyu.data.db.TranslatedNovelDao
import com.haise.jiyu.data.db.TranslatedPageDao
import com.haise.jiyu.data.db.entity.TranslatedNovelEntity
import com.haise.jiyu.data.db.entity.TranslatedPageEntity
import com.haise.jiyu.util.report
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslateRepository @Inject constructor(
    private val ocrEngine: OcrEngine,
    private val pageBitmapLoader: PageBitmapLoader,
    private val groqClient: GroqTranslateClient,
    private val geminiClient: GeminiTranslateClient,
    private val glossaryRepository: GlossaryRepository,
    private val providerHealth: ProviderHealth,
    private val mangaDao: MangaDao,
    private val dao: TranslatedPageDao,
    private val novelDao: TranslatedNovelDao,
) {
    val isApiKeyConfigured: Boolean get() = groqClient.isConfigured

    private suspend fun glossaryFor(mangaId: String, targetLanguage: String): Map<String, String> =
        glossaryRepository.getMap(mangaId, targetLanguage)

    /**
     * Krátký kontext o samotné maze (název/typ obsahu/žánry) pro [GeminiUltraPrompt] -
     * model bez něj nemá tušení, jestli překládá temné fantasy, komedii nebo herní systém,
     * a volí tón/slovník podle toho. Prázdný řetězec, když se manga nenajde nebo nemá
     * vyplněné žánry (starý/ještě nenačtený záznam) - prompt takový řádek prostě vynechá.
     */
    private suspend fun mangaContextFor(mangaId: String): String {
        val manga = mangaDao.getById(mangaId) ?: return ""
        val genres = manga.genres.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return buildString {
            append("Název: \"${manga.title}\" (${manga.contentType.lowercase()})")
            if (genres.isNotEmpty()) append(", žánry: ${genres.joinToString(", ")}")
        }
    }

    /**
     * Vrátí přeložené bloky pro jednu stránku.
     * Cache-first: pokud jsou v Room, vrátí okamžitě.
     * @return bloky nebo emptyList() pokud OCR/API selže
     */
    suspend fun translatePage(
        pageUrl: String,
        chapterId: String,
        mangaId: String,
        pageIndex: Int,
        targetLanguage: String = "Czech",
        sourceLanguage: String = "Auto",
    ): List<TranslatedBlock> {
        getCachedPage(chapterId, pageIndex, targetLanguage, sourceLanguage, pageUrl)?.let { return it }
        // Když jsou odstavení všichni provideři, nemá smysl stahovat bitmapu ani pouštět OCR -
        // překlad z toho stejně nevznikne. Viz [ProviderHealth.allUnavailable].
        if (providerHealth.allUnavailable()) throw RateLimitedException()

        // Stejný strop jako v translateChapter - bez něj by jedna zaseklá síťová odpověď
        // (viz komentář tam, až 180s na jeden obrázek) nechala appku bez zpětné vazby
        // stejně dlouho i tady, jen na jedné stránce místo celé kapitoly.
        val rawBlocks = withTimeoutOrNull(PAGE_OCR_TIMEOUT_MILLIS) {
            val bitmap = pageBitmapLoader.load(pageUrl) ?: return@withTimeoutOrNull emptyList()
            ocrEngine.recognize(bitmap, sourceLanguage)
        } ?: emptyList()
        if (rawBlocks.isEmpty()) return emptyList()

        val glossary = glossaryFor(mangaId, targetLanguage)
        val mangaContext = mangaContextFor(mangaId)
        val classified = BubbleClassifier.classifyPage(rawBlocks, sourceLanguage)

        // GeminiUltraPrompt je napsaný natvrdo pro češtinu (znakové limity a kompresní
        // pravidla mají české příklady) - pro jiný cílový jazyk zůstáváme na obecném
        // Groq promptu (translate-proxy mode="manga"), který jazyk dostává jako parametr.
        val blocks = if (targetLanguage == "Czech") {
            // 1) Gemini. 2) Stejný "ultra" prompt (komprese/sylabické dělení), ale přes Groq
            //    jako upstream - viz GeminiTranslateClient.translateBubbles(provider="groq") -
            //    zachytí Gemini-specifické selhání (deprekovaný model, jeho vlastní výpadek)
            //    beze ztráty kvality. 3) Stejný "ultra" prompt, ale přes OpenRouter free-tier
            //    model (provider="openrouter") - čtvrtá (resp. třetí přes stejný prompt) záchrana,
            //    než klesneme na 4) holý Groq překlad bez komprese jako poslední záchranu.
            // RateLimitedException z JEDNOHO kroku už neznamená konec (viz translateChain) -
            // Gemini/Groq/OpenRouter jsou tři nezávislé komerční služby s vlastní kvótou,
            // 429 na proxy je jen jeho VLASTNÍ limit počtu požadavků (viz komentář u
            // RateLimitedException), ne nutně důkaz, že mají vyčerpáno i ostatní dva.
            translateChain(
                { translateWithGemini(classified, glossary, mangaContext, provider = "gemini", mangaId, targetLanguage) },
                { translateWithGemini(classified, glossary, mangaContext, provider = "groq", mangaId, targetLanguage) },
                { translateWithGemini(classified, glossary, mangaContext, provider = "openrouter", mangaId, targetLanguage) },
                { translateWithGroq(classified, glossary, targetLanguage, sourceLanguage, provider = "groq") },
                { translateWithGroq(classified, glossary, targetLanguage, sourceLanguage, provider = "openrouter") },
            )
        } else {
            // GeminiUltraPrompt je psaný natvrdo pro češtinu, takže pro jiné cílové jazyky
            // nemá smysl - ale i tak appka dřív měla jen JEDNU cestu (holý Groq) bez jakékoli
            // zálohy. Teď zkusí Groq a při selhání OpenRouter (stejný obecný "manga"/"novel"
            // prompt parametrizovaný cílovým jazykem, viz translate-proxy systemPromptFor).
            translateChain(
                { translateWithGroq(classified, glossary, targetLanguage, sourceLanguage, provider = "groq") },
                { translateWithGroq(classified, glossary, targetLanguage, sourceLanguage, provider = "openrouter") },
            )
        }
        if (blocks.isEmpty()) return emptyList()

        dao.upsert(TranslatedPageEntity(id = cacheId(chapterId, pageIndex, targetLanguage, sourceLanguage), blocksJson = blocks.serialize()))
        return blocks
    }

    /**
     * Přeloží celou kapitolu v omezeném počtu dávkových API volání místo jednoho volání na
     * stránku (viz [translatePage]) - snižuje počet požadavků na proxy a odstraňuje potřebu
     * prodlevy mezi KAŽDOU stránkou v ReaderViewModelu, protože jedno volání umí přeložit
     * bubliny z více stránek najednou.
     *
     * Bublinám z více stránek se nepřidává žádné "page" pole do promptu ani JSON schématu -
     * stránky se před odesláním jen spojí do jednoho plochého seznamu (stejné id-schéma jako
     * [translatePage] pro jednu stránku, viz [GeminiUltraPrompt.buildUserPrompt] - "id" je
     * pozice v předaném seznamu) a po odpovědi se podle známého počtu bublin na stránku
     * rozdělí zpátky. Díky tomu tahle cesta nevyžaduje žádnou změnu [GeminiUltraPrompt] ani
     * Edge Function proxy.
     *
     * Dávky mají omezenou velikost (viz [chunkPages]/[CHAPTER_CHUNK_CHAR_LIMIT]), aby výstup
     * nepřekročil limit tokenů jednoho API volání - jedno volání na CELOU kapitolu by se u
     * delší kapitoly snadno oříznulo v půlce JSON odpovědi a celá kapitola by neuspěla najednou.
     *
     * @param onPageReady zavolá se pro KAŽDOU stránku zvlášť, jakmile je hotová (i když šla
     *   v dávce s ostatními) - zachovává postupné zobrazování stránek v ReaderViewModelu
     *   místo čekání na celou kapitolu najednou.
     * @throws RateLimitedException stejná sémantika jako [translatePage] - NEODCHYTÁVÁ se
     *   tady, volající (ReaderViewModel) na to má vlastní hlášku a přeruší zbytek kapitoly.
     */
    suspend fun translateChapter(
        pages: List<String>,
        chapterId: String,
        mangaId: String,
        targetLanguage: String = "Czech",
        sourceLanguage: String = "Auto",
        onPageReady: suspend (pageIndex: Int, blocks: List<TranslatedBlock>) -> Unit,
    ) {
        val uncached = mutableListOf<Int>()
        for (pageIndex in pages.indices) {
            val cached = getCachedPage(chapterId, pageIndex, targetLanguage, sourceLanguage, pages[pageIndex])
            if (cached != null) onPageReady(pageIndex, cached) else uncached += pageIndex
        }
        if (uncached.isEmpty()) return

        val glossary = glossaryFor(mangaId, targetLanguage)
        val mangaContext = mangaContextFor(mangaId)

        // Stahování bitmap (síť, viz PageBitmapLoader) a OCR (ML Kit, viz OcrEngine) mají
        // rozdílnou povahu souběžnosti - stahování je I/O čekání, snese víc paralelních
        // požadavků najednou; OCR recognizery jsou sdílené instance a plné rozlišení víc
        // stránek v paměti najednou by zbytečně riskovalo OOM na slabších telefonech, proto
        // má vlastní, přísnější limit.
        val bitmapLoadSemaphore = Semaphore(BITMAP_LOAD_PARALLELISM)
        val ocrSemaphore = Semaphore(OCR_PARALLELISM)
        val bubblesByPage: Map<Int, List<ClassifiedBubble>> = coroutineScope {
            uncached.map { pageIndex ->
                async(Dispatchers.IO) {
                    // Postup se hlásí (onPageReady) až PO téhle celé awaitAll - jedna jediná
                    // stránka s pomalým/zaseklým síťovým požadavkem (OkHttp má 30s connect +
                    // 30s read, RetryInterceptor to opakuje 3x = až 180s na jeden obrázek)
                    // by bez stropu zamrazila ukazatel postupu na 0/N pro CELOU kapitolu, i
                    // kdyby zbylých deset stránek dávno doběhlo (viz uživatelská zpětná vazba
                    // "3 minuty prekladam a porad 0/11" - 3 minuty odpovídá přesně tomu
                    // nejhoršímu případu). Timeout tu stránku prostě přeskočí jako bez textu,
                    // místo aby nechal viset celou dávku na neurčito.
                    val raw = withTimeoutOrNull(PAGE_OCR_TIMEOUT_MILLIS) {
                        val bitmap = bitmapLoadSemaphore.withPermit { pageBitmapLoader.load(pages[pageIndex]) }
                        bitmap?.let { bmp -> ocrSemaphore.withPermit { ocrEngine.recognize(bmp, sourceLanguage) } } ?: emptyList()
                    } ?: emptyList()
                    pageIndex to BubbleClassifier.classifyPage(raw, sourceLanguage)
                }
            }.awaitAll()
        }.toMap()

        val translatable = uncached.filter { bubblesByPage.getValue(it).isNotEmpty() }
        for (pageIndex in uncached) {
            if (pageIndex !in translatable) onPageReady(pageIndex, emptyList())
        }
        if (translatable.isEmpty()) return

        chunkPages(translatable, bubblesByPage).forEachIndexed { chunkIndex, chunk ->
            if (chunkIndex > 0) delay(800L)
            // Zbytek kapitoly by jen rychle "doběhl" s prázdnými výsledky - radši srozumitelná
            // hláška o limitu. Viz [ProviderHealth.allUnavailable].
            if (providerHealth.allUnavailable()) throw RateLimitedException()
            val flatBubbles = chunk.flatMap { bubblesByPage.getValue(it) }

            // Stejný fallback řetězec jako translatePage - viz komentář tam. Volá se přes
            // sdílené translateWithGemini/translateWithGroq beze změny: obě funkce už dnes
            // vždy vrací seznam přesně dlouhý jako vstupní "flatBubbles" (chybějící "id" v
            // odpovědi se doplní originálem, nikdy se nezahodí), takže rozdělení jedné
            // odpovědi zpátky po stránkách podle počtu bublin níž je bezpečné.
            val blocks = if (targetLanguage == "Czech") {
                translateChain(
                    { translateWithGemini(flatBubbles, glossary, mangaContext, provider = "gemini", mangaId, targetLanguage) },
                    { translateWithGemini(flatBubbles, glossary, mangaContext, provider = "groq", mangaId, targetLanguage) },
                    { translateWithGemini(flatBubbles, glossary, mangaContext, provider = "openrouter", mangaId, targetLanguage) },
                    { translateWithGroq(flatBubbles, glossary, targetLanguage, sourceLanguage, provider = "groq") },
                    { translateWithGroq(flatBubbles, glossary, targetLanguage, sourceLanguage, provider = "openrouter") },
                )
            } else {
                translateChain(
                    { translateWithGroq(flatBubbles, glossary, targetLanguage, sourceLanguage, provider = "groq") },
                    { translateWithGroq(flatBubbles, glossary, targetLanguage, sourceLanguage, provider = "openrouter") },
                )
            }

            val perPage = splitBlocksByPage(chunk, chunk.map { bubblesByPage.getValue(it).size }, blocks)
            for ((pageIndex, pageBlocks) in perPage) {
                if (pageBlocks.isNotEmpty()) {
                    dao.upsert(TranslatedPageEntity(id = cacheId(chapterId, pageIndex, targetLanguage, sourceLanguage), blocksJson = pageBlocks.serialize()))
                }
                onPageReady(pageIndex, pageBlocks)
            }
        }
    }

    /**
     * Rozdělí stránky (v pořadí) do dávek, kde součet délky bublinových textů v jedné dávce
     * nepřekročí [CHAPTER_CHUNK_CHAR_LIMIT] - jedna stránka je vždy atomická (nikdy se
     * nerozdělí mezi dvě dávky), stejný princip jako [chunkParagraphs] u novel překladu.
     */
    internal fun chunkPages(pageIndices: List<Int>, bubblesByPage: Map<Int, List<ClassifiedBubble>>): List<List<Int>> {
        val chunks = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        var currentLen = 0
        for (pageIndex in pageIndices) {
            val len = bubblesByPage.getValue(pageIndex).sumOf { it.raw.text.length }
            if (current.isNotEmpty() && currentLen + len > CHAPTER_CHUNK_CHAR_LIMIT) {
                chunks += current
                current = mutableListOf()
                currentLen = 0
            }
            current += pageIndex
            currentLen += len
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }

    /**
     * @param provider "gemini", "groq" nebo "openrouter" - viz [GeminiTranslateClient.translateBubbles].
     *   Všichni tři provideři používají STEJNÝ [GeminiUltraPrompt] (komprese, sylabické dělení),
     *   liší se jen upstream model, na který proxy request přepošle.
     * @return null když se nepodařilo přeložit ani jednu bublinu (proxy nemá nasazený
     *   "gemini" mód, síť selhala po všech pokusech, upstream model vrátil chybu...) -
     *   volající ([translatePage]) pak zkusí dalšího providera nebo nakonec
     *   [translateWithGroq] (bez komprese) jako poslední záchrannou síť.
     * @throws RateLimitedException když je vyčerpaná sdílená denní kvóta proxy - viz
     *   [translatePage], tohle se záměrně NEODCHYTÁVÁ tady.
     */
    private suspend fun translateWithGemini(
        classified: List<ClassifiedBubble>,
        glossary: Map<String, String>,
        mangaContext: String,
        provider: String,
        mangaId: String,
        targetLanguage: String,
    ): List<TranslatedBlock>? {
        if (!geminiClient.isConfigured) return null
        val response = geminiClient.translateBubbles(classified, glossary, provider, mangaContext) ?: return null

        // Model občas bublinu v odpovědi vynechá nebo pro ni vrátí prázdný řetězec. Doptáme se
        // JEN na ty chybějící (ne na celou dávku znovu) - je jich pár, takže je to jedno krátké
        // volání navíc, a bez něj by se místo překladu vykreslil anglický originál nebo prázdná
        // bublina (viz [TranslationMerge] a uživatelské screenshoty s "THE FIRST PLACE.").
        val missing = missingTranslationIndices(classified, response.bubbles.associateBy { it.id })
        val byId = if (missing.isEmpty()) {
            response.bubbles.associateBy { it.id }
        } else {
            val retryResponse = geminiClient.translateBubbles(
                bubbles = missing.map { classified[it] },
                glossary = glossary,
                provider = provider,
                mangaContext = mangaContext,
            )
            mergeRetry(response.bubbles.associateBy { it.id }, missing, retryResponse, classified)
        }

        // Auto-učení glosáře (viz GeminiUltraPrompt sekce "NOVÉ POJMY") - model sám
        // identifikuje vlastní jména v téhle dávce, appka je uloží, aby byla konzistentní
        // i v dalších kapitolách BEZ nutnosti ručního zásahu. Ruční záznam uživatele má
        // vždycky přednost - proto se přeskočí, když glosář už stejný zdrojový termín má
        // (ignoreCase, protože ID záznamu je case-insensitive, viz GlossaryRepository.upsert).
        for (term in response.newTerms) {
            if (glossary.keys.none { it.equals(term.source, ignoreCase = true) }) {
                glossaryRepository.upsert(mangaId, term.source, term.target, targetLanguage)
            }
        }

        // mapIndexed (ne mapIndexedNotNull) - chybějící "id" v odpovědi musí zůstat na svém
        // místě jako blok s originálem místo zmizet, jinak by se posunula pozice ostatních
        // bublin v seznamu, na které translateChapter spoléhá při rozdělování jedné dávky
        // (víc stránek najednou) zpátky po stránkách podle počtu bublin.
        val result = classified.mapIndexed { i, c ->
            if (c.isSfx) return@mapIndexed sfxBlock(c)
            val t = byId[i]
            // Bublina je "nepřeložená" ve čtyřech případech: model vrátil UNTRANSLATED_MARKER
            // (OCR nedává smysl, viz prompt), vrátil prázdný řetězec, vynechal ji v odpovědi
            // úplně (a to i po opravném dotazu výš), nebo jeho echované "original" neodpovídá
            // tomu, co bublina doopravdy obsahovala (viz [originalMatches] - model si spletl
            // číslování "id" pod velkou dávkou a odpověděl na JINOU bublinu). Ve VŠECH čtyřech
            // se musí označit isUntranslated, aby ji TranslationLayer vůbec nekreslil a originál
            // zůstal čitelný. Bez posledního případu appka slepě věřila poli "id" a bublina
            // dostala cizí text patřící jiné bublině o pár pozic dál - viz uživatelská zpětná
            // vazba ("NOT LIKE THAT." zobrazila překlad patřící jiné bublině na jiné stránce).
            val isUntranslated = !isUsableTranslation(t, c.raw.text)
            val translatedText = if (isUntranslated) c.raw.text else t!!.translated
            // Model syllable_breaks se použije JEN, když opravdu odpovídá translatedText po
            // odstranění rozdělovníků (viz isValidSyllableBreaks) - jinak by poškozený/
            // neshodující se výstup modelu potichu nahradil správný překlad viditelně
            // rozbitým textem (viz uživatelská zpětná vazba - "OKAMŽITĚ" -> "OKAM" + zbytek).
            // ensureFallbackHyphens navíc doplní rozdělovník do dlouhých slov, která ho
            // nemají ani po týhle validaci (model ho pro ně nevrátil vůbec).
            val syllableBreaks = t?.syllableBreaks
            val validatedDisplay = if (syllableBreaks != null && isValidSyllableBreaks(translatedText, syllableBreaks)) {
                syllableBreaks
            } else {
                translatedText
            }
            TranslatedBlock(
                originalText = c.raw.text,
                translatedText = translatedText,
                leftF = c.raw.leftF,
                topF = c.raw.topF,
                rightF = c.raw.rightF,
                bottomF = c.raw.bottomF,
                displayText = if (isUntranslated) c.raw.text else ensureFallbackHyphens(validatedDisplay),
                bgColorArgb = c.raw.bgColorTopArgb,
                bgColorBottomArgb = c.raw.bgColorBottomArgb,
                isSfx = false,
                lineCount = c.lineCount,
                shape = c.raw.shape,
                bubbleType = c.bubbleType,
                isUntranslated = isUntranslated,
                bgUniform = c.raw.bgUniform,
                nativeLineHeightF = c.raw.nativeLineHeightF,
            )
        }
        return result.ifEmpty { null }
    }

    /**
     * Legacy/fallback cesta - vrací jen holé přeložené texty, žádné syllable_breaks.
     * @param provider "groq" (výchozí) nebo "openrouter" - stejný obecný prompt
     *   (translate-proxy mode="manga"/"novel"), jiný upstream model.
     * @return null při selhání (žádný text se nepřeložil) - volající zkusí dalšího
     *   providera nebo nakonec vrátí prázdný seznam.
     */
    private suspend fun translateWithGroq(
        classified: List<ClassifiedBubble>,
        glossary: Map<String, String>,
        targetLanguage: String,
        sourceLanguage: String,
        provider: String = "groq",
    ): List<TranslatedBlock>? {
        val toTranslate = classified.filter { !it.isSfx }
        val translations = if (toTranslate.isEmpty()) emptyList() else groqClient.translateBatch(
            texts = toTranslate.map { it.raw.text },
            targetLanguage = targetLanguage,
            sourceLanguage = sourceLanguage,
            glossary = glossary,
            provider = provider,
        )
        if (toTranslate.isNotEmpty() && translations.isEmpty()) return null

        var ti = 0
        return classified.map { c ->
            if (c.isSfx) {
                sfxBlock(c)
            } else {
                val raw = translations.getOrNull(ti)?.trim()
                ti++
                // Stejné pravidlo jako u translateWithGemini: chybějící (kratší odpověď než
                // vstup), prázdný i UNTRANSLATED_MARKER výsledek znamená NEPŘELOŽENO. Dřív se
                // v prvních dvou případech potichu propadl originál a vykreslil se jako
                // překlad - viz [TranslationMerge].
                val usable = raw?.takeIf { it.isNotEmpty() && it != GeminiUltraPrompt.UNTRANSLATED_MARKER }
                val isUntranslated = usable == null
                val translated = usable ?: c.raw.text
                TranslatedBlock(
                    originalText = c.raw.text,
                    translatedText = translated,
                    leftF = c.raw.leftF,
                    topF = c.raw.topF,
                    rightF = c.raw.rightF,
                    bottomF = c.raw.bottomF,
                    // Groq/OpenRouter cesta nemá žádný syllable_breaks od modelu (jen
                    // GeminiUltraPrompt ho umí) - ensureFallbackHyphens je tu JEDINÁ ochrana
                    // proti tomu, aby dlouhé slovo přeteklo a Compose ho useklo bez pomlčky.
                    displayText = if (isUntranslated) translated else ensureFallbackHyphens(translated),
                    bgColorArgb = c.raw.bgColorTopArgb,
                    bgColorBottomArgb = c.raw.bgColorBottomArgb,
                    isSfx = false,
                    lineCount = c.lineCount,
                    shape = c.raw.shape,
                    bubbleType = c.bubbleType,
                    isUntranslated = isUntranslated,
                    bgUniform = c.raw.bgUniform,
                    nativeLineHeightF = c.raw.nativeLineHeightF,
                )
            }
        }
    }

    /** SFX bublina se nikdy nepřekládá (viz [BubbleClassifier]) - originál zůstává, jen si nese klasifikaci pro render. */
    private fun sfxBlock(c: ClassifiedBubble) = TranslatedBlock(
        originalText = c.raw.text,
        translatedText = c.raw.text,
        leftF = c.raw.leftF,
        topF = c.raw.topF,
        rightF = c.raw.rightF,
        bottomF = c.raw.bottomF,
        displayText = c.raw.text,
        bgColorArgb = c.raw.bgColorTopArgb,
        bgColorBottomArgb = c.raw.bgColorBottomArgb,
        isSfx = true,
        lineCount = c.lineCount,
        shape = c.raw.shape,
        bubbleType = c.bubbleType,
        bgUniform = c.raw.bgUniform,
        nativeLineHeightF = c.raw.nativeLineHeightF,
    )

    /**
     * Vrátí výsledek z Room cache bez volání překladového API; null = není v cache.
     * @param pageUrl když je zadané a cache záznam ještě nemá dopočítaný tvar bubliny
     *   (starý formát), dopočítá se tvar (bez nového OCR/překladu) a cache se přepíše -
     *   viz OcrEngine.detectShapesOnly. Bitmapa se stahuje (přes PageBitmapLoader) JEN když
     *   je migrace opravdu potřeba, ne při každém cache-hitu. Bez pageUrl (starší volající,
     *   co ho nemají po ruce) se migrace přeskočí a bloky zůstanou s shape=null (heuristický
     *   fallback v layoutu). Selhání stažení bitmapy vrátí nezmigrované bloky, ne null.
     */
    suspend fun getCachedPage(
        chapterId: String,
        pageIndex: Int,
        targetLanguage: String,
        sourceLanguage: String = "Auto",
        pageUrl: String? = null,
    ): List<TranslatedBlock>? {
        val id = cacheId(chapterId, pageIndex, targetLanguage, sourceLanguage)
        val cached = dao.getById(id)?.deserialize() ?: return null
        if (pageUrl == null) return cached

        val needsShapeMigration = cached.any { !it.isSfx && it.shape == null }
        if (!needsShapeMigration) return cached

        // translateChapter volá getCachedPage SEKVENČNĚ pro každou stránku (viz cache-check
        // smyčka) ještě PŘED paralelní dávkou nepřeložených stránek - stejný strop jako tam,
        // ať zaseklá migrace jedné staré stránky nezablokuje i tenhle úvodní průchod.
        return withTimeoutOrNull(PAGE_OCR_TIMEOUT_MILLIS) {
            val bitmap = pageBitmapLoader.load(pageUrl) ?: return@withTimeoutOrNull cached
            val migrated = ocrEngine.detectShapesOnly(bitmap, cached)
            dao.upsert(TranslatedPageEntity(id = id, blocksJson = migrated.serialize()))
            migrated
        } ?: cached
    }

    private fun cacheId(chapterId: String, pageIndex: Int, targetLanguage: String, sourceLanguage: String) =
        "$chapterId::$pageIndex::$sourceLanguage::$targetLanguage::v$PIPELINE_VERSION"

    companion object {
        /**
         * Verze překladového pipeline (OCR klasifikace + prompt), zahrnutá do klíče cache.
         * Bez ní zůstávaly po aktualizaci appky viset staré, rozbité výsledky - uživatel
         * nainstaloval opravu, ale pořád viděl přesně tu chybu, co byla opravená, protože se
         * stránka vzala z cache a znovu se nezpracovala (viz uživatelská zpětná vazba
         * "nic si neopravil"). Zvyš tohle číslo VŽDY, když se změní něco, co ovlivňuje
         * ULOŽENÁ data (klasifikace SFX/vodoznaku, prompt, struktura bloků) - ne když se
         * mění jen vykreslování (to se počítá při zobrazení a na cache nezávisí).
         *
         * v2 (2026-07-27): detekce vodoznaku scanlation skupiny + krátká slova už nejsou SFX.
         * v3 (2026-07-31): chybějící/prázdná odpověď modelu se ukládá jako isUntranslated
         *   místo tichého propadnutí originálu (viz [TranslationMerge]) - staré záznamy nesou
         *   isUntranslated=false u bublin, kde má nově být true, takže se musí přepočítat.
         * v4 (2026-07-31): dvě změny mění, co se z OCR/modelu doopravdy uloží -
         *   (1) hasWallBetween veden středem PŘEKRYVU, ne středy celých bloků (viz
         *   BubbleMerge.kt) - kaskádová/"dvouhrbá" bublina s vodorovně posunutým druhým
         *   řádkem se dřív omylem rozdělila na dvě bubliny a půlka textu zmizela (viz
         *   uživatelská zpětná vazba "GOOD HEAVENS," chybějící z "TO GET LOST..."); (2)
         *   ověření modelem echovaného "original" proti tomu, co bublina doopravdy
         *   obsahovala (viz [originalMatches]) - dřív appka slepě věřila poli "id" a
         *   posunuté číslování u velké dávky (víc stránek najednou) přeneslo překlad na
         *   jinou bublinu, než pro kterou byl určen. Staré cache záznamy mají obojí
         *   spočítané podle starší, chybové logiky, proto se musí přepočítat.
         * v5 (2026-07-31): další dvě změny mění, co se z OCR doopravdy uloží -
         *   (1) hasWallBetween teď vyžaduje VĚTŠINU vzorků na úsečce, ne jediný, aby
         *   prohlásil "zeď" (viz BubbleMerge.kt) - tenký/diagonální vodoznak nastříknutý
         *   přes bublinu (např. "VORTEXSCANS.COM" ležící mezi dvěma půlkami repliky)
         *   protínal přímou úsečku jen v 1 z 5 bodů, což dřív stačilo na rozdělení jedné
         *   bubliny na dvě a půlka textu zmizela z překladu; (2) BubbleClassifier.classifyPage
         *   navíc detekuje rozházený/dlaždicovaný vodoznak NAPŘÍČ CELOU stránkou (víc
         *   samostatných OCR bloků se stejným, různě zkomoleným jménem skenlační skupiny),
         *   ne jen jeden blok samotný - staré záznamy mají obojí spočítané podle starší
         *   logiky, proto se musí přepočítat.
         * v6 (2026-08-01): dvě změny mění, co se z OCR doopravdy uloží -
         *   (1) zdrojový jazyk "Auto" konečně vybírá rozpoznávač podle toho, co na stránce
         *   opravdu je (viz [resolveAutoLanguage]) - dřív pro něj nebyla větev a spadl na
         *   LATINKOVÝ model, takže japonská/korejská/čínská stránka vrátila nesmysl nebo nic
         *   a bubliny se navíc seřadily zleva doprava; (2) shlukování dlaždicovaného vodoznaku
         *   už nebere souvislý úsek jako důkaz (viz [looksLikeGarbledRepeat]) - tři repliky,
         *   kde každá jen prodlužuje předchozí ("HELP" / "HELP ME" / "HELP ME NOW"), se dřív
         *   označily za vodoznak a vůbec se nepřeložily. Staré záznamy mají obojí spočítané
         *   podle starší logiky, proto se musí přepočítat.
         * v7 (2026-08-01): prompt nově dostává informaci o tom, které bubliny tvoří JEDNU
         *   větu rozdělenou do dvou (viz [detectContinuations] a sekce "VĚTY PŘES VÍC BUBLIN"
         *   v [GeminiUltraPrompt]). Dřív model viděl jen plochý seznam textů a kaskádovou
         *   repliku překládal po kouscích - každou půlku bez druhé, takže z první vypadl úvod
         *   nebo se druhá přeložila jako samostatná věta. Změna ovlivňuje ULOŽENÝ překlad,
         *   proto se staré záznamy musí přepočítat.
         * v8 (2026-08-01): obrys bubliny se ořezává, aby nesahal přes text bubliny sousední
         *   (viz [clampShapeToOwnLobe]). Kaskádová replika bývá nakreslená jako dvě
         *   PŘEKRÝVAJÍCÍ SE bublinky tvořící jednu spojitou bílou plochu; flood-fill se přes
         *   ten pas přelil do druhého laloku a výplň spodní bubliny pak přemalovala text té
         *   horní - včetně textu, který se vůbec nepřeložil, takže z něj nezbylo nic. Obrys se
         *   ukládá do bloku, proto se staré záznamy musí přepočítat.
         * v9 (2026-08-01): ořez z v8 se u kaskádové bubliny vůbec nespustil. Rozhodoval se podle
         *   toho, jestli se OCR boxy vodorovně překrývají aspoň ze čtvrtiny - jenže laloky
         *   kaskádové bubliny jsou ZÁMĚRNĚ posunuté do stran (horní vpravo, spodní vlevo), právě
         *   to jim dává ten schodovitý tvar, takže se boxy překrývají sotva. Změřeno na zařízení:
         *   oba bloky dostaly totožný tvar celého balónu (0.102..0.637), přesně jako bez opravy.
         *   Nově rozhoduje, jestli tvar POKRÝVÁ cizí text (viz [clampShapeToOwnLobe]); po opravě
         *   tytéž bloky dostaly 0.102..0.311 a 0.335..0.637. Obrys se ukládá, proto přepočet.
         * v10 (2026-08-01): [detectContinuations] u kaskádové bubliny nikdy nesepnulo. Vyžadovalo
         *   vodorovný překryv aspoň 0,35 užší bubliny, jenže laloky jsou posunuté do stran -
         *   změřeno na nahlášené stránce: 0,178. Model se tedy nedozvěděl, že obě půlky tvoří
         *   JEDNU větu, a přeložil je odděleně. Práh je nově 0,15. Ovlivňuje prompt, tedy
         *   ULOŽENÝ překlad, proto se staré záznamy musí přepočítat.
         */
        private const val PIPELINE_VERSION = 10

        /** Maximální počet znaků originálu na jedno API volání - drží výstup pod limitem max_tokens. */
        private const val NOVEL_CHUNK_CHAR_LIMIT = 2500

        /**
         * Maximální součet délky bublinových textů (přes všechny stránky v jedné dávce)
         * pro [translateChapter]/[chunkPages] - nižší než [NOVEL_CHUNK_CHAR_LIMIT], protože
         * odpověď na jednu bublinu nese original+translated+syllable_breaks+notes (několik
         * násobků vstupní délky) plus JSON obálku, ne jen jeden přeložený odstavec.
         */
        private const val CHAPTER_CHUNK_CHAR_LIMIT = 1200

        /** Kolik stránek smí [translateChapter] OCR-ovat souběžně - ML Kit recognizery jsou
         *  sdílené instance a plné rozlišení víc stránek najednou v paměti by zbytečně
         *  riskovalo OOM na slabších telefonech. */
        private const val OCR_PARALLELISM = 3

        /** Kolik stránek smí [translateChapter] stahovat (přes [PageBitmapLoader]) souběžně -
         *  čistě síťové I/O čekání, snese vyšší souběžnost než samotné OCR ([OCR_PARALLELISM]). */
        private const val BITMAP_LOAD_PARALLELISM = 5

        /**
         * Tvrdý strop na stažení bitmapy + OCR JEDNÉ stránky (viz [translateChapter],
         * [translatePage], [getCachedPage] migrace).
         *
         * Bez něj mohla jediná stránka s pomalou/mrtvou síťovou odpovědí viset neomezeně
         * dlouho: OkHttp klient má 30s connect + 30s read timeout a RetryInterceptor to
         * opakuje 3x (viz AppModule), takže jeden nešťastný obrázek dokázal zablokovat
         * celý požadavek až 180 sekund - a [translateChapter] hlásí postup (onPageReady)
         * teprve AŽ PO dokončení OCR úplně všech stránek dávky, takže ta jedna zaseklá
         * stránka zamrazila ukazatel na 0/N pro CELOU kapitolu, i kdyby zbytek dávno
         * doběhl (viz uživatelská zpětná vazba "3 minuty prekladam a porad 0/11" -
         * 3 minuty odpovídají přesně tomu nejhoršímu případu 30s×2×3).
         *
         * 40 sekund je dost pro pomalou mobilní síť, ale citelně pod oněmi 180s - stránka,
         * která tenhle strop nestihne, se prostě přeskočí jako bez textu místo toho, aby
         * appka vypadala zaseklá donekonečna.
         */
        private const val PAGE_OCR_TIMEOUT_MILLIS = 40_000L
    }

    // ── Light novel překlad (prostý text, ne obrázek) ────────────────────────

    /**
     * Přeloží celou kapitolu light novel (odstavce oddělené \n). Rozdělí dlouhý text
     * do více dávek, aby výstup nepřekročil limit tokenů jednoho API volání.
     * @return přeložený text (odstavce spojené \n) nebo null při selhání
     */
    suspend fun translateNovelChapter(
        chapterId: String,
        mangaId: String,
        text: String,
        targetLanguage: String = "Czech",
        sourceLanguage: String = "Auto",
    ): String? {
        getCachedNovel(chapterId, targetLanguage, sourceLanguage)?.let { return it }
        if (!groqClient.isConfigured) return null

        val paragraphs = text.split("\n").filter { it.isNotBlank() }
        if (paragraphs.isEmpty()) return null

        val glossary = glossaryFor(mangaId, targetLanguage)
        // Odstavce se nikdy nedělí NAPŘÍČ dávkami (viz chunkUnits) - jediná výjimka je jeden
        // odstavec delší než limit sám o sobě, ten se rozseká na věty (viz toTranslationUnits),
        // nikdy uprostřed věty.
        val units = toTranslationUnits(paragraphs)
        val chunks = chunkUnits(units)
        val translatedUnits = mutableListOf<String>()
        for (chunk in chunks) {
            val translated = groqClient.translateNovelBatch(chunk.map { it.text }, targetLanguage, sourceLanguage, glossary)
            if (translated.size != chunk.size) return null // dávka selhala nebo neúplná -> necachovat polovičatý výsledek
            translatedUnits += translated
        }

        // Rekonstrukce odstavců: "continuation" kousky (části jednoho moc dlouhého odstavce
        // rozdělené podle vět, viz toTranslationUnits) se spojí zpátky mezerou do JEDNOHO
        // odstavce - teprve mezi SKUTEČNÝMI odstavci jde nový řádek.
        val resultParagraphs = mutableListOf<StringBuilder>()
        units.forEachIndexed { i, unit ->
            if (unit.isContinuation && resultParagraphs.isNotEmpty()) {
                resultParagraphs.last().append(" ").append(translatedUnits[i])
            } else {
                resultParagraphs += StringBuilder(translatedUnits[i])
            }
        }
        val result = resultParagraphs.joinToString("\n") { it.toString() }
        novelDao.upsert(TranslatedNovelEntity(id = novelCacheId(chapterId, sourceLanguage, targetLanguage), translatedText = result))
        return result
    }

    suspend fun getCachedNovel(chapterId: String, targetLanguage: String, sourceLanguage: String = "Auto"): String? =
        novelDao.getById(novelCacheId(chapterId, sourceLanguage, targetLanguage))?.translatedText

    /**
     * Klíč cache přeložených novel. [PIPELINE_VERSION] tu dřív CHYBĚL, i když ho klíč
     * stránek má odjakživa - překlady novel se proto po opravě promptu nikdy nepřepočítaly
     * a uživatel viděl starou, rozbitou verzi navždycky. Přesně tomu mělo verzování zabránit.
     */
    internal fun novelCacheId(chapterId: String, sourceLanguage: String, targetLanguage: String) =
        "$chapterId::$sourceLanguage::$targetLanguage::v$PIPELINE_VERSION"

    /**
     * Jeden "kousek" poslaný k překladu jako samostatná položka dávky. Normální (krátký)
     * odstavec je jeden unit s [isContinuation]=false. Odstavec delší než
     * [NOVEL_CHUNK_CHAR_LIMIT] se rozseká na věty ([splitAtSentenceBoundaries]) do víc units -
     * první má isContinuation=false (začíná nový odstavec), zbytek true (patří k tomu samému
     * odstavci, při skládání výsledku zpátky se spojí mezerou, ne novým řádkem).
     */
    private data class TranslationUnit(val text: String, val isContinuation: Boolean)

    private fun toTranslationUnits(paragraphs: List<String>): List<TranslationUnit> {
        val units = mutableListOf<TranslationUnit>()
        for (p in paragraphs) {
            if (p.length <= NOVEL_CHUNK_CHAR_LIMIT) {
                units += TranslationUnit(p, isContinuation = false)
            } else {
                splitAtSentenceBoundaries(p, NOVEL_CHUNK_CHAR_LIMIT).forEachIndexed { i, piece ->
                    units += TranslationUnit(piece, isContinuation = i > 0)
                }
            }
        }
        return units
    }

    /**
     * Rozdělí text na konce vět (. ! ?) a hladově balí do kusů pod [limit] - NIKDY neuseknuté
     * uprostřed věty. Když ani jedna věta sama o sobě nevejde do limitu (extrémně dlouhá věta
     * bez interpunkce), vrátí ji jako jeden předimenzovaný kus - radši jedno moc velké API
     * volání než rozseknutá věta v půlce.
     */
    private fun splitAtSentenceBoundaries(text: String, limit: Int): List<String> {
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        if (sentences.size <= 1) return listOf(text)

        val pieces = mutableListOf<String>()
        val current = StringBuilder()
        for (s in sentences) {
            if (current.isNotEmpty() && current.length + s.length + 1 > limit) {
                pieces += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(" ")
            current.append(s)
        }
        if (current.isNotEmpty()) pieces += current.toString()
        return pieces
    }

    private fun chunkUnits(units: List<TranslationUnit>): List<List<TranslationUnit>> {
        val chunks = mutableListOf<List<TranslationUnit>>()
        var current = mutableListOf<TranslationUnit>()
        var currentLen = 0
        for (u in units) {
            if (current.isNotEmpty() && currentLen + u.text.length > NOVEL_CHUNK_CHAR_LIMIT) {
                chunks += current
                current = mutableListOf()
                currentLen = 0
            }
            current += u
            currentLen += u.text.length
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }

    // ── JSON (de)serialization ───────────────────────────────────────────────

    private fun List<TranslatedBlock>.serialize(): String = JSONArray().also { arr ->
        forEach { b ->
            arr.put(JSONObject().apply {
                put("orig", b.originalText)
                put("trans", b.translatedText)
                put("disp", b.displayText)
                put("bg", b.bgColorArgb)
                put("bgBottom", b.bgColorBottomArgb)
                put("sfx", b.isSfx)
                put("lc", b.lineCount)
                put("type", b.bubbleType.name)
                put("untrans", b.isUntranslated)
                put("bgUniform", b.bgUniform)
                put("nlh", b.nativeLineHeightF.toDouble())
                b.shape?.let { shape ->
                    put("shape", JSONArray().apply {
                        shape.forEach { p ->
                            put(JSONArray().apply { put(p.yF.toDouble()); put(p.leftF.toDouble()); put(p.rightF.toDouble()) })
                        }
                    })
                }
                // put(String, float) na Android org.json.JSONObject neexistuje (jen desktopová
                // verze knihovny) -> NoSuchMethodError za běhu. Double overload existuje vždy.
                put("l", b.leftF.toDouble())
                put("t", b.topF.toDouble())
                put("r", b.rightF.toDouble())
                put("b", b.bottomF.toDouble())
            })
        }
    }.toString()

    /** disp/bg/sfx/lc/shape/type chybí ve starších cache záznamech - optXxx s výchozí hodnotou stejnou jako [TranslatedBlock] defaults, ať se nic nerozbije. */
    private fun TranslatedPageEntity.deserialize(): List<TranslatedBlock> = try {
        val arr = JSONArray(blocksJson)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val translated = o.getString("trans")
            val shapeArr = o.optJSONArray("shape")
            val shape = if (shapeArr != null) {
                List(shapeArr.length()) { j ->
                    val p = shapeArr.getJSONArray(j)
                    BubbleShapePoint(yF = p.getDouble(0).toFloat(), leftF = p.getDouble(1).toFloat(), rightF = p.getDouble(2).toFloat())
                }
            } else null
            TranslatedBlock(
                originalText = o.getString("orig"),
                translatedText = translated,
                leftF = o.getDouble("l").toFloat(),
                topF = o.getDouble("t").toFloat(),
                rightF = o.getDouble("r").toFloat(),
                bottomF = o.getDouble("b").toFloat(),
                displayText = o.optString("disp", translated),
                bgColorArgb = if (o.has("bg")) o.getInt("bg") else DEFAULT_BUBBLE_BG_ARGB,
                // Starší cache záznamy nemají "bgBottom" - fallback na horní barvu (stejné
                // chování jako TranslatedBlock default), takže degradují na plnou barvu bez
                // gradientu místo pádu, dokud se stránka znovu nepřeloží.
                bgColorBottomArgb = o.optInt("bgBottom", if (o.has("bg")) o.getInt("bg") else DEFAULT_BUBBLE_BG_ARGB),
                isSfx = o.optBoolean("sfx", false),
                lineCount = o.optInt("lc", 1),
                shape = shape,
                bubbleType = try { BubbleType.valueOf(o.optString("type", "SPEECH")) } catch (e: Exception) { BubbleType.SPEECH },
                isUntranslated = o.optBoolean("untrans", false),
                // Starší cache záznamy nemají "bgUniform" - default true (rovnoměrné pozadí)
                // odpovídá chování PŘED touhle změnou (heuristika roztahovala box stejně
                // štědře pro všechny bloky bez tvaru), takže staré záznamy vypadají stejně,
                // dokud se stránka znovu nepřeloží.
                bgUniform = o.optBoolean("bgUniform", true),
                // Starší cache záznamy nemají "nlh" - default 0f (neznámá nativní velikost)
                // znamená, že fitter spadne na dřívější chování (hledej rovnou největší
                // velikost, co se vejde), dokud se stránka znovu nepřeloží.
                nativeLineHeightF = o.optDouble("nlh", 0.0).toFloat(),
            )
        }
    } catch (e: Exception) {
        // Poškozený/nečitelný cache záznam. Prázdný seznam je správná reakce (stránka se
        // přeloží znovu), ale tiše to spolknout znamenalo, že se rozbitá serializace nikdy
        // neprojevila jinak než "překlad se občas záhadně dělá znovu".
        e.report("translate:cache:deserialize")
        emptyList()
    }
}

/**
 * Zkusí [steps] popořadě - výsledek prvního, co vrátí ne-null seznam, se použije. Na
 * rozdíl od prostého řetězce `?:` (dřívější řešení) [RateLimitedException] z JEDNOHO
 * kroku už neznamená okamžitý konec: Gemini/Groq/OpenRouter jsou tři nezávislé komerční
 * služby s vlastní kvótou, 429 z proxy je jen JEJÍ VLASTNÍ limit počtu požadavků (viz
 * komentář u [RateLimitedException]), ne důkaz, že mají vyčerpáno i zbylé dva kroky -
 * "první rate limit = vzdej to" tak zbytečně promarnilo kapacitu, kterou další krok
 * třeba ještě měl.
 *
 * [RateLimitedException] se propaguje dál JEN když byly rate-limited (nebo selhaly)
 * úplně všechny kroky - `ReaderViewModel` na ni má vlastní hlášku a měl by ji dostat
 * pořád, jen ne už po prvním neúspěchu.
 *
 * Top-level (ne metoda [TranslateRepository]) - jde tak otestovat čistě na dvojici
 * fake suspend lambd, bez nutnosti mockovat celý repository se všemi závislostmi.
 */
/**
 * Rozdělí JEDNU dávkovou odpověď zpátky po stránkách podle toho, kolik bublin která stránka
 * poslala. [pageIndices] a [bubbleCounts] jsou dvě strany téhož - i-tá stránka poslala
 * i-tý počet bublin.
 *
 * Vytaženo z [TranslateRepository.translateChapter] kvůli testům: tohle je přesně to místo,
 * kde se text může tiše ztratit nebo (hůř) přesunout k cizí bublině, a přitom to bylo
 * schované uvnitř 90řádkové suspend funkce se sítí, OCR i databází, takže se to nedalo
 * otestovat jinak než celou kapitolou.
 *
 * Když je [blocks] kratší, než součet počtů (model odpověděl méně, než dostal), dostanou
 * chybějící stránky prázdný seznam místo výjimky - nepřeložená stránka je pořád lepší než
 * spadlý překlad celé kapitoly.
 */
internal fun splitBlocksByPage(
    pageIndices: List<Int>,
    bubbleCounts: List<Int>,
    blocks: List<TranslatedBlock>,
): List<Pair<Int, List<TranslatedBlock>>> {
    var offset = 0
    return pageIndices.mapIndexed { i, pageIndex ->
        val count = bubbleCounts[i]
        val from = offset.coerceAtMost(blocks.size)
        val to = (offset + count).coerceAtMost(blocks.size)
        offset += count
        pageIndex to blocks.subList(from, to)
    }
}

internal suspend fun translateChain(vararg steps: suspend () -> List<TranslatedBlock>?): List<TranslatedBlock> {
    var anyRateLimited = false
    for (step in steps) {
        try {
            val result = step()
            if (result != null) return result
        } catch (e: RateLimitedException) {
            anyRateLimited = true
        }
    }
    if (anyRateLimited) throw RateLimitedException()
    return emptyList()
}
