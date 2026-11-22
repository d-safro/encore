package dev.safro.encore.wear.net

import dev.safro.encore.wear.data.Account
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerRouterTest {
    private val main = "https://music.example.com"
    private val home = "http://music.home.example.com:4533"
    private var account: Account? = Account(main, "alice", "pw", localAddress = home)
    private var net = NetKind.WIFI
    private var homeAnswers = true
    private var now = 0L
    private val probes = mutableListOf<String>()

    private val router = ServerRouter(
        account = { account },
        network = { net },
        probe = { address, _ -> probes += address; homeAnswers },
        clock = { now },
        recheckMs = 30_000,
    )

    @Test fun homeAddressWhileItAnswers() = runTest {
        assertEquals(home, router.base())
        assertTrue(router.onHomeAddress)
    }

    @Test fun mainAddressWhenHomeDoesNotAnswer() = runTest {
        homeAnswers = false
        assertEquals(main, router.base())
        assertFalse(router.onHomeAddress)
    }

    @Test fun noHomeAddressMeansNoCheck() = runTest {
        account = account!!.copy(localAddress = "")
        assertEquals(main, router.base())
        assertTrue(probes.isEmpty())
    }

    @Test fun mobileDataNeverTriesHome() = runTest {
        net = NetKind.CELLULAR
        assertEquals(main, router.base())
        assertTrue(probes.isEmpty())
        net = NetKind.NONE
        assertEquals(main, router.base())
    }

    @Test fun throughThePhoneTriesHome() = runTest {
        net = NetKind.PHONE // the phone may well be on the home Wi-Fi
        assertEquals(home, router.base())
    }

    @Test fun checksAtMostEvery30Seconds() = runTest {
        router.base(); router.base()
        now = 29_999
        router.base()
        assertEquals(1, probes.size)
        now = 30_000
        router.base()
        assertEquals(2, probes.size)
    }

    @Test fun oneMissIsNotEnoughToLeaveHome() = runTest {
        assertEquals(home, router.base())
        homeAnswers = false
        now += 30_000
        assertEquals(home, router.base()) // busy network, maybe
        now += 30_000
        assertEquals(main, router.base()) // two in a row: away from home
        homeAnswers = true
        now += 30_000
        assertEquals(home, router.base()) // back
    }

    @Test fun aNewNetworkLooksAgainAtOnce() = runTest {
        router.base()
        homeAnswers = false
        router.networkChanged()
        assertEquals(main, router.base()) // a fresh start: no benefit of the doubt from the last network
        assertEquals(2, probes.size)
        net = NetKind.WIFI
    }

    @Test fun anotherAccountLooksAgain() = runTest {
        router.base()
        account = account!!.copy(localAddress = "http://other.home.example.com")
        assertEquals("http://other.home.example.com", router.base())
        assertEquals(2, probes.size)
    }

    @Test fun homeAddressWithoutSchemeIsHttps() = runTest {
        account = account!!.copy(localAddress = "lan.example.com:4533/")
        assertEquals("https://lan.example.com:4533", router.base())
    }
}
