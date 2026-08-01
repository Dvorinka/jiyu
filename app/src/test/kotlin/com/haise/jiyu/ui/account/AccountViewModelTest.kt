package com.haise.jiyu.ui.account

import android.content.Context
import com.haise.jiyu.anilist.AniListRepository
import com.haise.jiyu.auth.AuthRepository
import com.haise.jiyu.sync.SyncRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Testy [AccountViewModel] - konkrétně resetu hesla.
 *
 * Proč zrovna tohle: neúspěšný reset dřív nastavoval `AuthUiState.Error` s textem
 * "Email pro reset odeslán (pokud účet existuje)". Obrazovka ten stav vykresluje přes
 * `Chyba: %1$s`, takže uživateli vyskočila věta, která si protiřečí sama se sebou -
 * a skutečná výjimka se zahodila. Kdo tedy resetoval heslo bez sítě, čekal na e-mail,
 * který nikdy neodešel. Testy drží obě větve odděleně.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var authRepository: AuthRepository
    private lateinit var syncRepository: SyncRepository
    private lateinit var aniListRepository: AniListRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepository = mockk(relaxed = true)
        syncRepository = mockk(relaxed = true)
        aniListRepository = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { authRepository.currentUser } returns flowOf(null)
        every { aniListRepository.isAuthenticated } returns flowOf(false)

        // Bez tohohle vrací relaxed mock z getString() prázdný řetězec a test "nesmí tvrdit,
        // že e-mail odešel" by prošel i nad rozbitým kódem - prostě proto, že by neměl co
        // porovnávat. Vrací se tu proto doslovný text, který v chybové větvi býval;
        // schválně ne přes R.string, ten resource už v projektu není.
        every { context.getString(any()) } returns "Email pro reset odeslán (pokud účet existuje)"
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        AccountViewModel(context, authRepository, syncRepository, aniListRepository)

    @Test
    fun `a successful reset reports done, not an error`() = runTest(dispatcher) {
        coEvery { authRepository.resetPassword(any()) } returns Unit

        val vm = viewModel()
        vm.sendPasswordReset("a@b.cz")
        advanceUntilIdle()

        assertEquals(AuthUiState.Done, vm.authState.value)
    }

    @Test
    fun `a failed reset must not claim the e-mail was sent`() = runTest(dispatcher) {
        // JÁDRO NAHLÁŠENÉ CHYBY: chybová větev vypisovala hlášku o úspěšném odeslání.
        coEvery { authRepository.resetPassword(any()) } throws IOException("connection reset")

        val vm = viewModel()
        vm.sendPasswordReset("a@b.cz")
        advanceUntilIdle()

        val state = vm.authState.value
        assertTrue("selhání musí skončit v Error, ne v Done", state is AuthUiState.Error)
        val message = (state as AuthUiState.Error).message
        assertTrue(
            "hláška nesmí tvrdit, že e-mail odešel - bylo v ní '$message'",
            !message.contains("odesl", ignoreCase = true),
        )
    }

    @Test
    fun `a failed reset explains the actual cause`() = runTest(dispatcher) {
        // Výpadek sítě je nejčastější důvod selhání, takže se uživatel musí dozvědět
        // právě tohle - ne obecné "něco se nepovedlo".
        coEvery { authRepository.resetPassword(any()) } throws IOException("connection reset")

        val vm = viewModel()
        vm.sendPasswordReset("a@b.cz")
        advanceUntilIdle()

        val message = (vm.authState.value as AuthUiState.Error).message
        assertTrue("čekala se zmínka o síti, bylo '$message'", message.contains("sít", ignoreCase = true))
    }
}
