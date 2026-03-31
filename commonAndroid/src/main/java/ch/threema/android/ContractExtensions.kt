package ch.threema.android

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

fun Fragment.registerSimpleActivityResultContract(callback: (ActivityResult) -> Unit = {}): ActivityResultLauncher<Intent> =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        callback(result)
    }

fun AppCompatActivity.registerSimpleActivityResultContract(callback: (ActivityResult) -> Unit = {}): ActivityResultLauncher<Intent> =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        callback(result)
    }

fun Fragment.registerPermissionResultContract(callback: (isGranted: Boolean) -> Unit = {}): ActivityResultLauncher<String> =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        callback(isGranted)
    }

fun AppCompatActivity.registerPermissionResultContract(callback: (isGranted: Boolean) -> Unit = {}): ActivityResultLauncher<String> =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        callback(isGranted)
    }
