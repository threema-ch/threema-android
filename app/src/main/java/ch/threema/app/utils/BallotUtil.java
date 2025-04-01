/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.utils;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.slf4j.Logger;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ballot.BallotMatrixActivity;
import ch.threema.app.dialogs.BallotVoteDialog;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.UserService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.connection.ConnectionState;
import ch.threema.domain.protocol.csp.MessageTooLongException;
import ch.threema.domain.protocol.csp.messages.ballot.BallotId;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ballot.BallotChoiceModel;
import ch.threema.storage.models.ballot.BallotModel;

@SuppressWarnings("rawtypes")
public class BallotUtil {
    private static final Logger logger = LoggingUtil.getThreemaLogger("BallotUtil");

    public static boolean canVote(BallotModel model, String identity) {
        return model != null
            && identity != null
            && model.getState() == BallotModel.State.OPEN;
    }

    public static boolean canViewMatrix(BallotModel model, String identity) {
        return model != null
            && identity != null
            && (
            model.getType() == BallotModel.Type.INTERMEDIATE
                || model.getState() == BallotModel.State.CLOSED);
    }

    public static boolean canCopy(BallotModel model, String identity) {
        return model != null
            && identity != null
            && model.getState() == BallotModel.State.CLOSED;
    }

    public static boolean canClose(BallotModel model, String identity) {
        return model != null
            && identity != null
            && model.getState() == BallotModel.State.OPEN
            && TestUtil.compare(model.getCreatorIdentity(), identity);
    }

    public static boolean isMine(BallotModel model, UserService userService) {
        return model != null
            && userService != null
            && !TestUtil.isEmptyOrNull(userService.getIdentity())
            && TestUtil.compare(userService.getIdentity(), model.getCreatorIdentity());
    }

    public static boolean openDefaultActivity(Context context, FragmentManager fragmentManager, BallotModel ballotModel, String identity) {
        if (context != null && fragmentManager != null) {
            if (canVote(ballotModel, identity)) {
                return openVoteDialog(fragmentManager, ballotModel, identity);
            } else if (canViewMatrix(ballotModel, identity)) {
                return openMatrixActivity(context, ballotModel, identity);
            }
        }
        return false;
    }

    public static boolean openVoteDialog(FragmentManager fragmentManager, BallotModel ballotModel, String identity) {
        if (fragmentManager != null && canVote(ballotModel, identity)) {
            BallotVoteDialog.newInstance(ballotModel.getId()).show(fragmentManager, "vote");
            return true;
        }
        return false;
    }

    public static boolean openMatrixActivity(Context context, BallotModel ballotModel, String identity) {
        if (context != null && canViewMatrix(ballotModel, identity)) {
            Intent intent = new Intent(context, BallotMatrixActivity.class);
            IntentDataUtil.append(ballotModel, intent);
            context.startActivity(intent);

            return intent != null;
        }
        return false;
    }

    public static String getNotificationString(Context context, AbstractMessageModel messageModel) {
        String message = "";
        BallotService ballotService = null;
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();

        if (serviceManager != null) {
            try {
                ballotService = serviceManager.getBallotService();
            } catch (Exception e) {
                //
            }
        }

        if (ballotService != null && messageModel.getBallotData() != null) {
            BallotModel ballotModel = ballotService.get(messageModel.getBallotData().getBallotId());
            if (ballotModel != null) {
                if (ballotModel.getState() == BallotModel.State.OPEN) {
                    message += " " + ballotModel.getName();
                } else if (ballotModel.getState() == BallotModel.State.CLOSED) {
                    message += " " + context.getResources().getString(R.string.ballot_message_closed);
                }
            }
        }
        return message;
    }

    public static void requestCloseBallot(BallotModel ballotModel, String identity, Fragment targetFragment, AppCompatActivity targetActivity) {
        if (BallotUtil.canClose(ballotModel, identity)) {
            FragmentManager fragmentManager = targetActivity != null ? targetActivity.getSupportFragmentManager() : targetFragment.getFragmentManager();
            if (ThreemaApplication.getServiceManager().getConnection().getConnectionState() == ConnectionState.LOGGEDIN) {
                GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.ballot_close, R.string.ballot_really_close, R.string.ok, R.string.cancel);
                dialog.setData(ballotModel);
                if (targetFragment != null) {
                    dialog.setTargetFragment(targetFragment, 0);
                }
                dialog.show(fragmentManager, ThreemaApplication.CONFIRM_TAG_CLOSE_BALLOT);
            } else {
                SimpleStringAlertDialog dialog = SimpleStringAlertDialog.newInstance(R.string.ballot_close, R.string.ballot_not_connected);
                dialog.show(fragmentManager, "na");
            }
        }
    }

    /**
     * Close the ballot.
     *
     * @param activity      if this is not null, a progress dialog is shown
     * @param ballotModel   the ballot model that will be closed
     * @param ballotService the ballot service
     * @param messageId     the message id needs to be specified to potentially match the message id
     *                      of the reflected outgoing message. In case the trigger source of closing
     *                      the ballot is not a reflected outgoing poll setup message, a randomly
     *                      generated message id must be passed.
     * @param triggerSource the trigger source of this action. If it is sync, then there won't be
     *                      any csp messages sent out
     */
    public static void closeBallot(
        @Nullable AppCompatActivity activity,
        @Nullable final BallotModel ballotModel,
        @NonNull final BallotService ballotService,
        @NonNull MessageId messageId,
        @NonNull TriggerSource triggerSource
    ) {
        if (ballotModel != null && ballotModel.getState() != BallotModel.State.CLOSED) {
            Runnable ballotCloseRunnable = () -> {
                try {
                    ballotService.close(ballotModel.getId(), messageId, triggerSource);
                } catch (final NotAllowedException | MessageTooLongException e) {
                    logger.error("Could not close poll", e);
                }
            };
            if (activity != null) {
                LoadingUtil.runInAlert(
                    activity.getSupportFragmentManager(),
                    R.string.ballot_close,
                    R.string.please_wait,
                    ballotCloseRunnable
                );
            } else {
                ballotCloseRunnable.run();
            }
        }
    }

    /**
     * Create a ballot.
     *
     * @param receiver              the message receiver
     * @param description           the description of the ballot (in some places also called
     *                              title)
     * @param ballotType            the type of the ballot (with intermediate results or not)
     * @param ballotAssessment      the assessment (single vs multiple choice)
     * @param ballotChoiceModelList the choices that are available
     * @param ballotId              the ballot id must be a random id, except when the ballot is
     *                              created as a result of a reflected outgoing poll setup message
     * @param messageId             the message id needs to be specified to potentially match the
     *                              message id of the reflected outgoing message. In case the
     *                              trigger source of creating the ballot is not a reflected
     *                              outgoing poll setup message, a randomly generated message id
     *                              must be passed.
     * @param triggerSource         the trigger source of this action. If it is sync, then there
     *                              won't be any csp messages sent out
     */
    @Nullable
    public static BallotModel createBallot(
        MessageReceiver receiver,
        String description,
        BallotModel.Type ballotType,
        BallotModel.Assessment ballotAssessment,
        List<BallotChoiceModel> ballotChoiceModelList,
        @NonNull BallotId ballotId,
        @NonNull MessageId messageId,
        @NonNull TriggerSource triggerSource
    ) {
        @NonNull
        BallotModel ballotModel;

        try {
            BallotService ballotService = ThreemaApplication.getServiceManager().getBallotService();
            BallotModel.ChoiceType choiceType = BallotModel.ChoiceType.TEXT;

            switch (receiver.getType()) {
                case MessageReceiver.Type_GROUP:
                    ballotModel = ballotService.create(
                        ((GroupMessageReceiver) receiver).getGroup(),
                        description,
                        BallotModel.State.TEMPORARY,
                        ballotAssessment,
                        ballotType,
                        choiceType,
                        ballotId
                    );
                    break;

                case MessageReceiver.Type_CONTACT:
                    ballotModel = ballotService.create(
                        ((ContactMessageReceiver) receiver).getContact(),
                        description,
                        BallotModel.State.TEMPORARY,
                        ballotAssessment,
                        ballotType,
                        choiceType,
                        ballotId
                    );
                    break;
                default:
                    throw new NotAllowedException("not allowed");
            }

            //generate ids
            Random r = new SecureRandom();

            int[] ids = new int[ballotChoiceModelList.size()];
            for (int n = 0; n < ids.length; n++) {
                int rId;
                boolean exists;
                do {
                    exists = false;
                    rId = Math.abs(r.nextInt());
                    for (int id : ids) {
                        if (id == rId) {
                            exists = true;
                            break;
                        }
                    }
                }
                while (exists);
                ids[n] = rId;

                BallotChoiceModel b = ballotChoiceModelList.get(n);
                if (b != null) {
                    b.setOrder(n + 1);
                    if (b.getApiBallotChoiceId() <= 0) {
                        b.setApiBallotChoiceId(rId);
                    }
                }
            }

            //add choices
            for (BallotChoiceModel c : ballotChoiceModelList) {
                ballotService.update(ballotModel, c);
            }

            try {
                ballotService.modifyFinished(ballotModel, messageId, triggerSource);
                if (triggerSource == TriggerSource.LOCAL) {
                    RuntimeUtil.runOnUiThread(() -> Toast.makeText(ThreemaApplication.getAppContext(), R.string.ballot_created_successfully, Toast.LENGTH_LONG).show());
                }
            } catch (MessageTooLongException e) {
                ballotService.remove(ballotModel);
                RuntimeUtil.runOnUiThread(() -> Toast.makeText(ThreemaApplication.getAppContext(), R.string.message_too_long, Toast.LENGTH_LONG).show());
                logger.error("Exception", e);
            }
            return ballotModel;
        } catch (Exception e) {
            RuntimeUtil.runOnUiThread(() -> Toast.makeText(ThreemaApplication.getAppContext(), R.string.error, Toast.LENGTH_LONG).show());
            logger.error("Exception", e);
        }

        return null;
    }
}
