package org.slf4j.impl;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

import ch.threema.logging.LoggerManager;

public class ThreemaLoggerFactory implements ILoggerFactory {
    @Override
    public Logger getLogger(String name) {
        return LoggerManager.getLogger(name);
    }
}
