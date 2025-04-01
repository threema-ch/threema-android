/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.voip;

import android.content.Context;
import android.content.res.Configuration;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import ch.threema.app.R;
import ch.threema.app.utils.MessageUtil;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.data.status.VoipStatusDataModel;

import static ch.threema.storage.models.data.status.VoipStatusDataModel.NO_CALL_ID;
import static org.junit.Assert.assertEquals;

/**
 * Test proper rendering of VoIP status messags.
 */
@RunWith(AndroidJUnit4.class)
public class VoipStatusMessageTest {
    private static int ICON_OUTGOING = R.drawable.ic_call_missed_outgoing_black_24dp;
    private static int ICON_INCOMING = R.drawable.ic_call_missed_black_24dp;
    private static int COLOR_RED = R.color.material_red;
    private static int COLOR_ORANGE = R.color.material_orange;

    /**
     * Return a context where the locale has been overriden to "en".
     */
    private Context getContext() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final Configuration config = new Configuration();
        config.setLocale(new Locale("en"));
        return context.createConfigurationContext(config);
    }

    class TestCase {
        private Context context;
        private AbstractMessageModel messageModel;
        private int expectedIcon;
        private int expectedColor;
        private String expectedPlaceholder;
        private String expectedText;

        public TestCase(
            Context context,
            boolean outgoing,
            VoipStatusDataModel dataModel,
            int expectedIcon,
            int expectedColor,
            String expectedPlaceholder,
            String expectedText
        ) {
            this.context = context;
            final MessageModel messageModel = new MessageModel(true);
            messageModel.setOutbox(outgoing);
            messageModel.setVoipStatusData(dataModel);
            this.messageModel = messageModel;
            this.expectedIcon = expectedIcon;
            this.expectedColor = expectedColor;
            this.expectedPlaceholder = expectedPlaceholder;
            this.expectedText = expectedText;
        }

        public void test() {
            final MessageUtil.MessageViewElement element = MessageUtil.getViewElement(this.context, this.messageModel);
            assertEquals((Integer) this.expectedIcon, element.icon);
            assertEquals((Integer) this.expectedColor, element.color);
            assertEquals(this.expectedPlaceholder, element.placeholder);
            assertEquals(this.expectedText, element.text);
        }
    }

    @Test
    public void testIncomingMissed() {
        new TestCase(
            this.getContext(),
            false,
            VoipStatusDataModel.createMissed(NO_CALL_ID, null),
            ICON_INCOMING,
            COLOR_RED,
            "Missed call",
            "Missed call"
        ).test();
    }

    @Test
    public void testIncomingRejectedUnknown() {
        new TestCase(
            this.getContext(),
            false,
            VoipStatusDataModel.createRejected(NO_CALL_ID, VoipCallAnswerData.RejectReason.UNKNOWN),
            ICON_INCOMING,
            COLOR_RED,
            "Missed call",
            "Missed call"
        ).test();
    }

    @Test
    public void testIncomingRejectedBusy() {
        new TestCase(
            this.getContext(),
            false,
            VoipStatusDataModel.createRejected(NO_CALL_ID, VoipCallAnswerData.RejectReason.BUSY),
            ICON_INCOMING,
            COLOR_RED,
            "Missed call (Busy)",
            "Missed call (Busy)"
        ).test();
    }

    @Test
    public void testIncomingRejectedTimeout() {
        new TestCase(
            this.getContext(),
            false,
            VoipStatusDataModel.createRejected(NO_CALL_ID, VoipCallAnswerData.RejectReason.TIMEOUT),
            ICON_INCOMING,
            COLOR_RED,
            "Missed call",
            "Missed call"
        ).test();
    }

    @Test
    public void testIncomingRejectedRejected() {
        new TestCase(
            this.getContext(),
            false,
            VoipStatusDataModel.createRejected(NO_CALL_ID, VoipCallAnswerData.RejectReason.REJECTED),
            ICON_INCOMING,
            COLOR_ORANGE,
            "Call declined",
            "Call declined"
        ).test();
    }

    @Test
    public void testIncomingRejectedDisabled() {
        new TestCase(
            this.getContext(),
            false,
            VoipStatusDataModel.createRejected(NO_CALL_ID, VoipCallAnswerData.RejectReason.DISABLED),
            ICON_INCOMING,
            COLOR_ORANGE,
            "Call declined",
            "Call declined"
        ).test();
    }

    @Test
    public void testOutgoingRejectedUnknown() {
        new TestCase(
            this.getContext(),
            true,
            VoipStatusDataModel.createRejected(NO_CALL_ID, VoipCallAnswerData.RejectReason.UNKNOWN),
            ICON_OUTGOING,
            COLOR_RED,
            "Call declined",
            "Call declined"
        ).test();
    }

    @Test
    public void testOutgoingRejectedBusy() {
        new TestCase(
            this.getContext(),
            true,
            VoipStatusDataModel.createRejected(NO_CALL_ID, VoipCallAnswerData.RejectReason.BUSY),
            ICON_OUTGOING,
            COLOR_RED,
            "Call recipient is busy",
            "Call recipient is busy"
        ).test();
    }

    @Test
    public void testOutgoingRejectedTimeout() {
        new TestCase(
            this.getContext(),
            true,
            VoipStatusDataModel.createRejected(NO_CALL_ID, VoipCallAnswerData.RejectReason.TIMEOUT),
            ICON_OUTGOING,
            COLOR_RED,
            "Call recipient is unavailable",
            "Call recipient is unavailable"
        ).test();
    }

    @Test
    public void testOutgoingRejectedRejected() {
        new TestCase(
            this.getContext(),
            true,
            VoipStatusDataModel.createRejected(NO_CALL_ID, VoipCallAnswerData.RejectReason.REJECTED),
            ICON_OUTGOING,
            COLOR_RED,
            "Call declined",
            "Call declined"
        ).test();
    }

    @Test
    public void testOutgoingRejectedDisabled() {
        new TestCase(
            this.getContext(),
            true,
            VoipStatusDataModel.createRejected(NO_CALL_ID, VoipCallAnswerData.RejectReason.DISABLED),
            ICON_OUTGOING,
            COLOR_RED,
            "Threema calls disabled by recipient",
            "Threema calls disabled by recipient"
        ).test();
    }
}
