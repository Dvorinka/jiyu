package com.haise.jiyu.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Odpočet do konce čtení. Když doběhne, ohlásí to přes [finished].
 *
 * Dřív se místo toho předávala do [start] lambda `onFinish`, kterou čtečka volala jako
 * `{ activity.finish() }`. Jenže tahle třída je `@Singleton` a odpočet běží ve vlastním
 * scope, který nikdo neváže na životní cyklus - takže si singleton po celou dobu odpočtu
 * držel referenci na MainActivity. Kdo si pustil hodinový časovač a odešel ze čtečky,
 * nechal za sebou viset celou Activity; a po jejím znovuvytvoření (třeba otočením
 * displeje) mířil `finish()` na zahozenou instanci, takže časovač tiše přestal fungovat.
 *
 * Událost se proto jen vysílá a poslouchá si ji ten, kdo zrovna žije. [MutableSharedFlow]
 * je tu záměrně BEZ bufferu a bez replay: když v tu chvíli nikdo neposlouchá, událost
 * zmizí. To je správně - není co ukončovat, když uživatel ve čtečce není. S bufferem by
 * si ji vyzvedla až příští čtečka a bez varování by se sama zavřela.
 */
class SleepTimerManager(
    /**
     * Vyměnitelný jen kvůli testům - ty odpočet přetáčejí ve virtuálním čase, takže
     * hodinový časovač doběhne okamžitě. V appce zůstává [Dispatchers.Default];
     * instanci vyrábí `AppModule.provideSleepTimerManager`, proto tu není `@Inject`.
     */
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private var timerJob: Job? = null

    private val _remainingSeconds = MutableStateFlow<Int?>(null)
    val remainingSeconds: StateFlow<Int?> = _remainingSeconds.asStateFlow()

    private val _finished = MutableSharedFlow<Unit>()
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    fun start(minutes: Int) {
        timerJob?.cancel()
        timerJob = scope.launch {
            var remaining = minutes * 60
            _remainingSeconds.value = remaining
            while (remaining > 0) {
                delay(1000)
                remaining--
                _remainingSeconds.value = remaining
            }
            _remainingSeconds.value = null
            // Musí to být `emit`, ne `tryEmit`: při nulovém bufferu nemá tryEmit kam odložit
            // hodnotu, takže by vždycky vrátil false a událost by se zahodila i ve chvíli,
            // kdy čtečka poslouchá. `emit` bez odběratelů rovnou propadne (replay je 0),
            // s odběratelem počká, než si ji převezme.
            _finished.emit(Unit)
        }
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        _remainingSeconds.value = null
    }
}
