package com.haise.jiyu.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/**
 * Android-závislá část [TileScramble] - skutečné kopírování pixelů podle
 * spočítaných [TileScramble.TileCopy] operací. Odděleno od čisté matematiky
 * v [TileScramble], protože to používají dvě různá místa: online čtečka
 * (Coil `Transformation`, viz `com.haise.jiyu.ui.reader.TileDescrambleTransformation`)
 * a offline stahování (`ChapterDownloadWorker`, mimo Coil).
 */
object TileScrambleBitmap {

    fun descramble(input: Bitmap, grid: Int, seed: Long): Bitmap {
        val copies = TileScramble.computeTileCopies(input.width, input.height, grid, seed)
        // `Bitmap.config` je v novějších SDK stubech nullable - u hardwarové bitmapy opravdu
        // null je. Bez náhrady by se ten null propašoval do createBitmap a spadlo by to.
        val output = Bitmap.createBitmap(input.width, input.height, input.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val srcRect = Rect()
        val dstRect = Rect()
        for (copy in copies) {
            srcRect.set(copy.srcX, copy.srcY, copy.srcX + copy.srcW, copy.srcY + copy.srcH)
            dstRect.set(copy.dstX, copy.dstY, copy.dstX + copy.dstW, copy.dstY + copy.dstH)
            canvas.drawBitmap(input, srcRect, dstRect, paint)
        }
        return output
    }
}
