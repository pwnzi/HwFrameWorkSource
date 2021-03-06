package com.huawei.zxing.resultdispatch.wifi;

import android.os.Build.VERSION;
import android.util.Log;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

public abstract class PlatformSupportManager<T> {
    private static final String TAG = PlatformSupportManager.class.getSimpleName();
    private final T defaultImplementation;
    private final SortedMap<Integer, String> implementations;
    private final Class<T> managedInterface;

    protected PlatformSupportManager(Class<T> managedInterface, T defaultImplementation) {
        if (!managedInterface.isInterface()) {
            throw new IllegalArgumentException();
        } else if (managedInterface.isInstance(defaultImplementation)) {
            this.managedInterface = managedInterface;
            this.defaultImplementation = defaultImplementation;
            this.implementations = new TreeMap(Collections.reverseOrder());
        } else {
            throw new IllegalArgumentException();
        }
    }

    protected final void addImplementationClass(int minVersion, String className) {
        this.implementations.put(Integer.valueOf(minVersion), className);
    }

    public final T build() {
        for (Integer minVersion : this.implementations.keySet()) {
            if (VERSION.SDK_INT >= minVersion.intValue()) {
                try {
                    Class<? extends T> clazz = Class.forName((String) this.implementations.get(minVersion)).asSubclass(this.managedInterface);
                    String str = TAG;
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("Using implementation ");
                    stringBuilder.append(clazz);
                    stringBuilder.append(" of ");
                    stringBuilder.append(this.managedInterface);
                    stringBuilder.append(" for SDK ");
                    stringBuilder.append(minVersion);
                    Log.i(str, stringBuilder.toString());
                    return clazz.getConstructor(new Class[0]).newInstance(new Object[0]);
                } catch (ClassNotFoundException cnfe) {
                    Log.w(TAG, cnfe);
                } catch (IllegalAccessException iae) {
                    Log.w(TAG, iae);
                } catch (InstantiationException ie) {
                    Log.w(TAG, ie);
                } catch (NoSuchMethodException nsme) {
                    Log.w(TAG, nsme);
                } catch (InvocationTargetException ite) {
                    Log.w(TAG, ite);
                }
            }
        }
        String str2 = TAG;
        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append("Using default implementation ");
        stringBuilder2.append(this.defaultImplementation.getClass());
        stringBuilder2.append(" of ");
        stringBuilder2.append(this.managedInterface);
        Log.i(str2, stringBuilder2.toString());
        return this.defaultImplementation;
    }
}
