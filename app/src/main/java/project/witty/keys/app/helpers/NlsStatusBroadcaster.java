package project.witty.keys.app.helpers;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * Broadcasts NLS connection status to keyboard and other components.
 * Components can listen for ACTION_NLS_STATUS to show/hide NLS-dependent features.
 */
public class NlsStatusBroadcaster {

    public static final String ACTION_NLS_STATUS = "project.witty.keys.NLS_STATUS";
    public static final String EXTRA_IS_CONNECTED = "is_connected";
    private static final String TAG = "WK_NLS";

    private static boolean lastKnownStatus = false;

    public static void sendStatus(Context context, boolean isConnected) {
        lastKnownStatus = isConnected;
        Intent intent = new Intent(ACTION_NLS_STATUS);
        intent.putExtra(EXTRA_IS_CONNECTED, isConnected);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        Log.d(TAG, "[NLS_STATUS] Broadcast: connected=" + isConnected);
    }

    public static boolean isNlsConnected() {
        return lastKnownStatus;
    }
}
