package ch.threema.app.utils;


import java.util.List;
import java.util.Map;

import ch.threema.base.utils.Utils;

public class ListReader {

    private final List<Object> list;
    private int pos = 0;

    public ListReader(List<Object> list) {
        this.list = list;
    }

    public String nextString() {
        return (String) this.next();
    }

    public byte[] nextStringAsByteArray() {
        String v = this.nextString();
        if (v != null && !v.isEmpty()) {
            return Utils.hexStringToByteArray(v);
        }
        return null;
    }

    public Integer nextInteger() {
        return (Integer) this.next();
    }

    public Boolean nextBool() {
        return (Boolean) this.next();
    }

    public Map<String, Object> nextMap() {
        Object n = this.next();
        if (n instanceof Map) {
            return (Map<String, Object>) n;
        }

        return null;
    }

    private Object next() {
        if (this.list.size() > this.pos) {
            return this.list.get(this.pos++);
        }

        return null;
    }
}
