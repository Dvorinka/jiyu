package com.haise.jiyu.update

/**
 * Časování rozkvětu skleněného květu (viz [UpdateProgressOverlay]) - kdy se která vrstva
 * plátků otevírá vzhledem k celkovému postupu stahování.
 *
 * Kdyby se všechny vrstvy otevíraly současně, květ působí jako jedna plochá "hvězdička",
 * co se jen zvětšuje. Postupné otevírání (zadní vrstva první, přední poslední) je to, co
 * dělá dojem skutečného rozkvétání a hloubky - proto stagger, ne jedna společná hodnota.
 *
 * Odděleno od Compose kvůli JVM testovatelnosti (BloomScheduleTest) - má netriviální
 * hraniční podmínky (clamp postupu, poslední vrstva musí dojet přesně na 1.0).
 */
internal object BloomSchedule {

    /**
     * Ani při nulovém postupu není květ úplně zavřený - zůstává poupě. Plátky složené
     * do nulové délky by zmizely a zbylo by jen svítící jádro bez tvaru.
     */
    const val MIN_OPENNESS = 0.16f

    /** Jak velkou část celkového postupu zabere otevření JEDNÉ vrstvy (zbytek je stagger offset). */
    private const val LAYER_SPAN = 0.6f

    /**
     * @return otevřenost vrstvy [layerIndex] v rozsahu [MIN_OPENNESS]..1f.
     *   Vrstva 0 (zadní, největší plátky) začíná hned, poslední vrstva dojede přesně
     *   na 1.0 při [progress] == 1f.
     */
    fun layerOpenness(progress: Float, layerIndex: Int, layerCount: Int): Float {
        val p = progress.coerceIn(0f, 1f)
        val lastLayer = (layerCount - 1).coerceAtLeast(1)
        val idx = layerIndex.coerceIn(0, lastLayer)

        val start = (1f - LAYER_SPAN) * (idx.toFloat() / lastLayer)
        val t = ((p - start) / LAYER_SPAN).coerceIn(0f, 1f)

        // Cubic ease-out - plátek vyrazí rychle a doměkka dosedne, jako skutečné rozvíjení
        // (lineární průběh vypadá mechanicky, jako by se tvar jen škáloval).
        val eased = 1f - (1f - t) * (1f - t) * (1f - t)
        return MIN_OPENNESS + (1f - MIN_OPENNESS) * eased
    }
}
