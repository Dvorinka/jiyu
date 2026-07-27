package com.haise.jiyu.translate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.abs

/**
 * Čistý JVM test [isJaggedShape] - syntetická data se 24 vzorky (stejně jako skutečný
 * [BubbleShapeDetector.SAMPLE_COUNT]), aby test odpovídal reálné granularitě, ne
 * zjednodušeným 2-3bodovým tvarům z jiných testů (tam by i hladký dvojkruh vyšel jako
 * "trsovitý" jen kvůli hrubému vzorkování).
 */
class BubbleShapeAnalysisTest {

    private fun shapeFromWidths(widths: List<Float>): List<BubbleShapePoint> =
        widths.mapIndexed { i, w ->
            val center = 0.5f
            BubbleShapePoint(yF = i / (widths.size - 1).toFloat(), leftF = center - w / 2f, rightF = center + w / 2f)
        }

    @Test
    fun `smooth oval bubble is not jagged`() {
        // Plynulá šířka jako u oválu - jeden hladký oblouk přes všech 24 vzorků.
        val widths = (0 until 24).map { i -> 0.3f + 0.5f * sin(PI * i / 23.0).toFloat() }
        assertFalse(isJaggedShape(shapeFromWidths(widths)))
    }

    @Test
    fun `smooth hourglass (double-circle thought bubble) is not jagged`() {
        // Široko nahoře -> plynule se zúží k "pasu" uprostřed -> plynule rozšíří dole -
        // JEDEN pomalý přechod přes celou výšku, ne opakované výkyvy.
        val widths = (0 until 24).map { i ->
            val t = i / 23f
            0.3f + 0.5f * abs(t - 0.5f) * 2f
        }
        assertFalse(isJaggedShape(shapeFromWidths(widths)))
    }

    @Test
    fun `spiky starburst shout bubble is jagged`() {
        // Hroty a prohlubně střídající se KAŽDÝ vzorek - simuluje hvězdicovitý výbuch
        // kolem "shout" bubliny (viz nahlášený bug - "UŽ JDOU..!").
        val widths = (0 until 24).map { i -> if (i % 2 == 0) 0.85f else 0.35f }
        assertTrue(isJaggedShape(shapeFromWidths(widths)))
    }

    @Test
    fun `too few points is never considered jagged`() {
        val widths = listOf(0.3f, 0.8f)
        assertFalse(isJaggedShape(shapeFromWidths(widths)))
    }
}
