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

package com.DrmSDK.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 上报日志类
 * Report logs.
 *
 * @since 2020/07/01
 */
public class ReportUtils {
    /**
     * 日志分隔符
     * Log Separator
     */
    public static final String SEPERATOR = "|";
    private static final String DRM = "26";
    private static final String GET_SIGN = "01";
    private static final String CHECK_SIGN = "02";
    private static final String SUCCESS = "01";
    private static final String FAILED = "02";
    /**
     * KEY:DRM(26) + getSign(01) + success(01)
     */
    public static final String KEY_GET_SIGN_SUCCESS = DRM + GET_SIGN + SUCCESS;
    /**
     * KEY:DRM(26) + getSign(01) + failed(02)
     */
    public static final String KEY_GET_SIGN_FAILED = DRM + GET_SIGN + FAILED;
    /**
     * KEY:DRM(26) + checkSign(02) + success(01)
     */
    public static final String KEY_CHECK_SIGN_SUCCESS = DRM + CHECK_SIGN
        + SUCCESS;
    /**
     * KEY:DRM(26) + checkSign(02) + failed(02)
     */
    public static final String KEY_CHECK_SIGN_FAILED = DRM + CHECK_SIGN
        + FAILED;
    /**
     * 操作类型：获取签名
     * Operation type: Obtain signature
     */
    public static final String ACTION_GET_SIGN = "getSign";
    /**
     * 操作类型：鉴权
     * Operation type: authentication
     */
    public static final String ACTION_CHECK_SIGN = "checkSign";
    /**
     * 成功码
     * Success code.
     */
    public static final int CODE_SUCCESS = 99;
    /**
     * 失败码：不支持购买版本
     * Failure code: The version cannot be purchased.
     */
    public static final int CODE_STORE_NOT_AVAILABLE = 0;
    /**
     * 失败码：获取签名失败
     * Failure code: Failed to obtain the signature.
     */
    public static final int CODE_GET_SIGN_FAILED = 1;
    /**
     * 失败码：无网络连接
     * Failure code: No network connection is available.
     */
    public static final int CODE_NO_NETWORK = 2;
    /**
     * 失败码：拒绝应用市场使用协议
     * Failure code: AppGallery protocol use rejection
     */
    public static final int CODE_HIAPP_AGREEMENT_DECLINED = 3;
    /**
     * 失败码：鉴权失败
     * Failure code: Authentication failed.
     */
    public static final int CODE_CHECK_FAILED = 5;
    /**
     * 失败码：用户按返回键取消鉴权
     * Failure code: The user presses the back key to cancel authentication.
     */
    public static final int CODE_USER_INTERRUPT = 6;
    /**
     * 失败码：没有登录华为账号
     * Failure code: You have not logged in to your Huawei ID.
     */
    public static final int CODE_ACCOUNT_NOT_LOGGED = 7;
    /**
     * 失败码：登录华为账号但是市场登录不成功
     * Failure code: Failed to log in to the Huawei account.
     */
    public static final int CODE_STORE_NOT_LOGGED = 8;
    /**
     * 失败码：无订购关系
     * Failure code: No subscription relationship exists.
     */
    public static final int CODE_NO_ORDER = 9;
    /**
     * 失败码：ts为空或格式错误
     * Failure code: The TS is empty or the format is incorrect.
     */
    public static final int CODE_TS_INVALID = 10;
    /**
     * 失败码：签名超有效期但联网
     * Failure code: The signature has expired but is connected to the Internet.
     */
    public static final int CODE_SIGN_INVALID_WITH_INTERNET = 11;
    /**
     * 失败码：签名为空
     * Error code: The signature is empty.
     */
    public static final int CODE_SIGN_EMPTY = 12;
    /**
     * 失败码：其他错误
     * Failure code: Other errors
     */
    public static final int CODE_OTHERS = 13;
    /**
     * 失败码：超过使用设备数量
     * Failure code: Exceeded the number of used devices.
     */
    public static final int CODE_OVER_LIMIT = 14;

    /**
     * 生成报告
     * Generate a report.
     *
     * @param key   报告key类型(Report key type)
     * @param items 报告内容(Report Content)
     * @return 报告map(Report map)
     */
    public static Map generateReport(String key, String[] items) {
        if (items == null) {
            return null;
        }
        Map map = new HashMap<String, String>();
        StringBuilder buffer = new StringBuilder();
        for (String string : items) {
            buffer.append(string);
            buffer.append("|");
        }
        String value = buffer.substring(0, buffer.length() - 1);
        map.put(key, value);
        return map;
    }
}
