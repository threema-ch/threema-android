package ch.threema.app.services;

/**
 * Detect a browser based on the user agent.
 */
public interface BrowserDetectionService {
    enum Browser {
        CHROME,
        FIREFOX,
        OPERA,
        EDGE,
        SAFARI,
        WEBTOP,
        UNKNOWN,
    }

    Browser detectBrowser(String userAgent);
}
