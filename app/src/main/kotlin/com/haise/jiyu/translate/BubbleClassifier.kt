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

    fun classify(raw: RawTextBlock, lineCount: Int): ClassifiedBubble {
        val trimmed = raw.text.trim()
        val letters = trimmed.filter { it.isLetter() }
        val isSfx = detectSfx(trimmed, letters)

        val sizeTag = when {
            isSfx -> SizeTag.SFX
            else -> classifySize(raw, trimmed)
        }

        val bubbleType = when {
            isSfx -> BubbleType.SFX
            systemKeywords.any { trimmed.uppercase().contains(it) } -> BubbleType.SYSTEM
            letters.isNotEmpty() && letters.all { it.isUpperCase() } && trimmed.endsWith("!") -> BubbleType.SHOUT
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

    private fun detectSfx(trimmed: String, letters: String): Boolean {
        if (trimmed.isEmpty()) return false

        // Čistě symboly/interpunkce - "!!!", "???", "*gasp*" bez písmen kolem
        if (letters.isEmpty() && trimmed.any { it == '!' || it == '?' || it == '*' }) return true

        val core = trimmed.trim('*', '!', '?', '.', ' ')
        if (core.isEmpty()) return false

        // Holé číslo bez jediného písmene - typicky číslo panelu/stránky vypálené do skenu
        // (běžné u starších scanlation releasů jako MangaStream), ne replika. Skutečný dialog
        // se nikdy nezúží na samotnou číslici bez okolního textu. Bez tohohle OCR box kolem
        // takového čísla prochází i shape detekcí, kde floodfill z okolního bílého pozadí
        // často "uteče" do sousední skutečné bubliny a vytvoří tvar mimo obě.
        if (letters.isEmpty() && core.all { it.isDigit() }) return true

        // Krátký ALL CAPS text bez mezer (typicky zvuk, ne věta) - "BOOM!!!" ale ne "NO WAY"
        if (letters.length in 1..6 && letters.all { it.isUpperCase() } && !core.contains(' ')) return true

        if (sfxWords.contains(core.uppercase())) return true

        // CJK zvuky bývají krátký text složený z opakující se znakové sekvence (např. "ドドド"),
        // na rozdíl od běžné repliky, kde se znaky neopakují takhle mechanicky.
        if (core.length in 2..6 && core.any { it.code > 0x3000 } && isRepeatingPattern(core)) return true

        return false
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
