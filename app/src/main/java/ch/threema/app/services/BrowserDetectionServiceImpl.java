package ch.threema.app.services;

final public class BrowserDetectionServiceImpl implements BrowserDetectionService {
    @Override
    public Browser detectBrowser(String userAgent) {
        if (userAgent != null && !userAgent.isEmpty()) {
            final String desc = userAgent.toLowerCase().trim();
            if (desc.contains("threemadesktop")) {
                return Browser.WEBTOP;
            } else if (desc.contains("mozilla") && desc.contains("applewebkit")
                && desc.contains("chrome") && desc.contains("safari")
                && desc.contains("opr")) {
                return Browser.OPERA;
            } else if (desc.contains("chrome") && desc.contains("webkit") && !desc.contains("edge")
                && !desc.contains("edg")) {
                return Browser.CHROME;
            } else if (desc.contains("mozilla") && desc.contains("firefox")) {
                return Browser.FIREFOX;
            } else if (desc.contains("safari") && desc.contains("applewebkit") && !desc.contains("chrome")) {
                return Browser.SAFARI;
            } else if (desc.contains("edge") || desc.contains("edg")) {
                return Browser.EDGE;
            }
        }
        return Browser.UNKNOWN;
    }
}
