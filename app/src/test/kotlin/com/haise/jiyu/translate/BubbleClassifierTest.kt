package com.haise.jiyu.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleClassifierTest {

    private fun rawBlock(
        text: String,
        shape: List<BubbleShapePoint>? = null,
        leftF: Float = 0.1f,
        topF: Float = 0.1f,
        rightF: Float = 0.2f,
        bottomF: Float = 0.15f,
        lineCount: Int = 1,
    ) = RawTextBlock(
        text = text,
        leftF = leftF,
        topF = topF,
        rightF = rightF,
        bottomF = bottomF,
        shape = shape,
        lineCount = lineCount,
    )

    /** Trsovitý/hvězdicovitý obrys (24 vzorků, hroty/prohlubně střídající se každý vzorek) - viz [isJaggedShape]. */
    private fun jaggedShoutShape(): List<BubbleShapePoint> =
        (0 until 24).map { i ->
            val width = if (i % 2 == 0) 0.85f else 0.35f
            BubbleShapePoint(yF = i / 23f, leftF = 0.5f - width / 2f, rightF = 0.5f + width / 2f)
        }

    @Test
    fun `bare page or panel number is classified as sfx (noise, not dialogue)`() {
        val result = BubbleClassifier.classify(rawBlock("3"), lineCount = 1)
        assertTrue(result.isSfx)
        assertEquals(BubbleType.SFX, result.bubbleType)
    }

    @Test
    fun `multi-digit bare number is classified as sfx`() {
        val result = BubbleClassifier.classify(rawBlock("12"), lineCount = 1)
        assertTrue(result.isSfx)
    }

    @Test
    fun `number embedded in real dialogue is not sfx`() {
        val result = BubbleClassifier.classify(rawBlock("THERE'S A MEASLY 1800 YEN LEFT."), lineCount = 1)
        assertFalse(result.isSfx)
    }

    @Test
    fun `ellipsis only bubble is not treated as noise`() {
        val result = BubbleClassifier.classify(rawBlock("..."), lineCount = 1)
        assertFalse(result.isSfx)
    }

    @Test
    fun `real sfx word is still detected`() {
        val result = BubbleClassifier.classify(rawBlock("BOOM!!!"), lineCount = 1)
        assertTrue(result.isSfx)
    }

    @Test
    fun `jagged bubble shape is classified as shout even when text alone would not suggest it`() {
        // Text sam o sobe (male pismeno, bez vykricniku) by dal SPEECH - jagged obrys
        // (skutecna kresba, viz isJaggedShape) musi klasifikaci pretlacit na SHOUT.
        val result = BubbleClassifier.classify(rawBlock("Uz jdou", shape = jaggedShoutShape()), lineCount = 1)
        assertEquals(BubbleType.SHOUT, result.bubbleType)
    }

    @Test
    fun `smooth bubble shape does not force shout classification`() {
        val smoothShape = (0 until 24).map { i -> BubbleShapePoint(yF = i / 23f, leftF = 0.2f, rightF = 0.8f) }
        val result = BubbleClassifier.classify(rawBlock("Uz jdou", shape = smoothShape), lineCount = 1)
        assertEquals(BubbleType.SPEECH, result.bubbleType)
    }

    // ── krátká skutečná slova nesmí spadnout do SFX (viz uživatelská zpětná vazba -
    //    "DAMN..." zůstalo nepřeložené, protože SFX bublina se nikdy nevykresluje) ──

    @Test
    fun `short common interjection is not classified as sfx`() {
        val result = BubbleClassifier.classify(rawBlock("DAMN..."), lineCount = 1)
        assertFalse(result.isSfx)
    }

    @Test
    fun `short common word wait is not classified as sfx`() {
        val result = BubbleClassifier.classify(rawBlock("WAIT!"), lineCount = 1)
        assertFalse(result.isSfx)
    }

    @Test
    fun `short common word stop is not classified as sfx`() {
        val result = BubbleClassifier.classify(rawBlock("STOP"), lineCount = 1)
        assertFalse(result.isSfx)
    }

    @Test
    fun `real sfx word boom is still detected even though it is short and all caps`() {
        // Sanity - vyjimka pro bezna slova nesmi rozbit skutecne SFX detekce.
        val result = BubbleClassifier.classify(rawBlock("BOOM"), lineCount = 1)
        assertTrue(result.isSfx)
    }

    // ── vodoznak scanlation skupiny (viz uzivatelska zpetna vazba - cerna skvrna pres kresbu) ──

    @Test
    fun `scanlation domain watermark is classified as sfx`() {
        val result = BubbleClassifier.classify(rawBlock("SIRENSCANS.COM"), lineCount = 1)
        assertTrue(result.isSfx)
    }

    @Test
    fun `watermark read letter by letter with spaces is still detected as domain`() {
        val result = BubbleClassifier.classify(rawBlock("E N S C A N S . C O M"), lineCount = 8)
        assertTrue(result.isSfx)
    }

    @Test
    fun `very tall narrow block with many merged lines is treated as decorative watermark`() {
        val result = BubbleClassifier.classify(
            rawBlock("some vertical text", leftF = 0.5f, topF = 0.1f, rightF = 0.52f, bottomF = 0.9f, lineCount = 10),
            lineCount = 10,
        )
        assertTrue(result.isSfx)
    }

    @Test
    fun `normal long narration block is not mistaken for a watermark`() {
        val result = BubbleClassifier.classify(
            rawBlock(
                "This is a perfectly normal long narration line that spans the width of the panel comfortably.",
                leftF = 0.05f, topF = 0.1f, rightF = 0.95f, bottomF = 0.30f, lineCount = 4,
            ),
            lineCount = 4,
        )
        assertFalse(result.isSfx)
    }

    @Test
    fun `dialogue containing the word scan as a normal sentence is not a watermark`() {
        val result = BubbleClassifier.classify(rawBlock("Let me scan the area first."), lineCount = 1)
        assertFalse(result.isSfx)
    }

    // ── opakovaný dlaždicovaný vodoznak napříč stránkou (viz [BubbleClassifier.classifyPage]) ──

    @Test
    fun `reproduces the reported case - garbled tiled group name scattered across a page`() {
        // Presny scenar z uzivatelske zpetne vazby: pet samostatnych bloku, zadny sam o sobe
        // nesplnuje existujici pravidla (MADRASCANS je moc dlouhe na kratke-ALL-CAPS pravidlo
        // [>6 pismen], "MAD ANS" a merged blok obsahuji mezeru), ale napric strankou tvori
        // jasny vzorec opakovaneho jmena skenlacni skupiny.
        val blocks = listOf(
            rawBlock("MADRASCANS MADRASCANS", leftF = 0.1f, topF = 0.05f, rightF = 0.4f, bottomF = 0.10f, lineCount = 2),
            rawBlock("MAD ANS", leftF = 0.5f, topF = 0.20f, rightF = 0.7f, bottomF = 0.23f),
            rawBlock("4ANS", leftF = 0.6f, topF = 0.40f, rightF = 0.75f, bottomF = 0.43f),
            rawBlock("MADRASCANS", leftF = 0.2f, topF = 0.60f, rightF = 0.4f, bottomF = 0.63f),
            rawBlock("MADRASCANS", leftF = 0.3f, topF = 0.80f, rightF = 0.5f, bottomF = 0.83f),
            // Skutecna replika na te same strance - nesmi se chytit do shluku.
            rawBlock("Wait, is someone there?", leftF = 0.1f, topF = 0.5f, rightF = 0.6f, bottomF = 0.55f),
        )

        val classified = BubbleClassifier.classifyPage(blocks)

        assertTrue("MADRASCANS MADRASCANS should be flagged as watermark", classified[0].isSfx)
        assertTrue("MAD ANS should be flagged as watermark", classified[1].isSfx)
        assertTrue("MADRASCANS (index 3) should be flagged as watermark", classified[3].isSfx)
        assertTrue("MADRASCANS (index 4) should be flagged as watermark", classified[4].isSfx)
        assertFalse("real dialogue must not be swept into the watermark cluster", classified[5].isSfx)
    }

    @Test
    fun `a short repeated word alone does not form a false-positive cluster`() {
        // "MAS" je jen 3 znaky (pod WATERMARK_MIN_OVERLAP_CHARS) - klasifikuje se (nebo ne)
        // podle existujicich pravidel, ne podle noveho shlukovani.
        val blocks = List(3) { rawBlock("MAS", leftF = 0.1f * it, topF = 0.1f * it, rightF = 0.2f + 0.1f * it, bottomF = 0.15f + 0.1f * it) }
        val indices = BubbleClassifier.detectTiledWatermarkIndices(blocks)
        assertTrue("too short to be confidently clustered", indices.isEmpty())
    }

    @Test
    fun `a name repeated identically several times is not treated as a watermark`() {
        // Postava rekne "BAXTER" trikrat - VSECHNY vyskyty jsou bajt-po-bajtu stejne, zadna
        // odchylka. Vodoznak se pozna prave podle toho, ze se cte KAZDYKRAT JINAK (ruzne
        // zkomoleniny), ne podle toho, ze se holt opakuje stejne slovo.
        val blocks = List(3) { i ->
            rawBlock("BAXTER", leftF = 0.1f, topF = 0.1f + 0.2f * i, rightF = 0.4f, bottomF = 0.15f + 0.2f * i)
        }
        val indices = BubbleClassifier.detectTiledWatermarkIndices(blocks)
        assertTrue("identical repeats alone must not be flagged - could be a real repeated name", indices.isEmpty())
    }

    @Test
    fun `two occurrences alone are below the cluster threshold`() {
        val blocks = listOf(
            rawBlock("MADRASCANS", leftF = 0.1f, topF = 0.1f, rightF = 0.4f, bottomF = 0.15f),
            rawBlock("MAD ANS", leftF = 0.1f, topF = 0.5f, rightF = 0.4f, bottomF = 0.55f),
        )
        assertTrue(BubbleClassifier.detectTiledWatermarkIndices(blocks).isEmpty())
    }

    @Test
    fun `a long genuine narration block is never eligible for clustering regardless of coincidental overlap`() {
        val blocks = listOf(
            rawBlock("MADRASCANS", leftF = 0.1f, topF = 0.05f, rightF = 0.4f, bottomF = 0.10f),
            rawBlock("MAD ANS", leftF = 0.1f, topF = 0.20f, rightF = 0.4f, bottomF = 0.23f),
            rawBlock("4ANS", leftF = 0.1f, topF = 0.40f, rightF = 0.4f, bottomF = 0.43f),
            // Dlouha veta, ktera by nahodou mohla obsahovat MADRASCANS jako podposloupnost,
            // kdyby normalizace nemela strop delky - musi zustat mimo shluk.
            rawBlock(
                "My animal draconic sensory abilities notice a scan of narrative sequences approaching us.",
                leftF = 0.05f, topF = 0.6f, rightF = 0.95f, bottomF = 0.7f, lineCount = 3,
            ),
        )
        val indices = BubbleClassifier.detectTiledWatermarkIndices(blocks)
        assertTrue("the long narration block must never be swept into the cluster", 3 !in indices)
    }

    @Test
    fun `a short spanish word is not swallowed as a sound effect`() {
        // NALEZ Z AUDITU: pravidlo "kratky text velkymi pismeny bez mezer = zvuk" ma jako
        // jedinou pojistku ANGLICKY seznam beznych slov. U spanelskeho/francouzskeho/
        // indoneskeho komiksu tedy platilo bez site a bezna kratka replika se oznacila za
        // SFX - takova bublina se nikdy neprelozi ani nevykresli, takze na strance zustal
        // original.
        val result = BubbleClassifier.classify(rawBlock("VAMOS"), lineCount = 1, sourceLanguage = "Spanish")
        assertFalse("spanelska replika nesmi propadnout jako zvuk", result.isSfx)
    }

    @Test
    fun `english short words keep their existing protection`() {
        val stop = BubbleClassifier.classify(rawBlock("STOP"), lineCount = 1, sourceLanguage = "English")
        assertFalse(stop.isSfx)
    }

    @Test
    fun `a real sound effect is still caught in every language`() {
        // sfxWords je vyslovny seznam, ten plati porad - vypnuti se tyka jen toho
        // nebezpecneho "kratke velke pismenka" pravidla.
        listOf("English", "Spanish", "French", "Auto").forEach { language ->
            val result = BubbleClassifier.classify(rawBlock("BOOM"), lineCount = 1, sourceLanguage = language)
            assertTrue("BOOM ma zustat zvukem i pro $language", result.isSfx)
        }
    }

    @Test
    fun `dialogue that simply gets extended is not a watermark cluster`() {
        // NALEZ Z AUDITU: tri repliky, kde kazda jen prodluzuje tu predchozi, se shlukly do
        // "vodoznaku" a VSECHNY se oznacily jako SFX - tedy se vubec neprelozily a na strance
        // zustal original. "HELP / HELP ME / HELP ME NOW" neni v akcni scene nic vyjimecneho.
        //
        // Rozdil oproti skutecnemu vodoznaku: tady je kratsi text SOUVISLYM usekem toho
        // delsiho (proste pokracovani vety). Vodoznak precteny OCR pokazde jinak ma naopak
        // uvnitr DIRY - vypadla nebo zamenena pismena, viz test s MADRASCANS vys.
        val blocks = listOf(
            rawBlock("HELP", leftF = 0.1f, topF = 0.05f, rightF = 0.3f, bottomF = 0.10f),
            rawBlock("HELP ME", leftF = 0.1f, topF = 0.30f, rightF = 0.4f, bottomF = 0.35f),
            rawBlock("HELP ME NOW", leftF = 0.1f, topF = 0.60f, rightF = 0.5f, bottomF = 0.65f),
        )
        val indices = BubbleClassifier.detectTiledWatermarkIndices(blocks)
        assertTrue("postupne prodluzovana replika neni vodoznak, bylo $indices", indices.isEmpty())
    }

    @Test
    fun `a name with different honorifics attached is not a watermark cluster`() {
        val blocks = listOf(
            rawBlock("NARUTO", leftF = 0.1f, topF = 0.05f, rightF = 0.3f, bottomF = 0.10f),
            rawBlock("NARUTO KUN", leftF = 0.1f, topF = 0.30f, rightF = 0.4f, bottomF = 0.35f),
            rawBlock("NARUTO SAN", leftF = 0.1f, topF = 0.60f, rightF = 0.4f, bottomF = 0.65f),
        )
        val indices = BubbleClassifier.detectTiledWatermarkIndices(blocks)
        assertTrue("jmeno s ruznymi priponami neni vodoznak, bylo $indices", indices.isEmpty())
    }

    @Test
    fun `classifyPage leaves non-watermark blocks with their normal classification untouched`() {
        val blocks = listOf(
            rawBlock("MADRASCANS", leftF = 0.1f, topF = 0.05f, rightF = 0.4f, bottomF = 0.10f),
            rawBlock("MAD ANS", leftF = 0.1f, topF = 0.20f, rightF = 0.4f, bottomF = 0.23f),
            rawBlock("4ANS", leftF = 0.1f, topF = 0.40f, rightF = 0.4f, bottomF = 0.43f),
            rawBlock("BOOM!!!", leftF = 0.1f, topF = 0.6f, rightF = 0.3f, bottomF = 0.65f),
        )
        val classified = BubbleClassifier.classifyPage(blocks)
        // BOOM!!! je uz beztak SFX z existujiciho pravidla, ne z noveho shlukovani - jen sanity,
        // ze classifyPage normalni klasifikaci vubec nerozbije.
        assertTrue(classified[3].isSfx)
        assertEquals(BubbleType.SFX, classified[3].bubbleType)
    }
}
