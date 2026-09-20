package com.golfv.canobserver;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

/**
 * Passive observer for the QC4250 vendor CAN Binder service.
 *
 * This deliberately implements only the receive callback. It never invokes
 * ICanbusService.setValue(), deviceOnkey(), or setCanbusDataToUser().
 */
public final class CanObserver {
    private static final String SERVICE_NAME = "canbus_service";
    private static final String SERVICE_DESCRIPTOR = "android.os.ICanbusService";
    private static final String CALLBACK_DESCRIPTOR = "android.os.ICanbusInterface";
    private static final String CAR_EVENT_DESCRIPTOR = "android.os.ICarEventXYInerface";

    // Reconstructed from the device's framework.jar.
    private static final int TRANSACTION_SET_CANBUS_INTERFACE = 3;
    private static final int TRANSACTION_REMOVE_CANBUS_INTERFACE = 6;
    private static final int TRANSACTION_REGISTER_CAR_EVENT = 8;
    private static final int TRANSACTION_UNREGISTER_CAR_EVENT = 9;
    private static final int CALLBACK_ON_RESULT = 1;
    private static final int CALLBACK_BACKCAR = 2;
    private static final int CAR_EVENT_ON_EVENT = 1;
    private static final int CAR_EVENT_GET_CALLER_ID = 2;
    private static final int CAR_EVENT_GET_CALLER_PACKAGE = 3;

    private CanObserver() {}

    private static final class ReceiveCallback extends Binder implements IInterface {
        ReceiveCallback() {
            attachInterface(this, CALLBACK_DESCRIPTOR);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(CALLBACK_DESCRIPTOR);
                return true;
            }

            if (code == CALLBACK_ON_RESULT) {
                data.enforceInterface(CALLBACK_DESCRIPTOR);
                byte[] bytes = data.createByteArray();
                int declaredSize = data.readInt();
                printFrame(bytes, declaredSize);
                reply.writeNoException();
                return true;
            }

            if (code == CALLBACK_BACKCAR) {
                data.enforceInterface(CALLBACK_DESCRIPTOR);
                int state = data.readInt();
                System.out.printf(Locale.ROOT,
                        "%s elapsed=%d type=backcar state=%d%n",
                        Instant.now(), SystemClock.elapsedRealtime(), state);
                reply.writeNoException();
                return true;
            }

            return super.onTransact(code, data, reply, flags);
        }
    }

    private static final class CarEventCallback extends Binder implements IInterface {
        CarEventCallback() {
            attachInterface(this, CAR_EVENT_DESCRIPTOR);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(CAR_EVENT_DESCRIPTOR);
                return true;
            }

            data.enforceInterface(CAR_EVENT_DESCRIPTOR);
            if (code == CAR_EVENT_ON_EVENT) {
                int[] values = data.createIntArray();
                System.out.printf(Locale.ROOT,
                        "%s elapsed=%d type=car-event data=%s%n",
                        Instant.now(), SystemClock.elapsedRealtime(), formatInts(values));
                reply.writeNoException();
                return true;
            }
            if (code == CAR_EVENT_GET_CALLER_ID) {
                reply.writeNoException();
                reply.writeInt(android.os.Process.myPid());
                return true;
            }
            if (code == CAR_EVENT_GET_CALLER_PACKAGE) {
                reply.writeNoException();
                reply.writeString("com.golfv.canobserver");
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    }

    private static String formatInts(int[] values) {
        if (values == null) return "null";
        StringBuilder result = new StringBuilder(values.length * 6 + 2).append('[');
        for (int i = 0; i < values.length; i++) {
            if (i != 0) result.append(',');
            result.append(values[i]);
        }
        return result.append(']').toString();
    }

    private static void printFrame(byte[] bytes, int declaredSize) {
        int available = bytes == null ? 0 : bytes.length;
        int used = Math.max(0, Math.min(declaredSize, available));
        StringBuilder hex = new StringBuilder(used * 3);
        for (int i = 0; i < used; i++) {
            if (i != 0) hex.append(' ');
            hex.append(String.format(Locale.ROOT, "%02X", bytes[i] & 0xff));
        }
        System.out.printf(Locale.ROOT,
                "%s elapsed=%d type=frame declared=%d available=%d data=%s%n",
                Instant.now(), SystemClock.elapsedRealtime(), declaredSize, available, hex);
    }

    private static IBinder getService(String name) throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Method getService = serviceManager.getMethod("getService", String.class);
        return (IBinder) getService.invoke(null, name);
    }

    private static void register(IBinder service, IBinder callback, int transaction)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR);
            data.writeStrongBinder(callback);
            if (!service.transact(transaction, data, reply, 0)) {
                throw new RemoteException("Binder transaction " + transaction + " rejected");
            }
            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    public static void main(String[] args) throws Exception {
        IBinder service = getService(SERVICE_NAME);
        if (service == null) {
            throw new IllegalStateException(SERVICE_NAME + " is not registered");
        }

        ReceiveCallback callback = new ReceiveCallback();
        CarEventCallback carEventCallback = new CarEventCallback();
        register(service, callback, TRANSACTION_SET_CANBUS_INTERFACE);
        register(service, carEventCallback, TRANSACTION_REGISTER_CAR_EVENT);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                register(service, callback, TRANSACTION_REMOVE_CANBUS_INTERFACE);
            } catch (Exception ignored) {
                // Process death also removes the Binder callback from the service.
            }
            try {
                register(service, carEventCallback, TRANSACTION_UNREGISTER_CAR_EVENT);
            } catch (Exception ignored) {
                // Process death also removes the Binder callback from the service.
            }
        }));

        System.out.println("CAN_OBSERVER_READY receive-only=true raw=true car-events=true service="
                + SERVICE_NAME);
        System.out.flush();
        new CountDownLatch(1).await();
    }
}
