package ch.threema.app.adapters.decorators

interface ChatAdapterDecoratorListener {
    fun isActionModeEnabled(): Boolean
    fun isInChoiceMode(): Boolean
}
