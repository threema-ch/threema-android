package ch.threema.app.messagereceiver

import ch.threema.app.utils.ContactUtil

fun MessageReceiver<*>.isGatewayChat(): Boolean =
    if (this is ContactMessageReceiver) {
        contact?.identity?.let { ContactUtil.isGatewayContact(it) } ?: false
    } else {
        false
    }
