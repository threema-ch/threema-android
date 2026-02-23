package ch.threema.domain.protocol.urls

import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("ParameterizedUrl")

abstract class ParameterizedUrl(
    private val template: String,
    requiredPlaceholders: Array<String>,
) {
    init {
        requiredPlaceholders.forEach { placeholder ->
            if ("{$placeholder}" !in template) {
                logger.error("Placeholder {} not found in template {}", placeholder, template)
            }
        }
    }

    protected fun getUrl(vararg parameters: Pair<String, String>): String {
        var url = template
        parameters.forEach { (placeholder, value) ->
            url = url.replace("{$placeholder}", value)
        }
        return url
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        return template == (other as ParameterizedUrl).template
    }

    override fun hashCode(): Int = template.hashCode()

    override fun toString() = "${super.toString()}($template)"
}
