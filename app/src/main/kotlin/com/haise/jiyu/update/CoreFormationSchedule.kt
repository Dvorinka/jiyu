package com.haise.jiyu.update

import kotlin.math.pow

/**
 * Časování formování jádra (viz [UpdateProgressOverlay]) - jak se postup stahování promítá
 * do hustoty pole, jeho teploty a do závěrečného záblesku.
 *
 * ## Proč dva kanály
 * Postup nese HUSTOTA (rozptýlená mlha → husté jádro) i TEPLOTA (chladná fialová → bílý žár).
 * Kdyby teplota kopírovala hustotu, nenesla by žádnou informaci navíc a byla by to jen ozdoba.
 * Proto teplota záměrně zaostává: teplo vzniká až stlačením, takže první polovina stahování je
 * studená a jádro se rozžhaví teprve na konci. Vedlejší efekt je, že postup jde odhadnout
 * i ze zastaveného snímku - hustota říká hrubě kde jsi, teplota upřesňuje závěr.
 *
 * Odděleno od Compose i od shaderu kvůli JVM testovatelnosti ([CoreFormationScheduleTest]).
 * Samotné vykreslení otestovat nejde (AGSL běží až na GPU zařízení), o to důležitější je mít
 * pod testy aspoň tuhle část.
 */
internal object CoreFormationSchedule {

    /**
     * Postup, od kterého se pole začíná zahřívat. Do té doby jen sbírá čchi z okolí.
     */
    private const val HEAT_ONSET = 0.5f

    /** Jak daleko za okraj pole doletí rázový prstenec, než zhasne. */
    private const val SHOCK_OVERSHOOT = 1.15f

    /**
     * Postup, od kterého čchi začíná stoupat páteří k temeni. Záměrně až za [HEAT_ONSET]:
     * stoupat může teprve to, co se předtím v tantienu nashromáždilo a zahřálo.
     */
    private const val MERIDIAN_ONSET = 0.55f

    /**
     * @return hustota pole: 0f = rozptýlená mlha na začátku, 1f = plně zhuštěné jádro.
     *   Mírně akcelerující (exponent nad 1), protože stlačování se samo urychluje - ale jen
     *   mírně, aby zůstal postup čitelný i v první polovině.
     */
    fun density(progress: Float): Float =
        progress.coerceIn(0f, 1f).pow(1.25f)

    /**
     * @return teplota jádra: 0f = chladná fialová, 1f = bílý žár. Do [HEAT_ONSET] přesně nula -
     *   rozptýlené pole se nemá čím zahřát - pak kvadraticky nahoru.
     */
    fun heat(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val t = ((p - HEAT_ONSET) / (1f - HEAT_ONSET)).coerceIn(0f, 1f)
        return t * t
    }

    /**
     * @return jak daleko vystoupala čchi páteří: 0f = pořád jen v tantienu, 1f = dosáhla
     *   temene (bai-hui). Smoothstep, aby se linie nerozjela ani nezastavila trhnutím.
     */
    fun meridianReach(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val t = ((p - MERIDIAN_ONSET) / (1f - MERIDIAN_ONSET)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * @return poloměr rázového prstence po ztuhnutí, v jednotkách poloměru pole.
     *   [t] je 0f–1f průběh samotného záblesku (ne postup stahování). Zpomaluje - vlna vyrazí
     *   a doznívá, nekreslí se rovnoměrnou rychlostí jako animovaný kroužek.
     */
    fun flashRadius(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        val inv = 1f - x
        return SHOCK_OVERSHOOT * (1f - inv * inv)
    }

    /** @return krytí rázového prstence pro tentýž [t]; na konci přesně nula, aby po sobě nenechal stopu. */
    fun flashAlpha(t: Float): Float {
        val inv = 1f - t.coerceIn(0f, 1f)
        return inv * inv
    }
}
