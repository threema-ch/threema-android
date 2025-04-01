/*
 * Copyright (C) 2016 Azimo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.azimolabs.conditionwatcher;

public class ConditionWatcher {

    public static final int CONDITION_NOT_MET = 0;
    public static final int CONDITION_MET = 1;
    public static final int TIMEOUT = 2;

    public static final int DEFAULT_TIMEOUT_LIMIT = 1000 * 60;
    public static final int DEFAULT_INTERVAL = 250;

    private int timeoutLimit = DEFAULT_TIMEOUT_LIMIT;
    private int watchInterval = DEFAULT_INTERVAL;

    private static ConditionWatcher conditionWatcher;

    private ConditionWatcher() {
        super();
    }

    public static ConditionWatcher getInstance() {
        if (conditionWatcher == null) {
            conditionWatcher = new ConditionWatcher();
        }
        return conditionWatcher;
    }

    public static void waitForCondition(Instruction instruction) throws Exception {
        int status = CONDITION_NOT_MET;
        int elapsedTime = 0;

        do {
            if (instruction.checkCondition()) {
                status = CONDITION_MET;
            } else {
                elapsedTime += getInstance().watchInterval;
                Thread.sleep(getInstance().watchInterval);
            }

            if (elapsedTime == getInstance().timeoutLimit) {
                status = TIMEOUT;
                break;
            }
        } while (status != CONDITION_MET);

        if (status == TIMEOUT)
            throw new Exception(instruction.getDescription() + " - took more than " + getInstance().timeoutLimit / 1000 + " seconds. Test stopped.");
    }

    public static void setWatchInterval(int watchInterval) {
        getInstance().watchInterval = watchInterval;
    }

    public static void setTimeoutLimit(int ms) {
        getInstance().timeoutLimit = ms;
    }
}
