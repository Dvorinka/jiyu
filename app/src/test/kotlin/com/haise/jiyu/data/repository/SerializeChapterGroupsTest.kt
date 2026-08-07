package com.haise.jiyu.data.repository

import com.haise.jiyu.source.SGroup
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SerializeChapterGroupsTest {

    @Test
    fun `empty groups list serializes to null, not an empty JSON array`() {
        assertNull(serializeChapterGroups(emptyList()))
    }

    @Test
    fun `groups with a slug round-trip through JSON`() {
        val json = serializeChapterGroups(listOf(SGroup(name = "Asura", slug = "asura")))!!
        val parsed = JSONArray(json)
        assertEquals(1, parsed.length())
        assertEquals("Asura", parsed.getJSONObject(0).getString("name"))
        assertEquals("asura", parsed.getJSONObject(0).getString("slug"))
    }

    @Test
    fun `a group without a slug serializes slug as JSON null`() {
        val json = serializeChapterGroups(listOf(SGroup(name = "Official", slug = null)))!!
        val parsed = JSONArray(json)
        assertEquals(true, parsed.getJSONObject(0).isNull("slug"))
    }
}
