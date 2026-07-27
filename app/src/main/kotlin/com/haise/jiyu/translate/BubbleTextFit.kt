package com.haise.jiyu.translate

/** Jeden zalomený řádek textu při konkrétní velikosti písma/šířce - viz [fitFontSizeToShape]. */
data class LineMetrics(val widthPx: Float, val topPx: Float, val bottomPx: Float)

/** Výsledek měření celého textu při dané velikosti písma a maximální šířce. */
data class TextMeasurement(val totalHeightPx: Float, val lines: List<LineMetrics>)

/** Vybraná velikost písma + šířka, na kterou se text má zalomit (viz [fitFontSizeToShape]). */
data class ShapeFitResult(val fontSp: Float, val widthPx: Float)

/**
 * Lineárně interpolovaná šířka (rightF-leftF, normalizované 0..1 souřadnice stránky) obrysu
 * bubliny v konkrétní výšce [yF] - viz [BubbleShapeDetector]. Body jsou seřazené odshora dolů
 * (rostoucí yF), mimo rozsah se hodnota přichytí na krajní bod.
 */
fun shapeWidthAtYF(shape: List<BubbleShapePoint>, yF: Float): Float {
    if (shape.isEmpty()) return 1f
    if (shape.size == 1) return (shape[0].rightF - shape[0].leftF).coerceAtLeast(0.0001f)

    val clamped = yF.coerceIn(shape.first().yF, shape.last().yF)
    var i = 0
    while (i < shape.size - 2 && shape[i + 1].yF < clamped) i++
    val a = shape[i]
    val b = shape[i + 1]
    val span = (b.yF - a.yF)
    if (span <= 0.0001f) return (a.rightF - a.leftF).coerceAtLeast(0.0001f)
    val t = (clamped - a.yF) / span
    val left = a.leftF + (b.leftF - a.leftF) * t
    val right = a.rightF + (b.rightF - a.rightF) * t
    return (right - left).coerceAtLeast(0.0001f)
}

/** Hrubý krok prvního sestupu z [ShapeFitResult] hledání (viz [fitFontSizeToShape]). */
private const val COARSE_STEP_SP = 2f

/** Jemný krok doladění kolem hrubě nalezené hranice. */
private const val FINE_STEP_SP = 0.25f

/** Kolik kol zpřesnění šířky smí [attemptFit] udělat, než se vzdá dané velikosti písma. */
private const val DEFAULT_MAX_ITERATIONS = 3

private data class FitAttempt(val fits: Boolean, val widthPx: Float)

/**
 * Zkusí, jestli se [text] vejde při [fontSp] do [maxHeightPx] - a pokud je zadané [widthAtYF]
 * (blok má skutečně detekovaný tvar bubliny), zároveň iterativně zužuje šířku zalomení, dokud
 * žádný vzniklý řádek nepřesahuje šířku tvaru v místě, kam podle svojí vlastní vertikální
 * pozice v bloku připadá (ne podle jedné globální nejširší šířky celého tvaru - to je přesně
 * bug, který tahle funkce řeší, viz [fitFontSizeToShape] dokumentace).
 */
private fun attemptFit(
    fontSp: Float,
    boxWidthPx: Float,
    maxHeightPx: Float,
    shapeTopF: Float,
    imageHeightPx: Float,
    widthAtYF: ((Float) -> Float)?,
    measure: (fontSp: Float, maxWidthPx: Float) -> TextMeasurement,
    maxIterations: Int,
): FitAttempt {
    var widthConstraintPx = boxWidthPx
    repeat(maxIterations) {
        val measured = measure(fontSp, widthConstraintPx)
        if (measured.totalHeightPx > maxHeightPx) return FitAttempt(false, widthConstraintPx)
        if (widthAtYF == null) return FitAttempt(true, widthConstraintPx)

        var tightestAvailable = Float.MAX_VALUE
        var anyOverflow = false
        for (line in measured.lines) {
            val midYF = shapeTopF + ((line.topPx + line.bottomPx) / 2f) / imageHeightPx
            val available = widthAtYF(midYF)
            if (line.widthPx > available + 0.5f) anyOverflow = true
            if (available < tightestAvailable) tightestAvailable = available
        }
        if (!anyOverflow) return FitAttempt(true, widthConstraintPx)

        val next = minOf(widthConstraintPx, tightestAvailable).coerceAtLeast(1f)
        // Žádný pokrok (další kolo by zúžilo o zanedbatelně málo, nebo vůbec) - tahle
        // velikost písma se do tvaru nevejde, ať zužujeme, jak chceme.
        if (next >= widthConstraintPx - 0.01f) return FitAttempt(false, widthConstraintPx)
        widthConstraintPx = next
    }
    return FitAttempt(false, widthConstraintPx)
}

/**
 * Najde největší velikost písma (mezi [minFontSp] a [maxFontSp]), při které se [measure]
 * vejde do [maxHeightPx] - a u bublin se skutečným tvarem ([widthAtYF] != null) navíc do
 * šířky tvaru v místě KAŽDÉHO zalomeného řádku, ne jen do jedné globální (nejširší) šířky
 * celého tvaru.
 *
 * Řeší dva propojené problémy najednou (viz analýza v konverzaci s uživatelem 2026-07):
 *  1) Starý fitter jen ZMENŠOVAL od pevného stropu (11sp) - obrovská "shout" bublina s
 *     trsy hrotů kolem tak měla vždycky malinký text s hromadou nevyužitého místa.
 *  2) Fitter měřil text proti CELÉ šířce ohraničujícího obdélníku tvaru (nejširší místo),
 *     ne proti šířce v místě, kam řádek podle svojí výšky skutečně padne - u nepravidelných/
 *     složených tvarů (dvojkruhové "myšlenkové" bubliny, hvězdicovité výbuchy) tak řádek
 *     vykreslený v užším místě bubliny (viz [BubbleClipShape]) reálně přesahoval obrys a
 *     tvar ho tam oříznul.
 *
 * Vrácená [ShapeFitResult.widthPx] není nutně [boxWidthPx] - u shape-aware bloků je to
 * zúžená šířka, na kterou se má text skutečně zalomit (vykreslený box zůstává v plné šířce
 * bubliny, jen text uvnitř něj se má vycentrovat do užšího sloupce, aby žádný řádek
 * nezasáhl místo, kde je tvar užší, než jeho celkový nejširší bod).
 *
 * Hledání je dvoufázové (hrubý krok [COARSE_STEP_SP], pak jemné doladění [FINE_STEP_SP]
 * kolem nalezené hranice) - lineární krok 0.5sp od velkého stropu (~36sp) dolů by u KAŽDÉ
 * bubliny na stránce znamenal desítky měření navíc oproti starému, mnohem užšímu rozsahu.
 */
fun fitFontSizeToShape(
    minFontSp: Float,
    maxFontSp: Float,
    boxWidthPx: Float,
    maxHeightPx: Float,
    shapeTopF: Float,
    imageHeightPx: Float,
    widthAtYF: ((Float) -> Float)?,
    measure: (fontSp: Float, maxWidthPx: Float) -> TextMeasurement,
    maxIterations: Int = DEFAULT_MAX_ITERATIONS,
): ShapeFitResult {
    fun attempt(fontSp: Float) =
        attemptFit(fontSp, boxWidthPx, maxHeightPx, shapeTopF, imageHeightPx, widthAtYF, measure, maxIterations)

    var coarse = maxFontSp
    var coarseResult = attempt(coarse)
    while (coarse > minFontSp && !coarseResult.fits) {
        coarse -= COARSE_STEP_SP
        coarseResult = attempt(coarse)
    }
    if (!coarseResult.fits) return ShapeFitResult(minFontSp, boxWidthPx)

    var fine = coarse
    var fineResult = coarseResult
    while (fine + FINE_STEP_SP <= maxFontSp) {
        val next = attempt(fine + FINE_STEP_SP)
        if (!next.fits) break
        fine += FINE_STEP_SP
        fineResult = next
    }
    return ShapeFitResult(fine.coerceIn(minFontSp, maxFontSp), fineResult.widthPx)
}
