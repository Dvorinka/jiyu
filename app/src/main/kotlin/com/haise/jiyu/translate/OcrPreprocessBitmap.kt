package com.haise.jiyu.translate

import android.graphics.Bitmap

/**
 * Bitmapové obaly nad [OcrPreprocess] - jediné místo, kde předzpracování vidí Android typ.
 * Vlastní výpočty jsou v čistých funkcích vedle, aby se daly měřit bez zařízení.
 */

/** Varianta předzpracování, kterou umí [preprocessForOcr]. Co z toho vyhrálo, řeší OcrPreprocessOnDeviceTest. */
enum class OcrPreprocessing {
    /** Syrová bitmapa - stav před touto prací a měřicí základ, proti kterému se ostatní poměřují. */
    NONE,

    /** Dvojnásobné zvětšení s bilineárním filtrem. Míří na malé písmo, kde ML Kitu chybí pixely. */
    UPSCALE_2X,

    /** Roztažení kontrastu na percentilech - míří na vybledlé skeny. */
    CONTRAST,

    /** Otsuova binarizace, u převážně tmavé předlohy i s inverzí. Míří na JPEG šum kolem tahů. */
    BINARIZE,

    /** Roztažení kontrastu a teprve pak zvětšení - obojí naráz. */
    CONTRAST_UPSCALE_2X,
}

/**
 * Vyrobí bitmapu pro OCR podle zvolené varianty. Pro [OcrPreprocessing.NONE] vrací vstup beze
 * změny (žádná kopie), takže se za měřicí základ nic nepřipočítává.
 *
 * Volající je zodpovědný za `recycle()` výsledku, pokud není totožný se vstupem - proto se
 * vrácená bitmapa porovnává referencí, ne obsahem.
 */
fun preprocessForOcr(bitmap: Bitmap, variant: OcrPreprocessing): Bitmap = when (variant) {
    OcrPreprocessing.NONE -> bitmap
    OcrPreprocessing.UPSCALE_2X -> upscale(bitmap, UPSCALE_FACTOR)
    OcrPreprocessing.CONTRAST -> mapPixels(bitmap) { pixels ->
        applyLutToGray(pixels, contrastStretchLut(lumaHistogram(pixels)))
    }
    OcrPreprocessing.BINARIZE -> mapPixels(bitmap) { pixels ->
        val histogram = lumaHistogram(pixels)
        val binary = binarize(pixels, otsuThreshold(histogram))
        if (isMostlyDark(histogram)) invert(binary) else binary
    }
    OcrPreprocessing.CONTRAST_UPSCALE_2X -> {
        val stretched = mapPixels(bitmap) { pixels ->
            applyLutToGray(pixels, contrastStretchLut(lumaHistogram(pixels)))
        }
        upscale(stretched, UPSCALE_FACTOR).also { if (it !== stretched) stretched.recycle() }
    }
}

private fun upscale(bitmap: Bitmap, factor: Int): Bitmap =
    Bitmap.createScaledBitmap(bitmap, bitmap.width * factor, bitmap.height * factor, /* filter = */ true)

/**
 * Načte pixely jedním voláním, přepočítá je čistou funkcí a složí novou bitmapu.
 *
 * Přes `getPixels`/`setPixels`, ne `getPixel` v cyklu: stránka webtoonu má klidně 15 000 px na
 * výšku a volání na pixel je tam o řády dražší.
 */
private inline fun mapPixels(bitmap: Bitmap, transform: (IntArray) -> IntArray): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    out.setPixels(transform(pixels), 0, width, 0, 0, width, height)
    return out
}

private const val UPSCALE_FACTOR = 2
