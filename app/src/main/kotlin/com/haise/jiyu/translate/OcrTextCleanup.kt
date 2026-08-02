package com.haise.jiyu.translate

/**
 * Spojí slovo, které lettering rozdělil na konci řádku pomlčkou.
 *
 * Nahlášený případ: bublina `EVERY-` / `ONE DON'T SCATTER, STAY TOGETHER!` dorazila k modelu
 * jako `EVERY- ONE DON'T SCATTER...`, tedy s rozsypaným začátkem věty - a překlad z ní vyšel
 * `VŠICHNI SE ROZPTÝLEJTE, ZŮSTÁVEJTE SPOLU!`, což si odporuje samo v sobě.
 *
 * Pomlčka se ZÁMĚRNĚ NEMAŽE, jen se odstraní zalomení za ní. Rozdíl mezi dělením slova
 * (`EVERY-` + `ONE`) a skutečným spojovníkem (`well-` + `known`) z textu nepoznáme, ale
 * ponechání pomlčky je správně v obou případech: `EVERY-ONE` model přečte bez potíží a
 * `well-known` zůstane přesně tím, čím je. Smazat pomlčku by druhý případ rozbilo.
 */
fun joinHyphenatedLineBreaks(text: String): String =
    HYPHEN_AT_LINE_END.replace(text) { m -> m.groupValues[1] + "-" }

/** Písmeno, pomlčka, konec řádku (a případné mezery), následované písmenem. */
private val HYPHEN_AT_LINE_END = Regex("(\\p{L})-[ \\t]*\\r?\\n[ \\t]*(?=\\p{L})")
