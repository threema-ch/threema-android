package ch.threema.app.webclient.usecases

class DetectBrowserUseCase {
    fun call(userAgent: String?): Browser {
        val description = userAgent?.lowercase()?.trim()
            ?: return Browser.UNKNOWN
        return when {
            "threemadesktop" in description -> Browser.WEBTOP
            "mozilla" in description &&
                "applewebkit" in description &&
                "chrome" in description &&
                "safari" in description &&
                "opr" in description
            -> Browser.OPERA
            "chrome" in description && "webkit" in description && "edg" !in description -> Browser.CHROME
            "mozilla" in description && "firefox" in description -> Browser.FIREFOX
            "safari" in description && "applewebkit" in description && "chrome" !in description -> Browser.SAFARI
            "edg" in description -> Browser.EDGE
            else -> Browser.UNKNOWN
        }
    }

    enum class Browser {
        CHROME,
        FIREFOX,
        OPERA,
        EDGE,
        SAFARI,
        WEBTOP,
        UNKNOWN,
    }
}
