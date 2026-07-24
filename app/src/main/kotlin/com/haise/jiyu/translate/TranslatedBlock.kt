package com.haise.jiyu.translate

/** Bílá - výchozí barva boxu, když se nepodaří nasamplovat pozadí bubliny z bitmapy. */
const val DEFAULT_BUBBLE_BG_ARGB: Int = android.graphics.Color.WHITE

/**
 * Jeden přeložený textový blok (bublina) na stránce mangy.
 * Souřadnice jsou relativní (0.0–1.0) vůči rozměrům obrázku,
 * takže fungují nezávisle na rozlišení displeje.
 *
 * @param displayText [translatedText] s měkkými rozdělovníky (viz [GeminiUltraPrompt]) na
 *   platných slabičných hranicích - render (viz ReaderScreen.kt) by měl zalamovat text
 *   podle tohohle pole, ne podle [translatedText], jinak Compose zalomí kdekoliv se vejde.
 * @param bgColorArgb barva pozadí bubliny nasamplovaná z originálního obrázku (viz
 *   [OcrEngine.sampleBackgroundColor]) - box pak vizuálně splyne s bublinou místo pevně
 *   bílé, která na barevných/šrafovaných bublinách nechávala prosvítat okraj originálu.
 * @param isSfx zvukový efekt - viz [BubbleClassifier]; render mu nedává box, jen ho nechá být.
 * @param lineCount kolik OCR řádků bylo sloučeno do téhle bubliny - signál pro
 *   [layoutTranslationBlocks], jestli má smysl box roztahovat nahoru (viz [PositionedTranslationBlock.minTopF]).
 */
data class TranslatedBlock(
    val originalText: String,
    val translatedText: String,
    val leftF: Float,
    val topF: Float,
    val rightF: Float,
    val bottomF: Float,
    val displayText: String = translatedText,
    val bgColorArgb: Int = DEFAULT_BUBBLE_BG_ARGB,
    val isSfx: Boolean = false,
    val lineCount: Int = 1,
)
