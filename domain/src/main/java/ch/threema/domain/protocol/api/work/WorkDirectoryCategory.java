package ch.threema.domain.protocol.api.work;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.NonNull;

public class WorkDirectoryCategory {
    public final String id;
    public final String name;

    public WorkDirectoryCategory(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public WorkDirectoryCategory(@NonNull JSONObject jsonObject) {
        this.id = jsonObject.optString("id");
        this.name = jsonObject.optString("name");
    }

    public String getName() {
        return this.name;
    }

    public String getId() {
        return this.id;
    }

    public String toJSON() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("id", getId());
            jsonObject.put("name", getName());

            return jsonObject.toString();
        } catch (JSONException e) {
            return "";
        }
    }
}
