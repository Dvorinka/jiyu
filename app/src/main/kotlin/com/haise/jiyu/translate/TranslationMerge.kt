package com.haise.jiyu.translate

/**
 * Slučování odpovědi modelu zpátky s bublinami, které se posílaly - a hlavně rozhodnutí,
 * co je vlastně použitelný překlad.
 *
 * Proč to má vlastní soubor: model občas nějakou bublinu v JSON odpovědi prostě vynechá,
 * nebo pro ni vrátí prázdný řetězec. Dřív se v takovém případě potichu propadl ORIGINÁL
 * (anglický text) do pole `translatedText` a vykreslil se přes bublinu jako plnohodnotný
 * překlad - čtenář tak viděl anglickou větu vysázenou "česky vypadajícím" způsobem přes
 * zakrytý originál. Tomu se říká tiché selhání a je horší než žádný překlad: kdyby se
 * bublina označila jako nepřeložená, overlay by ji vůbec nekreslil a originál by zůstal
 * čitelný (viz `TranslationLayer` a `TranslatedBlock.isUntranslated`).
 */

/**
 * true = tenhle záznam z odpovědi modelu se dá použít jako překlad.
 *
 * Nepoužitelný je záznam, který chybí úplně (model bublinu vynechal), má prázdný překlad,
 * nebo nese [GeminiUltraPrompt.UNTRANSLATED_MARKER] (model sám říká "tohle OCR nedává smysl").
 * Ve všech třech případech patří bublina mezi nepřeložené, ne mezi přeložené originálem.
 */
internal fun isUsableTranslation(translation: GeminiBubbleTranslation?): Boolean {
    val text = translation?.translated?.trim() ?: return false
    return text.isNotEmpty() && text != GeminiUltraPrompt.UNTRANSLATED_MARKER
}

/**
 * Indexy bublin, na které model neodpověděl použitelně a má smysl se na ně doptat znovu.
 *
 * SFX se vynechávají - ty se schválně nepřekládají vůbec (viz [BubbleClassifier]), takže
 * chybějící odpověď u nich není chyba. Bubliny s [GeminiUltraPrompt.UNTRANSLATED_MARKER] se
 * taky vynechávají: model už jednou vědomě řekl "tohle nepřeložím", opakovaný dotaz na to
 * samé nedává smysl a jen by stál další požadavek.
 *
 * @param byId odpověď modelu naindexovaná podle "id" (= pozice v seznamu, který se posílal)
 */
internal fun missingTranslationIndices(
    classified: List<ClassifiedBubble>,
    byId: Map<Int, GeminiBubbleTranslation>,
): List<Int> = classified.indices.filter { i ->
    if (classified[i].isSfx) return@filter false
    val t = byId[i]
    if (t == null) return@filter true
    // Vědomé "nepřeložím" se neopakuje, prázdná/chybějící odpověď ano.
    t.translated.trim().isEmpty()
}

/**
 * Doplní do [byId] záznamy z opravného dotazu.
 *
 * Opravný dotaz posílá jen podmnožinu bublin, takže "id" v jeho odpovědi jsou pozice v TÉ
 * podmnožině (0..n-1), ne v původním seznamu - [retriedIndices] je převodní tabulka zpět.
 * Použitelný záznam z opravy má přednost; nepoužitelný se zahodí, aby nepřepsal případný
 * dřívější dobrý výsledek.
 */
internal fun mergeRetry(
    byId: Map<Int, GeminiBubbleTranslation>,
    retriedIndices: List<Int>,
    retryResponse: GeminiTranslationResponse?,
): Map<Int, GeminiBubbleTranslation> {
    if (retryResponse == null) return byId
    val merged = byId.toMutableMap()
    for (bubble in retryResponse.bubbles) {
        val originalIndex = retriedIndices.getOrNull(bubble.id) ?: continue
        if (isUsableTranslation(bubble)) merged[originalIndex] = bubble
    }
    return merged
}
