package zed.rainxch.core.data.repository

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zed.rainxch.core.data.data_source.TokenStore
import zed.rainxch.core.domain.repository.AuthenticationState
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class AuthenticationStateImpl(
    private val tokenStore: TokenStore,
) : AuthenticationState {
    private val _sessionExpiredEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val sessionExpiredEvent: SharedFlow<Unit> = _sessionExpiredEvent.asSharedFlow()

    private val sessionExpiredMutex = Mutex()

    // 401 debounce state. We don't sign the user out on the first 401
    // anymore — a single hit can legitimately come from a stale request
    // that fired before the user finished signing in, an endpoint that
    // requires extra scope, or a transient GitHub edge. Wait for two
    // consecutive 401s under the same token within FAILURE_WINDOW_MS
    // before deciding the session is genuinely dead.
    private var failingTokenSnapshot: String? = null
    private var firstFailureAtMillis: Long = 0L
    private var consecutiveFailures: Int = 0

    override fun isUserLoggedIn(): Flow<Boolean> =
        tokenStore
            .tokenFlow()
            .map {
                it != null
            }

    override suspend fun isCurrentlyUserLoggedIn(): Boolean = tokenStore.currentToken() != null

    override suspend fun notifySessionExpired() {
        sessionExpiredMutex.withLock {
            val token = tokenStore.currentToken() ?: run {
                resetCounter()
                return@withLock
            }
            val tokenKey = token.accessToken
            val now = Clock.System.now().toEpochMilliseconds()

            if (tokenKey != failingTokenSnapshot ||
                now - firstFailureAtMillis > FAILURE_WINDOW_MS
            ) {
                // Either a re-auth happened (different token) or the
                // last 401 is too old to count as part of a streak.
                // Start a fresh window.
                failingTokenSnapshot = tokenKey
                firstFailureAtMillis = now
                consecutiveFailures = 1
            } else {
                consecutiveFailures += 1
            }

            if (consecutiveFailures < REQUIRED_CONSECUTIVE_FAILURES) {
                Logger.w(TAG) {
                    "notifySessionExpired: 401 count=$consecutiveFailures (need " +
                        "$REQUIRED_CONSECUTIVE_FAILURES); deferring sign-out"
                }
                return@withLock
            }

            Logger.w(TAG) {
                "notifySessionExpired: $consecutiveFailures consecutive 401s within " +
                    "window; clearing token"
            }
            tokenStore.clear()
            resetCounter()
            _sessionExpiredEvent.emit(Unit)
        }
    }

    private fun resetCounter() {
        failingTokenSnapshot = null
        firstFailureAtMillis = 0L
        consecutiveFailures = 0
    }

    private companion object {
        const val TAG = "AuthState"
        const val REQUIRED_CONSECUTIVE_FAILURES = 2
        const val FAILURE_WINDOW_MS = 60_000L
    }
}
