package project.witty.keys.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.FirebaseException;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.TimeUnit;

import project.witty.keys.R;
import project.witty.keys.app.countryselector.CountryBottomSheetFragment;
import project.witty.keys.app.entities.User;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.helpers.EventHelpers;
import project.witty.keys.app.helpers.NotchHandler;

public class AuthenticationActivity extends BaseActivity {

    private static final int RC_SIGN_IN = 9001;
    private static final String TAG = "AuthenticationActivity";

    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private EditText phoneEditText, otpEditText;
    private String verificationId;
    private PhoneAuthProvider.ForceResendingToken verificationResendToken;
    private LinearLayout otpLayout;
    private Button showBottomSheetButton;
    private View loadingOverlay;
    private TextView loadingMessage;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotchHandler.configureEdgeToEdge(this);
        setContentView(R.layout.activity_authentication);
        NotchHandler.handleSystemBars(this);
        findViewById(R.id.auth_back_button).setOnClickListener(v -> finish());
        EncryptedPreferences.initialize(this);

        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        findViewById(R.id.sign_in_button).setOnClickListener(v -> signIn());
        findViewById(R.id.login_with_phone_button).setOnClickListener(v -> loginWithPhone());

        phoneEditText = findViewById(R.id.phone_edit_text);
        otpEditText = findViewById(R.id.otp_edit_text);
        otpLayout = findViewById(R.id.otp_layout);

        findViewById(R.id.verify_otp_button).setOnClickListener(v -> verifyOtp());
        findViewById(R.id.resend_otp_button).setOnClickListener(v -> resendOtp());

        showBottomSheetButton = findViewById(R.id.country_code_button);
        showBottomSheetButton.setOnClickListener(v -> showCountryBottomSheet());

        loadingOverlay = findViewById(R.id.loading_overlay);
        loadingMessage = loadingOverlay.findViewById(R.id.loading_text);
    }

    private void showLoadingOverlay(String message) {
            loadingMessage.setText(message);
            loadingOverlay.setVisibility(View.VISIBLE);
    }

    private void hideLoadingOverlay() {
            loadingOverlay.setVisibility(View.GONE);

    }

    private void showCountryBottomSheet() {
        CountryBottomSheetFragment bottomSheet = new CountryBottomSheetFragment();
        bottomSheet.setOnCountrySelectedListener(country -> {
            showBottomSheetButton.setText(country.getPhoneCode());
            Toast.makeText(this, "Selected: " + country.getName() + " (" + country.getPhoneCode() + ")", Toast.LENGTH_LONG).show();
        });
        bottomSheet.show(getSupportFragmentManager(), bottomSheet.getTag());
    }

    private void signIn() {
        showLoadingOverlay("Signing In...");
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void loginWithPhone() {
        String phoneDigits = phoneEditText.getText().toString().trim();
        if (phoneDigits.isEmpty()) {
            Toast.makeText(this, "Please enter a valid phone number.", Toast.LENGTH_SHORT).show();
            return;
        }
        String phoneNumber = showBottomSheetButton.getText() + phoneDigits;
        Log.d(TAG, "Phone sign-in requested");
        startPhoneVerification(phoneNumber, null);
    }

    private void resendOtp() {
        String phoneDigits = phoneEditText.getText().toString().trim();
        if (phoneDigits.isEmpty() || verificationResendToken == null) {
            Toast.makeText(this, "Please request an OTP first.", Toast.LENGTH_SHORT).show();
            return;
        }
        String phoneNumber = showBottomSheetButton.getText() + phoneDigits;
        Log.d(TAG, "Phone OTP resend requested");
        startPhoneVerification(phoneNumber, verificationResendToken);
    }

    private void startPhoneVerification(String phoneNumber, PhoneAuthProvider.ForceResendingToken resendToken) {
        showLoadingOverlay("Sending OTP...");
        PhoneAuthOptions.Builder optionsBuilder = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential phoneAuthCredential) {
                        signInWithPhoneAuthCredential(phoneAuthCredential);
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        hideLoadingOverlay();
                        Toast.makeText(AuthenticationActivity.this, "Verification failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken forceResendingToken) {
                        hideLoadingOverlay();
                        AuthenticationActivity.this.verificationId = verificationId;
                        AuthenticationActivity.this.verificationResendToken = forceResendingToken;
                        otpLayout.setVisibility(LinearLayout.VISIBLE);
                    }
                });
        if (resendToken != null) {
            optionsBuilder.setForceResendingToken(resendToken);
        }
        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build());
    }

    private void verifyOtp() {
        String otp = otpEditText.getText().toString();
        showLoadingOverlay("Verifying OTP...");
        if (otp.isEmpty() || verificationId == null) {
            hideLoadingOverlay();
            Toast.makeText(this, "Please enter a valid OTP.", Toast.LENGTH_SHORT).show();
            return;
        }

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
        signInWithPhoneAuthCredential(credential);
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        hideLoadingOverlay();
                        if (task.isSuccessful()) {
                            FirebaseUser user = task.getResult().getUser();
                            User.checkUserInFirestore(AuthenticationActivity.this,user, user.getPhoneNumber(), resolvePhoneAccountName(user), db, AuthenticationActivity.this::navigateToHome);
                        } else {
                            Toast.makeText(AuthenticationActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                // Google Sign-In was successful, authenticate with Firebase
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    firebaseAuthWithGoogle(account);
                }
            } catch (ApiException e) {
                hideLoadingOverlay();
                // Google Sign-In failed
                Log.w(TAG, "Google sign in failed", e);
            }
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        Log.d(TAG, "Google sign-in credential received");

        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    hideLoadingOverlay();
                    if (task.isSuccessful()) {
                        // Sign in success, check if user exists in Firestore
                        FirebaseUser user = mAuth.getCurrentUser();
                        User.checkUserInFirestore(AuthenticationActivity.this, user, user.getEmail(), user.getDisplayName(), db, this::navigateToHome);
                    } else {
                        // If sign in fails, display a message to the user.
                        Toast.makeText(AuthenticationActivity.this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String resolvePhoneAccountName(FirebaseUser user) {
        if (user == null) {
            return "";
        }
        String displayName = user.getDisplayName();
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName.trim();
        }
        String phoneNumber = user.getPhoneNumber();
        return phoneNumber != null ? phoneNumber : "";
    }

    public void navigateToHome() {
        Intent intent = new Intent(AuthenticationActivity.this, HomeActivity.class);
        Intent prevIntent = getIntent();
        String action = prevIntent.getStringExtra("action");
        Log.d(TAG, "Action: " + action);
        FirebaseAnalytics mFirebaseAnalytics =  FirebaseAnalytics.getInstance(this);
        User user = EncryptedPreferences.getUserLoggedInInfo();
        EventHelpers.triggerSignInEvent(user.getId(), action,"phone/google", mFirebaseAnalytics);
        startActivity(intent);
        finish();
    }


}
