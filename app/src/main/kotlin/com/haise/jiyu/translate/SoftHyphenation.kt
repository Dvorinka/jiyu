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
 * Nejmenší počet PÍSMEN, který smí zbýt na kterékoli straně zlomu.
 *
 * Stejné pravidlo, jaké si už dodržuje vlastní slabikování appky (viz [hyphenateWord] -
 * `word.length - c >= 2`). Písmena, ne znaky: "DNY." má čtyři znaky, ale jen tři písmena, a
 * zlom, po kterém zbyde jediné písmeno a tečka, je stejně ošklivý jako zlom před samotným "Í".
 */
private const val MIN_HYPHEN_CHUNK_LETTERS = 2

/**
 * True, když se [syllableBreaks] (model výstup s měkkými rozdělovníky, viz GeminiUltraPrompt
 * "DĚLENÍ SLOV") dá použít místo [translated].
 *
 * Kontroluje se dvojí:
 *
 * 1. Že po odstranění všech rozdělovníků vyjde přesně [translated]. Model občas vrátí
 *    syllable_breaks, který se od translated liší (jiná slova, chybějící/navíc znaky,
 *    poškozený JSON) - použití takového textu přímo by potichu nahradilo správný překlad
 *    viditelně poškozeným (viz uživatelská zpětná vazba - "OKAMŽITĚ" vyšlo jako "OKAM").
 *
 * 2. Že zlomy vůbec dávají smysl, tedy nenechávají osamocené písmeno. Tahle půlka dlouho
 *    chyběla, a je to přesně ta chyba, kterou nahlásil uživatel: v bublině vyšlo "POSLEDN"
 *    a pod tím osamocené "Í". Text po odstranění rozdělovníku seděl, takže první kontrola ho
 *    pustila dál - a KAM ten zlom padne, nekontroloval nikdo. Vlastní slabikování appky by
 *    takový zlom nikdy nevyrobilo, jen se na model spoléhalo víc, než si zasloužil.
 *
 * Volající (TranslateRepository) při neúspěchu spadne na [ensureFallbackHyphens] nad prostým
 * [translated], takže odmítnutí modelu neznamená text bez rozdělovníků - jen rozdělovníky
 * spočítané appkou.
 */
fun isValidSyllableBreaks(translated: String, syllableBreaks: String): Boolean {
    if (syllableBreaks.replace(SOFT_HYPHEN.toString(), "") != translated) return false
    return syllableBreaks.split(' ', '\n').none { token -> hasUnusableBreak(token) }
}

/** Rozpadá se [token] rozdělovníky na kus, ve kterém zbyde míň než [MIN_HYPHEN_CHUNK_LETTERS] písmen? */
private fun hasUnusableBreak(token: String): Boolean {
    if (SOFT_HYPHEN !in token) return false
    return token.split(SOFT_HYPHEN).any { chunk ->
        chunk.count { it.isLetter() } < MIN_HYPHEN_CHUNK_LETTERS
    }
}

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
