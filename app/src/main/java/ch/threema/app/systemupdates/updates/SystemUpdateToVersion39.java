package ch.threema.app.systemupdates.updates;

import org.koin.java.KoinJavaComponent;

import ch.threema.app.services.ContactService;
import ch.threema.domain.models.IdentityState;
import ch.threema.domain.protocol.ThreemaFeature;
import kotlin.Lazy;

/**
 * Update all Contacts with Feature Level < current Feature Level
 */
public class SystemUpdateToVersion39 implements SystemUpdate {

    private final Lazy<ContactService> contactServiceLazy = KoinJavaComponent.inject(ContactService.class);

    @Override
    public void run() {
        ContactService contactService = contactServiceLazy.getValue();

        // call find with fetchMissingFeatureLevel = true to fetch all contacts without current feature level
        contactService.find(new ContactService.Filter() {
            @Override
            public IdentityState[] states() {
                return null;
            }

            @Override
            public Long requiredFeature() {
                return ThreemaFeature.VOIP;
            }

            @Override
            public Boolean fetchMissingFeatureLevel() {
                return true;
            }

            @Override
            public Boolean includeMyself() {
                return true;
            }

            @Override
            public Boolean includeHidden() {
                return true;
            }
        });
    }

    @Override
    public String getDescription() {
        return "sync feature levels";
    }

    @Override
    public int getVersion() {
        return 39;
    }
}
