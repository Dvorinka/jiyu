package com.haise.jiyu.translate

/**
 * Neviditelný rozdělovník (U+00AD) - renderer (viz AutoFitTranslatedText) na něm smí
 * zalomit řádek, jinde ne. Sestaveno z číselného kódu přes [Char], ne jako literální
 * neviditelný znak v souboru - ten je nebezpečný na dohledání/úpravu (viz podobný
 * incident s NUL bajtem v ChapterStorage.kt).
 */
private val SOFT_HYPHEN: Char = 173.toChar()

private val CZECH_VOWELS = "aeiouyáéíóúůýěAEIOUYÁÉÍÓÚŮÝĚ".toSet()

/**
 * True, když [syllableBreaks] (model výstup s měkkými rozdělovníky, viz GeminiUltraPrompt
 * "DĚLENÍ SLOV") odpovídá [translated] po odstranění všech měkkých rozdělovníků. Model občas
 * vrátí syllable_breaks, který se od translated liší (jiná slova, chybějící/navíc znaky,
 * poškozený JSON) - použití takového textu přímo by potichu nahradilo správný překlad
 * viditelně poškozeným textem (viz uživatelská zpětná vazba - "OKAMŽITĚ" vyšlo jako "OKAM"
 * a zbytek nesmyslně rozbitý). Volající (TranslateRepository) při selhání validace fallbackne
 * na obyčejný [translated] bez rozdělovníků, ne na podezřelý text od modelu.
 */
fun isValidSyllableBreaks(translated: String, syllableBreaks: String): Boolean =
    syllableBreaks.replace(SOFT_HYPHEN.toString(), "") == translated

/**
 * Vloží záložní měkké rozdělovníky do slov dlouhých aspoň [minWordLength] znaků, která JEŠTĚ
 * žádný nemají (ani od modelu, viz [isValidSyllableBreaks]) - bezpečnostní síť pro případ, že
 * model syllable_breaks nevrátí vůbec, nebo je zahodíme jako neplatné. Nejde o lingvisticky
 * přesnou slabikaci, jen o rozumná místa zlomu blízko hranice samohláska-souhláska, aby
 * renderer nikdy neuřízl slovo doprostřed shluku souhlásek bez viditelné pomlčky (viz
 * uživatelská zpětná vazba - "BŘÍŠKO" rozlomené na "BŘÍŠ"/"KO" bez jakéhokoli spojovníku).
 *
 * Kontrola/vkládání probíhá PER SLOVO (ne za celý text najednou) - když model přidal
 * rozdělovník jen do JEDNOHO slova bubliny, ostatní dlouhá slova bez rozdělovníku pořád
 * dostanou tuhle záložní síť.
 */
fun ensureFallbackHyphens(text: String, minWordLength: Int = 6): String {
    val wordRegex = Regex("\\p{L}+")
    return wordRegex.replace(text) { match ->
        val word = match.value
        if (SOFT_HYPHEN in word || word.length < minWordLength) word else hyphenateWord(word)
    }
}

/**
 * Kandidát na zlom = samohláska bezprostředně následovaná souhláskou (konec slabičného
 * jádra) - nikdy uprostřed shluku souhlásek, nikdy na samém začátku/konci slova. Z kandidátů
 * se vybírají jen ty, které od posledního zlomu (nebo začátku slova) oddělují aspoň
 * [targetChunkLength] znaků A necháte za sebou aspoň 2 znaky do konce slova - jinak by vznikly
 * jednopísmenné/dvoupísmenné useknuté kusy, což vypadá stejně rozbitě jako žádný zlom vůbec.
 */
private fun hyphenateWord(word: String, targetChunkLength: Int = 3): String {
    val candidates = mutableListOf<Int>()
    for (i in 1 until word.length - 1) {
        if (word[i - 1] in CZECH_VOWELS && word[i] !in CZECH_VOWELS) candidates += i
    }
    if (candidates.isEmpty()) return word

    val breaks = mutableListOf<Int>()
    var lastBreak = 0
    for (c in candidates) {
        if (c - lastBreak >= targetChunkLength && word.length - c >= 2) {
            breaks += c
            lastBreak = c
        }
    }
    if (breaks.isEmpty()) return word

    val sb = StringBuilder()
    var prev = 0
    for (b in breaks) {
        sb.append(word, prev, b)
        sb.append(SOFT_HYPHEN)
        prev = b
    }
    sb.append(word, prev, word.length)
    return sb.toString()
}
