package com.haise.jiyu.util

/**
 * Kóduje/dekóduje parametry pro [TileScramble] jako query parametry přímo v URL
 * obrázku (`Page.imageUrl`). Tenhle string prochází beze změny až do dvou míst,
 * která obrázek skutečně stahují a zobrazují/ukládají - online čtečka
 * (`RetryableAsyncImage` v ReaderScreen.kt, přes Coil `Transformation`) a
 * offline stahování (`ChapterDownloadWorker`, přímo přes OkHttp bez Coil) -
 * proto je URL nejjednodušší společné místo, kudy tahle metadata protáhnout,
 * aniž by bylo nutné měnit datový model `Page`/`SChapter`.
 */
object ScrambledImageUrl {
    private const val PARAM_GRID = "jiyu_descramble_grid"
    private const val PARAM_SEED = "jiyu_descramble_seed"

    data class Params(val grid: Int, val seed: Long)

    fun encode(imageUrl: String, grid: Int, seed: Long): String {
        val separator = if (imageUrl.contains("?")) "&" else "?"
        return "$imageUrl$separator$PARAM_GRID=$grid&$PARAM_SEED=$seed"
    }

    fun parse(imageUrl: String): Params? {
        val query = imageUrl.substringAfter('?', "").takeIf { it.isNotEmpty() } ?: return null
        var grid: Int? = null
        var seed: Long? = null
        for (pair in query.split('&')) {
            val eq = pair.indexOf('=')
            if (eq < 0) continue
            when (pair.substring(0, eq)) {
                PARAM_GRID -> grid = pair.substring(eq + 1).toIntOrNull()
                PARAM_SEED -> seed = pair.substring(eq + 1).toLongOrNull()
            }
        }
        return if (grid != null && seed != null) Params(grid, seed) else null
    }
}
