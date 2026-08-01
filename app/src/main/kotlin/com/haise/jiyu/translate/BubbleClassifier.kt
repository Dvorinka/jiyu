package com.haise.jiyu.translate

/**
 * Klasifikuje OCR bloky lokálně (bez API volání) na velikost/typ/SFX, aby:
 *  1) zvukové efekty vůbec nešly na Gemini API (ušetří volání a nezničí "PING!"→"CINK!"),
 *  2) prompt (viz [GeminiUltraPrompt]) věděl, kolik znaků se do bubliny vejde, ještě než
 *     model něco přeloží - jinak se limit dá vynutit jen post-hoc ořezáním, které vypadá
 *     hůř než když model rovnou cílí na správnou délku.
 *
 * OCR nedává tvar/barvu/obrys bubliny, jen text a bounding box - THOUGHT/WHISPER/SHOUT
 * jsou tedy jen odhad z obsahu textu, ne rozpoznání kresby bubliny.
 */
object BubbleClassifier {

    private val sfxWords = setOf(
        "PING", "BOOM", "BAM", "CLICK", "TAP", "KNOCK", "SLAM", "BANG", "CRASH", "POP",
        "SNAP", "ZIP", "POW", "THUD", "CLANG", "DING", "GASP", "SIGH", "COUGH", "SNEEZE",
        "HICCUP", "GULP", "CHOMP", "WHAM", "CRACK", "SPLASH", "BUZZ", "RING", "HONK",
        "SWOOSH", "WHOOSH", "THUMP", "CREAK", "RATTLE", "ZAP", "BOING", "DOKIDOKI",
    )

    private val systemKeywords = listOf(
        "LEVEL UP", "SKILL", "STATUS", " HP", " MP", " EXP", "QUEST", "ACHIEVEMENT", "DUNGEON",
    )

    /**
     * Běžná krátká anglická citoslovce/replika bez mezery, co by jinak spadla do stejného
     * "krátký ALL CAPS bez mezery" pravidla jako opravdové zvukové efekty (viz [detectSfx]) -
     * a protože SFX bublina se nikdy nepřekládá ani nevykresluje (originál zůstává), takhle
     * zůstávala anglicky i naprosto běžná replika typu "DAMN..." (viz uživatelská zpětná
     * vazba - bublina zůstala nepřeložená). Seznam NENÍ o rozpoznání smyslu, jen o vyloučení
     * nejčastějších skutečných slov z falešně pozitivního zásahu.
     */
    private val shortWordsNotSfx = setOf(
        "DAMN", "WAIT", "STOP", "NO", "YES", "HEY", "WHAT", "WHY", "HELP", "RUN", "GO",
        "OK", "OKAY", "HUH", "WHO", "NOW", "LOOK", "COME", "MOVE", "OUT", "HERE", "THERE",
        "WHOA", "OH", "AH", "HA", "UGH", "NOPE", "YEAH", "SURE", "FINE", "GOOD", "BAD",
        "NEVER", "ALWAYS", "PLEASE", "SORRY", "THANKS", "WOW", "DAMMIT", "SHIT", "HELL",
    )

    /**
     * Klasifikuje VŠECHNY bloky jedné stránky najednou - na rozdíl od [classify] (jeden blok
     * bez kontextu okolních) umí navíc odhalit opakovaný dlaždicovaný vodoznak napříč
     * stránkou (viz [detectTiledWatermarkIndices]) a takové bloky označit jako SFX, i když
     * žádný z nich sám o sobě nesplňuje [looksLikeWatermark] - to je jediné místo, odkud má
     * smysl volat [detectTiledWatermarkIndices], protože potřebuje vidět VŠECHNY bloky
     * stránky najednou, ne jeden po druhém.
     */
    fun classifyPage(rawBlocks: List<RawTextBlock>, sourceLanguage: String = AUTO_LANGUAGE): List<ClassifiedBubble> {
        val watermarkIndices = detectTiledWatermarkIndices(rawBlocks)
        return rawBlocks.mapIndexed { i, raw ->
            val classified = classify(raw, raw.lineCount, sourceLanguage)
            if (i in watermarkIndices && !classified.isSfx) {
                classified.copy(isSfx = true, sizeTag = SizeTag.SFX, bubbleType = BubbleType.SFX)
            } else {
                classified
            }
        }
    }

    fun classify(raw: RawTextBlock, lineCount: Int, sourceLanguage: String = AUTO_LANGUAGE): ClassifiedBubble {
        val trimmed = raw.text.trim()
        val letters = trimmed.filter { it.isLetter() }
        val isSfx = detectSfx(raw, trimmed, letters, sourceLanguage)

        val sizeTag = when {
            isSfx -> SizeTag.SFX
            else -> classifySize(raw, trimmed)
        }

        val bubbleType = when {
            isSfx -> BubbleType.SFX
            systemKeywords.any { trimmed.uppercase().contains(it) } -> BubbleType.SYSTEM
            // Text (VELKÁ PÍSMENA + "!") NEBO skutečný detekovaný tvar bubliny (trsovitý/
            // hvězdicovitý obrys, viz isJaggedShape) - dřív se SHOUT hádal jen z textu, i
            // když appka od nedávna zná skutečný obrys bubliny (BubbleShapeDetector).
            (letters.isNotEmpty() && letters.all { it.isUpperCase() } && trimmed.endsWith("!")) ||
                raw.shape?.let { isJaggedShape(it) } == true -> BubbleType.SHOUT
            trimmed.startsWith("(") && trimmed.endsWith(")") -> BubbleType.WHISPER
            trimmed.endsWith("...") || trimmed.startsWith("...") -> BubbleType.THOUGHT
            lineCount >= 3 && letters.length > 60 -> BubbleType.NARRATION
            else -> BubbleType.SPEECH
        }

        return ClassifiedBubble(raw = raw, sizeTag = sizeTag, bubbleType = bubbleType, isSfx = isSfx, lineCount = lineCount)
    }

    private fun classifySize(raw: RawTextBlock, trimmed: String): SizeTag {
        val width = raw.rightF - raw.leftF
        val height = raw.bottomF - raw.topF
        val aspectRatio = if (height > 0f) width / height else 1f
        return when {
            aspectRatio > 3.0f -> SizeTag.WIDE
            aspectRatio < 0.5f -> SizeTag.TALL
            trimmed.length <= SizeTag.TINY.maxChars -> SizeTag.TINY
            trimmed.length <= SizeTag.SMALL.maxChars -> SizeTag.SMALL
            trimmed.length <= SizeTag.MEDIUM.maxChars -> SizeTag.MEDIUM
            else -> SizeTag.LARGE
        }
    }

    /** "SIRENSCANS.COM", "ENSCANS.COM" apod. - viz [looksLikeWatermark]. */
    private val domainPattern = Regex("[A-Z0-9]{2,}\\.(COM|NET|ORG|INFO|IO|TO|CC|ME)")

    /**
     * Smí se použít pravidlo "krátký text velkými písmeny bez mezer = zvuk"?
     *
     * To pravidlo je nebezpečné samo o sobě - stejně jako "BOOM" ho splňuje i spousta
     * skutečných krátkých replik. Jedinou pojistkou proti tomu je [shortWordsNotSfx], jenže
     * ten seznam je čistě ANGLICKÝ. U španělského, francouzského nebo indonéského komiksu
     * tedy pravidlo platí bez sítě a běžné krátké repliky označí za zvuk - a SFX bublina se
     * nikdy nepřekládá ani nevykresluje, takže na stránce prostě zůstane originál.
     *
     * Kde seznam neplatí, se pravidlo raději vynechá. Nejhorší, co se pak stane, je že se
     * přeloží i opravdový zvuk ("BOOM" -> "BUM") - o řád menší škoda než spolykaná replika.
     * Skutečné zvuky navíc pořád chytá [sfxWords] a u CJK pravidlo o opakovaném vzoru.
     *
     * Omezení: pod "Auto" se latinkové jazyky od sebe rozeznat nedají (všechny čte jeden
     * model, viz [AUTO_CANDIDATE_LANGUAGES]), takže tam zůstává anglické chování. Rozhoduje
     * to, co má uživatel NASTAVENÉ.
     */
    private fun canVetShortAllCaps(sourceLanguage: String): Boolean =
        sourceLanguage == AUTO_LANGUAGE || sourceLanguage == "English"

    private fun detectSfx(raw: RawTextBlock, trimmed: String, letters: String, sourceLanguage: String): Boolean {
        if (trimmed.isEmpty()) return false

        // Čistě symboly/interpunkce - "!!!", "???", "*gasp*" bez písmen kolem
        if (letters.isEmpty() && trimmed.any { it == '!' || it == '?' || it == '*' }) return true

        val core = trimmed.trim('*', '!', '?', '.', ' ')
        if (core.isEmpty()) return false

        if (looksLikeWatermark(raw, core)) return true

        // Holé číslo bez jediného písmene - typicky číslo panelu/stránky vypálené do skenu
        // (běžné u starších scanlation releasů jako MangaStream), ne replika. Skutečný dialog
        // se nikdy nezúží na samotnou číslici bez okolního textu. Bez tohohle OCR box kolem
        // takového čísla prochází i shape detekcí, kde floodfill z okolního bílého pozadí
        // často "uteče" do sousední skutečné bubliny a vytvoří tvar mimo obě.
        if (letters.isEmpty() && core.all { it.isDigit() }) return true

        // Krátký ALL CAPS text bez mezer (typicky zvuk, ne věta) - "BOOM!!!" ale ne "NO WAY".
        // Vyjímka pro běžná krátká slova (viz shortWordsNotSfx) - ta stejné pravidlo splňují,
        // ale jsou to skutečné repliky, ne zvukové efekty.
        if (canVetShortAllCaps(sourceLanguage) &&
            letters.length in 1..6 && letters.all { it.isUpperCase() } && !core.contains(' ') &&
            core.uppercase() !in shortWordsNotSfx
        ) return true

        if (sfxWords.contains(core.uppercase())) return true

        // CJK zvuky bývají krátký text složený z opakující se znakové sekvence (např. "ドドド"),
        // na rozdíl od běžné repliky, kde se znaky neopakují takhle mechanicky.
        if (core.length in 2..6 && core.any { it.code > 0x3000 } && isRepeatingPattern(core)) return true

        return false
    }

    /**
     * Vodoznak/tag scanlation skupiny přes kresbu (např. "SirenScans.com" diagonálně přes
     * panel) se chová jako normální OCR blok a dřív se přeložil a překryl plnou barevnou
     * plochou přes půl obrázku (viz uživatelská zpětná vazba - černá skvrna přes obličej
     * postavy). Dvě nezávislé stopy:
     *  1) Text obsahuje doménový vzor (".com"/".net"/...) - vodoznaky jsou skoro vždy
     *     web adresa skenlační skupiny, normální replika takhle nikdy nevypadá.
     *  2) Vodoznak čtený OCR "po písmenkách" (svisle otočený text) sloučí spoustu OCR
     *     řádků do jednoho hodně úzkého a hodně vysokého bloku - normální dialogová
     *     bublina takhle nevypadá ani u dlouhé replity.
     */
    private fun looksLikeWatermark(raw: RawTextBlock, core: String): Boolean {
        val collapsed = core.replace(" ", "").replace("\n", "").uppercase()
        if (domainPattern.containsMatchIn(collapsed)) return true

        val width = raw.rightF - raw.leftF
        val height = raw.bottomF - raw.topF
        val aspectRatio = if (height > 0f) width / height else 1f
        return raw.lineCount >= 8 && aspectRatio < 0.15f
    }

    private fun isRepeatingPattern(text: String): Boolean {
        for (unitLen in 1..2) {
            if (text.length % unitLen != 0 || text.length / unitLen < 2) continue
            val unit = text.substring(0, unitLen)
            if (text.chunked(unitLen).all { it == unit }) return true
        }
        return false
    }

    private const val WATERMARK_MIN_OVERLAP_CHARS = 4
    private const val WATERMARK_MAX_NORMALIZED_LENGTH = 24
    private const val WATERMARK_CLUSTER_MIN_SIZE = 3

    /**
     * Indexy bloků, které jsou součástí OPAKOVANÉHO DLAŽDICOVANÉHO VODOZNAKU - stejný krátký
     * text (typicky název/adresa skenlační skupiny) nastampovaný vícekrát po stránce, každý
     * výskyt jinak zkomolený OCR. Žádný JEDNOTLIVÝ výskyt sám o sobě nemusí vypadat podezřele
     * (na to je [looksLikeWatermark]), ale napříč stránkou tvoří jasný vzorec - viz uživatelská
     * zpětná vazba: "MADRASCANS MADRASCANS"/"MAD ANS"/"4ANS"/"MADRASCANS"/"MADRASCANS" jako pět
     * samostatných bloků na jedné stránce, žádný z nich sám o sobě nesplňoval existující
     * pravidla (moc dlouhý na krátké-ALL-CAPS pravidlo, nebo obsahuje mezeru).
     *
     * Union-find nad krátkými bloky (stejný vzor jako [mergeNearbyLines] v BubbleMerge.kt):
     * dva krátké bloky patří do stejného shluku, když kratší z jejich normalizovaných textů
     * (jen písmena/číslice, velká písmena, časté OCR záměny číslice->písmeno srovnané na
     * společný tvar) je PŘIBLIŽNÁ PODPOSLOUPNOST toho delšího - to zachytí i vypadlá/zaměněná
     * písmena, ne jen přesné podřetězce.
     *
     * Shluk se považuje za vodoznak, jen když má aspoň [WATERMARK_CLUSTER_MIN_SIZE] členů A
     * ZÁROVEŇ mezi nimi existuje aspoň jedna SKUTEČNÁ odchylka (ne všichni členové jsou
     * byte-po-bytu stejní) - jinak by stejné krátké slovo řečené vícekrát v dialogu (např.
     * jméno postavy) mohlo dopadnout stejně jako vodoznak. Vodoznak se pozná právě podle toho,
     * že se OPAKOVANĚ ČTE JINAK (různé zkomoleniny téhož), ne podle toho, že se opakuje.
     */
    internal fun detectTiledWatermarkIndices(blocks: List<RawTextBlock>): Set<Int> {
        val normalized = blocks.map { normalizeForWatermarkMatch(it.text) }

        val eligible = normalized.indices.filter {
            normalized[it].length in WATERMARK_MIN_OVERLAP_CHARS..WATERMARK_MAX_NORMALIZED_LENGTH
        }
        if (eligible.size < WATERMARK_CLUSTER_MIN_SIZE) return emptySet()

        val parent = IntArray(blocks.size) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) { val next = parent[c]; parent[c] = r; c = next }
            return r
        }
        fun union(a: Int, b: Int) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        for (i in eligible.indices) {
            for (j in i + 1 until eligible.size) {
                val a = eligible[i]
                val b = eligible[j]
                val (shorter, longer) = if (normalized[a].length <= normalized[b].length) {
                    normalized[a] to normalized[b]
                } else {
                    normalized[b] to normalized[a]
                }
                if (looksLikeGarbledRepeat(shorter, longer)) union(a, b)
            }
        }

        val result = mutableSetOf<Int>()
        for (members in eligible.groupBy { find(it) }.values) {
            if (members.size < WATERMARK_CLUSTER_MIN_SIZE) continue
            val distinctTexts = members.map { normalized[it] }.toSet()
            if (distinctTexts.size < 2) continue // vsichni bajt-po-bajtu stejni - moznadopakovana replika, ne vodoznak
            result += members
        }
        return result
    }

    /** Písmena+číslice, velká písmena, běžné OCR záměny číslice->písmeno srovnané na společný tvar. */
    private fun normalizeForWatermarkMatch(text: String): String {
        val ocrConfusions = mapOf('0' to 'O', '1' to 'I', '4' to 'A', '5' to 'S', '8' to 'B', '3' to 'E')
        return text.uppercase()
            .filter { it.isLetterOrDigit() }
            .map { ocrConfusions[it] ?: it }
            .joinToString("")
    }

    /**
     * Jsou tyhle dva texty dvěma ČTENÍMI TÉHOŽ nápisu, každé jinak zkomolené?
     *
     * Samotná "je podposloupnost" nestačí a dělala falešné poplachy: tři repliky, kde každá
     * jen prodlužuje předchozí ("HELP" / "HELP ME" / "HELP ME NOW", nebo jméno s různými
     * příponami), tuhle podmínku splňují taky - shlukly se do "vodoznaku", označily jako SFX
     * a tím pádem se vůbec nepřeložily; na stránce zůstal originál.
     *
     * Rozdíl je v tom, JAK se kratší text v delším nachází:
     *  - souvislý úsek ("HELP" v "HELPME") = jeden text prostě pokračuje, běžný dialog
     *  - podposloupnost s dírami ("MADANS" v "MADRASCANS") = uprostřed vypadla nebo se
     *    zaměnila písmena, což je přesně otisk OCR čtoucího tentýž nápis pokaždé jinak
     *
     * Skutečný nahlášený případ (MADRASCANS / MAD ANS / 4ANS / ...) tímhle prochází dál,
     * protože jeho varianty mají díry uvnitř, ne jen useknutý konec.
     */
    private fun looksLikeGarbledRepeat(shorter: String, longer: String): Boolean =
        isApproxSubsequence(shorter, longer) && !longer.contains(shorter)

    /** True, když se [needle] dá najít jako podposloupnost (ne nutně souvislá) v [haystack]. */
    private fun isApproxSubsequence(needle: String, haystack: String): Boolean {
        if (needle.isEmpty()) return false
        var hIdx = 0
        for (c in needle) {
            while (hIdx < haystack.length && haystack[hIdx] != c) hIdx++
            if (hIdx == haystack.length) return false
            hIdx++
        }
        return true
    }
}
