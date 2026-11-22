package dev.safro.encore.wear.data

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.safro.encore.wearlink.AccountInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The server the watch talks to. Setting names are the phone's: server, user, pass, localAddress. */
data class Account(
    val server: String,
    val user: String,
    val pass: String,
    val localAddress: String = "",
    /** true: it came from the phone (and follows the phone's sign-in); false: typed on the watch. */
    val fromPhone: Boolean = false,
) {
    override fun toString() = "Account(server=$server, user=$user, fromPhone=$fromPhone)" // never the password
}

/**
 * The watch's sign-in, in the app's private storage (only Encore can read it; the watch's storage is
 * encrypted by the system).
 */
class AccountStore(private val prefs: SharedPreferences) {
    private val _account = MutableStateFlow(read())
    val account: StateFlow<Account?> = _account.asStateFlow()

    val current: Account? get() = _account.value

    private fun read(): Account? {
        val server = prefs.getString(SERVER, null) ?: return null
        val user = prefs.getString(USER, null) ?: return null
        return Account(server, user, prefs.getString(PASS, "") ?: "", prefs.getString(LOCAL, "") ?: "", prefs.getBoolean(FROM_PHONE, false))
    }

    fun save(a: Account) {
        prefs.edit {
            putString(SERVER, a.server)
            putString(USER, a.user)
            putString(PASS, a.pass)
            putString(LOCAL, a.localAddress)
            putBoolean(FROM_PHONE, a.fromPhone)
        }
        _account.value = a
    }

    /**
     * The phone's sign-in arrived (first pairing, or the phone switched server). Taken when the watch has
     * none, or when the one it has came from the phone too; one typed on the watch is kept.
     * Returns true if it was taken.
     */
    fun offerFromPhone(info: AccountInfo): Boolean {
        val cur = current
        if (cur != null && !cur.fromPhone) return false
        val a = Account(info.server, info.user, info.pass, info.localAddress, fromPhone = true)
        if (a == cur) return false
        save(a)
        return true
    }

    /** The phone signed out: a sign-in copied from it goes too (password included); one typed here stays. */
    fun phoneSignedOut(): Boolean {
        if (current?.fromPhone != true) return false
        signOut()
        return true
    }

    fun setLocalAddress(address: String) {
        val a = current ?: return
        save(a.copy(localAddress = address.trim().trimEnd('/')))
    }

    fun signOut() {
        prefs.edit { remove(SERVER); remove(USER); remove(PASS); remove(LOCAL); remove(FROM_PHONE) }
        _account.value = null
    }

    companion object {
        const val SERVER = "server"
        const val USER = "user"
        const val PASS = "pass"
        const val LOCAL = "localAddress"
        const val FROM_PHONE = "accountFromPhone"
    }
}
