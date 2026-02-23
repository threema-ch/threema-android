package ch.threema.domain.protocol.api.work;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WorkOrganization {
    private String name;

    public WorkOrganization() {
    }

    public WorkOrganization(@NonNull JSONObject jsonObject) {
        this.name = jsonObject.optString("name");
    }

    @Nullable
    public String getName() {
        if (name != null && name.isEmpty()) {
            return null;
        }
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    public String toJSON() {
        JSONObject jsonObject = new JSONObject();
        try {
            var name = getName();
            if (name != null) {
                jsonObject.put("name", name);
            }
            return jsonObject.toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
