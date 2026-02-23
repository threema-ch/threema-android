package ch.threema.app.ui;

import java.io.Serializable;

public class SelectorDialogItem implements Serializable {
    private final String text;
    private final int icon;

    public SelectorDialogItem(String text, Integer icon) {
        this.text = text;
        this.icon = icon;
    }

    @Override
    public String toString() {
        return text;
    }

    public int getIcon() {
        return icon;
    }

    public String getText() {
        return text;
    }
}
