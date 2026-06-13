package project.witty.keys.app;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.List;

public class SubscriptionItem implements Parcelable{
    public String productId;
    public String originalPrice;
    public String finalPrice;
    public String name;
    public String billingPeriod;
    public List<String> benefits;

    public SubscriptionItem(String productId, String originalPrice, String finalPrice, String name, String billingPeriod, List<String> benefits) {
        this.productId = productId;
        this.originalPrice = originalPrice;
        this.finalPrice = finalPrice;
        this.name = name;
        this.billingPeriod = billingPeriod;
        this.benefits = benefits;
    }

    protected SubscriptionItem(Parcel in) {
        productId = in.readString();
        originalPrice = in.readString();
        finalPrice = in.readString();
        name = in.readString();
        billingPeriod = in.readString();
    }

    public static final Parcelable.Creator<SubscriptionItem> CREATOR = new Parcelable.Creator<SubscriptionItem>() {
        @Override
        public SubscriptionItem createFromParcel(Parcel in) {
            return new SubscriptionItem(in);
        }

        @Override
        public SubscriptionItem[] newArray(int size) {
            return new SubscriptionItem[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(productId);
        dest.writeString(originalPrice);
        dest.writeString(finalPrice);
        dest.writeString(name);
        dest.writeString(billingPeriod);
    }


}
