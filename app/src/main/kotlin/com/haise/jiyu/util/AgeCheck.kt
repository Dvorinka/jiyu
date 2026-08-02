package com.haise.jiyu.util

import java.time.LocalDate

/** Věk, od kterého se zpřístupní zdroje s obsahem pro dospělé. */
const val ADULT_AGE_YEARS = 18

/**
 * Dosáhl někdo narozený [birthDate] k datu [today] věku [ADULT_AGE_YEARS]?
 *
 * Proč to má vlastní funkci a testy: "dnešní rok minus rok narození" je klasická chyba -
 * kdo se narodil 31. 12. 2008, není 1. 1. 2026 plnoletý, ale takový výpočet tvrdí, že ano.
 * Rozhoduje celý datum, ne jen rok, a hranice je den narozenin (ten den už plnoletý je).
 *
 * Samotné datum narození appka NIKAM NEUKLÁDÁ - viz [SettingsKeys.IS_ADULT]. Z odpovědi si
 * nechá jen tenhle boolean a datum zahodí; k ničemu jinému ho nepotřebuje a ukládat cizí
 * datum narození v kroku, který má být o ochraně osobních údajů, by bylo obrácené naruby.
 */
fun isAdultOn(birthDate: LocalDate, today: LocalDate): Boolean =
    !birthDate.plusYears(ADULT_AGE_YEARS.toLong()).isAfter(today)

/**
 * Je zadané datum narození vůbec použitelné? Odmítne budoucnost (překlep v roce) a nesmyslně
 * dávnou minulost. Bez toho by "3025" prošlo jako "ještě mu není 18" a uživatel by netušil,
 * proč mu appka nic neodemkla.
 */
fun isPlausibleBirthDate(birthDate: LocalDate, today: LocalDate): Boolean =
    !birthDate.isAfter(today) && birthDate.isAfter(today.minusYears(120))
