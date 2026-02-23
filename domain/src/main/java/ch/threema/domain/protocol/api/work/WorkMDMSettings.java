package ch.threema.domain.protocol.api.work;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import androidx.annotation.NonNull;

public class WorkMDMSettings implements Serializable {
    // if true, parameters set here override those set by an AppConfig-style MDM
    public boolean override = false;
    @NonNull
    public final Map<String, Object> parameters = new HashMap<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkMDMSettings)) return false;
        WorkMDMSettings that = (WorkMDMSettings) o;
        return override == that.override && Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(override, parameters);
    }
}
