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
 * DRM鉴权结果回调
 * Callback of the DRM authentication result
 *
 * @since 2020/07/01
 */
public interface DrmCheckCallback {
    /**
     * Check successfully in the SDK，the developer can uses the callback parameters signData and signature
     * to carry out their own business processing, such as visiting their own business server for signature verification.
     *
     * @param signData  signed data for verification
     * @param signature server signature
     */
    void onCheckSuccess(String signData, String signature);

    /**
     * Check failed and user is not licensed to use this application.
     *
     * @param errorCode Error Code
     */
    public void onCheckFailed(int errorCode);
}
