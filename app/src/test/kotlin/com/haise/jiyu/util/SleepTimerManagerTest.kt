package com.haise.jiyu.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Testy [SleepTimerManager].
 *
 * Proč zrovna tohle: časovač dřív dostával callback `{ activity.finish() }`, který si
 * singleton držel po celou dobu odpočtu - a s ním i celou Activity. Po přechodu na
 * vysílanou událost je klíčové, aby se událost NEUKLÁDALA DO ZÁSOBY: kdyby si ji vyzvedla
 * až příští čtečka, zavřela by se sama od sebe hned po otevření. Přesně na to míří
 * poslední test.
 *
 * Odpočet stojí na `delay(1000)`, takže `runTest` ho odbaví ve virtuálním čase - hodinový
 * časovač se v testu odbaví okamžitě, žádné reálné čekání.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerManagerTest {

    @Test
    fun `a started timer counts down from the requested number of minutes`() = runTest {
        val manager = SleepTimerManager(StandardTestDispatcher(testScheduler))

        manager.start(2)
        advanceTimeBy(1)          // nechat spustit korutinu, ale netikat

        assertEquals(120, manager.remainingSeconds.value)

        advanceTimeBy(30_000)
        assertEquals(90, manager.remainingSeconds.value)
    }

    @Test
    fun `the timer reports the end to whoever is listening`() = runTest {
        val manager = SleepTimerManager(StandardTestDispatcher(testScheduler))
        var finishes = 0
        val listener = launch { manager.finished.collect { finishes++ } }
        advanceTimeBy(1)          // dát sběrateli šanci se přihlásit

        manager.start(1)
        advanceUntilIdle()

        assertEquals("odpočet měl skončit právě jednou", 1, finishes)
        assertNull("po doběhnutí se zbývající čas schovává", manager.remainingSeconds.value)
        listener.cancel()
    }

    @Test
    fun `cancelling stops the countdown and it never reports the end`() = runTest {
        val manager = SleepTimerManager(StandardTestDispatcher(testScheduler))
        var finishes = 0
        val listener = launch { manager.finished.collect { finishes++ } }
        advanceTimeBy(1)

        manager.start(1)
        advanceTimeBy(10_000)
        manager.cancel()
        advanceUntilIdle()

        assertEquals("zrušený časovač nesmí nic ohlásit", 0, finishes)
        assertNull(manager.remainingSeconds.value)
        listener.cancel()
    }

    @Test
    fun `starting again replaces the previous countdown instead of running two at once`() = runTest {
        val manager = SleepTimerManager(StandardTestDispatcher(testScheduler))
        var finishes = 0
        val listener = launch { manager.finished.collect { finishes++ } }
        advanceTimeBy(1)

        manager.start(5)
        advanceTimeBy(10_000)
        manager.start(1)          // uživatel si to rozmyslel
        advanceUntilIdle()

        assertEquals("dva souběžné odpočty by čtečku zavřely dvakrát", 1, finishes)
        listener.cancel()
    }

    @Test
    fun `an end with nobody listening is dropped, not kept for the next reader`() = runTest {
        // JÁDRO RIZIKA NOVÉHO NÁVRHU: kdyby MutableSharedFlow mělo buffer nebo replay,
        // událost by tu počkala a příští čtečka by se hned po otevření sama zavřela.
        val manager = SleepTimerManager(StandardTestDispatcher(testScheduler))

        manager.start(1)
        advanceUntilIdle()        // doběhne, když nikdo neposlouchá

        var finishes = 0
        val lateListener = launch { manager.finished.collect { finishes++ } }
        advanceUntilIdle()

        assertEquals("pozdní posluchač nesmí dostat starou událost", 0, finishes)
        lateListener.cancel()
    }
}
