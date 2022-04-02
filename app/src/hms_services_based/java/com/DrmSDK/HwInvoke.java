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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.util.Log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * hw Browser use some code which is change by framework but we cannot use it
 * directly,may be complier in other platform, so use invoke to use it
 *
 * @since 2020/07/01
 */
public class HwInvoke {
	private static final Logger logger = LoggerFactory.getLogger(EMUISupportUtil.class);

    private static void paramsCheck(Class cls, Class[] paramsType,
                                    Object[] params) throws IllegalArgumentException {
        if (null == cls) {
            throw new IllegalArgumentException("class is null in staticFun");
        }
        if (null == paramsType) {
            if (params != null) {
                throw new IllegalArgumentException(
                        "paramsType is null, but params is not null");
            }
        } else {
            if (null == params) {
                throw new IllegalArgumentException("paramsType or params should be same");
            }
            if (paramsType.length != params.length) {
                throw new IllegalArgumentException("paramsType len:" + paramsType.length
                        + " should equal params.len:" + params.length);
            }
        }
    }

    /**
     * @param cls        : whick class of entry for you call
     * @param obj        : which entry
     * @param funName    : entry function name
     * @param paramsType : function parame type
     * @param params     : function parames
     * @return function return
     * @throws Exception example: A a = new A(); if framework call like: B b =
     *                   a.fun(myint, mystring) you can call B b =
     *                   (B)invokeFun(a.class, a, new Class[]{int.class,
     *                   String.class}, new Object[]{myint, mystring});
     */
    public static Object invokeFun(Class<?> cls, Object obj, String funName, Class<?>[] paramsType, Object[] params) throws Exception {
        paramsCheck(cls, paramsType, params);
        Method method = null;
        try {
            method = cls.getMethod(funName, paramsType);
            try {
                return method.invoke(obj, params);
            } catch (IllegalAccessException e) {
                logger.error("IllegalAccessException");
            } catch (IllegalArgumentException e) {
                logger.error("IllegalArgumentException");
            } catch (InvocationTargetException e) {
                logger.error("InvocationTargetException");
            }
        } catch (NoSuchMethodException e) {
            // maybe not huawei phone
            throw e;
        } catch (Exception e) {
            logger.error("Exception");
        }
        return null;
    }

}
