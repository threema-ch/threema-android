package ch.threema.app.webclient.converter;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.data.datatypes.ContactNameFormat;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;

@AnyThread
public class DistributionList extends Converter {
    private final static String MEMBERS = "members";
    private final static String CAN_CHANGE_MEMBERS = "canChangeMembers";

    /**
     * Converts multiple distribution list models to MsgpackObjectBuilder instances.
     */
    @NonNull
    public static List<MsgpackBuilder> convert(@NonNull List<DistributionListModel> distributionLists) throws ConversionException {
        List<MsgpackBuilder> list = new ArrayList<>();
        for (DistributionListModel distributionList : distributionLists) {
            list.add(convert(distributionList));
        }
        return list;
    }

    /**
     * Converts a distribution list model to a MsgpackObjectBuilder instance.
     */
    @NonNull
    public static MsgpackObjectBuilder convert(@NonNull DistributionListModel distributionList) throws ConversionException {
        final @NonNull DistributionListService distributionListService = getDistributionListService();
        final @NonNull PreferenceService preferenceService = getPreferenceService();
        final @NonNull MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
        try {
            builder.put(Receiver.ID, getId(distributionList));
            builder.put(Receiver.DISPLAY_NAME, getName(distributionList, preferenceService.getContactNameFormat()));
            builder.put(Receiver.COLOR, getColor(distributionList));
            builder.put(Receiver.ACCESS, (new MsgpackObjectBuilder())
                .put(Receiver.CAN_DELETE, true)
                .put(CAN_CHANGE_MEMBERS, true));

            final boolean isPrivateChat = getConversationCategoryService().isPrivateChat(distributionListService.getUniqueIdString(distributionList));
            final boolean isVisible = !isPrivateChat || !getPreferenceService().arePrivateChatsHidden();
            builder.put(Receiver.LOCKED, isPrivateChat);
            builder.put(Receiver.VISIBLE, isVisible);

            final MsgpackArrayBuilder memberBuilder = new MsgpackArrayBuilder();
            for (ContactModel contactModel : distributionListService.getMembers(distributionList)) {
                memberBuilder.put(contactModel.getIdentity());
            }
            builder.put(MEMBERS, memberBuilder);
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
        return builder;
    }

    @NonNull
    public static String getId(@NonNull DistributionListModel distributionList) throws ConversionException {
        try {
            return String.valueOf(distributionList.getId());
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
    }

    @NonNull
    public static String getName(
        @NonNull DistributionListModel distributionList,
        @NonNull ContactNameFormat contactNameFormat
    ) throws ConversionException {
        try {
            return NameUtil.getDistributionListDisplayName(distributionList, getDistributionListService(), contactNameFormat);
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
    }

    @NonNull
    public static String getColor(@NonNull DistributionListModel distributionList) throws ConversionException {
        try {
            int idColor = distributionList.getIdColor().getColorLight();
            return String.format("#%06X", (0xFFFFFF & idColor));
        } catch (IllegalStateException | NullPointerException e) {
            throw new ConversionException(e);
        }
    }
}
