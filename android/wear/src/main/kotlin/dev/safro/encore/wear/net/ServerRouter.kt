package dev.safro.encore.wear.net

import dev.safro.encore.wear.data.Account
import dev.safro.encore.wear.subsonic.SubsonicException
import dev.safro.encore.wear.subsonic.SubsonicUrls
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How the watch is online right now. */
enum class NetKind {
    NONE,
    WIFI,
    /** Through the phone over Bluetooth: the phone's own network, often the home Wi-Fi. */
    PHONE,
    CELLULAR,
    OTHER;

    /** Where the server's home-network address is worth trying (as on the phone: not on mobile data). */
    val mayBeHome get() = this == WIFI || this == PHONE || this == OTHER
}

/**
 * Which address to use, the phone's way: the server's home-network address (setting "localAddress") while it
 * answers there, else the main address. The home address is checked at most every [recheckMs], again as soon
 * as the network changes, and one unanswered check while it was answering isn't enough to switch away (the
 * network may just be busy).
 */
class ServerRouter(
    private val account: () -> Account?,
    private val network: () -> NetKind,
    /** Does the server answer at this address with this login? (a ping with a short timeout) */
    private val probe: suspend (address: String, account: Account) -> Boolean,
    private val clock: () -> Long,
    private val recheckMs: Long = 30_000,
) {
    private val lock = Mutex()
    @Volatile private var checkedAt = Long.MIN_VALUE
    @Volatile private var checkedFor: Triple<String, String, NetKind>? = null
    private var homeAnswers = false
    private var misses = 0

    /** Using the home-network address now (as far as the last check knows). */
    val onHomeAddress get() = homeAnswers

    suspend fun base(): String {
        val a = account() ?: throw SubsonicException(10, "Not signed in")
        val main = SubsonicUrls.normalize(a.server)
        val home = SubsonicUrls.normalize(a.localAddress)
        val net = network()
        if (home.isEmpty() || !net.mayBeHome) return main
        return lock.withLock {
            val key = Triple(home, a.user, net)
            if (key != checkedFor) { // another address, login or network: look again from scratch
                checkedFor = key
                checkedAt = Long.MIN_VALUE
                homeAnswers = false
                misses = 0
            }
            val now = clock()
            if (checkedAt == Long.MIN_VALUE || now - checkedAt >= recheckMs) {
                val ok = probe(home, a)
                checkedAt = clock()
                misses = if (ok) 0 else misses + 1
                homeAnswers = ok || (homeAnswers && misses < 2)
            }
            if (homeAnswers) home else main
        }
    }

    /** The network changed: check again on the next request. */
    fun networkChanged() {
        checkedFor = null
    }

    /** A request at the home address just failed outright: count it as a miss and check next time. */
    fun homeFailed() {
        checkedAt = Long.MIN_VALUE
    }
}
