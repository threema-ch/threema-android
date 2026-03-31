package ch.threema.app.dev.androidcontactsync

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import ch.threema.android.buildActivityIntent
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.androidcontactsync.read.AndroidContactReadException
import ch.threema.app.androidcontactsync.read.AndroidContactReader
import ch.threema.app.androidcontactsync.types.EmailAddress
import ch.threema.app.androidcontactsync.types.PhoneNumber
import ch.threema.app.androidcontactsync.types.RawContact
import ch.threema.app.androidcontactsync.types.StructuredName
import ch.threema.base.utils.getThreemaLogger
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("AndroidContactDebugActivity")

class AndroidContactDebugActivity : AppCompatActivity() {
    private val androidContactReader: AndroidContactReader by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val rawContacts = remember { mutableStateOf<List<RawContact>>(emptyList()) }

            LaunchedEffect(Unit) {
                rawContacts.value = readRawContacts()
            }

            AndroidRawContactDebugInfo(rawContacts.value)
        }
    }

    @Composable
    private fun AndroidRawContactDebugInfo(rawContacts: List<RawContact>) {
        Scaffold { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
                    .padding(horizontal = dimensionResource(R.dimen.grid_unit_x8))
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                items(rawContacts) { rawContact ->
                    Column(
                        modifier = Modifier
                            .padding(8.dp),
                    ) {
                        Text(text = "lookupKey: ${rawContact.lookupInfo.lookupKey.key}")
                        Text(text = "contactId: ${rawContact.lookupInfo.contactId.id}")
                        Text(text = "rawContactId: ${rawContact.rawContactId.id}")
                        Text(text = "phoneNumbers: ${rawContact.phoneNumbers.toPhoneDebugString()}")
                        Text(text = "emailAddresses: ${rawContact.emailAddresses.toEmailDebugString()}")
                        Text(text = "structuredNames: ${rawContact.structuredNames.toStructuredNameDebugString()}")
                    }
                    Spacer(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }

    private fun Set<PhoneNumber>.toPhoneDebugString(): String {
        if (isEmpty()) {
            return "no phone numbers"
        }
        return joinToString(prefix = "\n  ", separator = "\n  ") { phoneNumber -> phoneNumber.phoneNumber }
    }

    private fun Set<EmailAddress>.toEmailDebugString(): String {
        if (isEmpty()) {
            return "no email addresses"
        }
        return joinToString(prefix = "\n  ", separator = "\n  ") { emailAddress -> emailAddress.emailAddress }
    }

    private fun Set<StructuredName>.toStructuredNameDebugString(): String {
        if (isEmpty()) {
            return "no structured names"
        }
        return joinToString(prefix = "\n", separator = "\n---\n") { structuredName ->
            buildString {
                appendLine("  prefix: ${structuredName.prefix}")
                appendLine("  givenName: ${structuredName.givenName}")
                appendLine("  middleName: ${structuredName.middleName}")
                appendLine("  familyName: ${structuredName.familyName}")
                appendLine("  suffix: ${structuredName.suffix}")
                appendLine("  displayName: ${structuredName.displayName}")
            }
        }
    }

    private suspend fun readRawContacts(): List<RawContact> {
        try {
            val rawContacts = androidContactReader.readAllAndroidContacts()
                .sortedBy { androidContact -> androidContact.lookupInfo.contactId.id }
                .flatMap { androidContact -> androidContact.rawContacts.sortedBy { rawContact -> rawContact.rawContactId.id } }
            return rawContacts
        } catch (exception: AndroidContactReadException) {
            logger.error("Could not read raw contacts", exception)
            when (exception) {
                is AndroidContactReadException.MissingPermission ->
                    showToast("No permission to read contacts")

                is AndroidContactReadException.MultipleContactIdsPerLookupKey ->
                    showToast("Multiple contact ids per lookup key")

                is AndroidContactReadException.MultipleLookupKeysPerContactId ->
                    showToast("Multiple lookup keys per contact id")

                is AndroidContactReadException.MultipleLookupKeysPerRawContact ->
                    showToast("Multiple lookup keys per raw contact")

                is AndroidContactReadException.Other ->
                    showToast("Error while reading contacts")
            }
            finish()
            return emptyList()
        }
    }

    companion object {
        fun createIntent(context: Context) = buildActivityIntent<AndroidContactDebugActivity>(context)
    }
}
