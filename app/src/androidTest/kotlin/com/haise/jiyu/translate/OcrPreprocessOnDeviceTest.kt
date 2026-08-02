package com.haise.jiyu.translate

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * MĚŘICÍ SONDA - zjišťuje, jestli předzpracování obrázku ([OcrPreprocess]) pomáhá OCR, a na
 * jakých předlohách. Do ML Kitu totiž stránka odjakživa chodí syrová a nikdo neověřil, jestli
 * je to dobře.
 *
 * Proč syntetické stránky a ne skutečné skeny: text si vykreslíme sami, takže ZNÁME správnou
 * odpověď a můžeme měřit skutečnou chybovost (Levenshtein proti předloze), ne jen "kolik toho
 * našel". Navíc se do repozitáře nedostane žádná cizí stránka.
 *
 * Čím je to omezené, ať se z čísel nevyvozuje víc, než unesou: vykreslené písmo je strojové,
 * kdežto skenlace bývají ručně psané. Závěry o ROZLIŠENÍ a KONTRASTU se přenášejí dobře -
 * jsou to vlastnosti obrázku, ne fontu. Závěry o tvarech písmen se nepřenášejí vůbec.
 *
 * Výsledky jdou do logcatu pod značkou "OcrProbe". Sonda schválně skoro nic netvrdí - jejím
 * úkolem je vyrobit čísla, podle kterých se teprve rozhodne, co se pustí do produkce.
 *
 * ## Naměřeno 2026-08-02 (emulátor API 34, 12 replik na stránku)
 *
 * Chybovost skončila na 0,0-0,5 % ve VŠECH scénářích a u všech variant; 0,5 % je jediný znak
 * ze zhruba dvou set. ML Kit přečte vykreslený text prakticky bezchybně i při 12 px a JPEG
 * kvalitě 20, takže chybovost tady nemá co rozlišovat - strop je vyčerpaný. Rozlišuje jen
 * confidence (průměr přes řádky, proti syrové předloze):
 *
 *     scénář              NONE    UPSCALE_2X   CONTRAST   BINARIZE
 *     malé písmo 12px     0.817     0.826        0.817      0.730
 *     malé písmo 16px     0.805     0.843        0.805      0.696
 *     malé písmo 22px     0.806     0.830        0.806      0.788
 *     malé písmo 32px     0.808     0.813        0.802      0.780
 *     vybledlý sken       0.821     0.823        0.803      0.783
 *     JPEG kvalita 20     0.803     0.823        0.780      0.773
 *     JPEG kvalita 40     0.802     0.816        0.800      0.768
 *     JPEG kvalita 70     0.798     0.820        0.797      0.783
 *     světlé na tmavém    0.786     0.819        0.785      0.791
 *
 * **BINARIZE je horší v osmi případech z devíti**, u malého písma drasticky (16px: 0.805 ->
 * 0.696). Otsuův práh zahodí odstíny, ze kterých si model skládá tvar tahu, a u drobného
 * písma je to většina informace. Zamítnuto.
 *
 * **CONTRAST nedělá nic** - shoda s NONE na tři desetinná místa, a kde se liší, tak k horšímu.
 * Přitom se opravdu aplikuje: `every_variant_really_alters_the_image_it_claims_to_fix` měří,
 * že vybledlý sken se roztáhne z rozsahu 120..200 na plných 0..255. Jediné vysvětlení, které
 * zbývá, je že si ML Kit kontrast normalizuje sám, takže mu ho předžvýkávat je zbytečné.
 * Zamítnuto.
 *
 * **UPSCALE_2X zvedne confidence v devíti případech z devíti**, nejvíc přesně tam, kde se to
 * čekalo (16px: +0.038; světlé na tmavém: +0.033). Do produkce přesto NEJDE, a je fér říct
 * proč: confidence je vlastní odhad modelu, ne přesnost, a přesnost se zlepšit nedá, protože
 * už je na stropě. Zaplatilo by se čtyřnásobkem paměti a času OCR na každé stránce - a stránka
 * webtoonu má klidně 15 000 px na výšku, kde je čtyřnásobek přímá cesta k OOM (viz
 * PageBitmapLoader.maxDimension). Prokazatelný zisk nula, jistá cena vysoká.
 *
 * ## Confidence jako varování? Změřeno 2026-08-02, NEPRŮKAZNÉ
 *
 * ML Kit vrací u každého řádku `getConfidence()` a appka ji nikdy nečetla. Nabízí se použít
 * ji jako signál "tady OCR plave" a modelu takovou bublinu označit, ať si nedomýšlí. Měřeno
 * na stránkách zašuměných tak, aby OCR začalo chybovat (`probe_doesConfidencePredictAWrongRead`):
 *
 *     šum    správně přečtené   špatně přečtené
 *     0        0.794 (11 řádků)   0.730 (1 řádek)
 *     60       0.773 (11)         0.772 (1)
 *     90       0.763 (11)         0.687 (1)
 *     110      0.732 (10)         0.716 (2)
 *
 * Špatně přečtený řádek má většinou nižší confidence, ale při šumu 60 vyšla prakticky
 * shodná se správnými - a celé to stojí na jednom až dvou špatných řádcích z dvanácti.
 * Z toho práh postavit nejde; vyšel by generátor náhodných poplachů.
 *
 * Pozor na správnou interpretaci: tohle hypotézu NEVYVRACÍ, jen ji nepotvrzuje. Dostatek
 * chybných řádků se na vykresleném písmu vyrobit nepodařilo - ML Kit ho čte správně i pod
 * šumem, který je pro člověka nepříjemný. Rozhodnout by šlo až na skutečných skenlacích
 * s ručním letteringem, kde OCR chybuje samo od sebe.
 *
 * Co tahle sonda odpovědět NEUMÍ: jestli některá z variant pomůže na SKUTEČNÝCH skenlacích
 * s ručně psaným písmem. Vykreslené strojové písmo je pro OCR lehčí a chybovost to ukázala -
 * na stropě nejde poznat rozdíl. K tomu by byla potřeba měření na reálných stránkách, kde
 * ale není známá správná odpověď, takže by zbyla jen confidence.
 */
@RunWith(AndroidJUnit4::class)
class OcrPreprocessOnDeviceTest {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /**
     * Repliky v délce, jaká v bublinách běžně bývá.
     *
     * Řádků je schválně hodně: při čtyřech byla jedna chyba rovnou 1,6 % a všechny rozdíly mezi
     * variantami se vešly do jednoho znaku, takže se z nich nedalo usoudit nic. Apostrof v textu
     * není - ML Kit ho vrací jako jiný znak, což přičítalo konstantní chybu ke všem variantám
     * stejně a jen zvedalo podlahu měření.
     */
    private val groundTruth = listOf(
        "THEY ARE COMING",
        "SHUT YOUR MOUTH",
        "EVERYONE STAY TOGETHER",
        "I WILL NOT LOSE AGAIN",
        "GET BACK FROM THE EDGE",
        "WHAT DID YOU JUST SAY",
        "THIS IS NOT OVER YET",
        "RUN BEFORE IT SEES US",
        "HOLD THE LINE",
        "I NEVER WANTED THIS",
        "ANSWER ME RIGHT NOW",
        "WE HAVE NO TIME LEFT",
    )

    private data class Measurement(val errorRate: Float, val confidence: Float, val linesFound: Int)

    /** Stránka s textem: [textColor] na [background], písmo velikosti [textSize]. */
    private fun page(
        textSize: Float,
        textColor: Int = Color.BLACK,
        background: Int = Color.WHITE,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(background)
        val paint = Paint().apply {
            color = textColor
            this.textSize = textSize
            isAntiAlias = true
        }
        var y = textSize * 2f
        groundTruth.forEach { line ->
            canvas.drawText(line, 40f, y, paint)
            y += textSize * 2f
        }
        return bmp
    }

    /** Projede bitmapu JPEGem dané kvality - tak, jak stránka doopravdy dorazí ze zdroje. */
    private fun jpegged(source: Bitmap, quality: Int): Bitmap {
        val out = ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.JPEG, quality, out)
        val bytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            .copy(Bitmap.Config.ARGB_8888, /* isMutable = */ false)
    }

    private suspend fun recognize(bitmap: Bitmap): Pair<List<String>, Float> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        val lines = result.textBlocks.flatMap { it.lines }
        // Confidence u řádků ML Kit vrací a appka ji dosud vůbec nečetla.
        val confidence = if (lines.isEmpty()) 0f else lines.map { it.confidence }.average().toFloat()
        return lines.map { it.text } to confidence
    }

    /** Vše na velká písmena a bez mezer - o mezery a zalomení nám tady nejde. */
    private fun normalize(lines: List<String>): String =
        lines.joinToString("").uppercase().filter { !it.isWhitespace() }

    private fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
            }
            previous = current
        }
        return previous[b.length]
    }

    private suspend fun measure(source: Bitmap, variant: OcrPreprocessing): Measurement {
        val prepared = preprocessForOcr(source, variant)
        val (lines, confidence) = recognize(prepared)
        if (prepared !== source) prepared.recycle()

        val expected = normalize(groundTruth)
        val actual = normalize(lines)
        val errorRate = levenshtein(expected, actual).toFloat() / expected.length
        return Measurement(errorRate, confidence, lines.size)
    }

    private fun report(label: String, results: Map<OcrPreprocessing, Measurement>) {
        Log.i(TAG, "--- $label ---")
        results.forEach { (variant, m) ->
            Log.i(
                TAG,
                "%-20s chybovost=%5.1f%%  confidence=%.3f  radku=%d".format(
                    variant.name, m.errorRate * 100, m.confidence, m.linesFound,
                ),
            )
        }
    }

    private fun runVariants(source: Bitmap): Map<OcrPreprocessing, Measurement> = runBlocking {
        OcrPreprocessing.entries.associateWith { measure(source, it) }
    }

    @Test
    fun probe_smallText() {
        // Nejpodezřelejší případ: zdroje, které servírují úzké obrázky, mají písmo o pár
        // pixelech. Zvětšení je jediná varianta, která tomu může pomoct.
        for (size in listOf(12f, 16f, 22f, 32f)) {
            report("male pismo ${size.toInt()}px", runVariants(page(textSize = size)))
        }
    }

    @Test
    fun probe_washedOutScan() {
        // Vybledlý sken: šedý text na světle šedém papíru, rozsah jasů využitý sotva ze čtvrtiny.
        val source = page(textSize = 32f, textColor = Color.rgb(120, 120, 120), background = Color.rgb(200, 200, 200))
        report("vybledly sken", runVariants(source))
    }

    @Test
    fun probe_jpegArtifacts() {
        // Takhle stránka doopravdy dorazí ze zdroje - kolem tahů je kompresní šum.
        for (quality in listOf(20, 40, 70)) {
            report("JPEG kvalita $quality", runVariants(jpegged(page(textSize = 28f), quality)))
        }
    }

    @Test
    fun probe_lightTextOnDark() {
        // Noční panel: bílé písmo na černé. Sem míří inverze v BINARIZE.
        val source = page(textSize = 32f, textColor = Color.WHITE, background = Color.BLACK)
        report("svetle pismo na tmavem", runVariants(source))
    }

    /** Přisype do obrázku náhodný šum - tím se OCR dá spolehlivě dotlačit k chybám. */
    private fun noisy(source: Bitmap, strength: Int, seed: Long = 42L): Bitmap {
        val random = java.util.Random(seed)
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val delta = random.nextInt(strength * 2 + 1) - strength
            val r = (((pixels[i] shr 16) and 0xFF) + delta).coerceIn(0, 255)
            val g = (((pixels[i] shr 8) and 0xFF) + delta).coerceIn(0, 255)
            val b = ((pixels[i] and 0xFF) + delta).coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    @Test
    fun probe_doesConfidencePredictAWrongRead() {
        // OTÁZKA: dá se confidence použít jako VAROVÁNÍ, že OCR na daném řádku plave?
        //
        // Proč to není samozřejmé: na čisté předloze vychází 0,70-0,85 i u naprosto správně
        // přečteného řádku (viz tabulka výš). Kdyby stejně vysoká vycházela i u přečteného
        // špatně, byl by z jakéhokoli prahu jen generátor náhodných poplachů. Bez tohohle
        // měření nemá smysl na ni v produkci sahat.
        //
        // Šum je tu proto, aby OCR vůbec začalo chybovat - na čisté předloze je bezchybné,
        // takže by nebylo co porovnávat.
        val expected = groundTruth.map { it.filter { ch -> !ch.isWhitespace() } }.toSet()

        for (strength in listOf(0, 60, 90, 110)) {
            val source = if (strength == 0) page(textSize = 28f) else noisy(page(textSize = 28f), strength)
            val (lines, _) = runBlocking { recognize(source) }

            val correct = mutableListOf<Float>()
            val wrong = mutableListOf<Float>()
            runBlocking {
                val image = InputImage.fromBitmap(source, 0)
                val result = suspendCancellableCoroutine { cont ->
                    recognizer.process(image)
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
                result.textBlocks.flatMap { it.lines }.forEach { line ->
                    val normalized = line.text.uppercase().filter { !it.isWhitespace() }
                    if (normalized in expected) correct += line.confidence else wrong += line.confidence
                }
            }

            Log.i(
                TAG,
                "sum=%3d  radku=%2d  spravne=%2d (confidence %.3f)  spatne=%2d (confidence %s)".format(
                    strength,
                    lines.size,
                    correct.size,
                    if (correct.isEmpty()) 0f else correct.average().toFloat(),
                    wrong.size,
                    if (wrong.isEmpty()) "-" else "%.3f".format(wrong.average()),
                ),
            )
        }
    }

    @Test
    fun every_variant_really_alters_the_image_it_claims_to_fix() {
        // POJISTKA PROTI MĚŘENÍ, KTERÉ NIC NEMĚŘÍ. CONTRAST vyšel v prvním běhu na všech devíti
        // scénářích identicky s NONE, včetně confidence na tři desetinná místa. To má dvě možná
        // vysvětlení - buď si ML Kit kontrast normalizuje sám, nebo naše funkce nedělá nic - a
        // ta se liší závěrem. Bez tohohle testu by se nedalo poznat které.
        val washedOut = page(textSize = 32f, textColor = Color.rgb(120, 120, 120), background = Color.rgb(200, 200, 200))
        val pixels = IntArray(washedOut.width * washedOut.height)
        washedOut.getPixels(pixels, 0, washedOut.width, 0, 0, washedOut.width, washedOut.height)
        val histogram = lumaHistogram(pixels)
        val lut = contrastStretchLut(histogram)

        Log.i(TAG, "vybledly sken: lut[120]=${lut[120]} lut[200]=${lut[200]} (identita by byla 120 a 200)")

        assertTrue(
            "roztažení kontrastu musí vybledlý sken opravdu změnit, jinak měření CONTRAST nic neříká",
            lut.toList() != IntArray(256) { it }.toList(),
        )

        for (variant in OcrPreprocessing.entries - OcrPreprocessing.NONE) {
            val prepared = preprocessForOcr(washedOut, variant)
            val preparedPixels = IntArray(prepared.width * prepared.height)
            prepared.getPixels(preparedPixels, 0, prepared.width, 0, 0, prepared.width, prepared.height)
            val changed = prepared.width != washedOut.width || !preparedPixels.contentEquals(pixels)
            if (prepared !== washedOut) prepared.recycle()

            assertTrue("$variant musí bitmapu změnit, jinak se měří pořád totéž", changed)
        }
    }

    @Test
    fun a_clean_page_is_read_correctly_without_any_preprocessing() {
        // Jediné skutečné tvrzení sondy, a je to pojistka proti tomu, aby měření nic neměřilo:
        // kdyby OCR selhávalo i na čisté předloze, byla by všechna čísla výš k ničemu.
        val measurement = runBlocking { measure(page(textSize = 40f), OcrPreprocessing.NONE) }

        Log.i(TAG, "cista stranka: chybovost=%.1f%% confidence=%.3f".format(measurement.errorRate * 100, measurement.confidence))
        assertTrue(
            "čistá stránka musí jít přečíst skoro bez chyb (vyšlo ${measurement.errorRate * 100} %)",
            measurement.errorRate < 0.1f,
        )
    }

    private companion object {
        private const val TAG = "OcrProbe"
    }
}
