package android.print;

import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.text.TextUtils;
import com.android.internal.util.Preconditions;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class PrinterInfo implements Parcelable {
    public static final Creator<PrinterInfo> CREATOR = new Creator<PrinterInfo>() {
        public PrinterInfo createFromParcel(Parcel parcel) {
            return new PrinterInfo(parcel, null);
        }

        public PrinterInfo[] newArray(int size) {
            return new PrinterInfo[size];
        }
    };
    public static final int STATUS_BUSY = 2;
    public static final int STATUS_IDLE = 1;
    public static final int STATUS_UNAVAILABLE = 3;
    private final PrinterCapabilitiesInfo mCapabilities;
    private final int mCustomPrinterIconGen;
    private final String mDescription;
    private final boolean mHasCustomPrinterIcon;
    private final int mIconResourceId;
    private final PrinterId mId;
    private final PendingIntent mInfoIntent;
    private final String mName;
    private final int mStatus;

    public static final class Builder {
        private PrinterCapabilitiesInfo mCapabilities;
        private int mCustomPrinterIconGen;
        private String mDescription;
        private boolean mHasCustomPrinterIcon;
        private int mIconResourceId;
        private PendingIntent mInfoIntent;
        private String mName;
        private PrinterId mPrinterId;
        private int mStatus;

        public Builder(PrinterId printerId, String name, int status) {
            this.mPrinterId = PrinterInfo.checkPrinterId(printerId);
            this.mName = PrinterInfo.checkName(name);
            this.mStatus = PrinterInfo.checkStatus(status);
        }

        public Builder(PrinterInfo other) {
            this.mPrinterId = other.mId;
            this.mName = other.mName;
            this.mStatus = other.mStatus;
            this.mIconResourceId = other.mIconResourceId;
            this.mHasCustomPrinterIcon = other.mHasCustomPrinterIcon;
            this.mDescription = other.mDescription;
            this.mInfoIntent = other.mInfoIntent;
            this.mCapabilities = other.mCapabilities;
            this.mCustomPrinterIconGen = other.mCustomPrinterIconGen;
        }

        public Builder setStatus(int status) {
            this.mStatus = PrinterInfo.checkStatus(status);
            return this;
        }

        public Builder setIconResourceId(int iconResourceId) {
            this.mIconResourceId = Preconditions.checkArgumentNonnegative(iconResourceId, "iconResourceId can't be negative");
            return this;
        }

        public Builder setHasCustomPrinterIcon(boolean hasCustomPrinterIcon) {
            this.mHasCustomPrinterIcon = hasCustomPrinterIcon;
            return this;
        }

        public Builder setName(String name) {
            this.mName = PrinterInfo.checkName(name);
            return this;
        }

        public Builder setDescription(String description) {
            this.mDescription = description;
            return this;
        }

        public Builder setInfoIntent(PendingIntent infoIntent) {
            this.mInfoIntent = infoIntent;
            return this;
        }

        public Builder setCapabilities(PrinterCapabilitiesInfo capabilities) {
            this.mCapabilities = capabilities;
            return this;
        }

        public PrinterInfo build() {
            return new PrinterInfo(this.mPrinterId, this.mName, this.mStatus, this.mIconResourceId, this.mHasCustomPrinterIcon, this.mDescription, this.mInfoIntent, this.mCapabilities, this.mCustomPrinterIconGen, null);
        }

        public Builder incCustomPrinterIconGen() {
            this.mCustomPrinterIconGen++;
            return this;
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
    }

    /* synthetic */ PrinterInfo(Parcel x0, AnonymousClass1 x1) {
        this(x0);
    }

    /* synthetic */ PrinterInfo(PrinterId x0, String x1, int x2, int x3, boolean x4, String x5, PendingIntent x6, PrinterCapabilitiesInfo x7, int x8, AnonymousClass1 x9) {
        this(x0, x1, x2, x3, x4, x5, x6, x7, x8);
    }

    private PrinterInfo(PrinterId printerId, String name, int status, int iconResourceId, boolean hasCustomPrinterIcon, String description, PendingIntent infoIntent, PrinterCapabilitiesInfo capabilities, int customPrinterIconGen) {
        this.mId = printerId;
        this.mName = name;
        this.mStatus = status;
        this.mIconResourceId = iconResourceId;
        this.mHasCustomPrinterIcon = hasCustomPrinterIcon;
        this.mDescription = description;
        this.mInfoIntent = infoIntent;
        this.mCapabilities = capabilities;
        this.mCustomPrinterIconGen = customPrinterIconGen;
    }

    public PrinterId getId() {
        return this.mId;
    }

    public Drawable loadIcon(Context context) {
        Drawable drawable = null;
        PackageManager packageManager = context.getPackageManager();
        if (this.mHasCustomPrinterIcon) {
            Icon icon = ((PrintManager) context.getSystemService("print")).getCustomPrinterIcon(this.mId);
            if (icon != null) {
                drawable = icon.loadDrawable(context);
            }
        }
        if (drawable != null) {
            return drawable;
        }
        try {
            String packageName = this.mId.getServiceName().getPackageName();
            ApplicationInfo appInfo = packageManager.getPackageInfo(packageName, null).applicationInfo;
            if (this.mIconResourceId != 0) {
                drawable = packageManager.getDrawable(packageName, this.mIconResourceId, appInfo);
            }
            if (drawable == null) {
                return appInfo.loadIcon(packageManager);
            }
            return drawable;
        } catch (NameNotFoundException e) {
            return drawable;
        }
    }

    public boolean getHasCustomPrinterIcon() {
        return this.mHasCustomPrinterIcon;
    }

    public String getName() {
        return this.mName;
    }

    public int getStatus() {
        return this.mStatus;
    }

    public String getDescription() {
        return this.mDescription;
    }

    public PendingIntent getInfoIntent() {
        return this.mInfoIntent;
    }

    public PrinterCapabilitiesInfo getCapabilities() {
        return this.mCapabilities;
    }

    private static PrinterId checkPrinterId(PrinterId printerId) {
        return (PrinterId) Preconditions.checkNotNull(printerId, "printerId cannot be null.");
    }

    private static int checkStatus(int status) {
        if (status == 1 || status == 2 || status == 3) {
            return status;
        }
        throw new IllegalArgumentException("status is invalid.");
    }

    private static String checkName(String name) {
        return (String) Preconditions.checkStringNotEmpty(name, "name cannot be empty.");
    }

    private PrinterInfo(Parcel parcel) {
        this.mId = checkPrinterId((PrinterId) parcel.readParcelable(null));
        this.mName = checkName(parcel.readString());
        this.mStatus = checkStatus(parcel.readInt());
        this.mDescription = parcel.readString();
        this.mCapabilities = (PrinterCapabilitiesInfo) parcel.readParcelable(null);
        this.mIconResourceId = parcel.readInt();
        this.mHasCustomPrinterIcon = parcel.readByte() != (byte) 0;
        this.mCustomPrinterIconGen = parcel.readInt();
        this.mInfoIntent = (PendingIntent) parcel.readParcelable(null);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeParcelable(this.mId, flags);
        parcel.writeString(this.mName);
        parcel.writeInt(this.mStatus);
        parcel.writeString(this.mDescription);
        parcel.writeParcelable(this.mCapabilities, flags);
        parcel.writeInt(this.mIconResourceId);
        parcel.writeByte((byte) this.mHasCustomPrinterIcon);
        parcel.writeInt(this.mCustomPrinterIconGen);
        parcel.writeParcelable(this.mInfoIntent, flags);
    }

    public int hashCode() {
        int i = 0;
        int result = 31 * ((31 * ((31 * ((31 * ((31 * ((31 * ((31 * ((31 * ((31 * 1) + this.mId.hashCode())) + this.mName.hashCode())) + this.mStatus)) + (this.mDescription != null ? this.mDescription.hashCode() : 0))) + (this.mCapabilities != null ? this.mCapabilities.hashCode() : 0))) + this.mIconResourceId)) + this.mHasCustomPrinterIcon)) + this.mCustomPrinterIconGen);
        if (this.mInfoIntent != null) {
            i = this.mInfoIntent.hashCode();
        }
        return result + i;
    }

    public boolean equalsIgnoringStatus(PrinterInfo other) {
        if (!this.mId.equals(other.mId) || !this.mName.equals(other.mName) || !TextUtils.equals(this.mDescription, other.mDescription)) {
            return false;
        }
        if (this.mCapabilities == null) {
            if (other.mCapabilities != null) {
                return false;
            }
        } else if (!this.mCapabilities.equals(other.mCapabilities)) {
            return false;
        }
        if (this.mIconResourceId != other.mIconResourceId || this.mHasCustomPrinterIcon != other.mHasCustomPrinterIcon || this.mCustomPrinterIconGen != other.mCustomPrinterIconGen) {
            return false;
        }
        if (this.mInfoIntent == null) {
            if (other.mInfoIntent != null) {
                return false;
            }
        } else if (!this.mInfoIntent.equals(other.mInfoIntent)) {
            return false;
        }
        return true;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PrinterInfo other = (PrinterInfo) obj;
        if (equalsIgnoringStatus(other) && this.mStatus == other.mStatus) {
            return true;
        }
        return false;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("PrinterInfo{");
        builder.append("id=");
        builder.append(this.mId);
        builder.append(", name=");
        builder.append(this.mName);
        builder.append(", status=");
        builder.append(this.mStatus);
        builder.append(", description=");
        builder.append(this.mDescription);
        builder.append(", capabilities=");
        builder.append(this.mCapabilities);
        builder.append(", iconResId=");
        builder.append(this.mIconResourceId);
        builder.append(", hasCustomPrinterIcon=");
        builder.append(this.mHasCustomPrinterIcon);
        builder.append(", customPrinterIconGen=");
        builder.append(this.mCustomPrinterIconGen);
        builder.append(", infoIntent=");
        builder.append(this.mInfoIntent);
        builder.append("\"}");
        return builder.toString();
    }
}
