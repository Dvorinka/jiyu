package com.haise.jiyu.ui.qr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Hlídá, že FileProvider pro sdílení QR kódu je opravdu deklarovaný v manifestu.
 *
 * Proč zrovna tohle: `MangaQrScreen.shareQr` si říká o authority
 * `"${context.packageName}.provider"`, jenže žádný takový provider v manifestu dlouho
 * nebyl. `getUriForFile` proto pokaždé vyhodilo IllegalArgumentException, kterou volající
 * spolkl prázdným catchem - tlačítko "Sdílet" tedy nedělalo vůbec nic a ani se neozvalo.
 * Chyba byla čistě v konfiguraci, takže ji žádný test nad Kotlinem chytit nemohl;
 * Robolectric ale běží nad SLOUČENÝM manifestem, takže na ni dosáhne.
 *
 * POZOR na rozsah: test schválně končí u registrace provideru a nejde až k
 * `FileProvider.getUriForFile`. Robolectric totiž z meta-dat `@xml/file_paths` nenačte
 * ANI JEDNU cestu - ověřeno tak, že ani `<root-path path="/">`, který by pasoval na
 * cokoliv, nestačil a volání pořád hlásilo "Failed to find configured root". Je to
 * omezení harness, ne chyba konfigurace; obsah `file_paths.xml` proto tenhle test
 * nepokrývá a jediný skutečný důkaz je zmáčknout Sdílet na telefonu.
 */
@RunWith(RobolectricTestRunner::class)
class QrShareFileProviderTest {

    @Test
    fun `the authority that shareQr asks for is actually declared`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Přesně ten výraz, který používá shareQr - kdyby se rozešel s manifestem, spadne to.
        val authority = "${context.packageName}.provider"

        val provider = context.packageManager.resolveContentProvider(authority, 0)

        assertNotNull("v manifestu chybí provider pro authority $authority", provider)
        assertEquals("androidx.core.content.FileProvider", provider!!.name)
    }
}
