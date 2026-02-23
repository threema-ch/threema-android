package ch.threema.domain.protocol.urls

class AppRatingUrl(template: String) : ParameterizedUrl(template, requiredPlaceholders = arrayOf("rating")) {
    fun get(rating: Int) = getUrl("rating" to rating.toString())
}
