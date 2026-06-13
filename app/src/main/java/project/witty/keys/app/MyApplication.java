package project.witty.keys.app;
import android.app.Application;
import android.util.Log;
import com.google.firebase.FirebaseApp;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import project.witty.keys.BuildConfig;
import project.witty.keys.app.helpers.JourneyTracer;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Crashlytics after Firebase is available. Robolectric unit tests
        // do not always run FirebaseInitProvider before Application.onCreate().
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this);
            }
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.setCrashlyticsCollectionEnabled(true);
            crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME);
            crashlytics.setCustomKey("build_type", BuildConfig.BUILD_TYPE);
            Log.d("WK_KEYBOARD", "[INIT] Crashlytics initialized");
        } catch (IllegalStateException e) {
            Log.w("WK_KEYBOARD", "[INIT] Crashlytics unavailable", e);
        }

        // Initialize JourneyTracer for SFOS observability
        JourneyTracer.init(
            "https://us-central1-tapai-e33d2.cloudfunctions.net",
            !BuildConfig.DEBUG  // production mode only in release builds
        );
    }
}
