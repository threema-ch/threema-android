/*
 * Copyright 2020. Huawei Technologies Co., Ltd. All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.DrmSDK.util;

import android.content.Context;

/**
 * ApplicationWrapper
 *
 * @since 2020/07/01
 */
public class ApplicationWrapper {

    private static final Object LOCK = new Object();
    private static ApplicationWrapper appWrapper = null;
    private Context context;

    /**
     * <默认构造函数>
     * <Default constructor>
     */
    public ApplicationWrapper(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 初始化
     * Initialize
     *
     * @param context 上下文
     */
    public static void init(Context context) {
        synchronized (LOCK) {
            if (appWrapper == null) {
                appWrapper = new ApplicationWrapper(context);
            }
        }
    }


    /**
     * 获取实例对象
     * Obtaining an instance object
     *
     * @return ApplicationWrapper实例对象(Instance object)
     */
    public static ApplicationWrapper getInstance() {
        synchronized (LOCK) {
            return appWrapper;
        }
    }


    /**
     * 获取应用上下文
     * Obtains the application context.
     *
     * @return context 上下文对象
     * @see [类、类#方法、类#成员][Class, Class#Method, Class#Member]
     */
    public Context getContext() {
        return context;
    }

}
