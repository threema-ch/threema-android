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
 * 公共常量定义类
 * Common constant definition class
 *
 * @since 2020/07/01
 */
public class Constants {
    /**
     * Bundle key: 拉起Activity.
     * Bundle key: Pull up the activity
     */
    public static final String KEY_EXTRA_ACTION = "drm_key_extra_actiion";
    /**
     * Bundle key: 拉起Activity的包名
     * Bundle key: name of the package for starting the activity.
     */
    public static final String KEY_EXTRA_PACKAGE = "drm_key_extra_package";
    /**
     * Bundle key: Dialog类型
     * Bundle key: Dialog type
     */
    public static final String KEY_EXTRA_DIALOG = "drm_key_extra_dialog";
    /**
     * Bundle key: 错误码类型
     * Bundle key: error code type.
     */
    public static final String KEY_EXTRA_CODE = "drm_key_extra_code";
    /**
     * Bundle key: 其他信息
     * Bundle key: other information
     */
    public static final String KEY_EXTRA_EXTRA = "drm_key_extra_extra";
    /**
     * Bundle key: 向外部activity传递的通用参数
     * Bundle key: common parameter transferred to an external activity.
     */
    public static final String KEY_JSON_EXTRA = "json_extra";
    /**
     * 拉起详情页的action
     * Action for starting the details page
     */
    public static final String DOOR_TO_ALLY_OF_DETAIL = "com.huawei.appmarket.intent.action.AppDetail";
    /**
     * Activity返回码：同意用户使用协议
     * Activity return code: agreeing to the user agreement
     */
    public static final int RESULT_CODE_AGREEMENT_AGREED = 1001;
    /**
     * Activity返回码：不同意用户使用协议
     * Activity return code: The user agreement is not approved.
     */
    public static final int RESULT_CODE_AGREEMENT_DECLINED = 1002;
    /**
     * Activity返回码：账号登陆成功
     * Activity return code: The login is successful.
     */
    public static final int RESULT_CODE_LOGIN_SUCCESS = 10001;
    /**
     * Activity返回码：账号登陆失败
     * Activity return code: account login failure
     */
    public static final int RESULT_CODE_LOGIN_FAILED = 10002;

    /**
     * 加入会员成功
     * Member added successfully.
     */
    public static final int RESULT_CODE_JOIN_MEMBER_FAILED = 20001;
    /**
     * 加入会员失败
     * Failed to join the member.
     */
    public static final int RESULT_CODE_JOIN_MEMBER_SUCCESS = 20002;
}
