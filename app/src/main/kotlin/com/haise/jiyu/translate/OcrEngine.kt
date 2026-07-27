package com.haise.jiyu.translate

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
    /** Barva pozadí horní poloviny prstence kolem bubliny - viz [OcrEngine.sampleBackgroundColor]. */
    val bgColorTopArgb: Int = DEFAULT_BUBBLE_BG_ARGB,
    /** Barva pozadí dolní poloviny prstence - společně s [bgColorTopArgb] tvoří gradient výplně (viz TranslationOverlay). */
    val bgColorBottomArgb: Int = DEFAULT_BUBBLE_BG_ARGB,
    /** Skutečný obrys bubliny (flood-fill) - viz [BubbleShapeDetector]. Null = detekce selhala, render použije heuristický obdélník. */
    val shape: List<BubbleShapePoint>? = null,
    /** false = pozadí kolem textu je barevně nesourodé (text napsaný přímo přes kresbu) - viz [OcrEngine.isColorUniform]/[TranslatedBlock.bgUniform]. */
    val bgUniform: Boolean = true,
)

/** Výsledek [OcrEngine.sampleBackgroundColor] - dvě barvy (gradient) + signál rovnoměrnosti pro [TranslatedBlock.bgUniform]. */
private data class BgSample(val topArgb: Int, val bottomArgb: Int, val uniform: Boolean)

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

/**
 * Čistě on-device ML Kit OCR - nemá s HTTP nic společného, stahování/dekódování bitmapy
 * stránky je zodpovědnost volajícího (viz [PageBitmapLoader]), ne tohohle enginu.
 */
@Singleton
class OcrEngine @Inject constructor() {
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

    suspend fun recognize(bitmap: Bitmap, language: String = "Japanese"): List<RawTextBlock> = withContext(Dispatchers.IO) {
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
        // Pořadí, ve kterém tenhle seznam skončí, je i pořadí, ve kterém bubliny uvidí
        // překladový model (viz GeminiUltraPrompt.buildUserPrompt) - bez řazení do
        // skutečného pořadí čtení dostával model repliky v podstatě náhodně (podle
        // union-find indexu z mergeNearbyLines), což kazilo návaznost dialogu.
        val merged = sortIntoReadingOrder(mergeNearbyLines(lines), rightToLeft = language == "Japanese")
        merged.map { block ->
            val bgSample = sampleBackgroundColor(bitmap, block)
            val shape = BubbleShapeDetector.detectShape(
                source = pixelSource,
                width = bitmap.width,
                height = bitmap.height,
                seeds = ringSeeds(block.leftF, block.topF, block.rightF, block.bottomF, bitmap.width, bitmap.height),
                // Detektor tvaru (flood-fill) potřebuje JEDNU referenční barvu pozadí, ne
                // gradient - průměr obou polovin je pro tenhle účel dost přesný.
                bgColorArgb = averageArgb(bgSample.topArgb, bgSample.bottomArgb),
            )
            block.copy(
                bgColorTopArgb = bgSample.topArgb,
                bgColorBottomArgb = bgSample.bottomArgb,
                bgUniform = bgSample.uniform,
                shape = shape,
            )
        }
    }

    /**
     * Dopočítá jen tvar bubliny pro už přeložené bloky ze starého cache formátu
     * (shape == null), bez nového OCR/ML Kit volání - viz TranslateRepository.getCachedPage
     * migrace. Blok, který už tvar má, nebo je SFX (nemá box vůbec), se přeskočí beze změny.
     */
    suspend fun detectShapesOnly(bitmap: Bitmap, blocks: List<TranslatedBlock>): List<TranslatedBlock> = withContext(Dispatchers.IO) {
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
     *
     * Vrací DVĚ barvy (horní/dolní polovina prstence podle svislé pozice vzorku) místo
     * jedné, pro gradient výplně - viz TranslationOverlay. Horní/dolní ŘÁDEK prstence jde
     * celý do své poloviny; levý/pravý SLOUPEC se rozpadne mezi obě poloviny sám podle
     * y-pozice každého vzorku ([sample] níž), žádná speciální logika navíc není potřeba.
     */
    private fun sampleBackgroundColor(bitmap: Bitmap, block: RawTextBlock): BgSample {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return BgSample(DEFAULT_BUBBLE_BG_ARGB, DEFAULT_BUBBLE_BG_ARGB, uniform = true)
        val margin = 4

        val left = (block.leftF * w).toInt()
        val top = (block.topF * h).toInt()
        val right = (block.rightF * w).toInt()
        val bottom = (block.bottomF * h).toInt()

        val ringLeft = (left - margin).coerceIn(0, w - 1)
        val ringTop = (top - margin).coerceIn(0, h - 1)
        val ringRight = (right + margin).coerceIn(0, w - 1)
        val ringBottom = (bottom + margin).coerceIn(0, h - 1)
        if (ringRight <= ringLeft || ringBottom <= ringTop) return BgSample(DEFAULT_BUBBLE_BG_ARGB, DEFAULT_BUBBLE_BG_ARGB, uniform = true)

        val topSamples = mutableListOf<IntArray>()
        val bottomSamples = mutableListOf<IntArray>()
        val midY = (ringTop + ringBottom) / 2

        fun sample(x: Int, y: Int) {
            if (x < 0 || x >= w || y < 0 || y >= h) return
            val px = bitmap.getPixel(x, y)
            val rgb = intArrayOf((px shr 16) and 0xFF, (px shr 8) and 0xFF, px and 0xFF)
            if (y <= midY) topSamples += rgb else bottomSamples += rgb
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

        val uniform = isColorUniform(topSamples + bottomSamples)
        return BgSample(colorFor(topSamples, uniform), colorFor(bottomSamples, uniform), uniform)
    }

    /**
     * Vzorky pozadí jsou "rovnoměrné", když se navzájem barevně moc neliší - to je skutečná
     * nakreslená bublina s jednolitou/lehce stínovanou výplní. Text napsaný přímo přes
     * členitou kresbu (žádná bublina) má v prstenci kolem sebe mnohem větší rozptyl barev -
     * tenhle signál pak [layoutHeuristic] použije, aby box kolem takového textu neroztahoval
     * stejně štědře jako u skutečné bubliny (viz [TranslatedBlock.bgUniform]).
     */
    private fun isColorUniform(samples: List<IntArray>): Boolean {
        if (samples.size < 2) return true
        val avgR = samples.sumOf { it[0] } / samples.size
        val avgG = samples.sumOf { it[1] } / samples.size
        val avgB = samples.sumOf { it[2] } / samples.size
        val maxDeviation = samples.maxOf { s -> maxOf(Math.abs(s[0] - avgR), Math.abs(s[1] - avgG), Math.abs(s[2] - avgB)) }
        return maxDeviation <= UNIFORM_COLOR_THRESHOLD
    }

    /**
     * Rovnoměrné pozadí: prostý průměr (jako dřív - stabilní pro skutečné bubliny).
     * Nerovnoměrné (pestrá kresba): průměr jen z nejčastějšího barevného "kbelíku"
     * (kvantizace po [COLOR_BUCKET_SIZE] úrovních na kanál) místo průměru přes úplně
     * odlišné barvy - ten totiž skoro vždy vyjde jako neexistující "zabahněná" barva
     * (viz uživatelská zpětná vazba - hnědá placka přes barevnou titulní kresbu),
     * zatímco dominantní barva okolí aspoň vizuálně patří k té kresbě.
     */
    private fun colorFor(samples: List<IntArray>, uniform: Boolean): Int {
        if (samples.isEmpty()) return DEFAULT_BUBBLE_BG_ARGB
        val source = if (uniform) samples else {
            val buckets = HashMap<Triple<Int, Int, Int>, MutableList<IntArray>>()
            for (s in samples) {
                val key = Triple(s[0] / COLOR_BUCKET_SIZE, s[1] / COLOR_BUCKET_SIZE, s[2] / COLOR_BUCKET_SIZE)
                buckets.getOrPut(key) { mutableListOf() }.add(s)
            }
            buckets.values.maxByOrNull { it.size } ?: samples
        }
        val avgR = source.sumOf { it[0] } / source.size
        val avgG = source.sumOf { it[1] } / source.size
        val avgB = source.sumOf { it[2] } / source.size
        return android.graphics.Color.rgb(avgR, avgG, avgB)
    }

    private companion object {
        private const val UNIFORM_COLOR_THRESHOLD = 45
        private const val COLOR_BUCKET_SIZE = 32
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

}
