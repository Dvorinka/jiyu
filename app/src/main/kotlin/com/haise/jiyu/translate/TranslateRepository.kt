package com.haise.jiyu.translate

import com.haise.jiyu.data.db.TranslatedNovelDao
import com.haise.jiyu.data.db.TranslatedPageDao
import com.haise.jiyu.data.db.entity.TranslatedNovelEntity
import com.haise.jiyu.data.db.entity.TranslatedPageEntity
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslateRepository @Inject constructor(
    private val ocrEngine: OcrEngine,
    private val groqClient: GroqTranslateClient,
    private val geminiClient: GeminiTranslateClient,
    private val glossaryRepository: GlossaryRepository,
    private val dao: TranslatedPageDao,
    private val novelDao: TranslatedNovelDao,
) {
    val isApiKeyConfigured: Boolean get() = groqClient.isConfigured

    private suspend fun glossaryFor(mangaId: String, targetLanguage: String): Map<String, String> =
        glossaryRepository.getMap(mangaId, targetLanguage)

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

        val rawBlocks = ocrEngine.recognize(pageUrl, sourceLanguage)
        if (rawBlocks.isEmpty()) return emptyList()

        val glossary = glossaryFor(mangaId, targetLanguage)
        val classified = rawBlocks.map { raw -> BubbleClassifier.classify(raw, raw.lineCount) }

        // GeminiUltraPrompt je napsaný natvrdo pro češtinu (znakové limity a kompresní
        // pravidla mají české příklady) - pro jiný cílový jazyk zůstáváme na obecném
        // Groq promptu (translate-proxy mode="manga"), který jazyk dostává jako parametr.
        val blocks = if (targetLanguage == "Czech") {
            // 1) Gemini. 2) Stejný "ultra" prompt (komprese/sylabické dělení), ale přes Groq
            //    jako upstream - viz GeminiTranslateClient.translateBubbles(provider="groq") -
            //    zachytí Gemini-specifické selhání (deprekovaný model, jeho vlastní výpadek)
            //    beze ztráty kvality. 3) Holý Groq překlad bez komprese jako poslední záchrana.
            // [RateLimitedException] se NEODCHYTÁVÁ ani na jednom kroku - proxy má jednu
            // sdílenou denní kvótu pro všechny tři cesty (viz translate-proxy checkQuota),
            // takže jakmile je vyčerpaná, další pokus by dopadl stejně - rovnou se propaguje
            // až do ReaderViewModelu, který na to má vlastní hlášku.
            translateWithGemini(classified, glossary, provider = "gemini")
                ?: translateWithGemini(classified, glossary, provider = "groq")
                ?: translateWithGroq(classified, glossary, targetLanguage, sourceLanguage)
        } else {
            translateWithGroq(classified, glossary, targetLanguage, sourceLanguage)
        }
        if (blocks.isEmpty()) return emptyList()

        dao.upsert(TranslatedPageEntity(id = cacheId(chapterId, pageIndex, targetLanguage, sourceLanguage), blocksJson = blocks.serialize()))
        return blocks
    }

    /**
     * @param provider "gemini" nebo "groq" - viz [GeminiTranslateClient.translateBubbles].
     *   Oba provideři používají STEJNÝ [GeminiUltraPrompt] (komprese, sylabické dělení),
     *   liší se jen upstream model, na který proxy request přepošle.
     * @return null když se nepodařilo přeložit ani jednu bublinu (proxy nemá nasazený
     *   "gemini" mód, síť selhala po všech pokusech, upstream model vrátil chybu...) -
     *   volající ([translatePage]) pak zkusí druhého providera nebo nakonec
     *   [translateWithGroq] (bez komprese) jako poslední záchrannou síť.
     * @throws RateLimitedException když je vyčerpaná sdílená denní kvóta proxy - viz
     *   [translatePage], tohle se záměrně NEODCHYTÁVÁ tady.
     */
    private suspend fun translateWithGemini(
        classified: List<ClassifiedBubble>,
        glossary: Map<String, String>,
        provider: String,
    ): List<TranslatedBlock>? {
        if (!geminiClient.isConfigured) return null
        val response = geminiClient.translateBubbles(classified, glossary, provider) ?: return null
        val byId = response.bubbles.associateBy { it.id }

        val result = classified.mapIndexedNotNull { i, c ->
            if (c.isSfx) return@mapIndexedNotNull sfxBlock(c)
            val t = byId[i] ?: return@mapIndexedNotNull null
            TranslatedBlock(
                originalText = c.raw.text,
                translatedText = t.translated,
                leftF = c.raw.leftF,
                topF = c.raw.topF,
                rightF = c.raw.rightF,
                bottomF = c.raw.bottomF,
                displayText = t.syllableBreaks.ifBlank { t.translated },
                bgColorArgb = c.raw.bgColorArgb,
                isSfx = false,
                lineCount = c.lineCount,
                shape = c.raw.shape,
                bubbleType = c.bubbleType,
            )
        }
        return result.ifEmpty { null }
    }

    /** Legacy/fallback cesta - Groq vrací jen holé přeložené texty, žádné syllable_breaks. */
    private suspend fun translateWithGroq(
        classified: List<ClassifiedBubble>,
        glossary: Map<String, String>,
        targetLanguage: String,
        sourceLanguage: String,
    ): List<TranslatedBlock> {
        val toTranslate = classified.filter { !it.isSfx }
        val translations = if (toTranslate.isEmpty()) emptyList() else groqClient.translateBatch(
            texts = toTranslate.map { it.raw.text },
            targetLanguage = targetLanguage,
            sourceLanguage = sourceLanguage,
            glossary = glossary,
        )
        if (toTranslate.isNotEmpty() && translations.isEmpty()) return emptyList()

        var ti = 0
        return classified.map { c ->
            if (c.isSfx) {
                sfxBlock(c)
            } else {
                val translated = translations.getOrElse(ti) { c.raw.text }
                ti++
                TranslatedBlock(
                    originalText = c.raw.text,
                    translatedText = translated,
                    leftF = c.raw.leftF,
                    topF = c.raw.topF,
                    rightF = c.raw.rightF,
                    bottomF = c.raw.bottomF,
                    displayText = translated,
                    bgColorArgb = c.raw.bgColorArgb,
                    isSfx = false,
                    lineCount = c.lineCount,
                    shape = c.raw.shape,
                    bubbleType = c.bubbleType,
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
        bgColorArgb = c.raw.bgColorArgb,
        isSfx = true,
        lineCount = c.lineCount,
        shape = c.raw.shape,
        bubbleType = c.bubbleType,
    )

    /**
     * Vrátí výsledek z Room cache bez volání překladového API; null = není v cache.
     * @param pageUrl když je zadané a cache záznam ještě nemá dopočítaný tvar bubliny
     *   (starý formát), dopočítá se tvar (bez nového OCR/překladu) a cache se přepíše -
     *   viz OcrEngine.detectShapesOnly. Bez pageUrl (starší volající, co ho nemají po ruce)
     *   se migrace přeskočí a bloky zůstanou s shape=null (heuristický fallback v layoutu).
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

        val migrated = ocrEngine.detectShapesOnly(pageUrl, cached)
        dao.upsert(TranslatedPageEntity(id = id, blocksJson = migrated.serialize()))
        return migrated
    }

    private fun cacheId(chapterId: String, pageIndex: Int, targetLanguage: String, sourceLanguage: String) =
        "$chapterId::$pageIndex::$sourceLanguage::$targetLanguage"

    companion object {
        /** Maximální počet znaků originálu na jedno API volání - drží výstup pod limitem max_tokens. */
        private const val NOVEL_CHUNK_CHAR_LIMIT = 2500
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
        val chunks = chunkParagraphs(paragraphs)
        val translatedParagraphs = mutableListOf<String>()
        for (chunk in chunks) {
            val translated = groqClient.translateNovelBatch(chunk, targetLanguage, sourceLanguage, glossary)
            if (translated.size != chunk.size) return null // dávka selhala nebo neúplná -> necachovat polovičatý výsledek
            translatedParagraphs += translated
        }

        val result = translatedParagraphs.joinToString("\n")
        novelDao.upsert(TranslatedNovelEntity(id = novelCacheId(chapterId, sourceLanguage, targetLanguage), translatedText = result))
        return result
    }

    suspend fun getCachedNovel(chapterId: String, targetLanguage: String, sourceLanguage: String = "Auto"): String? =
        novelDao.getById(novelCacheId(chapterId, sourceLanguage, targetLanguage))?.translatedText

    private fun novelCacheId(chapterId: String, sourceLanguage: String, targetLanguage: String) =
        "$chapterId::$sourceLanguage::$targetLanguage"

    private fun chunkParagraphs(paragraphs: List<String>): List<List<String>> {
        val chunks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var currentLen = 0
        for (p in paragraphs) {
            if (current.isNotEmpty() && currentLen + p.length > NOVEL_CHUNK_CHAR_LIMIT) {
                chunks += current
                current = mutableListOf()
                currentLen = 0
            }
            current += p
            currentLen += p.length
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
                put("sfx", b.isSfx)
                put("lc", b.lineCount)
                put("type", b.bubbleType.name)
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
                isSfx = o.optBoolean("sfx", false),
                lineCount = o.optInt("lc", 1),
                shape = shape,
                bubbleType = try { BubbleType.valueOf(o.optString("type", "SPEECH")) } catch (e: Exception) { BubbleType.SPEECH },
            )
        }
    } catch (e: Exception) { emptyList() }
}
