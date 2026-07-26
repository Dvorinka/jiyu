package com.haise.jiyu.util

/**
 * Čistá (bez Android závislosti) reimplementace "tiled-v1" scramble algoritmu
 * použitého mangadenizi.net k ochraně stránek proti hotlinkování. Obrázek je
 * rozdělený na grid×grid dlaždic, které server prohodí (seedovaný Fisher-Yates
 * shuffle zvlášť pro řádky a sloupce) a poskládá za sebe do kompaktního
 * "zamíchaného" obrázku. Tahle třída spočítá, odkud (ze zamíchaného obrázku)
 * a kam (do původního rozložení) se má která dlaždice zkopírovat, aby výsledek
 * byl zase čitelný - přesná reimplementace klientské JS logiky webu (funkce
 * Mn/Wt/Yt/Gt/En/$n v jejich reader JS bundlu, zjištěno reverzováním JS
 * chunků ve složce _nuxt 2026-07-26, ověřeno na reálné zamíchané stránce
 * pixel-perfect).
 */
object TileScramble {

    data class Segment(val offset: Int, val length: Int)

    /** Jedna kopírovací operace: obdélník ze zdroje (zamíchaný obrázek) do cíle (výsledek). */
    data class TileCopy(
        val srcX: Int, val srcY: Int, val srcW: Int, val srcH: Int,
        val dstX: Int, val dstY: Int, val dstW: Int, val dstH: Int,
    )

    private const val MASK32 = 0xFFFFFFFFL
    private const val SEED_FALLBACK = 2463534242L
    private const val MIX_COLUMNS = 2246822507L // "Rn" v JS - míchá seed pro permutaci sloupců
    private const val MIX_ROWS = 2654435769L    // "Pn" v JS - míchá seed pro permutaci řádků

    private fun xorshift32Next(state: Long): Long {
        var s = state and MASK32
        s = (s xor (s shl 13)) and MASK32
        s = (s xor (s ushr 17)) and MASK32
        s = (s xor (s shl 5)) and MASK32
        return s
    }

    private fun combineSeed(a: Long, b: Long): Long {
        val n = (a and MASK32) xor (b and MASK32)
        return if (n == 0L) SEED_FALLBACK else n
    }

    /** Fisher-Yates permutace [0, count) - stejný algoritmus (včetně modulo biasu) jako En() v JS. */
    private fun permutation(count: Int, seed: Long): IntArray {
        val arr = IntArray(count) { it }
        var state = if (seed == 0L) SEED_FALLBACK else (seed and MASK32)
        for (i in arr.size - 1 downTo 1) {
            state = xorshift32Next(state)
            val j = (state % (i + 1)).toInt()
            val tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp
        }
        return arr
    }

    /** Rozdělí `total` px do `parts` co nejrovnoměrnějších úseků - stejné jako Wt() v JS. */
    private fun splitSegments(total: Int, parts: Int): List<Segment> {
        val n = maxOf(1, total)
        val s = maxOf(1, minOf(parts, n))
        return (0 until s).map { i ->
            val start = i * n / s
            val end = (i + 1) * n / s
            Segment(start, maxOf(1, end - start))
        }
    }

    /** Přeskládaná (souvislá) poloha každé dlaždice v zamíchaném obrázku - stejné jako Gt() v JS. */
    private fun packedLayout(original: List<Segment>, order: IntArray): List<Segment> {
        var offset = 0
        return order.map { idx ->
            val length = maxOf(1, original.getOrNull(idx)?.length ?: 1)
            Segment(offset, length).also { offset += length }
        }
    }

    /**
     * Spočítá seznam kopírovacích operací pro rozskládání zamíchaného obrázku
     * (`width`×`height`, stejné rozměry jako výsledek) zpátky do čitelné podoby.
     * Volající je provede přes Canvas.drawBitmap/podobně - viz
     * [com.haise.jiyu.ui.reader.TileDescrambleTransformation].
     */
    fun computeTileCopies(width: Int, height: Int, grid: Int, seed: Long): List<TileCopy> {
        val w = maxOf(1, width)
        val h = maxOf(1, height)
        val f = maxOf(1, minOf(grid, minOf(w, h)))

        val cols = splitSegments(w, f)
        val rows = splitSegments(h, f)
        val colOrder = permutation(f, combineSeed(seed, MIX_COLUMNS))
        val rowOrder = permutation(f, combineSeed(seed, MIX_ROWS))
        val packedCols = packedLayout(cols, colOrder)
        val packedRows = packedLayout(rows, rowOrder)

        val copies = ArrayList<TileCopy>(f * f)
        for (row in 0 until f) {
            val destRow = rows[rowOrder[row]]
            val srcRow = packedRows[row]
            for (col in 0 until f) {
                val destCol = cols[colOrder[col]]
                val srcCol = packedCols[col]
                copies += TileCopy(
                    srcX = srcCol.offset, srcY = srcRow.offset, srcW = srcCol.length, srcH = srcRow.length,
                    dstX = destCol.offset, dstY = destRow.offset, dstW = destCol.length, dstH = destRow.length,
                )
            }
        }
        return copies
    }
}
