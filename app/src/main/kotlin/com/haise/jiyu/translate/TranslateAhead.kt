package com.haise.jiyu.translate

/**
 * Které kapitoly zařadit, když si uživatel řekne o překlad [count] kapitol dopředu.
 *
 * Čistá funkce s minimem vstupu (číslo, příznak přečteno, id), aby šla testovat bez Roomu
 * a bez ViewModelu - výběr je totiž to jediné, co se tu dá splést.
 *
 * Pravidla, a proč zrovna takhle:
 *  - Jen NEPŘEČTENÉ. "Dopředu" znamená kam se čtenář ještě nedostal; překládat znovu to, co
 *    má za sebou, by jen snědlo znakovou kvótu.
 *  - Seřazeno podle ČÍSLA kapitoly, ne podle pořadí v seznamu. Ten je na detailu běžně
 *    otočený (nejnovější nahoře), takže bez řazení by "5 kapitol dopředu" přeložilo pět
 *    NEJNOVĚJŠÍCH - přesný opak toho, co uživatel chce, když se chystá číst dál.
 *  - Nezáporný počet; nula i záporné číslo vrací prázdno místo výjimky.
 */
fun chaptersToTranslateAhead(chapters: List<TranslatableChapter>, count: Int): List<String> {
    if (count <= 0) return emptyList()
    return chapters
        .filter { !it.read }
        .sortedBy { it.number }
        .take(count)
        .map { it.id }
}

/** Minimum, které [chaptersToTranslateAhead] potřebuje vědět o kapitole. */
data class TranslatableChapter(
    val id: String,
    val number: Float,
    val read: Boolean,
)
