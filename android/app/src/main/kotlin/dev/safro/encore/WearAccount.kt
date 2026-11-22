package dev.safro.encore

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dev.safro.encore.wearlink.AccountInfo
import dev.safro.encore.wearlink.Paths
import dev.safro.encore.wearlink.Wire

/**
 * The signed-in server, kept in the Wearable Data Layer for Encore on a paired watch (it signs in with it on
 * first pairing, and follows when you switch server). The Data Layer only reaches apps with Encore's
 * package name and signature on your own watches. Called from Dart (data/wear_link.dart).
 */
object WearAccount {
    fun share(context: Context, server: String, user: String, pass: String, localAddress: String) {
        val req = PutDataMapRequest.create(Paths.ACCOUNT)
        req.dataMap.putString(Paths.ACCOUNT_KEY, Wire.encode(AccountInfo(server, user, pass, localAddress)))
        Wearable.getDataClient(context).putDataItem(req.asPutDataRequest().setUrgent())
    }

    fun signedOut(context: Context) {
        Wearable.getDataClient(context).deleteDataItems(Uri.Builder().scheme("wear").path(Paths.ACCOUNT).build())
    }
}
