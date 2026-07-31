package com.haise.jiyu.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException

/**
 * Čistý JVM test rozhodovací logiky [ErrorReporter.isExpectedNoise] - samotné odeslání do
 * Crashlytics/logcatu otestovat nejde (potřebuje Android + Firebase), ale to, CO se za
 * chybu vůbec považuje, ano. A právě na tom záleží: kdyby sem propadl běžný síťový šum,
 * skutečné chyby se v dashboardu utopí a nástroj přestane být k něčemu.
 */
class ErrorReporterTest {

    @Test
    fun `cancelling a coroutine is not an error`() {
        // Uzivatel odlistoval pryc / ViewModel zanikl - normalni rizeni toku, ne defekt.
        assertTrue(ErrorReporter.isExpectedNoise(CancellationException("scope closed")))
    }

    @Test
    fun `network failures are treated as expected noise, not defects`() {
        // Appka leze na desitky cizich webu - vypadek je provozni stav, ne chyba v kodu.
        assertTrue(ErrorReporter.isExpectedNoise(UnknownHostException("no dns")))
        assertTrue(ErrorReporter.isExpectedNoise(SocketTimeoutException("timeout")))
        assertTrue(ErrorReporter.isExpectedNoise(InterruptedIOException("interrupted")))
        assertTrue(ErrorReporter.isExpectedNoise(IOException("socket closed")))
    }

    @Test
    fun `a genuine programming error is reportable`() {
        assertFalse(ErrorReporter.isExpectedNoise(IllegalStateException("bad state")))
        assertFalse(ErrorReporter.isExpectedNoise(NullPointerException()))
        assertFalse(ErrorReporter.isExpectedNoise(IndexOutOfBoundsException("idx 5")))
    }

    @Test
    fun `a malformed server response is reportable, not silently dropped`() {
        // Presne tenhle druh chyby se driv polykal: zdroj zmenil HTML/JSON, parsovani
        // spadlo, appka ukazala prazdny seznam a nikdo se nedozvedel proc.
        assertFalse(ErrorReporter.isExpectedNoise(org.json.JSONException("no value for id")))
        assertFalse(ErrorReporter.isExpectedNoise(NumberFormatException("not a number")))
    }
}
