package com.haise.jiyu.ui.reader

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import com.haise.jiyu.util.TileScrambleBitmap

/**
 * Coil [Transformation], která rozskládá stránky z mangadenizi.net zpátky do
 * čitelné podoby - server je servíruje rozřezané na dlaždice a zpřeházené
 * (viz [com.haise.jiyu.util.TileScramble]). URL nese parametry `grid`/`seed`
 * zakódované přes [com.haise.jiyu.util.ScrambledImageUrl].
 */
class TileDescrambleTransformation(private val grid: Int, private val seed: Long) : Transformation {

    override val cacheKey: String = "tile_descramble_${grid}_$seed"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap =
        TileScrambleBitmap.descramble(input, grid, seed)
}
