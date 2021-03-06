package com.android.ims;

import android.content.Context;

public class HwImsManager {
    public static boolean isEnhanced4gLteModeSettingEnabledByUser(Context context, int subId) {
        return HwImsManagerInner.isEnhanced4gLteModeSettingEnabledByUser(context, subId);
    }

    public static boolean isNonTtyOrTtyOnVolteEnabled(Context context, int subId) {
        return HwImsManagerInner.isNonTtyOrTtyOnVolteEnabled(context, subId);
    }

    public static boolean isVolteEnabledByPlatform(Context context, int subId) {
        return HwImsManagerInner.isVolteEnabledByPlatform(context, subId);
    }

    public static boolean isVtEnabledByPlatform(Context context, int subId) {
        return HwImsManagerInner.isVtEnabledByPlatform(context, subId);
    }

    public static boolean isVtEnabledByUser(Context context, int subId) {
        return HwImsManagerInner.isVtEnabledByUser(context, subId);
    }

    public static boolean isWfcEnabledByUser(Context context, int subId) {
        return HwImsManagerInner.isWfcEnabledByUser(context, subId);
    }

    public static void setWfcSetting(Context context, boolean enabled, int subId) {
        HwImsManagerInner.setWfcSetting(context, enabled, subId);
    }

    public static int getWfcMode(Context context, int subId) {
        return HwImsManagerInner.getWfcMode(context, subId);
    }

    public static void setWfcMode(Context context, int wfcMode, int subId) {
        HwImsManagerInner.setWfcMode(context, wfcMode, subId);
    }

    public static int getWfcMode(Context context, boolean roaming, int subId) {
        return HwImsManagerInner.getWfcMode(context, roaming, subId);
    }

    public static void setWfcMode(Context context, int wfcMode, boolean roaming, int subId) {
        HwImsManagerInner.setWfcMode(context, wfcMode, roaming, subId);
    }

    public static boolean isWfcRoamingEnabledByUser(Context context, int subId) {
        return HwImsManagerInner.isWfcRoamingEnabledByUser(context, subId);
    }

    public static void setWfcRoamingSetting(Context context, boolean enabled, int subId) {
        HwImsManagerInner.setWfcRoamingSetting(context, enabled, subId);
    }

    public static boolean isWfcEnabledByPlatform(Context context, int subId) {
        return HwImsManagerInner.isWfcEnabledByPlatform(context, subId);
    }

    public static void updateImsServiceConfig(Context context, int subId, boolean force) {
        HwImsManagerInner.updateImsServiceConfig(context, subId, force);
    }

    public static void factoryReset(Context context, int subId) {
        HwImsManagerInner.factoryReset(context, subId);
    }

    public static boolean isDualImsAvailable() {
        return HwImsManagerInner.isDualImsAvailable();
    }

    public static void setImsSmsConfig(Context context, int setImsSmsConfig, int subId) {
        HwImsManagerInner.setImsSmsConfig(context, setImsSmsConfig, subId);
    }

    public static void getImsSmsConfig(Context context, int subId) {
        HwImsManagerInner.getImsSmsConfig(context, subId);
    }

    public static boolean setRttEnable(Context context, int subId, int enable) {
        return false;
    }

    public static int getRttEnable(Context context, int subId) {
        return 0;
    }
}
