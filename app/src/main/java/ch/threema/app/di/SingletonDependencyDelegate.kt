package ch.threema.app.di

import kotlin.reflect.KProperty

class SingletonDependencyDelegate<T : Any>(private val getDependency: () -> T) {
    private val dependency by lazy(LazyThreadSafetyMode.PUBLICATION) { getDependency() }

    operator fun getValue(thisRef: Any, property: KProperty<*>): T = dependency
}
