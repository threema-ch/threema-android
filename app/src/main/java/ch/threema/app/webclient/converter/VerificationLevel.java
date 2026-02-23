package ch.threema.app.webclient.converter;

import androidx.annotation.AnyThread;

import ch.threema.app.webclient.exceptions.ConversionException;

@AnyThread
public class VerificationLevel extends Converter {
    public final static int UNVERIFIED = 1;
    public final static int SERVER_VERIFIED = 2;
    public final static int FULLY_VERIFIED = 3;

    public static int convert(ch.threema.domain.models.VerificationLevel verificationLevel) throws ConversionException {
        try {
            switch (verificationLevel) {
                case UNVERIFIED:
                    return VerificationLevel.UNVERIFIED;
                case SERVER_VERIFIED:
                    return VerificationLevel.SERVER_VERIFIED;
                case FULLY_VERIFIED:
                    return VerificationLevel.FULLY_VERIFIED;
                default:
                    throw new ConversionException("Unknown verification level: " + verificationLevel);
            }
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
    }
}
