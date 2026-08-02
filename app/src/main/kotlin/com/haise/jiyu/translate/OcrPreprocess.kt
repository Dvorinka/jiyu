package com.haise.jiyu.translate

/**
 * Předzpracování obrázku před OCR.
 *
 * Do ML Kitu šla stránka vždycky syrová ([OcrEngine.recognize] volá `InputImage.fromBitmap`
 * rovnou nad staženou bitmapou). U čistého skenu to stačí, u slabé předlohy ne - a "slabá
 * předloha" je u skenlací běžná: malé písmo, vybledlý kontrast, JPEG artefakty.
 *
 * Funkce tady jsou schválně bez `android.graphics`: berou pole ARGB pixelů a vrací pole nebo
 * převodní tabulku, takže se dají měřit obyčejným unit testem. Bitmapové obaly jsou
 * v OcrPreprocessBitmap.kt.
 *
 * ## Pozor: do OCR cesty tohle záměrně NENÍ zapojené
 * Vzniklo to jako měřicí aparát k otázce "nejde OCR vylepšit předzpracováním?" a odpověď
 * z měření zní ne. Binarizace je prokazatelně horší, roztažení kontrastu nedělá nic (ML Kit
 * si kontrast normalizuje sám) a zvětšení sice zvedne confidence, ale ne přesnost, přitom
 * stojí čtyřnásobek paměti. Čísla i s postupem jsou v KDoc u OcrPreprocessOnDeviceTest.
 *
 * Nechává se tu proto, aby se ta otázka nezkoušela znovu od nuly, a aby šlo měření zopakovat,
 * až se ML Kit povýší. Volá to jenom ta sonda - když sem někdo sáhne s úmyslem to zapojit,
 * ať si nejdřív přečte naměřené hodnoty.
 */

/** Jas podle Rec. 601 - stejné vážení, jaké používá převod do odstínů šedi. */
internal fun luma(argb: Int): Int {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (r * 299 + g * 587 + b * 114) / 1000
}

/** Četnost jednotlivých úrovní jasu (0..255). */
internal fun lumaHistogram(pixels: IntArray): IntArray {
    val histogram = IntArray(256)
    for (px in pixels) histogram[luma(px)]++
    return histogram
}

/**
 * Otsuův práh: úroveň jasu, která nejlépe rozdělí histogram na dvě skupiny (písmo a pozadí).
 *
 * Hledá se maximum mezitřídního rozptylu. Počítá se přírůstkově přes všechny prahy - je to
 * 256 kroků nad hotovým histogramem, takže cena je zanedbatelná proti jednomu průchodu obrázkem.
 *
 * Vrací STŘED plošiny stejně dobrých prahů, ne její první hodnotu. Stránka komiksu má
 * histogram skoro prázdný: pár úrovní kolem tahů písma, pár kolem papíru, a mezi tím nic.
 * Každý práh v té mezeře dělí úplně stejně, takže mezitřídní rozptyl vyjde na všech doslova
 * shodný - a "první nejlepší" pak padne přímo na tmavou špičku. Dokud jsou tahy jednolité,
 * funguje to; jakmile kolem té hodnoty kolísají (a po JPEG kompresi kolísají vždycky),
 * rozsekne je práh napůl. Střed mezery je od obou špiček co nejdál.
 */
internal fun otsuThreshold(histogram: IntArray): Int {
    val total = histogram.sum().toLong()
    if (total == 0L) return 128

    var sumAll = 0L
    for (level in 0..255) sumAll += level.toLong() * histogram[level]

    var sumBackground = 0L
    var weightBackground = 0L
    var bestVariance = -1.0
    var plateauStart = 128
    var plateauEnd = 128

    for (level in 0..255) {
        weightBackground += histogram[level]
        if (weightBackground == 0L) continue
        val weightForeground = total - weightBackground
        if (weightForeground == 0L) break

        sumBackground += level.toLong() * histogram[level]
        val meanBackground = sumBackground.toDouble() / weightBackground
        val meanForeground = (sumAll - sumBackground).toDouble() / weightForeground
        val diff = meanBackground - meanForeground
        val variance = weightBackground.toDouble() * weightForeground.toDouble() * diff * diff

        when {
            variance > bestVariance -> {
                bestVariance = variance
                plateauStart = level
                plateauEnd = level
            }
            // Porovnání Doublů na přesnou rovnost je tu v pořádku: přes prázdné úrovně se do
            // výpočtu nepřičte nic, takže jde doslova o tentýž výraz nad týmiž čísly.
            variance == bestVariance -> plateauEnd = level
        }
    }
    return (plateauStart + plateauEnd) / 2
}

/**
 * Převodní tabulka pro roztažení kontrastu: jas na percentilu [lowPercentile] se namapuje na 0,
 * jas na [highPercentile] na 255, mezi tím lineárně.
 *
 * Percentily, ne minimum a maximum: jediný černý pixel loga a jediný přepálený bílý stačí, aby
 * "min..max" pokrylo celý rozsah a roztažení neudělalo vůbec nic. Odseknutím okrajů se řídí
 * podle toho, kde je většina obrázku.
 *
 * Když je předloha už rozeklaná přes celý rozsah, vrací se identita - to je hlídané testem,
 * protože zbytečné přemapování jen přidá zaokrouhlovací šum.
 */
internal fun contrastStretchLut(histogram: IntArray, lowPercentile: Float = 0.02f, highPercentile: Float = 0.98f): IntArray {
    val total = histogram.sum()
    val identity = IntArray(256) { it }
    if (total == 0) return identity

    val lowCount = (total * lowPercentile).toInt()
    val highCount = (total * highPercentile).toInt()

    var running = 0
    var low = 0
    var high = 255
    for (level in 0..255) {
        running += histogram[level]
        if (running <= lowCount) low = level
        if (running <= highCount) high = level
    }

    // Příliš úzké okno by z jemných přechodů udělalo placku; příliš široké nemá co zlepšit.
    //
    // Horní mez se schválně neptá na `low == 0 && high == 255` - to s percentily nenastane
    // NIKDY. Odseknutá dvě procenta posunou konce dovnitř i u obrázku, který celý rozsah
    // poctivě využívá (naměřeno na rovnoměrném histogramu: 4..249), takže by se roztahovalo
    // pořád a přidávalo jen zaokrouhlovací šum. Rozhoduje proto šířka okna.
    if (high - low < MIN_STRETCH_RANGE || high - low >= NEARLY_FULL_RANGE) return identity

    val span = (high - low).toFloat()
    return IntArray(256) { level ->
        (((level - low) / span) * 255f).toInt().coerceIn(0, 255)
    }
}

/** Aplikuje převodní tabulku [lut] na jas každého pixelu; barevnost se zahazuje (OCR ji nepotřebuje). */
internal fun applyLutToGray(pixels: IntArray, lut: IntArray): IntArray =
    IntArray(pixels.size) { index ->
        val value = lut[luma(pixels[index])]
        (0xFF shl 24) or (value shl 16) or (value shl 8) or value
    }

/** Převod na čistě černobílý obraz podle prahu [threshold] - pixel jasnější než práh je bílý. */
internal fun binarize(pixels: IntArray, threshold: Int): IntArray =
    IntArray(pixels.size) { index ->
        if (luma(pixels[index]) > threshold) WHITE_ARGB else BLACK_ARGB
    }

/**
 * Je většina obrázku tmavá? Pak jde nejspíš o světlé písmo na tmavém podkladu (noční panel,
 * inverzní caption) a [binarize] by ho vrátil jako bílé na černém.
 *
 * Samo o sobě to není chyba - jestli s tím ML Kit má potíž, ukáže až měření, proto je to
 * oddělená funkce a ne skrytá součást binarizace.
 */
internal fun isMostlyDark(histogram: IntArray): Boolean {
    val total = histogram.sum().toLong()
    if (total == 0L) return false
    var dark = 0L
    for (level in 0 until 128) dark += histogram[level]
    return dark * 2 > total
}

/** Prohodí černou a bílou - používá se po [binarize] pro světlé písmo na tmavém podkladu. */
internal fun invert(pixels: IntArray): IntArray =
    IntArray(pixels.size) { index -> pixels[index] xor 0x00FFFFFF }

private const val MIN_STRETCH_RANGE = 32
private const val NEARLY_FULL_RANGE = 240
private const val WHITE_ARGB = 0xFFFFFFFF.toInt()
private const val BLACK_ARGB = 0xFF000000.toInt()
