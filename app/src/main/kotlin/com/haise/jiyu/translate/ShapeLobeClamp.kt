package com.haise.jiyu.translate

import kotlin.math.max
import kotlin.math.min

/**
 * Ořízne obrys bubliny tak, aby nesahal přes text bubliny sousední.
 *
 * ## Co se dělo
 * Kaskádová replika bývá nakreslená jako dvě PŘEKRÝVAJÍCÍ SE bublinky - a ty tvoří jednu
 * spojitou bílou plochu. Flood-fill, který [BubbleShapeDetector] pouští kolem spodní bubliny,
 * se přes ten pas přelije nahoru a vrátí tvar pokrývající OBA laloky. Výplň se pak natáhne
 * přes obojí, a protože se bubliny kreslí shora dolů (viz [sortIntoReadingOrder]), spodní
 * přemaluje text té horní.
 *
 * Nejhorší na tom je, že tím zmizí i text, který appka VŮBEC NEPŘELOŽILA - ať už horní bublinu
 * nenašlo OCR, nebo ji model vynechal. Nechat na stránce nepřeložený originál je vždycky lepší
 * než ho vygumovat: čtenář si ho aspoň přečte v původním jazyce.
 *
 * ## Jak se to řeší
 * Tvar se zkrátí na půli cesty mezi vlastní bublinou a tou sousední - dost na to, aby vlastní
 * bublina zůstala celá zakrytá, ale ne tak daleko, aby zasáhla cizí text. Ořezává se jen podle
 * sousedů, kteří se s bublinou VODOROVNĚ překrývají; bubliny vedle sebe si nepřekážejí.
 *
 * @param own OCR box bubliny, které tenhle obrys patří
 * @param others OCR boxy ostatních bublin na stránce
 */
internal fun clampShapeToOwnLobe(
    shape: List<BubbleShapePoint>,
    own: RawTextBlock,
    others: List<RawTextBlock>,
): List<BubbleShapePoint> {
    if (shape.isEmpty()) return shape

    var upperLimit = 0f
    var lowerLimit = 1f
    for (other in others) {
        if (!overlapsHorizontally(own, other)) continue
        if (other.bottomF <= own.topF) {
            // Soused nad námi - tvar smí sahat nanejvýš do půli mezery mezi nimi.
            upperLimit = max(upperLimit, (other.bottomF + own.topF) / 2f)
        } else if (other.topF >= own.bottomF) {
            lowerLimit = min(lowerLimit, (own.bottomF + other.topF) / 2f)
        }
    }

    // Vlastní bublina musí zůstat zakrytá za všech okolností - limit ji nikdy nesmí ukrojit.
    upperLimit = min(upperLimit, own.topF)
    lowerLimit = max(lowerLimit, own.bottomF)
    if (upperLimit <= 0f && lowerLimit >= 1f) return shape

    val clamped = shape.filter { it.yF in upperLimit..lowerLimit }
    // Prázdný tvar by znamenal, že se bublina vůbec nezakryje a prosvítal by originál pod
    // překladem - to je horší než mírně velkorysý obrys.
    return clamped.ifEmpty { shape }
}

/** Překrývají se boxy vodorovně natolik, že si můžou stát v cestě? */
private fun overlapsHorizontally(a: RawTextBlock, b: RawTextBlock): Boolean {
    val overlap = min(a.rightF, b.rightF) - max(a.leftF, b.leftF)
    if (overlap <= 0f) return false
    val narrower = min(a.rightF - a.leftF, b.rightF - b.leftF)
    if (narrower <= 0f) return false
    return overlap / narrower >= MIN_HORIZONTAL_OVERLAP
}

private const val MIN_HORIZONTAL_OVERLAP = 0.25f
