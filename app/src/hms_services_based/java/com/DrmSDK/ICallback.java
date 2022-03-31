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
 * 回调AIDL生成代码
 * Callback AIDL generation code
 *
 * @since 2020/07/01
 */
public interface ICallback extends android.os.IInterface {
    /**
     * Local-side IPC implementation stub class.
     */
    public static abstract class Stub extends android.os.Binder implements
            ICallback {
        private static final String DESCRIPTOR = "com.huawei.appmarket.service.pay.drm.ICallback";

        /**
         * Construct the stub at attach it to the interface.
         */
        public Stub() {
            this.attachInterface(this, DESCRIPTOR);
        }

        /**
         * Cast an IBinder object into an
         * com.huawei.appmarket.service.pay.drm.ICallback interface, generating
         * a proxy if needed.
         */
        public static ICallback asInterface(
                android.os.IBinder obj) {
            if ((obj == null)) {
                return null;
            }
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (((iin != null) && (iin instanceof ICallback))) {
                return ((ICallback) iin);
            }
            return new ICallback.Stub.Proxy(
                    obj);
        }

        @Override
        public android.os.IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags)
                throws android.os.RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(DESCRIPTOR);
                return true;
            }
            if (code == TRANSACTION_ON_RESULT) {
                data.enforceInterface(DESCRIPTOR);
                java.util.Map arg0;
                ClassLoader cl = (ClassLoader) this
                        .getClass().getClassLoader();
                arg0 = data.readHashMap(cl);
                this.onResult(arg0);
                reply.writeNoException();
                return true;
            } else {
                return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements
                ICallback {
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
            public void onResult(java.util.Map result)
                    throws android.os.RemoteException {
                android.os.Parcel data = android.os.Parcel.obtain();
                android.os.Parcel reply = android.os.Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeMap(result);
                    mRemote.transact(Stub.TRANSACTION_ON_RESULT, data, reply,
                            0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }
        }

        static final int TRANSACTION_ON_RESULT = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    }


    /**
     * 回调结果(Callback result)
     *
     * @param result 结果集合(Result set)
     * @throws android.os.RemoteException 远程连接异常(Remote connection exception.)
     */
    public void onResult(java.util.Map result)
            throws android.os.RemoteException;
}
