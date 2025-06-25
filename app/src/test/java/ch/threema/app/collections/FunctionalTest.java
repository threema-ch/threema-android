/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

package ch.threema.app.collections;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.Assert.assertEquals;

public class FunctionalTest {

    @Test
    public void filterNonNull() {
        final List<String> names = new ArrayList<>();
        names.add("MC Hammer");
        names.add("Knöppel");
        names.add(null);
        names.add("Nella Martinetti");

        final List<String> loud = Functional.filter(names, new IPredicateNonNull<String>() {
            @Override
            public boolean apply(@NonNull String value) {
                return value.equals("Knöppel");
            }
        });

        assertEquals(2, loud.size());
        assertEquals("Knöppel", loud.get(0));
        assertEquals(null, loud.get(1));
    }

}
