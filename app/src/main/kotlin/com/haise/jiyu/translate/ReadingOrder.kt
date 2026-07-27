package com.haise.jiyu.translate

/**
 * Seřadí OCR bloky do skutečného pořadí čtení - shora dolů po "řádcích" bublin
 * (bublinách na podobné výšce), uvnitř řádku ZPRAVA DOLEVA pro japonštinu (tradiční
 * manga se čte pravo-levě panel po panelu), jinak ZLEVA DOPRAVA (manhwa/manhua/webtoon
 * mají obvykle západní - LTR - rozvržení).
 *
 * Bez tohohle [OcrEngine.mergeNearbyLines] vracel bloky v podstatě v náhodném pořadí
 * (podle interního indexu union-find skupiny, ne podle skutečné pozice na stránce) -
 * [GeminiUltraPrompt] pak dostával repliky v jiném pořadí, než v jakém je uvidí čtenář,
 * což kazilo návaznost dialogu a konzistenci zájmen v překladu.
 *
 * Řádky se detekují jednoduchým chamtivým 1D shlukováním podle svislého překryvu (žádná
 * skutečná detekce hranic panelů) - u diagonálně navazujících bublin nebo neobvyklého
 * rozvržení nemusí být dokonalé, ale je to podstatně blíž skutečnému čtecímu pořadí než
 * předchozí prakticky nahodilé pořadí.
 */
fun sortIntoReadingOrder(blocks: List<RawTextBlock>, rightToLeft: Boolean): List<RawTextBlock> {
    if (blocks.size <= 1) return blocks
    val sorted = blocks.sortedBy { it.topF }

    val rows = mutableListOf<MutableList<RawTextBlock>>()
    var rowBottom = Float.NEGATIVE_INFINITY
    for (block in sorted) {
        if (rows.isEmpty() || block.topF >= rowBottom) {
            rows += mutableListOf(block)
            rowBottom = block.bottomF
        } else {
            rows.last() += block
            rowBottom = maxOf(rowBottom, block.bottomF)
        }
    }

    return rows.flatMap { row -> if (rightToLeft) row.sortedByDescending { it.leftF } else row.sortedBy { it.leftF } }
}
