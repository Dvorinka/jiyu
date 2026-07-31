package com.haise.jiyu.translate

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresní pojistka na paměťovou náročnost [BubbleShapeDetector.detectShape].
 *
 * Nejhorší případ je stránka BEZ uzavřeného obrysu (text přímo na jednolité ploše) - flood-fill
 * se rozlije, dokud nenarazí na plošný limit. Původní implementace v tu chvíli držela navštívené
 * pixely v `HashSet<Long>`, tedy zabalený `Long` plus uzel hashovací tabulky na KAŽDÝ pixel:
 * naměřeno 169 MB u 1440x3120 a 245 MB u 1440x9000, a to na jedinou bublinu. Stránky se přitom
 * zpracovávají po třech souběžně a bublin je na stránce víc - appka na telefonu tiše umírala na
 * nedostatek paměti (viz uživatelská zpětná vazba "u překladu se appka normálně vypne").
 *
 * Bitová mapa to srazila na jednotky MB. Strop v testu je schválně hodně nad naměřenou
 * skutečností (~1-3 MB), ale hluboko pod původními stovkami - tenhle test má chytit návrat
 * ke kolekci objektů, ne hlídat pár kilobajtů sem tam.
 */
class BubbleShapeMemoryTest {

    private val memoryCeilingBytes = 48L * 1024 * 1024

    /**
     * @return nejvyšší naměřený nárůst obsazené haldy během běhu [block] (vzorkováno z vlákna
     *   vedle, protože jde o špičku uvnitř výpočtu, ne o stav po jeho konci).
     */
    private fun peakAllocation(block: () -> Unit): Long {
        val runtime = Runtime.getRuntime()
        System.gc()
        Thread.sleep(100)
        val before = runtime.totalMemory() - runtime.freeMemory()
        // AtomicLong, ne prosté var - zapisuje ho hlídací vlákno, čte hlavní.
        val peak = java.util.concurrent.atomic.AtomicLong(before)

        val watcher = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val used = runtime.totalMemory() - runtime.freeMemory()
                    peak.updateAndGet { if (used > it) used else it }
                    Thread.sleep(2)
                }
            } catch (_: InterruptedException) {
            }
        }
        watcher.isDaemon = true
        watcher.start()
        try {
            block()
        } finally {
            watcher.interrupt()
            watcher.join(1000)
        }
        return peak.get() - before
    }

    /** Jednolitá plocha bez jakékoli hrany - flood-fill nemá kde zastavit dřív než na limitu. */
    private fun runawayFill(width: Int, height: Int): List<BubbleShapePoint>? {
        val white = 0xFFFFFFFF.toInt()
        return BubbleShapeDetector.detectShape(
            source = { _, _ -> white },
            width = width,
            height = height,
            seeds = listOf(width / 2 to height / 2),
            bgColorArgb = white,
        )
    }

    @Test
    fun `a runaway flood fill on a normal page stays far below the heap limit`() {
        var result: List<BubbleShapePoint>? = emptyList()
        val peak = peakAllocation { result = runawayFill(1440, 3120) }

        assertNull("no closed outline exists, the area cap must reject it", result)
        assertTrue(
            "flood fill used ${peak / 1024 / 1024} MB, ceiling is ${memoryCeilingBytes / 1024 / 1024} MB",
            peak < memoryCeilingBytes,
        )
    }

    @Test
    fun `a runaway flood fill on a tall webtoon page stays far below the heap limit`() {
        var result: List<BubbleShapePoint>? = emptyList()
        val peak = peakAllocation { result = runawayFill(1440, 9000) }

        assertNull(result)
        assertTrue(
            "flood fill used ${peak / 1024 / 1024} MB, ceiling is ${memoryCeilingBytes / 1024 / 1024} MB",
            peak < memoryCeilingBytes,
        )
    }

    @Test
    fun `an absurdly large page is refused outright instead of being processed`() {
        // Nekonecny webtoon pas - detekce se o nej ani nepokusi, volajici pouzije obdelnik.
        assertNull(runawayFill(4000, 20_000))
    }
}
