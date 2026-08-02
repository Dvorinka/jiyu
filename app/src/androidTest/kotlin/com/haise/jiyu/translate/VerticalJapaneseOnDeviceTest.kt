package com.haise.jiyu.translate

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * MĚŘICÍ SONDA ke svislé japonštině - zjišťuje, CO ML Kit u svisle sázeného textu vlastně
 * vrátí, než se podle toho začne cokoliv opravovat.
 *
 * Proč to nejde odhadnout od stolu: celá appka stojí na předpokladu, že jeden ML Kit "Line"
 * je vodorovný řádek. [shouldMerge] slučuje bloky podle pravidla
 *
 *     horizontalGap < avgHeight * 1.8f
 *
 * což u svislého sloupce znamená "výška celého sloupce krát 1,8" - tedy vzdálenost přes půl
 * stránky. Kdyby ML Kit vracel sloupce jako samostatné Line objekty, slily by se do jednoho
 * bloku i sloupce z úplně jiných bublin. Jestli se to opravdu děje ale závisí na tom, jak
 * svislý text vrací, a to je potřeba změřit, ne předpokládat.
 *
 * Sonda odpovídá na tři věci:
 *  1. Přečte ML Kit svislou japonštinu vůbec?
 *  2. Vrací sloupec jako jeden Line (vysoký a úzký), nebo to láme jinak?
 *  3. Slil by [shouldMerge] sloupce ze dvou různých bublin do jednoho bloku?
 *
 * Výsledky jdou do logcatu pod značkou "VertJpProbe".
 */
@RunWith(AndroidJUnit4::class)
class VerticalJapaneseOnDeviceTest {

    private val recognizer by lazy { TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()) }

    /** Dvě "bubliny" se svislým textem vedle sebe, každá o dvou sloupcích. Čte se zprava doleva. */
    private fun verticalPage(): Bitmap {
        val bmp = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 56f
            isAntiAlias = true
        }

        // Pravá bublina (čte se první), pak levá - tak, jak jde japonské pořadí.
        drawColumn(canvas, paint, "こんにちは", x = 780f, top = 140f)
        drawColumn(canvas, paint, "げんきですか", x = 700f, top = 140f)
        drawColumn(canvas, paint, "はしれ", x = 300f, top = 700f)
        drawColumn(canvas, paint, "いそげ", x = 220f, top = 700f)
        return bmp
    }

    /** Vysází znaky pod sebe do jednoho sloupce. */
    private fun drawColumn(canvas: Canvas, paint: Paint, text: String, x: Float, top: Float) {
        var y = top
        text.forEach { ch ->
            canvas.drawText(ch.toString(), x, y, paint)
            y += paint.textSize * 1.1f
        }
    }

    @Test
    fun probe_whatDoesMlKitReturnForVerticalJapanese() {
        val bitmap = verticalPage()
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        val result = runBlocking {
            val image = InputImage.fromBitmap(bitmap, 0)
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        }

        val lines = result.textBlocks.flatMap { it.lines }
        Log.i(TAG, "=== ML Kit vratil ${lines.size} radku (ocekavane 4 sloupce) ===")

        val blocks = lines.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            // isVertical se MUSÍ nastavit stejně jako v OcrEngine.recognizeLines. Když tenhle
            // řádek chyběl, sonda po opravě hlásila pořád starý výsledek: bloky bez příznaku
            // spadly do vodorovného pravidla a slily se, i když produkce už slučuje správně.
            val block = RawTextBlock(
                text = line.text,
                leftF = box.left / width,
                topF = box.top / height,
                rightF = box.right / width,
                bottomF = box.bottom / height,
                isVertical = isVerticalAngle(line.angle),
            )
            val boxWidth = box.width().toFloat()
            val boxHeight = box.height().toFloat()
            Log.i(
                TAG,
                "text=\"%s\" box=%dx%d pomer v/s=%.2f confidence=%.3f uhel=%.1f".format(
                    line.text, box.width(), box.height(),
                    if (boxWidth == 0f) 0f else boxHeight / boxWidth,
                    line.confidence, line.angle,
                ),
            )
            block
        }

        // Klíčová otázka: slil by shouldMerge sloupce ze DVOU RŮZNÝCH bublin?
        for (i in blocks.indices) {
            for (j in i + 1 until blocks.size) {
                if (shouldMerge(blocks[i], blocks[j])) {
                    Log.i(TAG, "shouldMerge SLUCUJE: \"${blocks[i].text}\" + \"${blocks[j].text}\"")
                }
            }
        }
        Log.i(TAG, "po slouceni zbyde bloku: ${mergeNearbyLines(blocks).size}")
        mergeNearbyLines(blocks).forEach { Log.i(TAG, "  blok: \"${it.text}\"") }

        assertTrue("ML Kit musi na svisle japonstine vratit aspon neco", lines.isNotEmpty())
    }

    private companion object {
        private const val TAG = "VertJpProbe"
    }
}
