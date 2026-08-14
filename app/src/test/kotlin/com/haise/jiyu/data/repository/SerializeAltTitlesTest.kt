package com.haise.jiyu.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class SerializeAltTitlesTest {

    @Test
    fun `deserializeAltTitles returns empty list for null or blank input`() {
        assertEquals(emptyList<String>(), deserializeAltTitles(null))
        assertEquals(emptyList<String>(), deserializeAltTitles(""))
    }

    @Test
    fun `deserializeAltTitles round-trips what serializeAltTitles produced`() {
        val original = listOf("Solo Leveling", "나 혼자만 레벨업", "I Alone Level-Up")
        val json = serializeAltTitles(original)
        assertEquals(original, deserializeAltTitles(json))
    }

    @Test
    fun `deserializeAltTitles returns empty list for malformed JSON instead of throwing`() {
        assertEquals(emptyList<String>(), deserializeAltTitles("not json"))
    }

    @Test
    fun `an empty list serializes to an empty JSON array, not null`() {
        assertEquals("[]", serializeAltTitles(emptyList()))
        assertEquals(emptyList<String>(), deserializeAltTitles(serializeAltTitles(emptyList())))
    }
}
