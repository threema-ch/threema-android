package ch.threema.app.notifications

import androidx.core.app.NotificationManagerCompat
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val notificationModule = module {
    factoryOf(::CallNotificationManagerImpl) bind CallNotificationManager::class
    factory { NotificationManagerCompat.from(androidContext()) }
}
