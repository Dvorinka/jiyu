package com.haise.jiyu.translate

import java.util.ArrayDeque

/**
 * Abstrakce nad zdrojem pixelů - viz spec docs/superpowers/specs/2026-07-24-bubble-shape-and-font-design.md.
 * Odděluje algoritmus od android.graphics.Bitmap, aby šel testovat čistým JVM testem.
 */
fun interface PixelSource {
    /** ARGB pixel na (x, y); mimo hranice smí vrátit cokoliv, volající si hranice hlídá sám. */
    fun colorAt(x: Int, y: Int): Int
}

/** Jeden vzorkovaný bod obrysu bubliny - normalizované (0..1) souřadnice jako zbytek kódu (leftF/topF). */
data class BubbleShapePoint(val yF: Float, val leftF: Float, val rightF: Float)

/**
 * Najde skutečný obrys bubliny flood-fillem od bodů na jejím pozadí (NE od středu OCR
 * textu - ten často padne na tmavý pixel písma, ne na pozadí; volající by měl posílat
 * body, o kterých už ví, že leží na pozadí - viz OcrEngine.ringSeeds).
 */
object BubbleShapeDetector {

    private const val SAMPLE_COUNT = 24
    private val NEIGHBOR_OFFSETS = arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

    /**
     * BFS flood-fill (fronta, ne rekurze - kvůli velkým bublinám a JVM stack limitu).
     * @return null když detekce selhala/vypadá nedůvěryhodně (žádný platný seed, nebo
     *   navštívená plocha přesáhla [maxAreaFraction] celé stránky - typicky text přímo
     *   na kresbě/SFX bez uzavřeného pozadí) - volající pak použije starý heuristický obdélník.
     */
    fun detectShape(
        source: PixelSource,
        width: Int,
        height: Int,
        seeds: List<Pair<Int, Int>>,
        bgColorArgb: Int,
        colorDistanceThreshold: Int = 40,
        maxAreaFraction: Float = 0.25f,
    ): List<BubbleShapePoint>? {
        if (width <= 0 || height <= 0) return null
        val maxArea = (width.toLong() * height.toLong() * maxAreaFraction).toLong()

        val visited = HashSet<Long>()
        fun key(x: Int, y: Int) = x.toLong() * height.toLong() + y.toLong()

        val validSeeds = seeds.filter { (x, y) ->
            x in 0 until width && y in 0 until height &&
                colorDistance(source.colorAt(x, y), bgColorArgb) < colorDistanceThreshold
        }
        if (validSeeds.isEmpty()) return null

        val queue = ArrayDeque<Pair<Int, Int>>()
        val rowMinMax = HashMap<Int, IntArray>() // y -> [minX, maxX]

        for ((sx, sy) in validSeeds) {
            if (visited.add(key(sx, sy))) queue.add(sx to sy)
        }

        var area = 0L
        while (queue.isNotEmpty()) {
            val (x, y) = queue.poll()
            area++
            if (area > maxArea) return null

            val minMax = rowMinMax.getOrPut(y) { intArrayOf(x, x) }
            if (x < minMax[0]) minMax[0] = x
            if (x > minMax[1]) minMax[1] = x

            for ((dx, dy) in NEIGHBOR_OFFSETS) {
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until width || ny !in 0 until height) continue
                if (!visited.add(key(nx, ny))) continue
                if (colorDistance(source.colorAt(nx, ny), bgColorArgb) >= colorDistanceThreshold) continue
                queue.add(nx to ny)
            }
        }

        if (rowMinMax.isEmpty()) return null

        val sortedRows = rowMinMax.keys.sorted()
        val topY = sortedRows.first()
        val bottomY = sortedRows.last()
        if (bottomY <= topY) return null

        return (0 until SAMPLE_COUNT).map { i ->
            val frac = i / (SAMPLE_COUNT - 1).toFloat()
            val targetY = (topY + frac * (bottomY - topY)).toInt().coerceIn(topY, bottomY)
            val nearestY = nearestRowWithData(sortedRows, targetY)
            val minMax = rowMinMax.getValue(nearestY)
            BubbleShapePoint(
                yF = nearestY / height.toFloat(),
                leftF = minMax[0] / width.toFloat(),
                rightF = minMax[1] / width.toFloat(),
            )
        }
    }

    /** Binární hledání nejbližšího řádku s daty - flood-fill nemusí vyplnit úplně každý řádek u šikmých okrajů bubliny. */
    private fun nearestRowWithData(sortedRows: List<Int>, target: Int): Int {
        var lo = 0
        var hi = sortedRows.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (sortedRows[mid] < target) lo = mid + 1 else hi = mid
        }
        if (lo > 0 && Math.abs(sortedRows[lo - 1] - target) <= Math.abs(sortedRows[lo] - target)) return sortedRows[lo - 1]
        return sortedRows[lo]
    }

    private fun colorDistance(a: Int, b: Int): Double {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return Math.sqrt((dr * dr + dg * dg + db * db).toDouble())
    }
}
