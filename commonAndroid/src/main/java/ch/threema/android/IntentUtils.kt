package ch.threema.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

inline fun <reified T : Activity> buildActivityIntent(context: Context, noinline build: Intent.() -> Unit = {}): Intent =
    buildActivityIntent(context, T::class, build)

fun <T : Activity> buildActivityIntent(context: Context, clazz: KClass<T>, build: Intent.() -> Unit = {}): Intent {
    val intent = Intent(context, clazz.java)
    intent.apply(build)
    return intent
}

fun buildIntent(build: Intent.() -> Unit): Intent =
    Intent().apply(build)

fun <T> Activity.bindExtra(getExtra: Intent.() -> T) = IntentExtraDelegate(::getIntent, getExtra)

class IntentExtraDelegate<T>(private val getIntent: () -> Intent, private val getExtra: Intent.() -> T) {
    operator fun getValue(thisRef: Any, property: KProperty<*>): T = getIntent().getExtra()
}
