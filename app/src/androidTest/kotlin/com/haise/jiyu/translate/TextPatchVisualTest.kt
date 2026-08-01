package com.haise.jiyu.translate

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Vyrobí obrázek PŘED a PO, aby šlo očima porovnat, jestli záplata skutečně vypadá líp než
 * jednolitá placka. Na tuhle otázku žádné `assert` neodpoví - výsledek se musí vidět.
 *
 * Obrázky se ukládají do externího adresáře appky a stahují se přes `adb pull`.
 *
 * Podklad je schválně "jako kresba": barevný přechod přes celou plochu plus tmavý pruh, tedy
 * přesně případ, kdy `bgUniform` vyjde false a dosavadní jednobarevná výplň dělá tu placku,
 * na kterou si uživatel stěžoval.
 */
@RunWith(AndroidJUnit4::class)
class TextPatchVisualTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun artworkWithText(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Podklad: barevný přechod (žlutá -> červená -> modrá), aby žádná JEDNA barva nemohla
        // celou plochu zastoupit.
        val bg = Paint().apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(Color.rgb(250, 210, 60), Color.rgb(200, 40, 40), Color.rgb(40, 60, 190)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

        // Kus "kresby" - tmavý pruh napříč, ať je vidět, jestli ho záplata zachová.
        canvas.drawRect(0f, h * 0.62f, w.toFloat(), h * 0.72f, Paint().apply {
            color = Color.rgb(30, 30, 40)
        })

        // Text přímo na kresbě, bez bubliny.
        val text = Paint().apply {
            color = Color.BLACK
            textSize = h * 0.16f
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.drawText("SAKUMA", w * 0.06f, h * 0.30f, text)
        canvas.drawText("ここにいる", w * 0.06f, h * 0.52f, text)
        return bmp
    }

    @Test
    fun renderBeforeAndAfterForVisualInspection() {
        val w = 700
        val h = 320
        val original = artworkWithText(w, h)

        // "PŘED": dosavadní chování - celá plocha jednou navzorkovanou barvou. Vzorek se bere
        // stejně jako v OcrEngine, tedy z prstence kolem boxu.
        val sampled = original.getPixel(4, 4)
        val before = original.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(before).drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { color = sampled })

        // "PO": záplata jen přes tahy písma.
        val patchArgb = buildTextPatch(
            source = { x, y -> original.getPixel(x, y) },
            imageWidth = w,
            imageHeight = h,
            left = 0, top = 0, right = w, bottom = h,
            bgArgb = sampled,
        )
        val after = Bitmap.createBitmap(patchArgb, w, h, Bitmap.Config.ARGB_8888)

        // Interni adresar appky - externi scoped storage adb (jako shell uzivatel) neprecte.
        val dir = File(context.filesDir, "patch-visual").apply { mkdirs() }
        listOf("1-original" to original, "2-before-placka" to before, "3-after-zaplata" to after)
            .forEach { (name, bmp) ->
                File(dir, "$name.png").outputStream().use {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        Log.i("PatchVisual", "ulozeno do ${dir.absolutePath}")

        // Aspoň hrubá pojistka, ať test není čistě "vyrob obrázek": záplata se nesmí zvrhnout
        // v jednu barvu. Kdyby ano, znamená to, že zakryla celou plochu - tedy přesně to, čeho
        // se má zbavit.
        val distinct = HashSet<Int>()
        for (y in 0 until h step 7) for (x in 0 until w step 7) distinct += after.getPixel(x, y)
        Log.i("PatchVisual", "ruznych barev v zaplate: ${distinct.size}")
        assertTrue("záplata nesmí být jednolitá plocha, bylo ${distinct.size} barev", distinct.size > 20)
    }
}
