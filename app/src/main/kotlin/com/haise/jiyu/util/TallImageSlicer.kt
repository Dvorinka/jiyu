package com.haise.jiyu.util

/**
 * Čistá (bez Android závislostí) logika pro rozdělení extrémně vysokého obrázku na menší
 * svislé kusy - zdroje jako DemonicScans serví jednu "stránku" jako jeden souvislý obrázek
 * o výšce v řádu tisíců px (pozorováno 720x11400), což přesahuje maximální rozměr GPU
 * textury na spoustě zařízení -> Compose Image se to vykreslit nepodaří (potichu, bez
 * chyby - viz DemonicScansSource.kt).
 */
object TallImageSlicer {

    /**
     * @return seznam navazujících (žádná mezera/překryv), 0-indexovaných řádkových rozsahů
     *   pokrývajících celou výšku obrázku, každý max [maxSliceHeight] vysoký. Pro obrázek
     *   v rámci limitu vrátí jediný rozsah pokrývající celou výšku (žádné dělení).
     */
    fun computeSlices(height: Int, maxSliceHeight: Int): List<IntRange> {
        if (height <= maxSliceHeight) return listOf(0 until height)

        val slices = mutableListOf<IntRange>()
        var y = 0
        while (y < height) {
            val end = minOf(y + maxSliceHeight, height)
            slices += y until end
            y = end
        }
        return slices
    }
}
