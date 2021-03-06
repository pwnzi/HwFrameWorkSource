package com.android.internal.telephony;

import android.content.Context;
import android.hardware.radio.V1_0.ApnTypes;
import android.hardware.radio.V1_0.RadioAccessFamily;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Registrant;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import com.android.internal.telephony.Call.State;
import com.android.internal.telephony.Connection.PostDialState;
import com.android.internal.telephony.cdma.CdmaCallWaitingNotification;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.google.android.mms.pdu.CharacterSets;
import huawei.cust.HwCfgFilePolicy;

public class GsmCdmaConnection extends AbstractGsmCdmaConnection {
    private static final boolean DBG = true;
    static final int EVENT_DTMF_DELAY_DONE = 5;
    static final int EVENT_DTMF_DONE = 1;
    static final int EVENT_NEXT_POST_DIAL = 3;
    static final int EVENT_PAUSE_DONE = 2;
    static final int EVENT_WAKE_LOCK_TIMEOUT = 4;
    private static final String LOG_TAG = "GsmCdmaConnection";
    static final int PAUSE_DELAY_MILLIS_CDMA = 2000;
    static final int PAUSE_DELAY_MILLIS_GSM = 3000;
    static final String PROP_ENABLE_TEST_CLEARCODE = "persist.enable.test.clearcode";
    static final String PROP_FOR_TEST_CLEARCODE = "persist.radio.test.clearcode";
    private static final boolean VDBG = false;
    static final int WAKE_LOCK_TIMEOUT_MILLIS = 60000;
    static final boolean mIsShowRedirectNumber = SystemProperties.get("ro.product.custom", "NULL").contains("docomo");
    long mDisconnectTime;
    boolean mDisconnected;
    private int mDtmfToneDelay = 0;
    Handler mHandler;
    int mIndex;
    private boolean mIsEmergencyCall = false;
    GsmCdmaCallTracker mOwner;
    GsmCdmaCall mParent;
    private WakeLock mPartialWakeLock;
    int mPreciseCause = 0;
    UUSInfo mUusInfo;
    String mVendorCause;

    class MyHandler extends Handler {
        MyHandler(Looper l) {
            super(l);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    GsmCdmaConnection.this.mHandler.sendMessageDelayed(GsmCdmaConnection.this.mHandler.obtainMessage(5), (long) GsmCdmaConnection.this.mDtmfToneDelay);
                    return;
                case 2:
                case 3:
                case 5:
                    GsmCdmaConnection.this.processNextPostDialChar();
                    return;
                case 4:
                    GsmCdmaConnection.this.releaseWakeLock();
                    return;
                default:
                    return;
            }
        }
    }

    public GsmCdmaConnection(GsmCdmaPhone phone, DriverCall dc, GsmCdmaCallTracker ct, int index) {
        super(phone.getPhoneType());
        createWakeLock(phone.getContext());
        acquireWakeLock();
        this.mOwner = ct;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mAddress = dc.number;
        this.mIsEmergencyCall = PhoneNumberUtils.isLocalEmergencyNumber(phone.getContext(), this.mAddress);
        this.mDialAddress = this.mAddress;
        this.mIsIncoming = dc.isMT;
        this.mCreateTime = System.currentTimeMillis();
        this.mCnapName = dc.name;
        this.mCnapNamePresentation = dc.namePresentation;
        this.mNumberPresentation = dc.numberPresentation;
        this.mUusInfo = dc.uusInfo;
        this.mIndex = index;
        this.mParent = parentFromDCState(dc.state);
        this.mParent.attach(this, dc);
        if (mIsShowRedirectNumber) {
            this.mRedirectAddress = dc.redirectNumber;
            this.mRedirectNumberPresentation = dc.redirectNumberPresentation;
        }
        fetchDtmfToneDelay(phone);
        setAudioQuality(getAudioQualityFromDC(dc.audioQuality));
    }

    public GsmCdmaConnection(GsmCdmaPhone phone, String dialString, GsmCdmaCallTracker ct, GsmCdmaCall parent, boolean isEmergencyCall) {
        super(phone.getPhoneType());
        createWakeLock(phone.getContext());
        acquireWakeLock();
        this.mOwner = ct;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        if (isPhoneTypeGsm()) {
            this.mDialString = dialString;
        } else {
            this.mDialString = dialString;
            String str = LOG_TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("[GsmCdmaConn] GsmCdmaConnection: dialString=");
            stringBuilder.append(maskDialString(dialString));
            Rlog.d(str, stringBuilder.toString());
            dialString = formatDialString(dialString);
            str = LOG_TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append("[GsmCdmaConn] GsmCdmaConnection:formated dialString=");
            stringBuilder.append(maskDialString(dialString));
            Rlog.d(str, stringBuilder.toString());
        }
        this.mAddress = PhoneNumberUtils.extractNetworkPortionAlt(this.mDialString);
        this.mDialAddress = PhoneNumberUtils.extractNetworkPortionAlt(dialString);
        this.mIsEmergencyCall = isEmergencyCall;
        this.mPostDialString = PhoneNumberUtils.extractPostDialPortion(dialString);
        this.mIndex = -1;
        this.mIsIncoming = false;
        this.mCnapName = null;
        this.mCnapNamePresentation = 1;
        this.mNumberPresentation = 1;
        this.mCreateTime = System.currentTimeMillis();
        if (parent != null) {
            this.mParent = parent;
            if (isPhoneTypeGsm()) {
                parent.attachFake(this, State.DIALING);
            } else if (parent.mState == State.ACTIVE) {
                parent.attachFake(this, State.ACTIVE);
            } else {
                parent.attachFake(this, State.DIALING);
            }
        }
        fetchDtmfToneDelay(phone);
    }

    public GsmCdmaConnection(Context context, CdmaCallWaitingNotification cw, GsmCdmaCallTracker ct, GsmCdmaCall parent) {
        super(parent.getPhone().getPhoneType());
        createWakeLock(context);
        acquireWakeLock();
        this.mOwner = ct;
        this.mHandler = new MyHandler(this.mOwner.getLooper());
        this.mAddress = cw.number;
        this.mDialAddress = this.mAddress;
        this.mNumberPresentation = cw.numberPresentation;
        this.mCnapName = cw.name;
        this.mCnapNamePresentation = cw.namePresentation;
        this.mIndex = -1;
        this.mIsIncoming = true;
        this.mCreateTime = System.currentTimeMillis();
        this.mConnectTime = 0;
        this.mParent = parent;
        parent.attachFake(this, State.WAITING);
    }

    public boolean isNewConnection(CdmaCallWaitingNotification cw) {
        return (cw == null || this.mAddress == null || this.mAddress.equals(cw.number)) ? false : true;
    }

    public void dispose() {
        clearPostDialListeners();
        if (this.mParent != null) {
            this.mParent.detach(this);
        }
        releaseAllWakeLocks();
    }

    static boolean equalsHandlesNulls(Object a, Object b) {
        if (a == null) {
            return b == null;
        } else {
            return a.equals(b);
        }
    }

    static boolean equalsBaseDialString(String a, String b) {
        if (a == null) {
            if (b != null) {
                return false;
            }
        } else if (b == null || !a.startsWith(b)) {
            return false;
        }
        return true;
    }

    public static String formatDialString(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        int length = phoneNumber.length();
        StringBuilder ret = new StringBuilder();
        int currIndex = 0;
        while (currIndex < length) {
            char c = phoneNumber.charAt(currIndex);
            if (!isPause(c) && !isWait(c)) {
                ret.append(c);
            } else if (currIndex < length - 1) {
                int nextIndex = findNextPCharOrNonPOrNonWCharIndex(phoneNumber, currIndex);
                if (nextIndex < length) {
                    ret.append(findPOrWCharToAppend(phoneNumber, currIndex, nextIndex));
                    if (nextIndex > currIndex + 1) {
                        currIndex = nextIndex - 1;
                    }
                } else if (nextIndex == length) {
                    currIndex = length - 1;
                }
            }
            currIndex++;
        }
        return PhoneNumberUtils.cdmaCheckAndProcessPlusCode(ret.toString());
    }

    boolean compareTo(DriverCall c) {
        boolean z = true;
        if (!this.mIsIncoming && !c.isMT) {
            return true;
        }
        if (isPhoneTypeGsm() && this.mOrigConnection != null) {
            return true;
        }
        String cAddress = PhoneNumberUtils.stringFromStringAndTOA(c.number, c.TOA);
        if (!(this.mIsIncoming == c.isMT && equalsHandlesNulls(this.mAddress, cAddress))) {
            z = false;
        }
        return z;
    }

    public String getOrigDialString() {
        return this.mDialString;
    }

    public GsmCdmaCall getCall() {
        return this.mParent;
    }

    public long getDisconnectTime() {
        return this.mDisconnectTime;
    }

    public long getHoldDurationMillis() {
        if (getState() != State.HOLDING) {
            return 0;
        }
        return SystemClock.elapsedRealtime() - this.mHoldingStartTime;
    }

    public State getState() {
        if (this.mDisconnected) {
            return State.DISCONNECTED;
        }
        return super.getState();
    }

    public void hangup() throws CallStateException {
        if (this.mDisconnected) {
            throw new CallStateException("disconnected");
        }
        this.mOwner.hangup(this);
    }

    public void deflect(String number) throws CallStateException {
        throw new CallStateException("deflect is not supported for CS");
    }

    public void separate() throws CallStateException {
        if (this.mDisconnected) {
            throw new CallStateException("disconnected");
        }
        this.mOwner.separate(this);
    }

    public void proceedAfterWaitChar() {
        if (this.mPostDialState != PostDialState.WAIT) {
            String str = LOG_TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("GsmCdmaConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WAIT but was ");
            stringBuilder.append(this.mPostDialState);
            Rlog.w(str, stringBuilder.toString());
            return;
        }
        setPostDialState(PostDialState.STARTED);
        processNextPostDialChar();
    }

    public void proceedAfterWildChar(String str) {
        StringBuilder stringBuilder;
        if (this.mPostDialState != PostDialState.WILD) {
            String str2 = LOG_TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append("GsmCdmaConnection.proceedAfterWaitChar(): Expected getPostDialState() to be WILD but was ");
            stringBuilder.append(this.mPostDialState);
            Rlog.w(str2, stringBuilder.toString());
            return;
        }
        setPostDialState(PostDialState.STARTED);
        StringBuilder buf = new StringBuilder(str);
        buf.append(this.mPostDialString.substring(this.mNextPostDialChar));
        this.mPostDialString = buf.toString();
        this.mNextPostDialChar = 0;
        stringBuilder = new StringBuilder();
        stringBuilder.append("proceedAfterWildChar: new postDialString is ");
        stringBuilder.append(this.mPostDialString);
        log(stringBuilder.toString());
        processNextPostDialChar();
    }

    public void cancelPostDial() {
        setPostDialState(PostDialState.CANCELLED);
    }

    void onHangupLocal() {
        this.mCause = 3;
        this.mPreciseCause = 0;
        this.mVendorCause = null;
    }

    int disconnectCauseFromCode(int causeCode) {
        if (SystemProperties.getBoolean(PROP_ENABLE_TEST_CLEARCODE, false)) {
            String testClearCode = SystemProperties.get(PROP_FOR_TEST_CLEARCODE, "-1");
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("disconnectCauseFromCode: real ClearCode = ");
            stringBuilder.append(causeCode);
            stringBuilder.append(", testClearCode = ");
            stringBuilder.append(testClearCode);
            log(stringBuilder.toString());
            try {
                causeCode = Integer.parseInt(testClearCode);
            } catch (NumberFormatException e) {
                String str = LOG_TAG;
                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append("disconnectCauseFromCode: NumberFormatException for testClearCode:");
                stringBuilder2.append(testClearCode);
                stringBuilder2.append(",");
                stringBuilder2.append(e);
                Rlog.e(str, stringBuilder2.toString());
            }
        }
        SystemProperties.set("persist.radio.disconnectCode", String.valueOf(causeCode));
        switch (causeCode) {
            case 17:
                return 4;
            case 18:
                return 1046;
            case 19:
                return 13;
            default:
                switch (causeCode) {
                    case 21:
                        return 1006;
                    case 22:
                        return 1007;
                    default:
                        switch (causeCode) {
                            case 27:
                                return 1003;
                            case 28:
                                return 7;
                            case 29:
                                return 1009;
                            case 30:
                                return 1010;
                            case 31:
                                return 65;
                            default:
                                switch (causeCode) {
                                    case 41:
                                        return 1013;
                                    case 42:
                                        return 1014;
                                    case 43:
                                        return CharacterSets.UTF_16;
                                    case 44:
                                        return 1016;
                                    default:
                                        switch (causeCode) {
                                            case 49:
                                                return 1018;
                                            case 50:
                                                return 1019;
                                            default:
                                                switch (causeCode) {
                                                    case 57:
                                                        return 1004;
                                                    case 58:
                                                        return 1021;
                                                    default:
                                                        switch (causeCode) {
                                                            case 68:
                                                                return 15;
                                                            case 69:
                                                                return RadioAccessFamily.HSUPA;
                                                            case 70:
                                                                return 1025;
                                                            default:
                                                                switch (causeCode) {
                                                                    case 87:
                                                                        return 1028;
                                                                    case 88:
                                                                        return 1029;
                                                                    default:
                                                                        switch (causeCode) {
                                                                            case 95:
                                                                                return 1031;
                                                                            case 96:
                                                                                return 1032;
                                                                            case 97:
                                                                                return 1033;
                                                                            case 98:
                                                                                return 1034;
                                                                            case 99:
                                                                                return 1035;
                                                                            case 100:
                                                                                return 1036;
                                                                            case 101:
                                                                                return 1037;
                                                                            case 102:
                                                                                return 1038;
                                                                            default:
                                                                                switch (causeCode) {
                                                                                    case 240:
                                                                                        return 20;
                                                                                    case 241:
                                                                                        return 21;
                                                                                    default:
                                                                                        switch (causeCode) {
                                                                                            case 243:
                                                                                                return 58;
                                                                                            case 244:
                                                                                                return 46;
                                                                                            case 245:
                                                                                                return 47;
                                                                                            case 246:
                                                                                                return 48;
                                                                                            default:
                                                                                                switch (causeCode) {
                                                                                                    case CallFailCause.EMERGENCY_TEMP_FAILURE /*325*/:
                                                                                                        return 63;
                                                                                                    case CallFailCause.EMERGENCY_PERM_FAILURE /*326*/:
                                                                                                        return 64;
                                                                                                    default:
                                                                                                        switch (causeCode) {
                                                                                                            case 1000:
                                                                                                                return 26;
                                                                                                            case 1001:
                                                                                                                return 27;
                                                                                                            case 1002:
                                                                                                                return 28;
                                                                                                            case 1003:
                                                                                                                return 29;
                                                                                                            case 1004:
                                                                                                                return 30;
                                                                                                            case 1005:
                                                                                                                return 31;
                                                                                                            case 1006:
                                                                                                                return 32;
                                                                                                            case 1007:
                                                                                                                return 33;
                                                                                                            case 1008:
                                                                                                                return 34;
                                                                                                            case 1009:
                                                                                                                return 35;
                                                                                                            default:
                                                                                                                switch (causeCode) {
                                                                                                                    case 1:
                                                                                                                        return 25;
                                                                                                                    case 3:
                                                                                                                        return 1044;
                                                                                                                    case 6:
                                                                                                                        return 1005;
                                                                                                                    case 8:
                                                                                                                        return 1045;
                                                                                                                    case 25:
                                                                                                                        return 1008;
                                                                                                                    case 34:
                                                                                                                        return 1043;
                                                                                                                    case 38:
                                                                                                                        return 1012;
                                                                                                                    case 47:
                                                                                                                        return 1017;
                                                                                                                    case 55:
                                                                                                                        return 1020;
                                                                                                                    case 63:
                                                                                                                        return 1022;
                                                                                                                    case 65:
                                                                                                                        return ApnTypes.ALL;
                                                                                                                    case 79:
                                                                                                                        return 1026;
                                                                                                                    case 81:
                                                                                                                        return 1027;
                                                                                                                    case 91:
                                                                                                                        return 1030;
                                                                                                                    case 111:
                                                                                                                        return 1039;
                                                                                                                    case 127:
                                                                                                                        return 1040;
                                                                                                                    default:
                                                                                                                        AppState uiccAppState;
                                                                                                                        GsmCdmaPhone phone = this.mOwner.getPhone();
                                                                                                                        int serviceState = phone.getServiceState().getState();
                                                                                                                        UiccCardApplication cardApp = phone.getUiccCardApplication();
                                                                                                                        if (cardApp != null) {
                                                                                                                            uiccAppState = cardApp.getState();
                                                                                                                        } else {
                                                                                                                            uiccAppState = AppState.APPSTATE_UNKNOWN;
                                                                                                                        }
                                                                                                                        if (serviceState == 3) {
                                                                                                                            return 17;
                                                                                                                        }
                                                                                                                        if (!this.mIsEmergencyCall) {
                                                                                                                            if (serviceState == 1 || serviceState == 2) {
                                                                                                                                if (PhoneNumberUtils.isEmergencyNumber(this.mAddress)) {
                                                                                                                                    return 2;
                                                                                                                                }
                                                                                                                                return 18;
                                                                                                                            } else if (uiccAppState != AppState.APPSTATE_READY && (isPhoneTypeGsm() || phone.mCdmaSubscriptionSource == 0)) {
                                                                                                                                return 19;
                                                                                                                            }
                                                                                                                        }
                                                                                                                        if (isPhoneTypeGsm()) {
                                                                                                                        }
                                                                                                                        if (causeCode == 16) {
                                                                                                                            return 2;
                                                                                                                        }
                                                                                                                        return 36;
                                                                                                                }
                                                                                                        }
                                                                                                }
                                                                                        }
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }
                }
        }
    }

    void onRemoteDisconnect(int causeCode, String vendorCause) {
        this.mPreciseCause = causeCode;
        this.mVendorCause = vendorCause;
        int cause = disconnectCauseFromCode(causeCode);
        if (this.mOwner.isInCsRedial()) {
            Rlog.d(LOG_TAG, "onRemoteDisconnect, CS redial.");
            cause = 500;
        }
        onDisconnect(cause);
    }

    public boolean onDisconnect(int cause) {
        boolean changed = false;
        this.mCause = cause;
        if (!this.mDisconnected) {
            doDisconnect();
            String str = LOG_TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("onDisconnect: cause=");
            stringBuilder.append(cause);
            Rlog.d(str, stringBuilder.toString());
            this.mOwner.getPhone().notifyDisconnect(this);
            notifyDisconnect(cause);
            if (this.mParent != null) {
                changed = this.mParent.connectionDisconnected(this);
            }
            this.mOrigConnection = null;
        }
        clearPostDialListeners();
        this.mOwner.cleanRilRecovery();
        releaseWakeLock();
        return changed;
    }

    void onLocalDisconnect() {
        if (!this.mDisconnected) {
            doDisconnect();
            if (this.mParent != null) {
                this.mParent.detach(this);
            }
        }
        releaseWakeLock();
    }

    public boolean update(DriverCall dc) {
        int subid;
        StringBuilder stringBuilder;
        boolean changed = false;
        boolean wasConnectingInOrOut = isConnectingInOrOut();
        boolean z = true;
        boolean wasHolding = getState() == State.HOLDING;
        boolean showConnectedNum = SystemProperties.getBoolean("ro.config.hw_mo_num_update", false);
        try {
            GsmCdmaPhone phone = this.mOwner.getPhone();
            if (phone != null) {
                subid = phone.getSubId();
                UiccCardApplication cardApp = phone.getUiccCardApplication();
                AppState uiccAppState;
                if (cardApp != null) {
                    uiccAppState = cardApp.getState();
                } else {
                    uiccAppState = AppState.APPSTATE_UNKNOWN;
                }
                Boolean custnumber = (Boolean) HwCfgFilePolicy.getValue("mo_num_update", SubscriptionManager.getSlotIndex(subid), Boolean.class);
                if (custnumber != null && uiccAppState == AppState.APPSTATE_READY) {
                    showConnectedNum = custnumber.booleanValue();
                }
            }
        } catch (Exception e) {
            log("update: read mo_num_update error!");
        }
        GsmCdmaCall newParent = parentFromDCState(dc.state);
        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append("parent= ");
        stringBuilder2.append(this.mParent);
        stringBuilder2.append(", newParent= ");
        stringBuilder2.append(newParent);
        log(stringBuilder2.toString());
        if (isPhoneTypeGsm() && this.mOrigConnection != null && (!showConnectedNum || this.mIsIncoming)) {
            log("update: mOrigConnection is not null");
        } else if (isIncoming() && !equalsHandlesNulls(this.mAddress, dc.number) && (!(this.mNumberConverted && equalsHandlesNulls(this.mConvertedNumber, dc.number)) && (showConnectedNum || this.mIsIncoming))) {
            log("update: phone # changed!");
            this.mAddress = TextUtils.equals(this.mDialAddress, dc.number) ? this.mAddress : dc.number;
            this.mDialAddress = TextUtils.isEmpty(this.mDialAddress) ? dc.number : this.mDialAddress;
            changed = true;
        }
        subid = getAudioQualityFromDC(dc.audioQuality);
        if (getAudioQuality() != subid) {
            stringBuilder = new StringBuilder();
            stringBuilder.append("update: audioQuality # changed!:  ");
            stringBuilder.append(subid == 2 ? "high" : "standard");
            log(stringBuilder.toString());
            setAudioQuality(subid);
            changed = true;
        }
        if (TextUtils.isEmpty(dc.name)) {
            if (!TextUtils.isEmpty(this.mCnapName)) {
                changed = true;
                this.mCnapName = "";
            }
        } else if (!dc.name.equals(this.mCnapName)) {
            changed = true;
            this.mCnapName = dc.name;
        }
        stringBuilder = new StringBuilder();
        stringBuilder.append("--dssds----");
        stringBuilder.append(this.mCnapName);
        log(stringBuilder.toString());
        this.mCnapNamePresentation = dc.namePresentation;
        this.mNumberPresentation = dc.numberPresentation;
        if (mIsShowRedirectNumber) {
            this.mRedirectAddress = dc.redirectNumber;
            this.mRedirectNumberPresentation = dc.redirectNumberPresentation;
        }
        if (newParent != this.mParent) {
            if (this.mParent != null) {
                this.mParent.detach(this);
            }
            newParent.attach(this, dc);
            this.mParent = newParent;
            changed = true;
        } else {
            boolean z2 = changed || this.mParent.update(this, dc);
            changed = z2;
        }
        stringBuilder = new StringBuilder();
        stringBuilder.append("update: parent=");
        stringBuilder.append(this.mParent);
        stringBuilder.append(", hasNewParent=");
        if (newParent == this.mParent) {
            z = false;
        }
        stringBuilder.append(z);
        stringBuilder.append(", wasConnectingInOrOut=");
        stringBuilder.append(wasConnectingInOrOut);
        stringBuilder.append(", wasHolding=");
        stringBuilder.append(wasHolding);
        stringBuilder.append(", isConnectingInOrOut=");
        stringBuilder.append(isConnectingInOrOut());
        stringBuilder.append(", changed=");
        stringBuilder.append(changed);
        log(stringBuilder.toString());
        if (wasConnectingInOrOut && !isConnectingInOrOut()) {
            onConnectedInOrOut();
        }
        if (changed && !wasHolding && getState() == State.HOLDING) {
            onStartedHolding();
        }
        return changed;
    }

    void fakeHoldBeforeDial() {
        if (this.mParent != null) {
            this.mParent.detach(this);
        }
        this.mParent = this.mOwner.mBackgroundCall;
        this.mParent.attachFake(this, State.HOLDING);
        onStartedHolding();
    }

    int getGsmCdmaIndex() throws CallStateException {
        if (this.mIndex >= 0) {
            return this.mIndex + 1;
        }
        throw new CallStateException("GsmCdma index not yet assigned");
    }

    void onConnectedInOrOut() {
        this.mConnectTime = System.currentTimeMillis();
        this.mConnectTimeReal = SystemClock.elapsedRealtime();
        this.mDuration = 0;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("onConnectedInOrOut: connectTime=");
        stringBuilder.append(this.mConnectTime);
        log(stringBuilder.toString());
        if (this.mIsIncoming) {
            releaseWakeLock();
        } else {
            processNextPostDialChar();
        }
    }

    private void doDisconnect() {
        this.mIndex = -1;
        this.mDisconnectTime = System.currentTimeMillis();
        this.mDuration = SystemClock.elapsedRealtime() - this.mConnectTimeReal;
        this.mDisconnected = true;
        clearPostDialListeners();
    }

    void onStartedHolding() {
        this.mHoldingStartTime = SystemClock.elapsedRealtime();
    }

    private boolean processPostDialChar(char c) {
        if (PhoneNumberUtils.is12Key(c)) {
            if (isPhoneTypeGsm()) {
                this.mOwner.mCi.sendDtmf(c, this.mHandler.obtainMessage(1));
            } else {
                this.mOwner.mCi.sendBurstDtmf(Character.toString(c), 0, 0, this.mHandler.obtainMessage(1));
            }
        } else if (isPause(c)) {
            if (!isPhoneTypeGsm()) {
                setPostDialState(PostDialState.PAUSE);
            }
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(2), isPhoneTypeGsm() ? 3000 : 2000);
        } else if (isWait(c)) {
            setPostDialState(PostDialState.WAIT);
        } else if (!isWild(c)) {
            return false;
        } else {
            setPostDialState(PostDialState.WILD);
        }
        return true;
    }

    public String getRemainingPostDialString() {
        String subStr = super.getRemainingPostDialString();
        if (isPhoneTypeGsm() || TextUtils.isEmpty(subStr)) {
            return subStr;
        }
        int wIndex = subStr.indexOf(59);
        int pIndex = subStr.indexOf(44);
        if (wIndex > 0 && (wIndex < pIndex || pIndex <= 0)) {
            return subStr.substring(0, wIndex);
        }
        if (pIndex > 0) {
            return subStr.substring(0, pIndex);
        }
        return subStr;
    }

    public void updateParent(GsmCdmaCall oldParent, GsmCdmaCall newParent) {
        if (newParent != oldParent) {
            if (oldParent != null) {
                oldParent.detach(this);
            }
            newParent.attachFake(this, State.ACTIVE);
            this.mParent = newParent;
        }
    }

    protected void finalize() {
        if (this.mPartialWakeLock != null && this.mPartialWakeLock.isHeld()) {
            Rlog.e(LOG_TAG, "UNEXPECTED; mPartialWakeLock is held when finalizing.");
        }
        clearPostDialListeners();
        releaseWakeLock();
    }

    private void processNextPostDialChar() {
        if (this.mPostDialState == PostDialState.CANCELLED) {
            releaseWakeLock();
            return;
        }
        char c;
        if (this.mPostDialString == null || this.mPostDialString.length() <= this.mNextPostDialChar) {
            setPostDialState(PostDialState.COMPLETE);
            releaseWakeLock();
            c = 0;
        } else {
            setPostDialState(PostDialState.STARTED);
            String str = this.mPostDialString;
            int i = this.mNextPostDialChar;
            this.mNextPostDialChar = i + 1;
            c = str.charAt(i);
            if (!processPostDialChar(c)) {
                this.mHandler.obtainMessage(3).sendToTarget();
                String str2 = LOG_TAG;
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("processNextPostDialChar: c=");
                stringBuilder.append(c);
                stringBuilder.append(" isn't valid!");
                Rlog.e(str2, stringBuilder.toString());
                return;
            }
        }
        notifyPostDialListenersNextChar(c);
        Registrant postDialHandler = this.mOwner.getPhone().getPostDialHandler();
        if (postDialHandler != null) {
            Message messageForRegistrant = postDialHandler.messageForRegistrant();
            Message notifyMessage = messageForRegistrant;
            if (messageForRegistrant != null) {
                PostDialState state = this.mPostDialState;
                AsyncResult ar = AsyncResult.forMessage(notifyMessage);
                ar.result = this;
                ar.userObj = state;
                notifyMessage.arg1 = c;
                notifyMessage.sendToTarget();
            }
        }
    }

    private boolean isConnectingInOrOut() {
        return this.mParent == null || this.mParent == this.mOwner.mRingingCall || this.mParent.mState == State.DIALING || this.mParent.mState == State.ALERTING;
    }

    private GsmCdmaCall parentFromDCState(DriverCall.State state) {
        switch (state) {
            case ACTIVE:
            case DIALING:
            case ALERTING:
                return this.mOwner.mForegroundCall;
            case HOLDING:
                return this.mOwner.mBackgroundCall;
            case INCOMING:
            case WAITING:
                return this.mOwner.mRingingCall;
            default:
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("illegal call state: ");
                stringBuilder.append(state);
                throw new RuntimeException(stringBuilder.toString());
        }
    }

    private int getAudioQualityFromDC(int audioQuality) {
        if (audioQuality == 2 || audioQuality == 9) {
            return 2;
        }
        return 1;
    }

    private void setPostDialState(PostDialState s) {
        if (s == PostDialState.STARTED || s == PostDialState.PAUSE) {
            synchronized (this.mPartialWakeLock) {
                if (this.mPartialWakeLock.isHeld()) {
                    this.mHandler.removeMessages(4);
                } else {
                    acquireWakeLock();
                }
                this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(4), 60000);
            }
        } else {
            this.mHandler.removeMessages(4);
            releaseWakeLock();
        }
        this.mPostDialState = s;
        notifyPostDialListeners();
    }

    private void createWakeLock(Context context) {
        this.mPartialWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, LOG_TAG);
    }

    private void acquireWakeLock() {
        if (this.mPartialWakeLock != null) {
            synchronized (this.mPartialWakeLock) {
                log("acquireWakeLock");
                this.mPartialWakeLock.acquire();
            }
        }
    }

    protected void releaseWakeLock() {
        if (this.mPartialWakeLock != null) {
            synchronized (this.mPartialWakeLock) {
                if (this.mPartialWakeLock.isHeld()) {
                    log("releaseWakeLock");
                    this.mPartialWakeLock.release();
                }
            }
        }
    }

    private void releaseAllWakeLocks() {
        if (this.mPartialWakeLock != null) {
            synchronized (this.mPartialWakeLock) {
                while (this.mPartialWakeLock.isHeld()) {
                    this.mPartialWakeLock.release();
                }
            }
        }
    }

    private static boolean isPause(char c) {
        return c == ',';
    }

    private static boolean isWait(char c) {
        return c == ';';
    }

    private static boolean isWild(char c) {
        return c == 'N';
    }

    private static int findNextPCharOrNonPOrNonWCharIndex(String phoneNumber, int currIndex) {
        boolean wMatched = isWait(phoneNumber.charAt(currIndex));
        int index = currIndex + 1;
        int length = phoneNumber.length();
        while (index < length) {
            char cNext = phoneNumber.charAt(index);
            if (isWait(cNext)) {
                wMatched = true;
            }
            if (!isWait(cNext) && !isPause(cNext)) {
                break;
            }
            index++;
        }
        if (index >= length || index <= currIndex + 1 || wMatched || !isPause(phoneNumber.charAt(currIndex))) {
            return index;
        }
        return currIndex + 1;
    }

    private static char findPOrWCharToAppend(String phoneNumber, int currPwIndex, int nextNonPwCharIndex) {
        char ret = isPause(phoneNumber.charAt(currPwIndex)) ? ',' : ';';
        if (nextNonPwCharIndex > currPwIndex + 1) {
            return ';';
        }
        return ret;
    }

    private String maskDialString(String dialString) {
        return "<MASKED>";
    }

    private void fetchDtmfToneDelay(GsmCdmaPhone phone) {
        PersistableBundle b = ((CarrierConfigManager) phone.getContext().getSystemService("carrier_config")).getConfigForSubId(phone.getSubId());
        if (b != null) {
            this.mDtmfToneDelay = b.getInt(phone.getDtmfToneDelayKey());
        }
    }

    private boolean isPhoneTypeGsm() {
        return this.mOwner.getPhone().getPhoneType() == 1;
    }

    private void log(String msg) {
        String str = LOG_TAG;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[GsmCdmaConn] ");
        stringBuilder.append(msg);
        Rlog.d(str, stringBuilder.toString());
    }

    public int getNumberPresentation() {
        return this.mNumberPresentation;
    }

    public UUSInfo getUUSInfo() {
        return this.mUusInfo;
    }

    public int getPreciseDisconnectCause() {
        return this.mPreciseCause;
    }

    public String getVendorDisconnectCause() {
        return this.mVendorCause;
    }

    public void migrateFrom(Connection c) {
        if (c != null) {
            super.migrateFrom(c);
            this.mUusInfo = c.getUUSInfo();
            setUserData(c.getUserData());
        }
    }

    public Connection getOrigConnection() {
        return this.mOrigConnection;
    }

    public boolean isMultiparty() {
        if (this.mOrigConnection != null) {
            return this.mOrigConnection.isMultiparty();
        }
        return false;
    }

    public void setPostDialString(String postDialString) {
        this.mPostDialString = PhoneNumberUtils.extractPostDialPortion(postDialString);
    }
}
