package com.haise.jiyu

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ověřuje české tvary počtů.
 *
 * Proč zrovna tohle: appka neměla ani jeden `<plurals>`, přestože čeština má u počtů tři
 * tvary (1 / 2-4 / 5+). Všude se tedy používal jeden pevný tvar a v UI se objevovaly
 * nesmysly typu „1 kapitol", „3 kapitol" nebo „Přidat 1 mang do kategorie". Chyba je
 * čistě v resources, takže na ni dosáhne jedině test, který si je nechá vyhodnotit.
 *
 * Zaměřeno na češtinu - ta je nejnáročnější a je zároveň výchozím jazykem appky.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "cs")
class CzechPluralsTest {

    private val res get() = ApplicationProvider.getApplicationContext<Context>().resources

    private fun quantity(id: Int, n: Int) = res.getQuantityString(id, n, n)

    @Test
    fun `chapter counts decline correctly`() {
        assertEquals("1 kapitola", quantity(R.plurals.detail_chapter_count, 1))
        assertEquals("3 kapitoly", quantity(R.plurals.detail_chapter_count, 3))
        assertEquals("5 kapitol", quantity(R.plurals.detail_chapter_count, 5))
        assertEquals("0 kapitol", quantity(R.plurals.detail_chapter_count, 0))
    }

    @Test
    fun `title counts decline correctly`() {
        assertEquals("1 titul", quantity(R.plurals.mylist_title_count, 1))
        assertEquals("2 tituly", quantity(R.plurals.mylist_title_count, 2))
        assertEquals("11 titulů", quantity(R.plurals.mylist_title_count, 11))
    }

    @Test
    fun `adding manga to a category reads naturally for every count`() {
        // Puvodne "Pridat 1 mang do kategorie" - nejvic do oci bijici pripad.
        assertEquals("Přidat 1 mangu do kategorie", quantity(R.plurals.mylist_add_n_to_category, 1))
        assertEquals("Přidat 4 mangy do kategorie", quantity(R.plurals.mylist_add_n_to_category, 4))
        assertEquals("Přidat 8 mang do kategorie", quantity(R.plurals.mylist_add_n_to_category, 8))
    }

    @Test
    fun `search result counts decline correctly`() {
        assertEquals("1 výsledek", quantity(R.plurals.search_result_count, 1))
        assertEquals("3 výsledky", quantity(R.plurals.search_result_count, 3))
        assertEquals("9 výsledků", quantity(R.plurals.search_result_count, 9))
    }

    @Test
    fun `page counts in the translation cache decline correctly`() {
        assertEquals("Uložené překlady: 1 stránka", quantity(R.plurals.settings_storage_translation_cache_count, 1))
        assertEquals("Uložené překlady: 2 stránky", quantity(R.plurals.settings_storage_translation_cache_count, 2))
        assertEquals("Uložené překlady: 5 stránek", quantity(R.plurals.settings_storage_translation_cache_count, 5))
    }

    @Test
    fun `the verb agrees with the count when groups are found`() {
        assertEquals("1 skupina nalezena", quantity(R.plurals.duplicates_groups_found, 1))
        assertEquals("2 skupiny nalezeny", quantity(R.plurals.duplicates_groups_found, 2))
        assertEquals("7 skupin nalezeno", quantity(R.plurals.duplicates_groups_found, 7))
    }

    @Test
    fun `the restore message composes two separately declined counts`() {
        // Jedina veta se dvema RUZNYMI pocty - plurals umi vybirat jen podle jednoho,
        // takze se sklada ze dvou fragmentu.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manga = quantity(R.plurals.backup_restored_manga, 1)
        val chapters = quantity(R.plurals.backup_restored_chapters, 3)

        assertEquals(
            "Obnoveno: 1 manga, 3 kapitoly",
            context.getString(R.string.settings_backup_import_success, manga, chapters),
        )
    }

    @Test
    fun `a two-argument plural still picks its form by the count, not the name`() {
        // U source_browse_dup_existing je pocet az DRUHY argument - snadno se splete.
        assertEquals(
            "• MangaDex (v knihovně): 1 kapitola",
            res.getQuantityString(R.plurals.source_browse_dup_existing, 1, "MangaDex", 1),
        )
        assertEquals(
            "• MangaDex (v knihovně): 6 kapitol",
            res.getQuantityString(R.plurals.source_browse_dup_existing, 6, "MangaDex", 6),
        )
    }
}
