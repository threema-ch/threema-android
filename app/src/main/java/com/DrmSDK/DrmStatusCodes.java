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

/**
 * 鉴权状态码
 * Authentication Status Code
 *
 * @since 2020/07/01
 */
public class DrmStatusCodes {

    /**
     * 获取签名结果:成功
     * Signature obtaining result: success
     */
    public static final int CODE_SUCCESS = 0;
    /**
     * 获取签名结果:失败，需要网络连接
     * Obtaining signature result: Failed. Network connection is required.
     */
    public static final int CODE_NEED_INTERNET = 1;
    /**
     * 获取签名结果:失败，没有同意应用市场中心协议
     * Signature obtaining result: Failed. The AppGallery agreement is not agreed.
     */
    public static final int CODE_PROTOCAL_UNAGREE = 3;
    /**
     * 获取签名结果:失败，登录华为帐号失败（2D产品）
     * Signature obtaining result: Failed. Failed to log in to the HUAWEI ID (2D product).
     */
    public static final int CODE_LOGIN_FAILED = 6;
    /**
     * 获取签名结果:失败，没有订购关系
     * Signature obtaining result: Failed because no subscription relationship exists.
     */
    public static final int CODE_NO_ORDER = 7;
    /**
     * 超过使用设备数量:切换账号重新购买
     * If the number of used devices exceeds the upper limit, switch to another account and purchase the device again.
     */
    public static final int CODE_OVER_LIMIT = 8;
    /**
     * 获取签名结果:失败，没有登录华为帐号（VR产品返回，2D产品无此错误码）
     * Signature obtaining result: Failed. No Huawei ID is logged in. (This error code is returned by VR products. This error code does not exist for 2D products.)
     */
    public static final int CODE_NOT_LOGIN = 9;
    /**
     * 获取签名结果:失败，需要拉起Activity
     * Obtaining the signature result: Failed. The activity needs to be started.
     */
    public static final int CODE_START_ACTIVITY = 100;

    /**
     * 错误的会员订阅关系
     * Incorrect membership subscription relationship.
     */
    public static final int CODE_INVALID_MEMBERSHIP = 10;

    /**
     * 获取签名结果:失败，内部错误(接口调用失败或获取数据不可用)
     * Signature obtaining result: failure, internal error (interface invoking failure or data obtaining unavailable)
     */
    public static final int CODE_DEFAULT = -1;

    /**
     * 获取签名结果:失败，市场不支持鉴权
     * Signature obtaining result: Failed. Marketing does not support authentication.
     */
    public static final int CODE_APP_UNSUPPORT = -2;
    /**
     * 获取签名时，用户点返回取消
     * When obtaining the signature, the user clicks Cancel.
     */
    public static final int CODE_CANCEL = -4;

}
