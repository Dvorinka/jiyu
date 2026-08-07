package com.haise.jiyu.source.comick

import com.haise.jiyu.settings.FakeDataStore
import com.haise.jiyu.settings.SettingsRepository
import com.haise.jiyu.source.MangaFilter
import com.haise.jiyu.source.MangaSource
import com.haise.jiyu.source.SChapter
import com.haise.jiyu.source.SManga
import com.haise.jiyu.source.SourceManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeSource(
    override val id: String,
    override val name: String,
    override val contentType: String,
    private val searchResults: List<SManga> = emptyList(),
    private val chapters: List<SChapter> = emptyList(),
    private val failSearch: Boolean = false,
) : MangaSource {
    override suspend fun search(query: String, page: Int, filter: MangaFilter) =
        if (failSearch) throw RuntimeException("boom") else searchResults
    override suspend fun getPopular(page: Int, filter: MangaFilter) = emptyList<SManga>()
    override suspend fun getMangaDetails(manga: SManga) = manga
    override suspend fun getChapterList(manga: SManga) = chapters
    override suspend fun getPageList(chapter: SChapter) = emptyList<com.haise.jiyu.source.Page>()
}

class ComicKChapterResolverTest {

    private lateinit var sourceManager: SourceManager
    private lateinit var settings: SettingsRepository
    private lateinit var comicKSource: ComicKSource
    private lateinit var resolver: ComicKChapterResolver

    @Before
    fun setUp() {
        sourceManager = mockk()
        settings = SettingsRepository(FakeDataStore())
        comicKSource = mockk()
        // Výchozí: žádné alternativní názvy - findCandidates spadne zpátky na comicKTitle
        // samotný, což zachovává chování testů psaných před zavedením alt. názvů.
        coEvery { comicKSource.getAlternateTitles(any()) } returns emptyList()
        resolver = ComicKChapterResolver(sourceManager, settings, comicKSource)
    }

    @Test
    fun `only searches sources in the same content-type group as the ComicK title`() = runTest {
        val manhwaMatch = SManga(sourceId = "src-manhwa", url = "u1", title = "Solo Leveling", coverUrl = null)
        val manhwaSource = FakeSource("src-manhwa", "Manhwa Site", "MANHWA", searchResults = listOf(manhwaMatch), chapters = listOf(chapter(1f)))
        val novelSource = FakeSource("src-novel", "Novel Site", "NOVEL", searchResults = listOf(manhwaMatch.copy(sourceId = "src-novel")))
        coEvery { sourceManager.getAll() } returns listOf(manhwaSource, novelSource)

        val result = resolver.findCandidates("comick-id-1", "u1", "Solo Leveling", "MANHWA", requestedChapterNumber = null)

        assertEquals(1, result.size)
        assertEquals("src-manhwa", result[0].source.id)
    }

    @Test
    fun `manga, manhwa and manhua sources are all treated as the same group`() = runTest {
        val match = SManga(sourceId = "src-manga", url = "u1", title = "Solo Leveling", coverUrl = null)
        val mangaSource = FakeSource("src-manga", "Manga Site", "MANGA", searchResults = listOf(match), chapters = listOf(chapter(1f)))
        coEvery { sourceManager.getAll() } returns listOf(mangaSource)

        // ComicK title is MANHWA, candidate source is generically tagged MANGA - must still match.
        val result = resolver.findCandidates("comick-id-2", "u1", "Solo Leveling", "MANHWA", requestedChapterNumber = null)

        assertEquals(1, result.size)
    }

    @Test
    fun `only keeps candidates whose normalized title matches`() = runTest {
        val wrongMatch = SManga(sourceId = "src-a", url = "u1", title = "A Completely Different Title", coverUrl = null)
        val source = FakeSource("src-a", "Site A", "MANHWA", searchResults = listOf(wrongMatch))
        coEvery { sourceManager.getAll() } returns listOf(source)

        val result = resolver.findCandidates("comick-id-3", "u1", "Solo Leveling", "MANHWA", requestedChapterNumber = null)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a source whose search throws is skipped, not propagated`() = runTest {
        val failing = FakeSource("src-fail", "Broken Site", "MANHWA", failSearch = true)
        coEvery { sourceManager.getAll() } returns listOf(failing)

        val result = resolver.findCandidates("comick-id-4", "u1", "Solo Leveling", "MANHWA", requestedChapterNumber = null)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `hasRequestedChapter is true when a candidate's chapter list contains a matching chapter number`() = runTest {
        val match = SManga(sourceId = "src-a", url = "u1", title = "Solo Leveling", coverUrl = null)
        val source = FakeSource("src-a", "Site A", "MANHWA", searchResults = listOf(match), chapters = listOf(chapter(1f), chapter(5f), chapter(5.5f)))
        coEvery { sourceManager.getAll() } returns listOf(source)

        val result = resolver.findCandidates("comick-id-5", "u1", "Solo Leveling", "MANHWA", requestedChapterNumber = 5f)

        assertEquals(1, result.size)
        assertTrue(result[0].hasRequestedChapter)
        assertEquals(3, result[0].matchedChapterCount)
    }

    @Test
    fun `matchedChapterCount counts distinct chapter numbers, not one row per scanlation group`() = runTest {
        // ComicK (a i jiné zdroje) uklada kazdy preklad kapitoly zvlast - stejne cislo
        // kapitoly muze mit vic radku, kdyz ji prelozilo vic skupin. Pomer v UI ma
        // ukazovat "kolik ruznych kapitol zdroj ma", ne "kolik radku ma v databazi".
        val match = SManga(sourceId = "src-a", url = "u1", title = "Solo Leveling", coverUrl = null)
        val duplicated = listOf(chapter(1f), chapter(1f), chapter(2f), chapter(2f), chapter(2f), chapter(3f))
        val source = FakeSource("src-a", "Site A", "MANHWA", searchResults = listOf(match), chapters = duplicated)
        coEvery { sourceManager.getAll() } returns listOf(source)

        val result = resolver.findCandidates("comick-id-5b", "u1", "Solo Leveling", "MANHWA", requestedChapterNumber = null)

        assertEquals(3, result[0].matchedChapterCount)
    }

    @Test
    fun `hasRequestedChapter is false when no candidate chapter is close enough`() = runTest {
        val match = SManga(sourceId = "src-a", url = "u1", title = "Solo Leveling", coverUrl = null)
        val source = FakeSource("src-a", "Site A", "MANHWA", searchResults = listOf(match), chapters = listOf(chapter(1f), chapter(2f)))
        coEvery { sourceManager.getAll() } returns listOf(source)

        val result = resolver.findCandidates("comick-id-6", "u1", "Solo Leveling", "MANHWA", requestedChapterNumber = 99f)

        assertEquals(1, result.size)
        assertTrue(!result[0].hasRequestedChapter)
    }

    @Test
    fun `requestedChapterNumber null means hasRequestedChapter is always true`() = runTest {
        val match = SManga(sourceId = "src-a", url = "u1", title = "Solo Leveling", coverUrl = null)
        val source = FakeSource("src-a", "Site A", "MANHWA", searchResults = listOf(match), chapters = listOf(chapter(1f)))
        coEvery { sourceManager.getAll() } returns listOf(source)

        val result = resolver.findCandidates("comick-id-7", "u1", "Solo Leveling", "MANHWA", requestedChapterNumber = null)

        assertTrue(result[0].hasRequestedChapter)
    }

    @Test
    fun `favorite sources are marked and sorted first`() = runTest {
        settings.toggleFavoriteSource("src-b")
        val matchA = SManga(sourceId = "src-a", url = "u1", title = "Solo Leveling", coverUrl = null)
        val matchB = SManga(sourceId = "src-b", url = "u2", title = "Solo Leveling", coverUrl = null)
        val sourceA = FakeSource("src-a", "Site A", "MANHWA", searchResults = listOf(matchA), chapters = listOf(chapter(1f)))
        val sourceB = FakeSource("src-b", "Site B", "MANHWA", searchResults = listOf(matchB), chapters = listOf(chapter(1f)))
        coEvery { sourceManager.getAll() } returns listOf(sourceA, sourceB)

        val result = resolver.findCandidates("comick-id-8", "u1", "Solo Leveling", "MANHWA", requestedChapterNumber = null)

        assertEquals("src-b", result[0].source.id)
        assertTrue(result[0].isFavorite)
        assertTrue(!result[1].isFavorite)
    }

    @Test
    fun `a second call for the same comicKMangaId does not re-search or re-fetch chapters`() = runTest {
        val match = SManga(sourceId = "src-a", url = "u1", title = "Solo Leveling", coverUrl = null)
        var searchCalls = 0
        val source = object : MangaSource {
            override val id = "src-a"
            override val name = "Site A"
            override val contentType = "MANHWA"
            override suspend fun search(query: String, page: Int, filter: MangaFilter): List<SManga> {
                searchCalls++
                return listOf(match)
            }
            override suspend fun getPopular(page: Int, filter: MangaFilter) = emptyList<SManga>()
            override suspend fun getMangaDetails(manga: SManga) = manga
            override suspend fun getChapterList(manga: SManga) = listOf(chapter(1f))
            override suspend fun getPageList(chapter: SChapter) = emptyList<com.haise.jiyu.source.Page>()
        }
        coEvery { sourceManager.getAll() } returns listOf(source)

        resolver.findCandidates("comick-id-9", "u1", "Solo Leveling", "MANHWA", requestedChapterNumber = 1f)
        resolver.findCandidates("comick-id-9", "u1", "Solo Leveling", "MANHWA", requestedChapterNumber = 2f)

        assertEquals(1, searchCalls)
    }

    @Test
    fun `searches and matches using the ComicK default alt title when it differs from the stored title`() = runTest {
        // Presne situace, ktera zpusobovala "zadny zdroj to nema" i kdyz zdroj existoval:
        // ComicK titul je ulozeny pod "I am the only the one who levels up", ale zdroj
        // (napr. Asura) ho eviduje pod "Solo Leveling" - to je zrovna alt. nazev s
        // is_default=true, ktery getAlternateTitles vraci jako prvni.
        coEvery { comicKSource.getAlternateTitles("u1") } returns listOf("Solo Leveling", "I Alone Level-Up")
        val match = SManga(sourceId = "src-a", url = "u1", title = "Solo Leveling", coverUrl = null)
        var searchedWith: String? = null
        val source = object : MangaSource {
            override val id = "src-a"
            override val name = "Site A"
            override val contentType = "MANHWA"
            override suspend fun search(query: String, page: Int, filter: MangaFilter): List<SManga> {
                searchedWith = query
                return listOf(match)
            }
            override suspend fun getPopular(page: Int, filter: MangaFilter) = emptyList<SManga>()
            override suspend fun getMangaDetails(manga: SManga) = manga
            override suspend fun getChapterList(manga: SManga) = listOf(chapter(1f))
            override suspend fun getPageList(chapter: SChapter) = emptyList<com.haise.jiyu.source.Page>()
        }
        coEvery { sourceManager.getAll() } returns listOf(source)

        val result = resolver.findCandidates(
            "comick-id-10", "u1", "I am the only the one who levels up", "MANHWA", requestedChapterNumber = null,
        )

        assertEquals("Solo Leveling", searchedWith)
        assertEquals(1, result.size)
        assertEquals("src-a", result[0].source.id)
    }

    @Test
    fun `falls back to comicKTitle when fetching alternate titles fails`() = runTest {
        coEvery { comicKSource.getAlternateTitles("u1") } throws RuntimeException("network down")
        val match = SManga(sourceId = "src-a", url = "u1", title = "Solo Leveling", coverUrl = null)
        val source = FakeSource("src-a", "Site A", "MANHWA", searchResults = listOf(match), chapters = listOf(chapter(1f)))
        coEvery { sourceManager.getAll() } returns listOf(source)

        val result = resolver.findCandidates("comick-id-11", "u1", "Solo Leveling", "MANHWA", requestedChapterNumber = null)

        assertEquals(1, result.size)
    }

    private fun chapter(number: Float) = SChapter(
        sourceId = "x", mangaUrl = "u", url = "c/$number", name = "Ch.$number",
        chapterNumber = number, dateUpload = 0L,
    )
}
