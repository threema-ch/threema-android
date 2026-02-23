package ch.threema.app.services;

import ch.threema.base.SessionScoped;

@SessionScoped
public interface LocaleService {
    String getCountryIsoCode();

    String getNormalizedPhoneNumber(String phoneNumber);

    String getHRPhoneNumber(String phoneNumber);

    boolean validatePhoneNumber(String phoneNumber);

    String getCountryCodePhonePrefix();
}
