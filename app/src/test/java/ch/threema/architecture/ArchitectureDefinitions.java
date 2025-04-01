/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

package ch.threema.architecture;

import androidx.annotation.NonNull;

import com.tngtech.archunit.library.Architectures;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

public class ArchitectureDefinitions {
    private ArchitectureDefinitions() {
    }

    public static final String THREEMA_ROOT_PACKAGE = "ch.threema";
    private static final String THREEMA_ROOT_PACKAGE_DOT = THREEMA_ROOT_PACKAGE + ".";

    // Layer names
    public static final String
        ANNOTATION = "annotation",
        APP = "app",
        BASE = "base",
        DATA = "data",
        DOMAIN = "domain",
        LOCALCRYPTO = "localcrypto",
        LOGGING = "logging",
        STORAGE = "storage";

    // Layer packages
    public static final String
        PACKAGE_ANNOTATION = THREEMA_ROOT_PACKAGE_DOT + ANNOTATION,
        PACKAGE_APP = THREEMA_ROOT_PACKAGE_DOT + APP,
        PACKAGE_BASE = THREEMA_ROOT_PACKAGE_DOT + BASE,
        PACKAGE_DATA = THREEMA_ROOT_PACKAGE_DOT + DATA,
        PACKAGE_DOMAIN = THREEMA_ROOT_PACKAGE_DOT + DOMAIN,
        PACKAGE_LOCALCRYPTO = THREEMA_ROOT_PACKAGE_DOT + LOCALCRYPTO,
        PACKAGE_LOGGING = THREEMA_ROOT_PACKAGE_DOT + LOGGING,
        PACKAGE_STORAGE = THREEMA_ROOT_PACKAGE_DOT + STORAGE;


    public static @NonNull Architectures.LayeredArchitecture getLayeredArchitecture() {
        return layeredArchitecture()
            .layer(APP).definedBy(PACKAGE_APP + "..")
            .layer(STORAGE).definedBy(PACKAGE_STORAGE + "..")
            .layer(LOCALCRYPTO).definedBy(PACKAGE_LOCALCRYPTO + "..")
            .layer(DATA).definedBy(PACKAGE_DATA + "..")
            .layer(DOMAIN).definedBy(PACKAGE_DOMAIN + "..")
            .layer(BASE).definedBy(PACKAGE_BASE + "..")
            .layer(LOGGING).definedBy(PACKAGE_LOGGING + "..")
            .layer(ANNOTATION).definedBy(PACKAGE_ANNOTATION + "..");
    }
}
