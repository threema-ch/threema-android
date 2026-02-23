package ch.threema.app.widget

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val widgetFeatureModule = module {
    factoryOf(::WidgetUpdater)
}
