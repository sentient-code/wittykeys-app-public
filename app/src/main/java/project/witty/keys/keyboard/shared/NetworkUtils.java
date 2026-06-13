package project.witty.keys.keyboard.shared;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.util.Objects;

import project.witty.keys.R;

public class NetworkUtils {
    private Context context;

    public NetworkUtils(Context context) {
        this.context = context;
    }
    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    public String formatErrorMessage(Throwable e) {
        if (Objects.requireNonNull(e.getMessage()).contains("Unable to resolve host")) {
            return context.getString(R.string.error_device_network);
        } else if (e.getMessage().contains("timeout")) {
            return context.getString(R.string.error_server_timeout);
        } else {
            return context.getString(R.string.error_generic);
        }
    }
}
