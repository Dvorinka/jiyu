package com.haise.jiyu.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreFormationScheduleTest {

    // ── Hustota ──────────────────────────────────────────────────────────────

    @Test
    fun `at zero progress the field is fully diffuse`() {
        assertEquals(0f, CoreFormationSchedule.density(0f), 0.0001f)
    }

    @Test
    fun `at full progress the core is fully condensed`() {
        assertEquals(1f, CoreFormationSchedule.density(1f), 0.0001f)
    }

    @Test
    fun `density never goes backwards as progress grows`() {
        var previous = -1f
        var p = 0f
        while (p <= 1f) {
            val value = CoreFormationSchedule.density(p)
            assertTrue("hustota klesla na p=$p", value >= previous - 0.0001f)
            previous = value
            p += 0.02f
        }
    }

    @Test
    fun `density outside 0-1 is clamped instead of overshooting`() {
        assertEquals(0f, CoreFormationSchedule.density(-3f), 0.0001f)
        assertEquals(1f, CoreFormationSchedule.density(7f), 0.0001f)
    }

    // ── Teplota ──────────────────────────────────────────────────────────────

    @Test
    fun `temperature is cold at the start and white hot at the end`() {
        assertEquals(0f, CoreFormationSchedule.heat(0f), 0.0001f)
        assertEquals(1f, CoreFormationSchedule.heat(1f), 0.0001f)
    }

    @Test
    fun `field stays cold while it is still only gathering`() {
        // Teplo vznika stlacenim - dokud je pole rozptylene, nema se cim zahrat.
        assertEquals(0f, CoreFormationSchedule.heat(0.3f), 0.0001f)
        assertEquals(0f, CoreFormationSchedule.heat(0.5f), 0.0001f)
    }

    @Test
    fun `temperature lags density through the whole middle of the run`() {
        // Dva nezavisle kanaly postupu maji smysl jen tehdy, kdyz se lisi. Kdyby teplota
        // kopirovala hustotu, nenese zadnou informaci navic.
        var p = 0.1f
        while (p < 0.95f) {
            val d = CoreFormationSchedule.density(p)
            val h = CoreFormationSchedule.heat(p)
            assertTrue("teplota nezaostava za hustotou na p=$p (d=$d, h=$h)", h < d)
            p += 0.05f
        }
    }

    @Test
    fun `temperature never goes backwards as progress grows`() {
        var previous = -1f
        var p = 0f
        while (p <= 1f) {
            val value = CoreFormationSchedule.heat(p)
            assertTrue("teplota klesla na p=$p", value >= previous - 0.0001f)
            previous = value
            p += 0.02f
        }
    }

    @Test
    fun `temperature outside 0-1 is clamped instead of overshooting`() {
        assertEquals(0f, CoreFormationSchedule.heat(-2f), 0.0001f)
        assertEquals(1f, CoreFormationSchedule.heat(4f), 0.0001f)
    }

    // ── Meridian ─────────────────────────────────────────────────────────────

    @Test
    fun `qi has not risen at all at the start and reaches the crown at the end`() {
        assertEquals(0f, CoreFormationSchedule.meridianReach(0f), 0.0001f)
        assertEquals(1f, CoreFormationSchedule.meridianReach(1f), 0.0001f)
    }

    @Test
    fun `qi only starts rising once the core has begun to gather and heat`() {
        // Stoupat muze teprve to, co se predtim v tantienu nashromazdilo.
        assertEquals(0f, CoreFormationSchedule.meridianReach(0.4f), 0.0001f)
        assertEquals(0f, CoreFormationSchedule.meridianReach(0.55f), 0.0001f)
    }

    @Test
    fun `qi never flows back down`() {
        var previous = -1f
        var p = 0f
        while (p <= 1f) {
            val value = CoreFormationSchedule.meridianReach(p)
            assertTrue("meridian klesl na p=$p", value >= previous - 0.0001f)
            previous = value
            p += 0.02f
        }
    }

    @Test
    fun `meridian outside 0-1 is clamped instead of overshooting`() {
        assertEquals(0f, CoreFormationSchedule.meridianReach(-1f), 0.0001f)
        assertEquals(1f, CoreFormationSchedule.meridianReach(3f), 0.0001f)
    }

    // ── Zablesk pri ztuhnuti ────────────────────────────────────────────────

    @Test
    fun `shock ring starts at the core and expands past the edge`() {
        assertEquals(0f, CoreFormationSchedule.flashRadius(0f), 0.0001f)
        assertTrue("prstenec musi dojet za okraj pole", CoreFormationSchedule.flashRadius(1f) > 1f)
    }

    @Test
    fun `shock ring only ever expands`() {
        var previous = -1f
        var t = 0f
        while (t <= 1f) {
            val value = CoreFormationSchedule.flashRadius(t)
            assertTrue("prstenec se smrstil na t=$t", value >= previous - 0.0001f)
            previous = value
            t += 0.02f
        }
    }

    @Test
    fun `shock ring fades out completely`() {
        assertEquals(1f, CoreFormationSchedule.flashAlpha(0f), 0.0001f)
        assertEquals(0f, CoreFormationSchedule.flashAlpha(1f), 0.0001f)
    }

    @Test
    fun `shock ring never brightens again once it starts fading`() {
        var previous = 2f
        var t = 0f
        while (t <= 1f) {
            val value = CoreFormationSchedule.flashAlpha(t)
            assertTrue("prstenec se rozjasnil na t=$t", value <= previous + 0.0001f)
            previous = value
            t += 0.02f
        }
    }

    @Test
    fun `flash inputs outside 0-1 are clamped`() {
        assertEquals(0f, CoreFormationSchedule.flashRadius(-1f), 0.0001f)
        assertEquals(1f, CoreFormationSchedule.flashAlpha(-1f), 0.0001f)
        assertEquals(0f, CoreFormationSchedule.flashAlpha(9f), 0.0001f)
    }
}
