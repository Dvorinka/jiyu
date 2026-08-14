package com.haise.jiyu.update

/**
 * Časování skládání krystalu (viz [UpdateProgressOverlay]) - kdy který vrchol doletí
 * z rozprášeného oblaku na své místo v ikosaedru.
 *
 * Kdyby všech dvanáct vrcholů letělo současně, celý tvar jen plynule zmenší poloměr a čte
 * se jako škálování, ne jako skládání. Stagger (první vrchol vyráží hned, poslední dosedá
 * přesně na konci) je to, co dělá dojem, že se těleso staví kus po kuse.
 *
 * Odděleno od Compose kvůli JVM testovatelnosti ([AssemblyScheduleTest]) - má netriviální
 * hraniční podmínky (clamp postupu, poslední vrchol musí dojet přesně na 1.0).
 */
internal object AssemblySchedule {

    /** Jak velkou část celkového postupu zabere přílet JEDNOHO vrcholu (zbytek je stagger offset). */
    private const val VERTEX_SPAN = 0.55f

    /**
     * Práh, od kterého se hrana začíná rozsvěcovat. Hrana kreslená dřív, než oba její konce
     * doopravdy dosednou, visí v prostoru mezi náhodnými body a čte se jako grafická chyba.
     */
    private const val EDGE_ONSET = 0.62f

    /**
     * @return jak moc je vrchol [index] na svém místě: 0f = pořád v rozprášeném oblaku,
     *   1f = přesně na svém vrcholu ikosaedru. Vrchol 0 vyráží hned, poslední dosedne
     *   přesně na 1.0 při [progress] == 1f.
     */
    fun vertexArrival(progress: Float, index: Int, count: Int): Float {
        val p = progress.coerceIn(0f, 1f)
        val last = (count - 1).coerceAtLeast(1)
        val idx = index.coerceIn(0, last)

        val start = (1f - VERTEX_SPAN) * (idx.toFloat() / last)
        val t = ((p - start) / VERTEX_SPAN).coerceIn(0f, 1f)

        // Cubic ease-out - vrchol vyrazí rychle a doměkka dosedne. Žádný overshoot: u
        // ukazatele postupu má pohyb působit pravdivě, ne hravě.
        return 1f - (1f - t) * (1f - t) * (1f - t)
    }

    /**
     * @return jak silně svítí hrana, jejíž konce dorazily z [a] a [b] procent. Řídí se tím
     *   slabším z obou konců - hrana existuje teprve tehdy, když existují OBA její vrcholy.
     */
    fun edgeStrength(a: Float, b: Float): Float {
        val weaker = minOf(a, b)
        return ((weaker - EDGE_ONSET) / (1f - EDGE_ONSET)).coerceIn(0f, 1f)
    }
}
