package ch.threema.domain.protocol.csp.messages;

import ch.threema.domain.protocol.csp.ProtocolDefines;

public class DeliveryReceiptUtils {
    private DeliveryReceiptUtils() {
    }

    /**
     * Return true if the specified delivery receipt type is an ACK or DEC.
     */
    public static boolean isReaction(int deliveryReceiptType) {
        switch (deliveryReceiptType) {
            case ProtocolDefines.DELIVERYRECEIPT_MSGUSERACK:
            case ProtocolDefines.DELIVERYRECEIPT_MSGUSERDEC:
                return true;
            default:
                return false;
        }
    }
}
