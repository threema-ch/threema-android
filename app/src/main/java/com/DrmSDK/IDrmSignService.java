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
 * 和应用市场通信服务AIDL生成代码
 * Generate code for AIDL of the HiApp communication service.
 *
 * @since 2020/07/01
 */
public interface IDrmSignService extends android.os.IInterface {
    /**
     * Local-side IPC implementation stub class.
     */
    public static abstract class Stub extends android.os.Binder implements
            IDrmSignService {
        private static final String DESCRIPTOR = "com.huawei.appmarket.service.pay.drm.IDrmSignService";

        /**
         * Construct the stub at attach it to the interface.
         */
        public Stub() {
            this.attachInterface(this, DESCRIPTOR);
        }

        /**
         * Cast an IBinder object into an
         * com.huawei.appmarket.service.pay.drm.IDrmSignService interface,
         * generating a proxy if needed.
         */
        public static IDrmSignService asInterface(
                android.os.IBinder obj) {
            if ((obj == null)) {
                return null;
            }
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (((iin != null) && (iin instanceof IDrmSignService))) {
                return ((IDrmSignService) iin);
            }
            return new IDrmSignService.Stub.Proxy(
                    obj);
        }

        @Override
        public android.os.IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags)
                throws android.os.RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION: {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                case TRANSACTION_GET_SIGN: {
                    data.enforceInterface(DESCRIPTOR);
                    java.util.Map arg0;
                    ClassLoader cl = (ClassLoader) this
                            .getClass().getClassLoader();
                    arg0 = data.readHashMap(cl);
                   ICallback arg1;
                    arg1 = ICallback.Stub
                            .asInterface(data.readStrongBinder());
                    this.getSign(arg0, arg1);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_REPORT: {
                    data.enforceInterface(DESCRIPTOR);
                    java.util.Map arg0;
                    ClassLoader cl = (ClassLoader) this
                            .getClass().getClassLoader();
                    arg0 = data.readHashMap(cl);
                    this.report(arg0);
                    reply.writeNoException();
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static class Proxy implements
                IDrmSignService {
            private android.os.IBinder mRemote;

            Proxy(android.os.IBinder remote) {
                mRemote = remote;
            }

            @Override
            public android.os.IBinder asBinder() {
                return mRemote;
            }

            public String getInterfaceDescriptor() {
                return DESCRIPTOR;
            }

            @Override
            public void getSign(java.util.Map params,
                                ICallback callback)
                    throws android.os.RemoteException {
                android.os.Parcel data = android.os.Parcel.obtain();
                android.os.Parcel reply = android.os.Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeMap(params);
                    data.writeStrongBinder((((callback != null)) ? (callback
                            .asBinder()) : (null)));
                    mRemote.transact(Stub.TRANSACTION_GET_SIGN, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void report(java.util.Map params)
                    throws android.os.RemoteException {
                android.os.Parcel data = android.os.Parcel.obtain();
                android.os.Parcel reply = android.os.Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeMap(params);
                    mRemote.transact(Stub.TRANSACTION_REPORT, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }
        }

        static final int TRANSACTION_GET_SIGN = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
        static final int TRANSACTION_REPORT = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    }

    /**
     * 获取签名串(Obtains the signature string.)
     *
     * @param params 签名串(Signature string)
     * @param callback 回调(Callback)
     * @throws android.os.RemoteException 远程连接异常(Remote connection exception.)
     */
    public void getSign(java.util.Map params,
                        ICallback callback)
            throws android.os.RemoteException;

    /**
     * 上报日志(Report logs.)
     *
     * @param params 日志信息(Log information)
     * @throws android.os.RemoteException 远程连接异常(Remote connection exception.)
     */
    public void report(java.util.Map params) throws android.os.RemoteException;
}
