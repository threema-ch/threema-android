package ch.threema.app.services

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService

class DeviceServiceImpl(private val context: Context) : DeviceService {
    override fun isOnline(): Boolean =
        context.getSystemService<ConnectivityManager>()?.activeNetworkInfo?.isConnectedOrConnecting == true
}
