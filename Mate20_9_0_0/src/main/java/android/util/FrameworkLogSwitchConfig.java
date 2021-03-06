package android.util;

public class FrameworkLogSwitchConfig {
    public static boolean FWK_DEBUG;
    public static boolean FWK_FLOW;
    public static FrameworkLogSwitchInfo[] FwkLogSwitchArray = new FrameworkLogSwitchInfo[18];

    static class FrameworkLogSwitchInfo {
        private boolean debug_switch;
        private boolean flow_switch;

        public FrameworkLogSwitchInfo(boolean debug, boolean flow) {
            this.debug_switch = debug;
            this.flow_switch = flow;
        }

        public FrameworkLogSwitchInfo(String module_tag) {
            boolean z = true;
            boolean z2 = FrameworkLogSwitchConfig.FWK_DEBUG || (Log.HWModuleLog && Log.isLoggable(module_tag, 3));
            this.debug_switch = z2;
            if (!(FrameworkLogSwitchConfig.FWK_FLOW || (Log.HWModuleLog && Log.isLoggable(module_tag, 4)))) {
                z = false;
            }
            this.flow_switch = z;
        }

        public boolean isDebug_switch() {
            return this.debug_switch;
        }

        public void setDebug_switch(boolean debug_switch) {
            this.debug_switch = debug_switch;
        }

        public boolean isFlow_switch() {
            return this.flow_switch;
        }

        public void setFlow_switch(boolean info_switch) {
            this.flow_switch = info_switch;
        }
    }

    public enum LOG_SWITCH {
        DEBUG,
        FLOW
    }

    static {
        boolean z = true;
        int i = 0;
        boolean z2 = Log.HWLog || (Log.HWModuleLog && Log.isLoggable(FrameworkTagConstant.FWK_MODULE_TAG[0], 3));
        FWK_DEBUG = z2;
        if (!(Log.HWINFO || (Log.HWModuleLog && Log.isLoggable(FrameworkTagConstant.FWK_MODULE_TAG[0], 4)))) {
            z = false;
        }
        FWK_FLOW = z;
        int i2 = 0;
        String[] strArr = FrameworkTagConstant.FWK_MODULE_TAG;
        int length = strArr.length;
        while (i < length) {
            int i3 = i2 + 1;
            FwkLogSwitchArray[i2] = new FrameworkLogSwitchInfo(strArr[i]);
            i++;
            i2 = i3;
        }
    }

    public static boolean getModuleLogSwitch(int module_tag, LOG_SWITCH log_switch) {
        int module_index = module_tag / 100;
        if (module_index >= 18) {
            return false;
        }
        switch (log_switch) {
            case DEBUG:
                return FwkLogSwitchArray[module_index].debug_switch;
            case FLOW:
                return FwkLogSwitchArray[module_index].flow_switch;
            default:
                return false;
        }
    }

    public static boolean setModuleLogSwitchON(int module_tag) {
        int module_index = module_tag / 100;
        if (module_index >= 18) {
            return false;
        }
        FwkLogSwitchArray[module_index].debug_switch = true;
        FwkLogSwitchArray[module_index].flow_switch = true;
        return true;
    }

    public static boolean setModuleLogSwitchOFF(int module_tag) {
        int module_index = module_tag / 100;
        if (module_index >= 18) {
            return false;
        }
        FwkLogSwitchArray[module_index].debug_switch = false;
        FwkLogSwitchArray[module_index].flow_switch = false;
        return true;
    }
}
