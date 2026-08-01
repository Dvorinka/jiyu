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
 * přemaluje text té horní - včetně textu, který se vůbec nepřeložil.
 *
 * ## Proč se to napoprvé neopravilo
 * První verze ořezávala jen podle sousedů, jejichž OCR BOX se s tím naším vodorovně překrýval
 * aspoň ze čtvrtiny. Jenže u kaskádové bubliny jsou laloky ZÁMĚRNĚ posunuté do stran (horní
 * vpravo, spodní vlevo) - právě to jim dává ten schodovitý tvar - takže se boxy překrývají
 * sotva a podmínka neprošla. Změřeno na zařízení: oba bloky pak dostaly totožný tvar celého
 * balónu, přesně jako bez opravy.
 *
 * Správná otázka nezní "překrývají se boxy", ale "POKRÝVÁ MŮJ TVAR CIZÍ TEXT?". Na to je
 * přesný nástroj: obrys zná svoje levé a pravé okraje v každé výšce (viz [shapeBoundsAtYF]),
 * takže stačí ověřit, jestli střed cizího bloku padne dovnitř.
 *
 * ## Jak se to řeší
 * Tvar se zkrátí na půli cesty mezi vlastní bublinou a tou sousední - dost na to, aby vlastní
 * bublina zůstala celá zakrytá, ale ne tak daleko, aby zasáhla cizí text.
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
        if (!shapeCovers(shape, other)) continue
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

/**
 * Leží střed cizího bloku uvnitř tohohle obrysu? Tedy: přemaloval by mu tvar text?
 *
 * Testuje se střed, ne celý box - u kaskádové bubliny cizí lalok z tvaru kouskem vyčnívá, ale
 * jeho text v něm leží celý.
 */
private fun shapeCovers(shape: List<BubbleShapePoint>, other: RawTextBlock): Boolean {
    val centerY = (other.topF + other.bottomF) / 2f
    if (centerY < shape.first().yF || centerY > shape.last().yF) return false
    val (left, right) = shapeBoundsAtYF(shape, centerY)
    val centerX = (other.leftF + other.rightF) / 2f
    return centerX in left..right
}
