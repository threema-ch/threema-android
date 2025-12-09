/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.services;

import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import ch.threema.app.preference.service.PreferenceService;

public class ProfilePictureRecipientsServiceImpl implements ProfilePictureRecipientsService {
    private static final String LIST_NAME = "identity_list_profilepics";

    private final Object lock = new Object();
    private String[] ids;
    private final PreferenceService preferenceService;

    public ProfilePictureRecipientsServiceImpl(@NonNull PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
        this.ids = preferenceService.getList(LIST_NAME);
    }

    @Override
    public boolean has(String id) {
        if (this.ids != null) {
            synchronized (this.lock) {
                return Arrays.asList(this.ids).contains(id);
            }
        }
        return false;
    }

    @Override
    public void remove(String id) {
        if (this.ids != null) {
            synchronized (this.lock) {
                List<String> idList = Arrays.asList(this.ids);
                if (idList.contains(id)) {
                    String[] newIdentities = new String[idList.size() - 1];
                    int pos = 0;
                    for (String other : idList) {
                        if (other != null && !other.equals(id)) {
                            newIdentities[pos++] = other;
                        }
                    }
                    this.preferenceService.setList(LIST_NAME, newIdentities);
                    this.ids = newIdentities;
                }
            }
        }
    }

    @Override
    public void add(String id) {
        if (this.ids != null && (id != null && !id.isEmpty())) {
            synchronized (this.lock) {
                List<String> idList = Arrays.asList(this.ids);
                if (!idList.contains(id)) {
                    this.ids = Arrays.copyOf(this.ids, this.ids.length + 1);
                    this.ids[ids.length - 1] = id;
                    this.preferenceService.setList(LIST_NAME, ids);
                }
            }
        }
    }

    @Override
    public synchronized String[] getAll() {
        return this.ids;
    }

    @Override
    public void removeAll() {
        this.ids = new String[0];
        this.preferenceService.setList(LIST_NAME, this.ids);
    }

    @Override
    public void replaceAll(String[] ids) {
        this.ids = ids;
        this.preferenceService.setList(LIST_NAME, this.ids);
    }
}
