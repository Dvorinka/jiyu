package com.haise.jiyu.translate

import kotlin.math.abs

/**
 * Práh podílu (průměrná změna šířky mezi sousedními vzorky / průměrná šířka tvaru), nad
 * kterým se obrys považuje za "trsovitý/hvězdicovitý" - viz [isJaggedShape].
 */
private const val JAGGED_THRESHOLD = 0.25f

/**
 * Odhadne, jestli je detekovaný obrys bubliny (viz [BubbleShapeDetector]) "trsovitý/
 * hvězdicovitý" - typický tvar pro "shout" výbuch (viz nahlášený bug "UŽ JDOU..!", kde
 * appka dřív hádala typ bubliny jen z textu, ne z kresby). Hladký ovál/kruh - i dvojkruhová
 * "myšlenková" bublina, která mění šířku jen JEDNÍM pomalým obloukem přes celou výšku -
 * má nízký poměr; hroty výbuchu kolem shout bubliny se ale střídavě vysouvají a zatahují
 * KAŽDÝCH pár vzorků, takže sousední vzorky kolísají mnohem víc vzhledem k průměrné šířce.
 *
 * Používá se jako DOPLŇKOVÝ signál v [BubbleClassifier] - nikdy nepřebíjí jasnější textové
 * signály (WHISPER/THOUGHT z interpunkce), jen doplňuje odhad SHOUT, který dřív spoléhal
 * čistě na VELKÁ PÍSMENA + "!" v textu.
 */
fun isJaggedShape(shape: List<BubbleShapePoint>): Boolean {
    if (shape.size < 4) return false
    val widths = shape.map { it.rightF - it.leftF }
    val avgWidth = widths.average().toFloat()
    if (avgWidth <= 0.0001f) return false
    val avgStepChange = (1 until widths.size).map { i -> abs(widths[i] - widths[i - 1]) }.average().toFloat()
    return (avgStepChange / avgWidth) > JAGGED_THRESHOLD
}
