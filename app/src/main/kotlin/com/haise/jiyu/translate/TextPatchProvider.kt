package com.haise.jiyu.translate

import android.graphics.Bitmap
import android.util.LruCache
import com.haise.jiyu.util.report
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vyrábí a drží záplaty pozadí pro bubliny, které leží přímo na kresbě (viz [buildTextPatch]).
 *
 * Počítá se to až při zobrazení stránky, ne při překladu, a výsledek se ukládá JEN do paměti.
 * Díky tomu do Room nic nepřibývá a nemusí se zvedat `PIPELINE_VERSION` - cache hotových
 * překladů tuhle změnu přežije.
 *
 * ## Proč tolik opatrnosti kolem paměti
 * Stránky webtoonů bývají extrémně vysoké (u některých zdrojů přes 15 000 px). Dekódovat
 * takovou stránku v plném rozlišení kvůli záplatě několika bublin je spolehlivý způsob, jak
 * appku shodit na OOM. Proto:
 *  - bitmapa se vůbec nenačítá, když na stránce žádná bublina záplatu nepotřebuje,
 *  - načítá se zmenšená na [PATCH_SOURCE_MAX_DIMENSION] (v záplatě se ztratí trochu ostrosti,
 *    ale nahrazuje se tím jednolitá barevná placka, takže je to výhodný obchod),
 *  - reference na ni se pouští hned po spočítání záplat, v paměti zůstávají jen ty malé výřezy,
 *  - a i ty drží [LruCache] se stropem v bajtech, ne v počtu položek.
 */
@Singleton
class TextPatchProvider @Inject constructor(
    private val pageBitmapLoader: PageBitmapLoader,
) {
    private val cache = object : LruCache<String, Map<Int, Bitmap>>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Map<Int, Bitmap>): Int =
            value.values.sumOf { it.byteCount }.coerceAtLeast(1)
    }

    /**
     * @param positioned bloky i s obdélníkem, přes který se doopravdy vykreslí - záplata se
     *   počítá přesně přes něj (viz [patchPlan]), ne přes OCR box textu.
     * @return záplaty klíčované pozicí v [positioned]; prázdná mapa, když není co záplatovat
     *   nebo se stránku nepodařilo načíst (volající pak jen nakreslí výplň jako dosud).
     */
    suspend fun patchesFor(pageUrl: String, positioned: List<PositionedTranslationBlock>): Map<Int, Bitmap> {
        val plan = patchPlan(positioned)
        if (plan.isEmpty()) return emptyMap()

        val key = "$pageUrl#" + plan.entries.joinToString(",") { (i, r) ->
            "$i:${r.leftF},${r.topF},${r.rightF},${r.bottomF}"
        }
        cache.get(key)?.let { return it }

        return withContext(Dispatchers.Default) {
            val bitmap = pageBitmapLoader.load(pageUrl, PATCH_SOURCE_MAX_DIMENSION)
                ?: return@withContext emptyMap()
            val result = runCatching { buildPatches(bitmap, positioned, plan) }
                .onFailure { it.report("translate:patch:build") }
                .getOrDefault(emptyMap())
            if (result.isNotEmpty()) cache.put(key, result)
            result
        }
    }

    private fun buildPatches(
        bitmap: Bitmap,
        positioned: List<PositionedTranslationBlock>,
        plan: Map<Int, PatchRect>,
    ): Map<Int, Bitmap> {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return emptyMap()
        val source = PixelSource { x, y -> bitmap.getPixel(x, y) }

        return plan.mapNotNull { (index, rect) ->
            val b = positioned[index].block
            val left = (rect.leftF * w).toInt()
            val top = (rect.topF * h).toInt()
            val right = (rect.rightF * w).toInt()
            val bottom = (rect.bottomF * h).toInt()
            val boxW = (right - left).coerceAtMost(w)
            val boxH = (bottom - top).coerceAtMost(h)
            if (boxW <= 0 || boxH <= 0) return@mapNotNull null
            // Absurdně velký box (chybná OCR souřadnice) by znamenal záplatu přes půl stránky -
            // to už není oprava, to je nová placka. Radši nechat původní výplň.
            if (boxW.toLong() * boxH > MAX_PATCH_PIXELS) return@mapNotNull null

            val argb = buildTextPatch(
                source = source,
                imageWidth = w,
                imageHeight = h,
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                bgArgb = b.bgColorArgb,
                // Písmo se hledá jen tam, kde ho OCR opravdu našlo - zbytek boxu je kresba,
                // kterou nemá smysl prahovat ani dopočítávat (viz [buildTextPatch]).
                textLeft = (b.leftF * w).toInt(),
                textTop = (b.topF * h).toInt(),
                textRight = (b.rightF * w).toInt(),
                textBottom = (b.bottomF * h).toInt(),
            )
            if (argb.isEmpty()) return@mapNotNull null
            val clampedW = minOf(boxW, w - left.coerceAtLeast(0))
            val rows = argb.size / clampedW.coerceAtLeast(1)
            if (rows <= 0) return@mapNotNull null
            index to Bitmap.createBitmap(argb, clampedW, rows, Bitmap.Config.ARGB_8888)
        }.toMap()
    }

    companion object {
        /** Zmenšení zdrojové stránky pro účely záplaty - viz komentář u třídy. */
        private const val PATCH_SOURCE_MAX_DIMENSION = 1600

        /**
         * Strop plochy jedné záplaty (~2,4 MB jako ARGB). Zvednuto z 300 000 spolu s přechodem
         * na obdélník celého boxu (viz [patchPlan]): ten je u textu na kresbě znatelně větší
         * než OCR box, ze kterého se počítalo dřív, a při starém stropu by řada bloků spadla
         * zpátky na jednolitou výplň - tedy přesně na tu placku, kvůli které záplata vznikla.
         */
        private const val MAX_PATCH_PIXELS = 600_000L

        /** Strop celé vyrovnávací paměti záplat. */
        private const val CACHE_BYTES = 12 * 1024 * 1024
    }
}
