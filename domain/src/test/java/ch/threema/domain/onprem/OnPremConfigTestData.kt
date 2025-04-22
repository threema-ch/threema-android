/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.domain.onprem

object OnPremConfigTestData {
    /**
     * The public key used to verify the OPPF signature.
     *
     * The corresponding secret key is ezDKBie96Hnu39gpM2iiIYwfE6cRXzON32K/KbLusYk=
     * It can be used to regenerate the signature whenever the data in [TEST_GOOD_OPPF] is modified.
     */
    const val PUBLIC_KEY: String = "jae1lgwR3W7YyKiGQlsbdqObG13FR1EvjVci2aDNIi8="

    /**
     * Wrong key that is not trusted
     */
    const val WRONG_PUBLIC_KEY: String = "3z1cAHQRAkeY+NJg3/st5DGUdEXICcvRWeMT4y5l0CQ="

    /**
     * An OPPF that is valid, unexpired and has a good signature
     */
    const val TEST_GOOD_OPPF: String = """{
    "license": {
        "expires": "2099-12-31",
        "count": 100,
        "id": "DUMMY-00000001"
    },
    "domains": {
        "rules": [
            {
                "fqdn": "threemaonprem.initrode.com",
                "matchMode": "include-subdomains",
                "spkis": [
                    {
                        "value": "DTJU4+0HObYPrx9lF4Kz8hhjcJL3WBL4k829L++UlSk=",
                        "algorithm": "sha256"
                    },
                    {
                        "value": "C19RmQgZXzwovKRRJ2st7bsokiRchKcYjBo3m63fvn8=",
                        "algorithm": "sha256"
                    }
                ]
            },
            {
                "fqdn": "another-host.initrode.com",
                "matchMode": "exact",
                "spkis": [
                    {
                        "value": "XIglSWPJ6aJ7LeIz6KsOrr0fNgNZ0PzGgDCDEZq5/U4=",
                        "algorithm": "sha256"
                    },
                    {
                        "value": "XIglSWPJ6aJ7LeIz6KsOrr0fNgNZ0PzGgDCDEZq5/U4=",
                        "algorithm": "unknown-algorithm"
                    }
                ]
            },
            {
                "fqdn": "unknown.initrode.com",
                "matchMode": "unknown-mode"
            }
        ]
    },
    "blob": {
        "uploadUrl": "https://blob.threemaonprem.initrode.com/blob/upload",
        "downloadUrl": "https://blob-{blobIdPrefix}.threemaonprem.initrode.com/blob/{blobId}",
        "doneUrl": "https://blob-{blobIdPrefix}.threemaonprem.initrode.com/blob/{blobId}/done"
    },
    "web": {"url": "https://web.threemaonprem.initrode.com/"},
    "chat": {
        "hostname": "chat.threemaonprem.initrode.com",
        "publicKey": "r9utIHN9ngo21q9OlZcotsQu1f2HwAW2Wi+u6Psp4Wc=",
        "ports": [
            5222,
            443
        ]
    },
    "work": {"url": "https://work.threemaonprem.initrode.com/"},
    "signatureKey": "jae1lgwR3W7YyKiGQlsbdqObG13FR1EvjVci2aDNIi8=",
    "safe": {"url": "https://safe.threemaonprem.initrode.com/"},
    "refresh": 86400,
    "avatar": {"url": "https://avatar.threemaonprem.initrode.com/"},
    "mediator": {
        "blob": {
            "uploadUrl": "https://mediator.threemaonprem.initrode.com/blob/upload",
            "downloadUrl": "https://mediator.threemaonprem.initrode.com/blob/{blobId}",
            "doneUrl": "https://mediator.threemaonprem.initrode.com/blob/{blobId}/done"
        },
        "url": "https://mediator.threemaonprem.initrode.com/"
    },
    "version": "1.0",
    "directory": {"url": "https://dir.threemaonprem.initrode.com/directory"}
}
mJb7U/0AudmPzsKUZ52qyGFXMHLs6bNvqGVg6PYn+KSF96Dunhn22v67fFYkcPsAwOmIXx33RWFM00ZYQjzQAw=="""
}
