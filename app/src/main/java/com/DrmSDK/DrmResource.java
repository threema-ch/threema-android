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

import android.content.Context;
import android.content.res.Resources;

/**
 * 通过资源名称获取资源id
 * Obtains the resource ID based on the resource name.
 *
 * @since 2020/07/01
 */
public final class DrmResource {
    private static String _packageName = null;
    private static Resources _resources = null;

    private static final String packageName(Context context) {
        if (_packageName == null) {
            _packageName = context.getPackageName();
        }
        return _packageName;
    }

    private static final int identifier(Context context, String name,
                                        String type) {
        if (_resources == null) {
            _resources = context.getResources();
        }
        return _resources.getIdentifier(name, type, packageName(context));
    }

    /**
     * 根据字符串资源名称获取资源id
     * Obtains the resource ID based on the character string resource name.
     *
     * @param context 上下文（context）
     * @param name    资源名称（Resource Name）
     * @return 资源id（Indicates the resource ID.）
     */
    public static final int string(Context context, String name) {
        return identifier(context, name, "string");
    }

}
