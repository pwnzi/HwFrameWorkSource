package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public final class IpSecTunnelInterfaceResponse implements Parcelable {
    public static final Creator<IpSecTunnelInterfaceResponse> CREATOR = new Creator<IpSecTunnelInterfaceResponse>() {
        public IpSecTunnelInterfaceResponse createFromParcel(Parcel in) {
            return new IpSecTunnelInterfaceResponse(in, null);
        }

        public IpSecTunnelInterfaceResponse[] newArray(int size) {
            return new IpSecTunnelInterfaceResponse[size];
        }
    };
    private static final String TAG = "IpSecTunnelInterfaceResponse";
    public final String interfaceName;
    public final int resourceId;
    public final int status;

    /* synthetic */ IpSecTunnelInterfaceResponse(Parcel x0, AnonymousClass1 x1) {
        this(x0);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(this.status);
        out.writeInt(this.resourceId);
        out.writeString(this.interfaceName);
    }

    public IpSecTunnelInterfaceResponse(int inStatus) {
        if (inStatus != 0) {
            this.status = inStatus;
            this.resourceId = -1;
            this.interfaceName = "";
            return;
        }
        throw new IllegalArgumentException("Valid status implies other args must be provided");
    }

    public IpSecTunnelInterfaceResponse(int inStatus, int inResourceId, String inInterfaceName) {
        this.status = inStatus;
        this.resourceId = inResourceId;
        this.interfaceName = inInterfaceName;
    }

    private IpSecTunnelInterfaceResponse(Parcel in) {
        this.status = in.readInt();
        this.resourceId = in.readInt();
        this.interfaceName = in.readString();
    }
}
