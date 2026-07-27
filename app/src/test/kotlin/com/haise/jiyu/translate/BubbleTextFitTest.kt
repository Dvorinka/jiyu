package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Čistý JVM test [fitFontSizeToShape]/[shapeWidthAtYF] (žádná Android/Compose závislost) -
 * [fakeMeasure] simuluje zalomení textu deterministicky (bez skutečného TextMeasureru),
 * aby šlo otestovat, že fitter (a) umí zvětšit písmo nad starý pevný strop 11sp, když má
 * bublina hodně místa, a zároveň (b) u nepravidelných/složených tvarů nezvolí šířku, do
 * které se zalomený řádek nevejde v místě, kam podle svojí výšky skutečně padne.
 */
class BubbleTextFitTest {

    /** Zjednodušený, ale deterministický model zalomení - monospace odhad šířky znaku. */
    private fun fakeMeasure(text: String, fontSp: Float, maxWidthPx: Float): TextMeasurement {
        val charWidth = fontSp * 0.6f
        val lineHeight = fontSp * 1.25f
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (w in words) {
            val candidate = if (current.isEmpty()) w else "$current $w"
            if (candidate.length * charWidth > maxWidthPx && current.isNotEmpty()) {
                lines += current.toString()
                current = StringBuilder(w)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        val lineMetrics = lines.mapIndexed { i, line ->
            LineMetrics(widthPx = line.length * charWidth, topPx = i * lineHeight, bottomPx = (i + 1) * lineHeight)
        }
        return TextMeasurement(totalHeightPx = lines.size * lineHeight, lines = lineMetrics)
    }

    @Test
    fun `shapeWidthAtYF interpolates width between sample points`() {
        val shape = listOf(
            BubbleShapePoint(yF = 0.0f, leftF = 0.3f, rightF = 0.5f), // width 0.2
            BubbleShapePoint(yF = 1.0f, leftF = 0.2f, rightF = 0.8f), // width 0.6
        )
        assertEquals(0.2f, shapeWidthAtYF(shape, 0.0f), 0.001f)
        assertEquals(0.6f, shapeWidthAtYF(shape, 1.0f), 0.001f)
        assertEquals(0.4f, shapeWidthAtYF(shape, 0.5f), 0.02f)
    }

    @Test
    fun `shapeCenterAtYF tracks the offset center of a compound double-circle shape`() {
        // Reprodukuje nahlaseny bug - horni (uzsi) kruh dvojkruhove bubliny ma jiny stred
        // nez spodni (sirsi) kruh, protoze bublina neni souose polozena.
        val shape = listOf(
            BubbleShapePoint(0.0f, 0.30f, 0.60f), // horni kruh: sirka 0.30, stred 0.45
            BubbleShapePoint(0.5f, 0.35f, 0.65f), // "pas" mezi kruhy
            BubbleShapePoint(1.0f, 0.10f, 0.90f), // spodni kruh: sirka 0.80, stred 0.50
        )
        assertEquals(0.45f, shapeCenterAtYF(shape, 0.0f), 0.001f)
        assertEquals(0.50f, shapeCenterAtYF(shape, 1.0f), 0.001f)
        // Prumerny/globalni stred cele ohranicujici plochy (0.10..0.90) by byl 0.50 pro OBA
        // konce - presne tohle je bug, ktery per-radkove centrovani resi (viz TranslationLayer).
    }

    @Test
    fun `shapeWidthAtYF clamps outside the sampled range`() {
        val shape = listOf(
            BubbleShapePoint(yF = 0.2f, leftF = 0.3f, rightF = 0.5f),
            BubbleShapePoint(yF = 0.8f, leftF = 0.2f, rightF = 0.8f),
        )
        assertEquals(0.2f, shapeWidthAtYF(shape, 0.0f), 0.001f)
        assertEquals(0.6f, shapeWidthAtYF(shape, 1.0f), 0.001f)
    }

    @Test
    fun `grows font size well beyond the old fixed 11sp cap when the box has plenty of room`() {
        // Regrese pro problém #2 (obrovská "shout" bublina s malinkým textem) - starý kód
        // začínal na baseFontSp=11f a jen zmenšoval, nikdy nezkusil jít výš.
        val result = fitFontSizeToShape(
            minFontSp = 6f,
            maxFontSp = 36f,
            boxWidthPx = 600f,
            maxHeightPx = 600f,
            shapeTopF = 0f,
            imageHeightPx = 1000f,
            widthAtYF = null,
            measure = { fontSp, maxW -> fakeMeasure("UZ JDOU", fontSp, maxW) },
        )
        assertTrue("expected font to grow well beyond the old fixed 11sp cap, got ${result.fontSp}", result.fontSp > 20f)
    }

    @Test
    fun `still shrinks long text down in a small heuristic box`() {
        // Regresní pojistka pro běžný případ (malá bublina, dlouhý překlad) - fitter musí
        // pořád umět jít i dolů, ne jen růst.
        val result = fitFontSizeToShape(
            minFontSp = 6f,
            maxFontSp = 36f,
            boxWidthPx = 120f,
            maxHeightPx = 60f,
            shapeTopF = 0f,
            imageHeightPx = 1000f,
            widthAtYF = null,
            measure = { fontSp, maxW -> fakeMeasure("TOHLE JE DLOUHY PREKLAD CO SE MUSI VEJIT DO MALE BUBLINY", fontSp, maxW) },
        )
        assertTrue("expected small font in a tiny box with long text, got ${result.fontSp}", result.fontSp < 20f)
    }

    @Test
    fun `keeps every wrapped line within the shape width at its own vertical position`() {
        // Reprodukuje nahlášený bug (dvojkruhová "myšlenková" bublina, dd0c4705/5bcb32b5) -
        // bublina je široká nahoře a dole, ale má úzký "pas" uprostřed (yF 0.45).
        val shape = listOf(
            BubbleShapePoint(0.40f, 0.10f, 0.90f), // width 0.80 - horní kruh
            BubbleShapePoint(0.45f, 0.35f, 0.65f), // width 0.30 - úzký pas
            BubbleShapePoint(0.50f, 0.15f, 0.85f), // width 0.70 - dolní kruh
        )
        val imageWidthPx = 1000f
        val imageHeightPx = 1000f
        val text = "KDYBYCH VEDEL JAKA TAKOVA BUDE NAD POPRVE"
        val widthAtYF: (Float) -> Float = { yF -> shapeWidthAtYF(shape, yF) * imageWidthPx }

        // Bez opravy by se šířka počítala z CELÉHO ohraničujícího obdélníku (0.80 * 1000 =
        // 800px, nejširší místo tvaru) - přesně tahle naivní šířka je tu jako boxWidthPx.
        val result = fitFontSizeToShape(
            minFontSp = 6f,
            maxFontSp = 36f,
            boxWidthPx = 0.80f * imageWidthPx,
            maxHeightPx = 100f,
            shapeTopF = 0.40f,
            imageHeightPx = imageHeightPx,
            widthAtYF = widthAtYF,
            measure = { fontSp, maxW -> fakeMeasure(text, fontSp, maxW) },
        )

        // Skutečný render zalomí text podle VRÁCENÉ (možná užší, než naivní boxWidthPx) šířky -
        // ověř, že při týhle šířce žádný řádek nepřesahuje šířku tvaru v místě SVÉ výšky.
        val finalMeasurement = fakeMeasure(text, result.fontSp, result.widthPx)
        assertTrue("expected at least one wrapped line", finalMeasurement.lines.isNotEmpty())
        for (line in finalMeasurement.lines) {
            val midYF = 0.40f + ((line.topPx + line.bottomPx) / 2f) / imageHeightPx
            val available = shapeWidthAtYF(shape, midYF) * imageWidthPx
            assertTrue(
                "line width ${line.widthPx} must fit within shape width $available at yF=$midYF (fontSp=${result.fontSp}, widthPx=${result.widthPx})",
                line.widthPx <= available + 0.5f,
            )
        }
    }

    @Test
    fun `wide open oval shape does not get needlessly shrunk below the naive box width`() {
        // Kontrolní případ - obyčejná oválná/kruhová bublina (šířka konzistentní přes celou
        // výšku) se nesmí zbytečně zúžit jen proto, že teď fitter umí i zužovat - výsledná
        // šířka by se měla rovnat plné šířce boxu (žádné umělé "waist").
        val shape = listOf(
            BubbleShapePoint(0.0f, 0.10f, 0.90f),
            BubbleShapePoint(0.5f, 0.08f, 0.92f),
            BubbleShapePoint(1.0f, 0.10f, 0.90f),
        )
        val imageWidthPx = 1000f
        val widthAtYF: (Float) -> Float = { yF -> shapeWidthAtYF(shape, yF) * imageWidthPx }

        val result = fitFontSizeToShape(
            minFontSp = 6f,
            maxFontSp = 36f,
            boxWidthPx = 0.80f * imageWidthPx,
            maxHeightPx = 200f,
            shapeTopF = 0f,
            imageHeightPx = 1000f,
            widthAtYF = widthAtYF,
            measure = { fontSp, maxW -> fakeMeasure("KRATKY TEXT", fontSp, maxW) },
        )
        assertEquals(0.80f * imageWidthPx, result.widthPx, 1f)
    }

    @Test
    fun `single narrow notch does not force tiny font on an otherwise spacious scalloped bubble`() {
        // Reprodukuje nahlaseny bug (velka bublina se zvlnenym/girlandovym okrajem vysla
        // s drobounkym pismem) - vroubkovany okraj strida siroko/uzko kazdy vzorek, ale
        // prumerovani pres celou vysku radku (ne jeden bod uprostred) tohle vyhladi.
        val scallopedShape = (0 until 24).map { i ->
            val yF = i / 23f
            // Siroka bublina (0.75) s pravidelnymi mensimi zarezy (0.55) kazdy druhy vzorek -
            // podstatne mirnejsi nez skutecny "starburst" (isJaggedShape), ale porad hodne
            // kolisajici bod od bodu.
            val width = if (i % 2 == 0) 0.75f else 0.55f
            BubbleShapePoint(yF = yF, leftF = 0.5f - width / 2f, rightF = 0.5f + width / 2f)
        }
        val imageWidthPx = 1000f
        val widthAtYF: (Float) -> Float = { yF -> shapeWidthAtYF(scallopedShape, yF) * imageWidthPx }

        val result = fitFontSizeToShape(
            minFontSp = 6f,
            maxFontSp = 36f,
            boxWidthPx = 0.75f * imageWidthPx,
            maxHeightPx = 300f,
            shapeTopF = 0f,
            imageHeightPx = 1000f,
            widthAtYF = widthAtYF,
            measure = { fontSp, maxW -> fakeMeasure("HOW COULD THAT BE SURELY WE ARE NOT FIRST TIMERS", fontSp, maxW) },
        )
        assertTrue(
            "expected a comfortably readable font size in a spacious scalloped bubble, got ${result.fontSp}",
            result.fontSp > 18f,
        )
    }

    @Test
    fun `averageWidthAcrossLine smooths a single narrow sample within the line span`() {
        val shape = listOf(
            BubbleShapePoint(0.0f, 0.0f, 1.0f), // width 1.0
            BubbleShapePoint(0.5f, 0.45f, 0.55f), // width 0.1 - narrow notch exactly at the middle
            BubbleShapePoint(1.0f, 0.0f, 1.0f), // width 1.0
        )
        val widthAtYF: (Float) -> Float = { yF -> shapeWidthAtYF(shape, yF) }
        // Cely radek pokryva rozsah 0.0..1.0 (stejny jako cely tvar) - prumer pres 5 bodu
        // (0, 0.25, 0.5, 0.75, 1.0) musi byt vyrazne vetsi nez jediny bod presne v zarezu (0.1).
        val avg = averageWidthAcrossLine(widthAtYF, 0.0f, 1.0f)
        assertTrue("expected averaging to smooth past the single narrow notch, got $avg", avg > 0.5f)
    }
}
