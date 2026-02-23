package ch.threema.domain.protocol.csp.coders;

import ch.threema.base.ThreemaException;

import java.io.Serializable;
import java.util.Arrays;

public class MetadataBox implements Serializable {
    private final byte[] box;

    public MetadataBox(byte[] box) throws ThreemaException {
        if (box.length > Short.MAX_VALUE) {
            throw new ThreemaException("Metadata box is too long");
        }
        this.box = box;
    }

    public byte[] getBox() {
        return box;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetadataBox that = (MetadataBox) o;
        return Arrays.equals(box, that.box);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(box);
    }
}
