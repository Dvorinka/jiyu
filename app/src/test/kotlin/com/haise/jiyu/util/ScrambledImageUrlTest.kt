package com.haise.jiyu.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrambledImageUrlTest {

    @Test
    fun `encode appends params with question mark when URL has no query string`() {
        val url = ScrambledImageUrl.encode("https://img.example.com/p/1.webp", grid = 10, seed = 3849681284L)
        assertEquals("https://img.example.com/p/1.webp?jiyu_descramble_grid=10&jiyu_descramble_seed=3849681284", url)
    }

    @Test
    fun `encode appends params with ampersand when URL already has a query string`() {
        val url = ScrambledImageUrl.encode("https://img.example.com/p/1.webp?v=2", grid = 4, seed = 7L)
        assertEquals("https://img.example.com/p/1.webp?v=2&jiyu_descramble_grid=4&jiyu_descramble_seed=7", url)
    }

    @Test
    fun `parse round-trips values encoded by encode`() {
        val url = ScrambledImageUrl.encode("https://img.example.com/p/1.webp", grid = 10, seed = 3849681284L)
        val params = ScrambledImageUrl.parse(url)
        assertEquals(ScrambledImageUrl.Params(10, 3849681284L), params)
    }

    @Test
    fun `parse returns null for a plain URL without scramble params`() {
        assertNull(ScrambledImageUrl.parse("https://img.example.com/p/1.webp"))
        assertNull(ScrambledImageUrl.parse("https://img.example.com/p/1.webp?v=2"))
    }

    @Test
    fun `parse ignores unrelated query params mixed in`() {
        val url = "https://img.example.com/p/1.webp?v=2&jiyu_descramble_grid=8&other=x&jiyu_descramble_seed=99"
        assertEquals(ScrambledImageUrl.Params(8, 99L), ScrambledImageUrl.parse(url))
    }
}
