package ch.threema.app.camera

import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel

class CameraViewModel : ViewModel() {
    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
}
