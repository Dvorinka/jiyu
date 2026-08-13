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
        // Rozliseni od chybejiciho klice: org.json.JSONObject.put(String, Object) s null
        // hodnotou klic ODEBERE misto zapisu JSON null - proto musime overit i has(),
        // ne jen isNull() (ktere vraci true i pro chybejici klic).
        assertEquals(true, parsed.getJSONObject(0).has("slug"))
        assertEquals(true, parsed.getJSONObject(0).isNull("slug"))
    }

    @Test
    fun `deserializeChapterGroups returns empty list for null or blank input`() {
        assertEquals(emptyList<SGroup>(), deserializeChapterGroups(null))
        assertEquals(emptyList<SGroup>(), deserializeChapterGroups(""))
    }

    @Test
    fun `deserializeChapterGroups round-trips what serializeChapterGroups produced`() {
        val original = listOf(SGroup(name = "Asura", slug = "asura"), SGroup(name = "Official", slug = null))
        val json = serializeChapterGroups(original)
        assertEquals(original, deserializeChapterGroups(json))
    }

    @Test
    fun `deserializeChapterGroups returns empty list for malformed JSON instead of throwing`() {
        assertEquals(emptyList<SGroup>(), deserializeChapterGroups("not json"))
    }
}
