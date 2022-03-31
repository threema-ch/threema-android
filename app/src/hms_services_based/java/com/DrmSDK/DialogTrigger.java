/*
 * Copyright 2020. Huawei Technologies Co., Ltd. All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.DrmSDK;

import android.app.Dialog;
import android.util.Log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 弹框触发器
 *Pop-up trigger
 *
 * @since 2020/07/01
 */
public final class DialogTrigger {

	private final Logger logger = LoggerFactory.getLogger(DialogTrigger.class);
    /**
     * 单例
     * singleton
     */
    private static DialogTrigger mInstance = null;

    /**
     * 构造函数
     * constructor
     */
    private DialogTrigger() {

    }

    private static final Object LOCK = new Object();

    /**
     * 单例模式
     * singleton pattern
     */
    public static DialogTrigger getInstance() {
        synchronized (LOCK) {
            if (null == mInstance) {
                mInstance = new DialogTrigger();
            }
            return mInstance;
        }
    }

    private DialogObserver observer;

    private boolean hasObserver = false;

    public boolean hasObserver() {
        return hasObserver;
    }

    public void registerObserver(DialogObserver ob) {
        observer = ob;
        if (observer == null) {
            hasObserver = false;
        } else {
            hasObserver = true;
        }

    }

    public void closeDialog() {
        if (observer != null) {
            observer.closeDlg();
        }

        logger.debug("closeDialog");
    }
}
