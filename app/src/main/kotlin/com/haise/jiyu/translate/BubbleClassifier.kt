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

    fun classify(raw: RawTextBlock, lineCount: Int): ClassifiedBubble {
        val trimmed = raw.text.trim()
        val letters = trimmed.filter { it.isLetter() }
        val isSfx = detectSfx(raw, trimmed, letters)

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

    private fun detectSfx(raw: RawTextBlock, trimmed: String, letters: String): Boolean {
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
        if (letters.length in 1..6 && letters.all { it.isUpperCase() } && !core.contains(' ') &&
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
}
