package ch.threema.app.video.transcoder.audio;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;

import ch.threema.app.video.transcoder.MediaComponent;

public class AudioComponent extends MediaComponent {
    public AudioComponent(Context context, Uri srcUri) throws IOException {
        super(context, srcUri, MediaComponent.COMPONENT_TYPE_AUDIO);
    }
}
