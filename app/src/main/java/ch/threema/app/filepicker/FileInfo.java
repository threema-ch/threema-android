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
