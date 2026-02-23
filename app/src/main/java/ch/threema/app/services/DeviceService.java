package ch.threema.app.services;

import ch.threema.base.SessionScoped;

@SessionScoped
public interface DeviceService {
    boolean isOnline();
}
