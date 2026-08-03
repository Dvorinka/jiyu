package com.haise.jiyu.translate

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Měkké rozdělovníky, které appka do překladu vkládá ([ensureFallbackHyphens] a `syllable_breaks`
 * od modelu), mají vůbec nějaký vliv na to, jak se text zalomí?
 *
 * NÁLEZ, kvůli kterému test vznikl: neměly. Rozdělovníky se počítaly a ukládaly, ale renderer
 * (TranslationLayer.StrokedTranslatedText) nikde nenastavoval [Hyphens], a výchozí hodnota je
 * [Hyphens.None] - při ní Android měkký rozdělovník při zalamování ignoruje. Když se pak dlouhé
 * slovo do řádku nevešlo, Compose ho rozseklo v libovolném místě a BEZ pomlčky: uživatelský
 * screenshot ukazuje "POSLEDNÍ" vykreslené jako "POSLEDN" + "Í", ačkoli slabikování appky
 * nabízelo "POSLE-DNÍ".
 *
 * Test musí běžet na zařízení: zalamování a dělení slov dělá Android (StaticLayout/LineBreaker),
 * ne Compose sám, takže v čistém JVM testu se ověřit nedá.
 */
class SoftHyphenRenderingOnDeviceTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Neviditelný rozdělovník U+00AD - stejně jako v [SoftHyphenation] sestavený z kódu. */
    private val softHyphen = 173.toChar()

    /** Slovo z nahlášené bubliny přesně tak, jak ho appka vloží do displayText. */
    private val hyphenated = "POSLE" + softHyphen + "DNÍ"

    private data class Broken(val lineCount: Int, val firstLine: String)

    /**
     * Zalomí [text] do šířky, do které se celý nevejde, a vrátí, na kolik řádků se rozpadl
     * a jak vypadá první z nich.
     */
    private fun breakIt(text: String, hyphens: Hyphens): Broken {
        lateinit var result: Broken
        composeRule.setContent {
            val measurer = rememberTextMeasurer()
            val style = TextStyle(fontSize = 20.sp, hyphens = hyphens)
            // Šířka, do které se slovo vcelku nevejde - vynutí zalomení uvnitř slova.
            val full = measurer.measure(text = text, style = style, softWrap = false).size.width
            val measured = measurer.measure(
                text = text,
                style = style,
                constraints = Constraints(maxWidth = (full * 0.7f).toInt()),
            )
            val first = text.substring(
                measured.getLineStart(0),
                measured.getLineEnd(0, visibleEnd = true),
            )
            result = Broken(measured.lineCount, first)
        }
        composeRule.waitForIdle()
        return result
    }

    @Test
    fun soft_hyphen_is_where_the_word_breaks_once_hyphenation_is_on() {
        val broken = breakIt(hyphenated, Hyphens.Auto)

        assertEquals("slovo se má zalomit na dva řádky", 2, broken.lineCount)
        // Zlom padl PŘESNĚ na měkký rozdělovník, tedy tam, kam ho slabikování appky umístilo.
        assertEquals("POSLE", broken.firstLine.trimEnd(softHyphen))
    }

    @Test
    fun the_default_hyphens_setting_already_respects_soft_hyphens() {
        // MĚŘENÍ, KTERÉ VYVRÁTILO PRVNÍ DIAGNÓZU, a proto tu zůstává.
        //
        // Nabízelo se, že za zlomy uprostřed slova může výchozí Hyphens.None ("žádné dělení"),
        // a že stačí přepnout na Hyphens.Auto. Není to pravda: Android respektuje U+00AD při
        // zalamování v OBOU režimech, takže by to byla změna bez účinku - a navíc by zapnula
        // automatické dělení podle jazykových vzorů. Renderer proto hyphens schválně nenastavuje.
        val broken = breakIt(hyphenated, Hyphens.None)

        assertEquals("i bez zapnutého dělení padne zlom na měkký rozdělovník", "POSLE", broken.firstLine.trimEnd(softHyphen))
        assertNotEquals("zlom rozhodně nepadá na konec slova", "POSLEDN", broken.firstLine.trimEnd(softHyphen))
    }

    @Test
    fun a_word_that_fits_is_never_broken_no_matter_the_setting() {
        // Pojistka proti přestřelení: zapnuté dělení nesmí lámat text, který se v pohodě vejde.
        lateinit var lineCount: Number
        composeRule.setContent {
            val measurer = rememberTextMeasurer()
            val style = TextStyle(fontSize = 20.sp, hyphens = Hyphens.Auto)
            val full = measurer.measure(text = hyphenated, style = style, softWrap = false).size.width
            lineCount = measurer.measure(
                text = hyphenated,
                style = style,
                constraints = Constraints(maxWidth = full * 2),
            ).lineCount
        }
        composeRule.waitForIdle()
        assertEquals("slovo se vejde, nemá se co dělit", 1, lineCount)
    }

    @Test
    fun the_rendered_word_never_loses_a_letter_to_the_break() {
        // Rozdělovník je neviditelný znak: po zalomení musí být pořád vidět celé slovo,
        // jen rozdělené. Kdyby se ztrácela písmena, byla by to horší chyba než ta původní.
        val broken = breakIt(hyphenated, Hyphens.Auto)
        val plain = hyphenated.replace(softHyphen.toString(), "")
        assertTrue(
            "první řádek musí být předponou celého slova, byl „${broken.firstLine}\"",
            plain.startsWith(broken.firstLine.trimEnd(softHyphen)),
        )
    }
}
