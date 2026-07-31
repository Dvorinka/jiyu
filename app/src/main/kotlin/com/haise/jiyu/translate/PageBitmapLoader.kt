package com.haise.jiyu.translate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.Transformation
import com.haise.jiyu.ui.reader.TileDescrambleTransformation
import com.haise.jiyu.util.ScrambledImageUrl
import com.haise.jiyu.util.report
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stahuje a dekóduje bitmapu stránky pro OCR - přes Coil (stejná cache jako UI zobrazení
 * v RetryableAsyncImage, takže se stránka typicky nestahuje dvakrát) a se stejnou
 * [TileDescrambleTransformation] jako tam.
 *
 * Dřív [OcrEngine] stahovala bitmapu sama přes syrový OkHttp bez jakékoli transformace -
 * u zdrojů s dlaždicovým scramblingem (anti-scraping ochrana, viz [ScrambledImageUrl]) tak
 * OCR běžel na poskládané/nesmyslné bitmapě a tiše vracel prázdný výsledek. Přesunutím
 * načítání sem (mimo OcrEngine, který je čistě on-device ML Kit a s HTTP nemá co dělat) se
 * tenhle bug opravuje jako vedlejší efekt sjednocení s cestou, kterou appka používá pro
 * zobrazení.
 */
@Singleton
class PageBitmapLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (url.startsWith("/") || url.startsWith("file://")) {
                BitmapFactory.decodeFile(url.removePrefix("file://"))
            } else {
                val scramble = ScrambledImageUrl.parse(url)
                val transforms = buildList<Transformation> {
                    scramble?.let { add(TileDescrambleTransformation(it.grid, it.seed)) }
                }
                val request = ImageRequest.Builder(context)
                    .data(url.substringBeforeLast("#")) // strip #mplus_key= fragment
                    .apply { if (transforms.isNotEmpty()) transformations(transforms) }
                    // Coil na API 26+ defaultně dekóduje do Config.HARDWARE (bitmapa žije v GPU
                    // paměti) - výsledek je pro zobrazení skvělý, ale bitmap.getPixel() na ní
                    // tvrdě spadne s IllegalStateException. Tahle bitmapa jde rovnou do OCR
                    // (BubbleShapeDetector, hasWallBetween/ringColor), které čtou pixely jeden
                    // po druhém - proto to appka spolehlivě shazovalo hned po startu hromadného
                    // překladu (viz uživatelská zpětná vazba "u překladu appka spadne").
                    .allowHardware(false)
                    .build()
                val result = Coil.imageLoader(context).execute(request)
                (result as? SuccessResult)?.drawable?.let { (it as? BitmapDrawable)?.bitmap }
            }
        } catch (e: Exception) {
            // Nenačtená stránka = nepřeložená stránka. Bez hlášení se to navenek projeví jen
            // tím, že překlad "některé stránky přeskočil", bez jakékoli stopy proč.
            e.report("translate:bitmap:load")
            null
        }
    }
}
