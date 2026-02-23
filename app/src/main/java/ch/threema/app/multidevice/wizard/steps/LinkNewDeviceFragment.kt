package ch.threema.app.multidevice.wizard.steps

import android.os.Bundle
import androidx.fragment.app.Fragment
import ch.threema.app.multidevice.wizard.LinkNewDeviceWizardViewModel
import org.koin.androidx.viewmodel.ext.android.activityViewModel

open class LinkNewDeviceFragment : Fragment() {
    val viewModel: LinkNewDeviceWizardViewModel by activityViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        viewModel.setCurrentFragment(this)

        super.onCreate(savedInstanceState)
    }
}
