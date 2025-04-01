/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2024 Threema GmbH
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

package ch.threema.app.filepicker;

public class FileInfo implements Comparable<FileInfo> {
    private String name;
    private String data;
    private String path;
    private long date;
    private boolean folder;
    private boolean parent;

    public FileInfo(String n, String d, String p, long date, boolean folder, boolean parent) {
        this.name = n;
        this.data = d;
        this.path = p;
        this.date = date;
        this.folder = folder;
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public String getData() {
        return data;
    }

    public String getPath() {
        return path;
    }

    public long getLastModified() {
        return date;
    }

    @Override
    public int compareTo(FileInfo o) {
        if (this.name != null)
            return this.name.toLowerCase().compareTo(o.getName().toLowerCase());
        else
            throw new IllegalArgumentException();
    }

    public boolean isFolder() {
        return folder;
    }

    public boolean isParent() {
        return parent;
    }
}
