package ch.threema.common

object Http {
    object Header {
        // Request headers
        const val ACCEPT = "Accept"
        const val ACCEPT_LANGUAGE = "Accept-Language"
        const val AUTHORIZATION = "Authorization"
        const val CONTENT_TYPE = "Content-Type"
        const val USER_AGENT = "User-Agent"

        // Response headers
        const val EXPIRES = "Expires"
    }

    object StatusCode {
        const val OK = 200
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
    }
}
