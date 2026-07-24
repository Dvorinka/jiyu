package com.haise.jiyu.translate

/**
 * Velikostní třída bubliny podle délky originálu - určuje, kolik znaků smí mít český
 * překlad, aby se vešel do bubliny bez přetečení (viz [GeminiUltraPrompt]).
 * [SFX] není skutečná velikost, ale značka "nepřekládat" pro zvukové efekty.
 */
enum class SizeTag(val maxChars: Int) {
    TINY(8),
    SMALL(18),
    MEDIUM(45),
    LARGE(90),
    WIDE(70),
    TALL(70),
    SFX(0),
}

/**
 * Typ bubliny odhadnutý z textu (VELKÁ PÍSMENA, interpunkce, klíčová slova) - OCR nám
 * nedává tvar/obrys bubliny, takže SPEECH/NARRATION/SYSTEM/SFX jsou rozlišitelné celkem
 * spolehlivě, zatímco THOUGHT/WHISPER/SHOUT jsou jen odhad z obsahu textu, ne z kresby.
 */
enum class BubbleType {
    SPEECH,
    NARRATION,
    SHOUT,
    THOUGHT,
    WHISPER,
    SYSTEM,
    SFX,
}

/**
 * Výsledek lokální klasifikace jednoho OCR bloku - viz [BubbleClassifier].
 * @param lineCount kolik původních OCR řádků bylo sloučeno do tohoto bloku (viz
 *   [OcrEngine.mergeNearbyLines]) - používá se v [layoutTranslationBlocks] jako signál
 *   pro to, jestli má smysl box roztahovat nahoru (jednořádkové bloky nikdy).
 */
data class ClassifiedBubble(
    val raw: RawTextBlock,
    val sizeTag: SizeTag,
    val bubbleType: BubbleType,
    val isSfx: Boolean,
    val lineCount: Int,
)

/**
 * Jedna přeložená bublina vrácená z Gemini API (viz [GeminiUltraPrompt] pro formát
 * odpovědi, kterou tenhle DTO parsuje).
 */
data class GeminiBubbleTranslation(
    val id: Int,
    val original: String,
    val translated: String,
    val bubbleSizeTag: String,
    val isSfx: Boolean,
    val syllableBreaks: String,
    val notes: String = "",
)

/** Naparsovaná odpověď z Gemini API - viz [GeminiTranslateClient]. */
data class GeminiTranslationResponse(
    val bubbles: List<GeminiBubbleTranslation>,
)
