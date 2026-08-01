package com.haise.jiyu

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Runner pro instrumentované testy.
 *
 * Bez něj se `androidTest` NESPUSTÍ vůbec: `build.gradle.kts` žádný
 * `testInstrumentationRunner` nedeklaroval, takže se použil dávno zastaralý výchozí
 * (`android.test.InstrumentationTestRunner`), který AndroidX testy neumí. Dva existující
 * testy (LibraryScreenTest, ReaderSmokeTest) tak byly celou dobu mrtvé - stejný případ
 * jako kdysi nedosažitelná obrazovka Statistik: kód existoval, jen ho nic nespouštělo.
 *
 * Navíc jsou označené `@HiltAndroidTest`, což vyžaduje aplikaci [HiltTestApplication]
 * místo běžné `JiyuApp` - a tu jde podstrčit jedině odsud.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
