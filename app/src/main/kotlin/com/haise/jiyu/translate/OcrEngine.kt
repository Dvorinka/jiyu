package com.haise.jiyu.translate

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RawTextBlock(
    val text: String,
    val leftF: Float,
    val topF: Float,
    val rightF: Float,
    val bottomF: Float,
    /** Kolik původních ML Kit "lines" bylo sloučeno do tohoto bloku - viz [OcrEngine.mergeNearbyLines]. */
    val lineCount: Int = 1,
    /** Barva pozadí bubliny nasamplovaná z bitmapy - viz [OcrEngine.sampleBackgroundColor]. */
    val bgColorArgb: Int = DEFAULT_BUBBLE_BG_ARGB,
    /** Skutečný obrys bubliny (flood-fill) - viz [BubbleShapeDetector]. Null = detekce selhala, render použije heuristický obdélník. */
    val shape: List<BubbleShapePoint>? = null,
)

/**
 * Čistá funkce (bez Bitmap) - body na obvodu OCR boxu s okrajem [margin], odkud je
 * bezpečné startovat flood-fill (jsou to body na pozadí bubliny, ne na textu). Testováno
 * v OcrRingSeedsTest.
 */
internal fun ringSeeds(leftF: Float, topF: Float, rightF: Float, bottomF: Float, w: Int, h: Int, margin: Int = 4): List<Pair<Int, Int>> {
    val left = (leftF * w).toInt()
    val top = (topF * h).toInt()
    val right = (rightF * w).toInt()
    val bottom = (bottomF * h).toInt()
    val midX = ((left + right) / 2).coerceIn(0, w - 1)
    val midY = ((top + bottom) / 2).coerceIn(0, h - 1)
    return listOf(
        midX to (top - margin).coerceIn(0, h - 1),
        midX to (bottom + margin).coerceIn(0, h - 1),
        (left - margin).coerceIn(0, w - 1) to midY,
        (right + margin).coerceIn(0, w - 1) to midY,
    )
}

/** Obaluje Bitmap do [PixelSource] pro [BubbleShapeDetector] - jediné místo, kde algoritmus vidí Android typ. */
private class BitmapPixelSource(private val bitmap: Bitmap) : PixelSource {
    override fun colorAt(x: Int, y: Int): Int = bitmap.getPixel(x, y)
}

@Singleton
class OcrEngine @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    // Lazy recognizers: CJK jazyky mají vlastní ML Kit model, ostatní spadají na latinkový výchozí
    private val japaneseRecognizer by lazy { TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()) }
    private val chineseRecognizer by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }
    private val koreanRecognizer by lazy { TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()) }
    private val latinRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private fun recognizerFor(language: String) = when (language) {
        "Japanese" -> japaneseRecognizer
        "Chinese", "Chinese (Traditional)" -> chineseRecognizer
        "Korean" -> koreanRecognizer
        else -> latinRecognizer
    }

    suspend fun recognize(pageUrl: String, language: String = "Japanese"): List<RawTextBlock> = withContext(Dispatchers.IO) {
        val bitmap = loadBitmap(pageUrl) ?: return@withContext emptyList()
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        if (w == 0f || h == 0f) return@withContext emptyList()

        val image = InputImage.fromBitmap(bitmap, 0)

        val result = suspendCancellableCoroutine { cont ->
            recognizerFor(language).process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

        // ML Kit "textBlocks" jsou odstavcová seskupení odladěná na fotky dokumentů/účtenek,
        // ne na manga bubliny - běžně buď slijí dvě sousední bubliny do jednoho bloku, nebo
        // naopak rozseknou jednu bublinu na víc bloků. Jdeme proto o úroveň níž na "lines"
        // (řádky) a slučujeme je vlastní geometrickou heuristikou (mergeNearbyLines), která
        // lépe odpovídá tomu, co člověk vnímá jako jednu bublinu.
        //
        // (Zkoušeno i slučování na úrovni slov/elements - u ručně psaného komiksového písma
        // ML Kit občas vrátí boundingBox jednoho "Line" objektu kratší, než je skutečná výška
        // víceřádkového textu, ale jednotlivá slova mají stejně chybné souřadnice, takže to
        // problém neřešilo, a navíc to rozbilo slučování slov na stejném řádku - viz [shouldMerge]
        // dole, jehož práh je odvozený z výšky vstupu, a slova jsou o řád nižší než řádky.
        // Oprava chybějící výšky řeší [lineCount] (kolik "lines" bylo do bloku sloučeno) -
        // viz [PositionedTranslationBlock.minTopF] v TranslationLayout.kt, kde se podle
        // tohohle signálu box bezpečně roztáhne nahoru jen u opravdu víceřádkových bloků.)
        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            if (line.text.isBlank()) return@mapNotNull null
            RawTextBlock(
                text = line.text,
                leftF = (box.left / w).coerceIn(0f, 1f),
                topF = (box.top / h).coerceIn(0f, 1f),
                rightF = (box.right / w).coerceIn(0f, 1f),
                bottomF = (box.bottom / h).coerceIn(0f, 1f),
            )
        }
        // Sampling barvy pozadí i detekce tvaru bubliny potřebují ještě živou bitmapu,
        // proto běží tady a ne až v TranslateRepository, kam se bitmapa vůbec nedostane
        // (jen relativní souřadnice).
        val pixelSource = BitmapPixelSource(bitmap)
        mergeNearbyLines(lines).map { block ->
            val bg = sampleBackgroundColor(bitmap, block)
            val shape = BubbleShapeDetector.detectShape(
                source = pixelSource,
                width = bitmap.width,
                height = bitmap.height,
                seeds = ringSeeds(block.leftF, block.topF, block.rightF, block.bottomF, bitmap.width, bitmap.height),
                bgColorArgb = bg,
            )
            block.copy(bgColorArgb = bg, shape = shape)
        }
    }

    /**
     * Dopočítá jen tvar bubliny pro už přeložené bloky ze starého cache formátu
     * (shape == null), bez nového OCR/ML Kit volání - viz TranslateRepository.getCachedPage
     * migrace. Blok, který už tvar má, nebo je SFX (nemá box vůbec), se přeskočí beze změny.
     */
    suspend fun detectShapesOnly(pageUrl: String, blocks: List<TranslatedBlock>): List<TranslatedBlock> = withContext(Dispatchers.IO) {
        val bitmap = loadBitmap(pageUrl) ?: return@withContext blocks
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return@withContext blocks
        val pixelSource = BitmapPixelSource(bitmap)
        blocks.map { tb ->
            if (tb.shape != null || tb.isSfx) return@map tb
            val shape = BubbleShapeDetector.detectShape(
                source = pixelSource,
                width = w,
                height = h,
                seeds = ringSeeds(tb.leftF, tb.topF, tb.rightF, tb.bottomF, w, h),
                bgColorArgb = tb.bgColorArgb,
            )
            tb.copy(shape = shape)
        }
    }

    /**
     * Spojí OCR řádky, které leží blízko sebe (malá svislá mezera vůči výšce písma a
     * vodorovné překrytí/blízkost), do jednoho bloku - to bývá jedna bublina s víc řádky.
     * Union-Find nad dvojicovým testem [shouldMerge]: O(n²), ale n (řádků na stránku)
     * bývá v řádu jednotek až nízkých desítek, takže to není problém výkonu.
     */
    private fun mergeNearbyLines(lines: List<RawTextBlock>): List<RawTextBlock> {
        if (lines.isEmpty()) return emptyList()
        val parent = IntArray(lines.size) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) { val next = parent[c]; parent[c] = r; c = next }
            return r
        }
        fun union(a: Int, b: Int) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        for (i in lines.indices) {
            for (j in i + 1 until lines.size) {
                if (shouldMerge(lines[i], lines[j])) union(i, j)
            }
        }

        return lines.indices.groupBy { find(it) }.map { (_, idxs) ->
            val group = idxs.map { lines[it] }.sortedWith(compareBy({ it.topF }, { it.leftF }))
            RawTextBlock(
                text = group.joinToString(" ") { it.text },
                leftF = group.minOf { it.leftF },
                topF = group.minOf { it.topF },
                rightF = group.maxOf { it.rightF },
                bottomF = group.maxOf { it.bottomF },
                lineCount = group.size,
            )
        }
    }

    /**
     * Nasampluje průměrnou barvu tenkého prstence pixelů těsně kolem OCR boxu (mimo text,
     * ale typicky pořád uvnitř bubliny) - viz [TranslatedBlock.bgColorArgb]. Bez tohohle
     * je přeložený box vždy bílý, což na barevných/šrafovaných bublinách (shout efekty,
     * system boxy) nechává viditelně prosvítat okraj originálu kolem hran boxu.
     */
    private fun sampleBackgroundColor(bitmap: Bitmap, block: RawTextBlock): Int {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return DEFAULT_BUBBLE_BG_ARGB
        val margin = 4

        val left = (block.leftF * w).toInt()
        val top = (block.topF * h).toInt()
        val right = (block.rightF * w).toInt()
        val bottom = (block.bottomF * h).toInt()

        val ringLeft = (left - margin).coerceIn(0, w - 1)
        val ringTop = (top - margin).coerceIn(0, h - 1)
        val ringRight = (right + margin).coerceIn(0, w - 1)
        val ringBottom = (bottom + margin).coerceIn(0, h - 1)
        if (ringRight <= ringLeft || ringBottom <= ringTop) return DEFAULT_BUBBLE_BG_ARGB

        var sumR = 0L; var sumG = 0L; var sumB = 0L; var count = 0
        fun sample(x: Int, y: Int) {
            if (x < 0 || x >= w || y < 0 || y >= h) return
            val px = bitmap.getPixel(x, y)
            sumR += (px shr 16) and 0xFF
            sumG += (px shr 8) and 0xFF
            sumB += px and 0xFF
            count++
        }

        // Vzorkujeme jen obvod prstence (ne celou plochu) - max ~80 bodů, dost na stabilní
        // průměr a zanedbatelné vůči jednomu OCR volání na stránku.
        val maxSamplesPerSide = 20
        val stepX = ((ringRight - ringLeft).coerceAtLeast(1) / maxSamplesPerSide).coerceAtLeast(1)
        var x = ringLeft
        while (x <= ringRight) { sample(x, ringTop); sample(x, ringBottom); x += stepX }
        val stepY = ((ringBottom - ringTop).coerceAtLeast(1) / maxSamplesPerSide).coerceAtLeast(1)
        var y = ringTop
        while (y <= ringBottom) { sample(ringLeft, y); sample(ringRight, y); y += stepY }

        if (count == 0) return DEFAULT_BUBBLE_BG_ARGB
        return android.graphics.Color.rgb((sumR / count).toInt(), (sumG / count).toInt(), (sumB / count).toInt())
    }

    private fun shouldMerge(a: RawTextBlock, b: RawTextBlock): Boolean {
        val avgHeight = ((a.bottomF - a.topF) + (b.bottomF - b.topF)) / 2f
        if (avgHeight <= 0f) return false

        val verticalGap = maxOf(0f, maxOf(a.topF, b.topF) - minOf(a.bottomF, b.bottomF))
        val horizontalOverlap = minOf(a.rightF, b.rightF) - maxOf(a.leftF, b.leftF)
        val horizontalGap = maxOf(0f, maxOf(a.leftF, b.leftF) - minOf(a.rightF, b.rightF))

        // Řádky stejné bubliny mívají mezeru mnohem menší než výška písma; mezi bublinami
        // bývá mezera srovnatelná s výškou písma nebo větší (okraj bubliny, kresba).
        return verticalGap < avgHeight * 0.9f && (horizontalOverlap > 0f || horizontalGap < avgHeight * 1.8f)
    }

    private fun loadBitmap(url: String): Bitmap? = try {
        if (url.startsWith("/") || url.startsWith("file://")) {
            val path = url.removePrefix("file://")
            BitmapFactory.decodeFile(path)
        } else {
            val cleanUrl = url.substringBeforeLast("#") // strip #mplus_key= fragment
            val req = Request.Builder().url(cleanUrl).build()
            httpClient.newCall(req).execute().use { resp ->
                resp.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
        }
    } catch (e: Exception) {
        null
    }
}
