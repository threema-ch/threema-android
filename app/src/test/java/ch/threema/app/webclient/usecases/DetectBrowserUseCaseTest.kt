package ch.threema.app.webclient.usecases

import kotlin.test.Test
import kotlin.test.assertEquals

class DetectBrowserUseCaseTest {
    @Test
    fun `null is considered unknown`() {
        assertEquals(
            DetectBrowserUseCase.Browser.UNKNOWN,
            DetectBrowserUseCase().call(null),
        )
    }

    @Test
    fun `empty is considered unknown`() {
        assertEquals(
            DetectBrowserUseCase.Browser.UNKNOWN,
            DetectBrowserUseCase().call(""),
        )
    }

    @Test
    fun `detect threema desktop`() {
        assertEquals(
            DetectBrowserUseCase.Browser.WEBTOP,
            DetectBrowserUseCase().call(
                "ThreemaDesktop",
            ),
        )
    }

    @Test
    fun `detect edge browser`() {
        assertEquals(
            DetectBrowserUseCase.Browser.EDGE,
            DetectBrowserUseCase().call(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 Edg/134.0.0.0",
            ),
        )
    }

    @Test
    fun `detect safari browser`() {
        assertEquals(
            DetectBrowserUseCase.Browser.SAFARI,
            DetectBrowserUseCase().call(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
                    "(KHTML, like Gecko) Version/18.3.1 Safari/605.1.15",
            ),
        )
    }

    @Test
    fun `detect chrome browser`() {
        assertEquals(
            DetectBrowserUseCase.Browser.CHROME,
            DetectBrowserUseCase().call(
                "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36>",
            ),
        )
    }

    @Test
    fun `detect firefox browser`() {
        assertEquals(
            DetectBrowserUseCase.Browser.FIREFOX,
            DetectBrowserUseCase().call(
                "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:15.0) Gecko/20100101 Firefox/15.0.1",
            ),
        )
    }

    @Test
    fun `detect opera browser`() {
        assertEquals(
            DetectBrowserUseCase.Browser.OPERA,
            DetectBrowserUseCase().call(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/143.0.0.0 Safari/537.36 OPR/124.0.0.0",
            ),
        )
    }

    @Test
    fun `unknown browser`() {
        assertEquals(
            DetectBrowserUseCase.Browser.UNKNOWN,
            DetectBrowserUseCase().call(
                "Microsoft Toaster/1.2.3",
            ),
        )
    }
}
