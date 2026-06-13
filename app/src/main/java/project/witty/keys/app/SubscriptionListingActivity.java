package project.witty.keys.app;

import static com.android.billingclient.api.ProductDetails.RecurrenceMode.INFINITE_RECURRING;
import static project.witty.keys.app.entities.Subscription.acknowledgePurchase;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter; // Keep this specific import
import androidx.viewpager.widget.ViewPager;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.google.common.collect.ImmutableList;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import project.witty.keys.R;
import project.witty.keys.app.entities.User;
import project.witty.keys.app.helpers.EncryptedPreferences;
import project.witty.keys.app.helpers.EventHelpers;
import project.witty.keys.app.helpers.NotchHandler;
import project.witty.keys.app.state.AccountEntitlementSnapshot;
import project.witty.keys.app.state.AccountEntitlementSnapshotProvider;
import project.witty.keys.app.utils.DailyUsageTracker;


public class SubscriptionListingActivity extends BaseActivity implements SubscriptionFragment.OnSubscriptionSelectedListener, PurchasesUpdatedListener, ProductDetailsResponseListener {
    private static final String TAG = "SubscriptionListingActivity";
    private static final String SUBSCRIPTION_BENEFITS_COLLECTION = "subscriptions_benefits"; // Collection name in Firestore
    private static final String PROMO_CODES = "promo_codes";
    private ViewPager viewPager;
    private TabLayout tabLayout;
    private MaterialButton subscribeButton;
    private SubscriptionItem selectedItem;
    private BillingClient billingClient;
    private ProductDetails selectedSkuDetails;
    public List<ProductDetails> productDetailsList = new ArrayList<>();
    public SubscriptionPagerAdapter adapter;
    public TextView extrainfotext;
    private FirebaseFirestore db;
    private View loadingOverlay;
    private TextView loadingMessage;
    private ReviewManager manager;
    private ReviewInfo reviewInfo;
    private FirebaseAnalytics mFirebaseAnalytics;
    private LinearLayout couponCodeContainer;
    private EditText couponEditText;
    private Button applyCouponButton;
    private String selecteOfferId; // Will store the offer ID from the applied coupon
    private String appliedCoupon; // Will store the code of the applied coupon
    private boolean isKeyboardShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotchHandler.configureEdgeToEdge(this);
        setContentView(R.layout.activity_subscription_listing);
        NotchHandler.handleSystemBars(this);
        View subscriptionBackButton = findViewById(R.id.subscription_back_button);
        if (subscriptionBackButton != null) {
            subscriptionBackButton.setOnClickListener(v -> finish());
        }

        // Show shimmer loader
        ShimmerFrameLayout shimmerFrameLayout = findViewById(R.id.shimmer_subscription_container);
        shimmerFrameLayout.startShimmer();

        subscribeButton = findViewById(R.id.subscribe_button);
        extrainfotext = findViewById(R.id.coupon_info); // TextView to show coupon confirmation
        couponCodeContainer = findViewById(R.id.coupon_code_container);
        couponEditText = findViewById(R.id.coupon_code_edit_text);
        applyCouponButton = findViewById(R.id.apply_coupon_button);
        db = FirebaseFirestore.getInstance();

        setupViewPager();
        setupBillingClient();
        EncryptedPreferences.initialize(this);
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        subscribeButton.setOnClickListener(v -> {
            if (selectedItem != null) {
                initiatePurchase();
            } else {
                Log.d(TAG, "Subscribe button clicked: No package selected!");
                Toast.makeText(SubscriptionListingActivity.this, "Please select a package", Toast.LENGTH_SHORT).show();
            }
        });
        bindActiveSubscriberState();

        loadingOverlay = findViewById(R.id.loading_overlay_subscription);
        loadingMessage = loadingOverlay.findViewById(R.id.loading_text); // Ensure R.id.loading_text exists within loading_overlay

        applyCouponButton.setOnClickListener(v -> {
            String couponCode = couponEditText.getText().toString().trim();

            if (TextUtils.isEmpty(couponCode)) {
                Toast.makeText(this, "Please enter a coupon code.", Toast.LENGTH_SHORT).show();
                return;
            }
            // Ensure an item is selected *before* applying coupon
            if (selectedItem == null) {
                Toast.makeText(this, "Please select a package first.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Clear previous coupon state before applying a new one
            selecteOfferId = null;
            appliedCoupon = null;
            extrainfotext.setVisibility(View.GONE);
            extrainfotext.setText(""); // Clear previous message


            new Thread(() -> {
                Map<String, String> couponData = fetchCouponFromFirestore(couponCode);

                // Check if the fetched coupon is valid and matches the selected package
                if (couponData != null && couponData.get("package") != null && couponData.get("package").equals(selectedItem.productId)) {
                    selecteOfferId = couponData.get("offer"); // This ID links to the discounted offer
                    appliedCoupon = couponData.get("code");
                    String discountPercent = couponData.get("discount");

                    // Validate if offer ID was retrieved
                    if (TextUtils.isEmpty(selecteOfferId)) {
                        Log.e(TAG, "Coupon data found for code " + couponCode + " but 'offer' field is missing or empty.");
                        runOnUiThread(() -> Toast.makeText(this, "Coupon configuration error. Please contact support.", Toast.LENGTH_LONG).show());
                        return;
                    }


                    Log.d(TAG, "Coupon Applied for " + selectedItem.productId + ". Discount: " + discountPercent + "%. OfferId: " + selecteOfferId);

                    runOnUiThread(() -> { // Update UI on the main thread
                        String confirmation = "Coupon '" + appliedCoupon + "' Applied (" + discountPercent + "% off)";
                        extrainfotext.setText(confirmation);
                        extrainfotext.setVisibility(View.VISIBLE);
                        Toast.makeText(this, confirmation, Toast.LENGTH_SHORT).show();
                        // DO NOT update the RecyclerView item price here.
                        // The confirmation text is sufficient. The purchase flow will use the selecteOfferId.
                    });
                } else if (couponData != null && couponData.get("package") != null && !couponData.get("package").equals(selectedItem.productId)) {
                    Log.d(TAG, "Coupon code '" + couponCode + "' is for a different package (" + couponData.get("package") + "), not the selected one (" + selectedItem.productId + ")");
                    runOnUiThread(() -> Toast.makeText(this, "This coupon code is for a different package.", Toast.LENGTH_LONG).show());
                } else {
                    // Coupon code is invalid or not found
                    Log.d(TAG, "Invalid Coupon Code: " + couponCode);
                    runOnUiThread(() -> Toast.makeText(this, "Invalid Coupon Code!", Toast.LENGTH_SHORT).show());
                }
            }).start();
        });

        setUpRatingNudge();
        setInsetListener();
    }

    private void bindActiveSubscriberState() {
        AccountEntitlementSnapshot snapshot = AccountEntitlementSnapshotProvider.current(this);
        if (snapshot.primaryCta != AccountEntitlementSnapshot.PrimaryCta.MANAGE_PLAN) {
            return;
        }
        subscribeButton.setText("Manage plan");
        subscribeButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/account/subscriptions"));
            startActivity(intent);
        });
        if (extrainfotext != null) {
            extrainfotext.setText(snapshot.planName + " active");
            extrainfotext.setVisibility(View.VISIBLE);
        }
        if (couponCodeContainer != null) {
            couponCodeContainer.setVisibility(View.GONE);
        }
    }

    private void setInsetListener() {
        Window window = getWindow();
        View rootView = window.getDecorView().getRootView();

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (view, windowInsets) -> {
            // Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemGestures()); // Not used currently
            Insets insetsSystemBar = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            int imeHeight = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            boolean isImeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime());

            if (isKeyboardShowing != isImeVisible) {
                if (imeHeight > 0) {
                    // Adjust padding to push coupon container above keyboard
                    couponCodeContainer.setPadding(0, 0, 0, (imeHeight - insetsSystemBar.bottom));
                } else {
                    // Reset padding when keyboard hides
                    couponCodeContainer.setPadding(0, 0, 0, 0);
                }
            }

            isKeyboardShowing = isImeVisible;
            // Apply top/bottom padding to root view for status/navigation bars
            view.setPadding(0, insetsSystemBar.top, 0, insetsSystemBar.bottom);
            // Consume system bar insets, return the rest
            return WindowInsetsCompat.CONSUMED; // Adjust if needed based on exact behavior desired

        });
    }

    private void showLoadingOverlay(String message) {
        if (loadingOverlay != null && loadingMessage != null) {
            loadingMessage.setText(message);
            loadingOverlay.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoadingOverlay() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
    }

    private void setupViewPager() {
        viewPager = findViewById(R.id.subscription_pager);
        tabLayout = findViewById(R.id.subscription_tabs);
        adapter = new SubscriptionPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(adapter);
        tabLayout.setupWithViewPager(viewPager);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                // When switching tabs, reset the selected item in the *newly visible* fragment's adapter
                // This prevents keeping an item selected visually if the user switches tabs.
                Fragment currentFragmentGeneric = adapter.getItem(position);
                if (currentFragmentGeneric instanceof SubscriptionFragment) {
                    SubscriptionFragment currentFragment = (SubscriptionFragment) currentFragmentGeneric;
                    if (currentFragment.getAdapter() != null) {
                        currentFragment.getAdapter().resetSelectedPosition(); // Assuming resetSelectedPosition exists in adapter
                    }
                }
                // Also clear the activity's selected item and coupon state when switching tabs
                selectedItem = null;
                appliedCoupon = null;
                selecteOfferId = null;
                extrainfotext.setVisibility(View.GONE);
                extrainfotext.setText("");
                couponEditText.setText(""); // Clear coupon input field
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });
    }

    private void setupBillingClient() {
        billingClient = BillingClient.newBuilder(this)
                .setListener(this)
                .enablePendingPurchases()
                .build();
        billingClient.startConnection(new BillingClientStateListener() {

            @Override
            public void onBillingServiceDisconnected() {
                hideLoadingOverlay();
                Log.d(TAG, "Billing:Service Disconnected. Retrying?");
                // Consider implementing a retry mechanism here.
            }

            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing: Setup Finished successfully.");
                    QueryProductDetailsParams queryProductDetailsParams =
                            QueryProductDetailsParams.newBuilder()
                                    .setProductList(
                                            ImmutableList.of(
                                                    QueryProductDetailsParams.Product.newBuilder()
                                                            .setProductId("monthly_standard") // Replace with your actual Product IDs
                                                            .setProductType(BillingClient.ProductType.SUBS)
                                                            .build(),
                                                    QueryProductDetailsParams.Product.newBuilder()
                                                            .setProductId("yearly_standard") // Replace with your actual Product IDs
                                                            .setProductType(BillingClient.ProductType.SUBS)
                                                            .build()
                                                    // Add more products if needed
                                            ))
                                    .build();
                    billingClient.queryProductDetailsAsync(
                            queryProductDetailsParams,
                            SubscriptionListingActivity.this // Pass the activity as the listener
                    );
                    queryPurchases(); // Check for existing purchases
                } else {
                    hideLoadingOverlay(); // Ensure loading overlay is hidden on failure
                    Log.e(TAG, "Billing: Setup Failed: " + billingResult.getDebugMessage());
                    Toast.makeText(SubscriptionListingActivity.this, "Error connecting to billing service.", Toast.LENGTH_LONG).show();
                    // Maybe disable purchase button or show error message permanently
                }
            }
        });
    }

    @Override
    public void onProductDetailsResponse(@NonNull BillingResult billingResult, @Nullable List<ProductDetails> productDetailsListResult) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsListResult != null) {
            Log.d(TAG, "Product details response successful. Count: " + productDetailsListResult.size());
            this.productDetailsList.clear(); // Clear previous list if any
            this.productDetailsList.addAll(productDetailsListResult);
            updateFragments(); // Update UI with fetched details
        } else {
            Log.e(TAG, "Failed to retrieve product details: " + billingResult.getDebugMessage());
            Toast.makeText(this, "Failed to load subscription plans. Please try again later.", Toast.LENGTH_LONG).show();
            // Hide shimmer and show an error message in the viewpager area if appropriate
            ShimmerFrameLayout shimmerFrameLayout = findViewById(R.id.shimmer_subscription_container);
            shimmerFrameLayout.stopShimmer();
            shimmerFrameLayout.setVisibility(View.GONE);
            // Potentially display an error view here
        }
    }

    private void updateFragments() {
        if (adapter != null && !productDetailsList.isEmpty()) {
            adapter.clearFragments(); // Clear existing fragments before adding new ones

            List<SubscriptionItem> monthlySubs = new ArrayList<>();
            List<SubscriptionItem> annualSubs = new ArrayList<>();

            // Process product details in background to avoid blocking UI thread
            new Thread(() -> {
                for (ProductDetails productDetails : productDetailsList) {
                    if (productDetails.getSubscriptionOfferDetails() != null && !productDetails.getSubscriptionOfferDetails().isEmpty()) {
                        Log.d(TAG, "Processing product: " + productDetails.getProductId() + ", Offers: " + productDetails.getSubscriptionOfferDetails().size());

                        // Find the relevant *base* offer to display initially (e.g., 'launch' or standard recurring)
                        // The price displayed initially should be the one without a special coupon.
                        ProductDetails.SubscriptionOfferDetails displayOffer = null;
                        for (ProductDetails.SubscriptionOfferDetails offerDetails : productDetails.getSubscriptionOfferDetails()) {
                            Log.d(TAG, "Processing product offerDetail: " + productDetails.getProductId() + ", Offers: " + offerDetails.getBasePlanId() +  ", Offers: " + offerDetails.getOfferId() + ", OfferToken:" + offerDetails.getOfferToken());

                            // Prioritize a 'launch' or similar default tag if you have one for the base price
                            if (offerDetails.getOfferId() == null) {
                                displayOffer = offerDetails;
                                Log.d(TAG, "Found 'launch' offer for initial display: " + offerDetails.getOfferId());
                                break;
                            }
                        }

                        // If no specific default tag found, maybe use the first offer? Be cautious.
                        // A safer approach might be to iterate pricing phases directly if no 'default' offer ID is reliable.
                        // For simplicity, let's assume we find *one* suitable offer to represent the base product display.
                        if (displayOffer == null) {
                            // Fallback: Maybe just take the first offer, but log a warning.
                            // displayOffer = productDetails.getSubscriptionOfferDetails().get(0);
                            // Log.w(TAG, "No 'launch' offer found for " + productDetails.getProductId() + ". Using first available offer ("+displayOffer.getOfferId()+") for base display. Ensure this is correct.");

                            // Alternative: Try finding the INFINITE_RECURRING phase directly if no clear default offer.
                            // This part needs careful mapping based on your Play Console setup. Let's stick to finding the designated 'displayOffer'.
                            Log.w(TAG, "Could not determine a default offer (e.g., 'launch') for initial display for product: " + productDetails.getProductId());
                            // Decide how to handle this - skip the product or make a best guess. Let's skip if no clear offer.
                            continue; // Skip this product if no suitable display offer found
                        }


                        // Check if the chosen offer has pricing phases
                        if (displayOffer.getPricingPhases() != null && !displayOffer.getPricingPhases().getPricingPhaseList().isEmpty()) {
                            ProductDetails.PricingPhase phase = displayOffer.getPricingPhases().getPricingPhaseList().get(0); // Use first phase for period determination
                            String billingPeriod = phase.getBillingPeriod(); // e.g., "P1M", "P1Y"
                            if (billingPeriod == null || billingPeriod.isEmpty()) {
                                Log.e(TAG, "Billing period is null or empty for offer: " + displayOffer.getOfferId());
                                continue; // Skip if billing period is invalid
                            }


                            // Create the SubscriptionItem using the chosen *displayOffer* details
                            SubscriptionItem subData = getSubscriptionItem(productDetails, displayOffer, billingPeriod);
                            Log.d(TAG, "Created SubscriptionItem: " + subData.productId);
                            if (subData != null) { // Check if item creation was successful
                                if (billingPeriod.toUpperCase().contains("M")) {
                                    monthlySubs.add(subData);
                                } else if (billingPeriod.toUpperCase().contains("Y")) {
                                    annualSubs.add(subData);
                                }
                            }
                        } else {
                            Log.w(TAG, "Selected display offer " + displayOffer.getOfferId() + " has no pricing phases for product " + productDetails.getProductId());
                        }

                    } else {
                        Log.w(TAG, "Product " + productDetails.getProductId() + " has no subscription offer details.");
                    }
                }

                // Update UI back on the main thread
                try {
                    runOnUiThread(() -> {
                        if (!monthlySubs.isEmpty()) {
                            adapter.addFragment(SubscriptionFragment.newInstance(monthlySubs, this), "Monthly");
                        }
                        if (!annualSubs.isEmpty()) {
                            adapter.addFragment(SubscriptionFragment.newInstance(annualSubs, this), "Annual");
                        }
                        adapter.notifyDataSetChanged(); // Crucial to refresh the ViewPager/TabLayout

                        // Hide shimmer loader and show content
                        ShimmerFrameLayout shimmerFrameLayout = findViewById(R.id.shimmer_subscription_container);
                        shimmerFrameLayout.stopShimmer();
                        shimmerFrameLayout.setVisibility(View.GONE);
                        viewPager.setVisibility(View.VISIBLE);
                        tabLayout.setVisibility(View.VISIBLE);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error updating fragments on UI thread: " + e.getMessage(), e);
                    // Handle UI update error, maybe show a persistent error message
                }
            }).start(); // Start the background thread
        } else {
            Log.w(TAG, "updateFragments called but adapter is null or productDetailsList is empty.");
            // Maybe hide shimmer and show error if product details are empty after response
            if (productDetailsList.isEmpty()) {
                ShimmerFrameLayout shimmerFrameLayout = findViewById(R.id.shimmer_subscription_container);
                shimmerFrameLayout.stopShimmer();
                shimmerFrameLayout.setVisibility(View.GONE);
                // Display error message
            }
        }
    }


    @NonNull
    private SubscriptionItem getSubscriptionItem(ProductDetails productDetails, @NonNull ProductDetails.SubscriptionOfferDetails offerDetails, @NonNull String billingPeriod) {
        String productId = productDetails.getProductId();
        String name = productDetails.getName();
        List<String> benefits = fetchBenefitsFromFirestore(productId); // Run synchronously for simplicity here, consider async if slow

        String priceToCharge = "N/A"; // This will be the price displayed and charged without coupon
        String basePlanPrice = null; // Store the base price for reference if needed

        // --- Determine the price to display (standard price) ---
        if (offerDetails.getPricingPhases() != null && !offerDetails.getPricingPhases().getPricingPhaseList().isEmpty()) {
            List<ProductDetails.PricingPhase> pricingPhases = offerDetails.getPricingPhases().getPricingPhaseList();

            // Find the standard recurring price (INFINITE_RECURRING) first
            for (ProductDetails.PricingPhase phase : pricingPhases) {
                Log.d(TAG, "Offer " + offerDetails.getBasePlanId() + " - Phase: Price=" + phase.getFormattedPrice() + ", Mode=" + phase.getRecurrenceMode() + ", Period=" + phase.getBillingPeriod());
                if (phase.getRecurrenceMode() == INFINITE_RECURRING) {
                    // This is typically the base price after any intro offers expire
                    basePlanPrice = phase.getFormattedPrice();
                    // If it's also the *only* phase, it's the price to charge initially.
                    if (pricingPhases.size() == 1) {
                        priceToCharge = phase.getFormattedPrice();
                    }
                    Log.d(TAG, "Found INFINITE_RECURRING price: " + basePlanPrice + " for offer " + offerDetails.getBasePlanId());
                    // Keep checking other phases - the first phase might be the actual initial charge price
                }
            }

            // Now determine the actual price to charge/display initially.
            // Often, this is the price of the *first* phase in the list for the offer.
//            if (!pricingPhases.isEmpty()) {
//                ProductDetails.PricingPhase initialPhase = pricingPhases.get(0);
//                // Use the first phase's price as the one to display/charge *unless* we only found an INFINITE one.
//                if (!"N/A".equals(priceToCharge)) {
//                    // Already set by a single INFINITE_RECURRING phase, keep it.
//                    Log.d(TAG, "Using single INFINITE_RECURRING phase price: " + priceToCharge);
//                } else {
//                    priceToCharge = initialPhase.getFormattedPrice();
//                    Log.d(TAG, "Using first phase price for initial display/charge: " + priceToCharge);
//                }
//
//                // If we didn't find an explicit basePlanPrice (no INFINITE phase), use the initial charge price as base too.
//                if (basePlanPrice == null) {
//                    basePlanPrice = priceToCharge;
//                    Log.d(TAG, "Setting basePlanPrice same as initial charge price as no INFINITE phase found.");
//                }
//            }

        } else {
            Log.e(TAG, "Offer " + offerDetails.getBasePlanId() + " has no pricing phases!");
            // Cannot determine price, maybe return null or a default state?
            return null; // Indicate failure to create item
        }

        Log.d(TAG, "Creating SubscriptionItem - ProductId: " + productId + ", Name: "+ name +", Final Price (No Coupon): " + priceToCharge + ", Base Price: " + basePlanPrice + ", Period: " + billingPeriod);

        priceToCharge = basePlanPrice != null ? basePlanPrice : priceToCharge; // Ensure priceToCharge is set to basePlanPrice since default discount is removed
        // Pass the priceToCharge as 'finalPrice' and basePlanPrice as 'originalPrice'
        // The 'discount' field is now always empty initially.
        // Ensure SubscriptionItem constructor matches: (String productId, String originalPrice, String discount, String finalPrice, String name, String billingPeriod, List<String> benefits)
        return new SubscriptionItem(productId, basePlanPrice != null ? basePlanPrice : priceToCharge, priceToCharge, name, billingPeriod, benefits);
    }

    private void initiatePurchase() {
        // 1. Check User Login and Selected Item validity
        User user = EncryptedPreferences.getUserLoggedInInfo();
        if (user == null) {
            // It's good practice to hide loading overlay if shown before this check
            hideLoadingOverlay();
            Log.w(TAG, "initiatePurchase: User not logged in.");
            Toast.makeText(this, "User not logged in. Please log in to subscribe.", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, AuthenticationActivity.class); // Redirect to login
            startActivity(intent);
            finish(); // Finish this activity as user needs to login first
            return;
        }

        if (selectedItem == null || TextUtils.isEmpty(selectedItem.productId)) {
            hideLoadingOverlay(); // Hide loading overlay if shown
            Log.e(TAG, "initiatePurchase: Invalid selected item or product ID.");
            showError("Please select a subscription plan first."); // More direct error message
            return;
        }

        // Optional: Show loading indicator immediately before starting async query
        // showLoadingOverlay("Preparing purchase...");

        // 2. Prepare Product Details Query using the selected item's productId
        final String productId = selectedItem.productId; // Use final for use in callback
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(ImmutableList.of(
                        QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(productId)
                                .setProductType(BillingClient.ProductType.SUBS) // Explicitly SUBS
                                .build()
                ))
                .build();

        Log.d(TAG, "initiatePurchase: Querying product details for: " + productId);

        // 3. Query Product Details Asynchronously
        billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
            // This callback runs on the main thread implicitly

            // 4. Handle Query Result (Check BillingResult)
            int responseCode = billingResult.getResponseCode();
            if (responseCode != BillingClient.BillingResponseCode.OK) {
                hideLoadingOverlay(); // Hide loading overlay on error
                Log.e(TAG, "queryProductDetailsAsync Error: " + responseCode + ", " + billingResult.getDebugMessage());
                showError("Error retrieving product information (" + responseCode + "). Please try again later."); // Show error code to user potentially
                return;
            }

            // 5. Handle Query Result (Check ProductDetails List)
            if (productDetailsList == null || productDetailsList.isEmpty()) {
                hideLoadingOverlay(); // Hide loading overlay if product not found
                Log.e(TAG, "queryProductDetailsAsync: No product details found for productId: " + productId);
                showError("Selected subscription plan is currently unavailable.");
                return;
            }

            // 6. Extract ProductDetails and Initialize Offer Token
            final ProductDetails productDetails = productDetailsList.get(0); // Get the first (and likely only) item
            selectedSkuDetails = productDetails; // Store for potential use elsewhere (e.g., analytics after purchase)
            String offerToken = null; // Initialize offerToken to null - THIS IS KEY

            // 7. Check if Subscription Offers Exist on the ProductDetails
            if (productDetails.getSubscriptionOfferDetails() == null || productDetails.getSubscriptionOfferDetails().isEmpty()) {
                hideLoadingOverlay();
                Log.e(TAG, "initiatePurchase: No subscription offers (pricing phases/options) found on product: " + productId);
                // This usually indicates a setup issue in Google Play Console
                showError("No pricing options found for this subscription.");
                return; // Cannot proceed without any offers defined
            }

            // 8. Determine Offer Token *only* if a valid coupon was applied (selecteOfferId is set)
            if (selecteOfferId != null && !selecteOfferId.isEmpty()) {
                Log.d(TAG, "initiatePurchase: Attempting to find offer for applied coupon ID: " + selecteOfferId);
                ProductDetails.SubscriptionOfferDetails selectedOffer = null;
                // Loop through all available offers for the product
                for (ProductDetails.SubscriptionOfferDetails offer : productDetails.getSubscriptionOfferDetails()) {
                    Log.d(TAG, "Checking offer: ID=" + offer.getOfferId()); // Log each offer ID being checked
                    if (offer.getOfferId() != null && offer.getOfferId().equals(selecteOfferId)) {
                        selectedOffer = offer; // Found the offer matching the coupon
                        break;
                    }
                }

                // 8a. Handle if the specific coupon offer was found
                if (selectedOffer != null) {
                    offerToken = selectedOffer.getOfferToken(); // Get the token required for the purchase flow
                    Log.d(TAG, "initiatePurchase: Found matching OFFER for applied coupon. OfferId: " + selecteOfferId + ", OfferToken: " + offerToken);

                    // CRITICAL: Validate the token itself isn't empty or null
                    if (TextUtils.isEmpty(offerToken)) {
                        hideLoadingOverlay();
                        Log.e(TAG, "initiatePurchase: OfferToken is NULL or EMPTY for selected Offer ID: " + selecteOfferId + ". This is invalid.");
                        showError("Selected coupon offer is currently invalid. Please try removing the coupon or contact support.");
                        // Reset coupon state as it's unusable
                        selecteOfferId = null;
                        appliedCoupon = null;
                        extrainfotext.setVisibility(View.GONE); // Assuming extrainfotext is accessible
                        extrainfotext.setText("");
                        return; // Stop purchase if token is invalid/empty
                    }
                    // If token is valid, proceed (offerToken is now set)
                } else {
                    // 8b. Handle if the specific coupon offer was NOT found (e.g., coupon expired, typo, wrong product)
                    hideLoadingOverlay();
                    Log.e(TAG, "initiatePurchase: Could not find OfferDetails for Offer ID: " + selecteOfferId + " (from coupon) on product: " + productId);
                    showError("The applied coupon code is not valid for this specific subscription plan.");
                    // Reset coupon state as it's invalid for this context
                    selecteOfferId = null;
                    appliedCoupon = null;
                    extrainfotext.setVisibility(View.GONE); // Assuming extrainfotext is accessible
                    extrainfotext.setText("");
                    return; // Stop purchase if coupon offer not found for the product
                }
            } else {
                // 9. *** NO valid coupon applied - FIND THE BASE PLAN OFFER TOKEN with offer id null ***
                Log.d(TAG, "initiatePurchase: No coupon applied. Finding BASE PLAN offer token...");

                ProductDetails.SubscriptionOfferDetails baseOffer = null;
                for (ProductDetails.SubscriptionOfferDetails offer : productDetails.getSubscriptionOfferDetails()) {
                    Log.d(TAG, "Checking offer for Base Plan match: ID=" + offer.getOfferId());
                    if (offer.getOfferId() == null) {
                        baseOffer = offer;
                        break;
                    }
                }

                if (baseOffer != null) {
                    offerToken = baseOffer.getOfferToken();
                    Log.d(TAG, "initiatePurchase: Found BASE PLAN offer. OfferId: " + baseOffer.getOfferId() + ", OfferToken: " + offerToken);
                    if (TextUtils.isEmpty(offerToken)) {
                        // Handle case where base offer token is invalid (Setup Error)
                        hideLoadingOverlay();
                        showError("Cannot process purchase: Base plan configuration error.");
                        return;
                    }
                } else {
                    // Handle case where the expected base offer ID wasn't found (Setup Error)
                    hideLoadingOverlay();
                    Log.e(TAG, "initiatePurchase: BASE PLAN Offer ID '" + offerToken + "' not found for Product ID: " + productId + ". Check Play Console setup!");
                    showError("Cannot process purchase: Base plan not found.");
                    return;
                }
            }

            // 10. Get Obfuscated Account ID (Mandatory for Subscriptions)
            // Re-fetch user just in case state changed, though should be valid from start check
            User currentUser = EncryptedPreferences.getUserLoggedInInfo();
            if (currentUser == null) { // Check again in case of unexpected state change
                hideLoadingOverlay();
                Log.e(TAG, "initiatePurchase: User became null before getting account ID.");
                showError("User session error. Please log in again.");
                return;
            }
            String obfuscatedAccountId = currentUser.getObfuscatedAccountId();
            if (obfuscatedAccountId == null) {
                hideLoadingOverlay();
                Log.e(TAG, "Obfuscated account id is null - Cannot proceed with subscription purchase.");
                showError("Cannot complete purchase: A unique user account identifier is required."); // User-friendly message
                return; // Stop if null, as it's required by Google Play for subscriptions
            }

            // 11. Build ProductDetailsParams: Include offerToken ONLY if it's non-null.
            BillingFlowParams.ProductDetailsParams.Builder productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails); // Set the base product details

            if (offerToken != null) {
                // This condition is ONLY true if a valid coupon was applied and its offer token was found and non-empty.
                Log.d(TAG, "initiatePurchase: Applying specific OfferToken (from coupon): " + offerToken);
                productParamsBuilder.setOfferToken(offerToken);
            } else {
                // This block executes if no coupon was applied, or if the coupon/offer was invalid.
                // No offerToken is added to the params, so Google Play uses the product's base plan price.
                Log.d(TAG, "initiatePurchase: No specific OfferToken provided. Google Play will use the base plan price for product " + productDetails.getProductId());
            }
            Log.d(TAG, "initiatePurchase: No specific OfferToken provided 2. Google Play will use the base plan price for product " + productDetails.getProductId());

            // 12. Build the final BillingFlowParams - ADDING DETAILED LOGS & TRY-CATCH
            BillingFlowParams flowParams = null; // Initialize to null
            try {
                BillingFlowParams.Builder flowParamsBuilder = BillingFlowParams.newBuilder();
                flowParamsBuilder.setProductDetailsParamsList(ImmutableList.of(productParamsBuilder.build()));
                flowParamsBuilder.setObfuscatedAccountId(obfuscatedAccountId);
                flowParams = flowParamsBuilder.build(); // Build the final object

            } catch (Throwable t) { // Catch Throwable to capture Errors as well as Exceptions
                hideLoadingOverlay();
                Log.e(TAG, "initiatePurchase: [Step 12 FAIL] CRITICAL ERROR building BillingFlowParams!", t); // Log the exception
                showError("Failed to prepare purchase details. Please try again. Error: " + t.getMessage());
                return; // Stop execution if building fails
            }

            // Check if flowParams is null (shouldn't be if build succeeded, but safety check)
            if (flowParams == null) {
                hideLoadingOverlay();
                showError("Failed to prepare purchase details (internal error).");
                return;
            }

            // 13. Log before launching the Google Play Billing Flow
            Log.d(TAG, "launchBillingFlow: Launching for Product: " + productDetails.getProductId() +
                    (offerToken != null ? " with OfferToken: " + offerToken : " (Base Price - No OfferToken)"));
            hideLoadingOverlay(); // Hide loading overlay *just before* launching the external flow

            // 14. Trigger Analytics Event (Log attempt)
            FirebaseAnalytics mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
            // Log based on whether a coupon was successfully applied *for this attempt*
            String analyticsCode = appliedCoupon != null ? appliedCoupon : (selecteOfferId != null ? selecteOfferId : "BASE_PRICE");
            EventHelpers.triggerSubscriptionJourneyStartedEvent(currentUser.getId(), selectedItem.productId + "/" + analyticsCode, mFirebaseAnalytics);

            // 15. Launch the Google Play Billing Flow
            BillingResult launchResult = billingClient.launchBillingFlow(SubscriptionListingActivity.this, flowParams);

            // 16. Optional: Log the immediate result of *launching* the flow (doesn't indicate purchase success)
            if (launchResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                Log.e(TAG, "launchBillingFlow Error: Failed to launch billing flow. ResponseCode: " + launchResult.getResponseCode() + ", DebugMessage: " + launchResult.getDebugMessage());
                // Show an immediate error if the flow itself failed to launch (rare)
                showError("Could not start the purchase process. Error: " + launchResult.getResponseCode());
                // Re-show loading overlay might not be needed, but consider state management
            } else {
                Log.d(TAG, "launchBillingFlow: Billing flow launched successfully.");
                // The result will come back in onPurchasesUpdated
            }

        }); // End of queryProductDetailsAsync callback lambda
    }

    private void resetCouponState() {
        selecteOfferId = null;
        appliedCoupon = null;
        runOnUiThread(() -> {
            extrainfotext.setVisibility(View.GONE);
            extrainfotext.setText("");
            couponEditText.setText(""); // Clear coupon input field
        });
    }

    private void showError(String errorMessage) {
        hideLoadingOverlay(); // Ensure loading is hidden when showing error
        runOnUiThread(() -> {
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
        int responseCode = billingResult.getResponseCode();
        String debugMessage = billingResult.getDebugMessage();
        Log.d(TAG, "onPurchasesUpdated: ResponseCode=" + responseCode + ", DebugMessage=" + debugMessage);

        // Determine analytics code based on state *before* purchase attempt
        String analyticsCode = appliedCoupon != null ? appliedCoupon : (selecteOfferId != null ? selecteOfferId : "DEFAULT");
        User user = EncryptedPreferences.getUserLoggedInInfo(); // Get user for logging
        String userId = (user != null) ? user.getId() : "UNKNOWN_USER";
        String productId = (selectedItem != null) ? selectedItem.productId : "UNKNOWN_PRODUCT";


        if (responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            Log.d(TAG, "Purchase successful! Processing " + purchases.size() + " purchases.");
            showLoadingOverlay("Activating subscription..."); // Show loading while acknowledging
            for (Purchase purchase : purchases) {
                handlePurchase(purchase); // Handle acknowledgment and backend update
            }
        } else if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            hideLoadingOverlay(); // User cancelled, hide loading
            Log.d(TAG, "BillingClient: User canceled the purchase flow.");
            EventHelpers.triggerSubscriptionJourneyCancelledEvent(userId, productId + "/" + analyticsCode, "USER_CANCELED", mFirebaseAnalytics);
            Toast.makeText(this, "Purchase canceled.", Toast.LENGTH_SHORT).show();
            resetCouponState(); // Reset coupon if user cancels
        } else if (responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            hideLoadingOverlay();
            Log.w(TAG, "BillingClient: Item already owned.");
            EventHelpers.triggerSubscriptionJourneyCancelledEvent(userId, productId + "/" + analyticsCode, "ITEM_ALREADY_OWNED", mFirebaseAnalytics);
            Toast.makeText(this, "You already have an active subscription.", Toast.LENGTH_LONG).show();
            // Query purchases again to ensure local state is correct, then navigate?
            queryPurchases();
            navigateToProfile(); // Navigate to profile as they likely already have it
            resetCouponState(); // Reset coupon state
        } else {
            // Handle other errors
            hideLoadingOverlay();
            Log.e(TAG, "BillingClient: Purchase failed: Error " + responseCode + " - " + debugMessage);
            EventHelpers.triggerSubscriptionJourneyCancelledEvent(userId, productId + "/" + analyticsCode, "ERROR_" + responseCode + ": " + debugMessage, mFirebaseAnalytics);
            Toast.makeText(this, "Purchase failed: " + debugMessage, Toast.LENGTH_LONG).show();
            resetCouponState(); // Reset coupon on failure
        }
    }

    private void queryPurchases() {
        if (billingClient == null || !billingClient.isReady()) {
            Log.e(TAG, "queryPurchases: BillingClient is not ready.");
            return;
        }

        // Query for active subscriptions
        billingClient.queryPurchasesAsync(
                // Use BillingClient.SkuType.SUBS for subscriptions
                BillingClient.SkuType.SUBS,
                (billingResult, purchasesList) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "queryPurchasesAsync (SUBS) successful. Found " + (purchasesList != null ? purchasesList.size() : 0) + " subscriptions.");
                        if (purchasesList != null) {
                            for (Purchase purchase : purchasesList) {
                                // Process active, unacknowledged purchases
                                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged()) {
                                    Log.d(TAG, "Found unacknowledged purchase: " + purchase.getSkus().get(0));
                                    showLoadingOverlay("Finalizing existing subscription...");
                                    // Acknowledge using "DEFAULT" as we don't know the original promo code here
                                    acknowledgePurchase(purchase, "DEFAULT", billingClient, this, this::navigateToProfile, this::hideLoadingOverlay);
                                }
                            }
                        }
                    } else {
                        hideLoadingOverlay(); // Hide loading if query failed
                        Log.e(TAG, "Error querying existing subscriptions: " + billingResult.getDebugMessage());
                    }
                }
        );

        // Consider also querying BillingClient.SkuType.INAPP if you have non-subscription products
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            Log.d(TAG, "handlePurchase: State is PURCHASED for SKU: " + purchase.getSkus().get(0));
            // Acknowledge the purchase if not already acknowledged
            if (!purchase.isAcknowledged()) {
                Log.d(TAG, "Purchase needs acknowledgement.");
                // Use the coupon code that was applied during *this* purchase flow attempt
                String code = appliedCoupon != null ? appliedCoupon : (selecteOfferId != null ? selecteOfferId : "DEFAULT");
                acknowledgePurchase(purchase, code, billingClient, this, this::navigateToProfile, this::hideLoadingOverlay);
            } else {
                // Already acknowledged, perhaps from a previous session or queryPurchases
                Log.d(TAG, "Purchase was already acknowledged.");
                hideLoadingOverlay(); // Hide loading if already acknowledged
                navigateToProfile(); // Navigate as it's confirmed
            }
            // Enable the paid AI action tier for this device.
            DailyUsageTracker.getInstance(this).setUnlimited(true);

            // Trigger success event regardless of acknowledgement state here (as purchase was successful)
            User user = EncryptedPreferences.getUserLoggedInInfo();
            String userId = (user != null) ? user.getId() : "UNKNOWN_USER";
            String productId = purchase.getSkus().isEmpty() ? "UNKNOWN_SKU" : purchase.getSkus().get(0);
            EventHelpers.triggerSubscriptionJourneySuccessEvent(userId, productId, mFirebaseAnalytics);

        } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            // Handle pending purchases (e.g., waiting for payment confirmation)
            hideLoadingOverlay();
            Log.d(TAG, "handlePurchase: State is PENDING for SKU: " + purchase.getSkus().get(0));
            Toast.makeText(this, "Your purchase is pending. We'll notify you once payment is confirmed.", Toast.LENGTH_LONG).show();
            // You might want to update your UI to reflect the pending state
            // Do NOT navigate to profile yet.
            resetCouponState();
        } else if (purchase.getPurchaseState() == Purchase.PurchaseState.UNSPECIFIED_STATE) {
            hideLoadingOverlay();
            Log.w(TAG, "handlePurchase: State is UNSPECIFIED for SKU: " + purchase.getSkus().get(0));
            Toast.makeText(this, "An unknown purchase error occurred. Please try again.", Toast.LENGTH_LONG).show();
            resetCouponState();
        }
    }


    public void navigateToProfile() {
        hideLoadingOverlay();
        launchRatingNudge(); // Attempt to show rating nudge after successful purchase confirmation
        Toast.makeText(this, "Subscription Activated!", Toast.LENGTH_LONG).show();

        // Log completion event
        User user = EncryptedPreferences.getUserLoggedInInfo();
        if (user != null && selectedItem != null) {
            String code = appliedCoupon != null ? appliedCoupon : (selecteOfferId != null ? selecteOfferId : "DEFAULT");
            EventHelpers.triggerSubscriptionJourneyCompletedEvent(user.getId(), selectedItem.productId + "/" + code, mFirebaseAnalytics);
        } else {
            Log.w(TAG,"Could not log completion event: user or selectedItem is null.");
        }

        // Navigate
        Intent intent = new Intent(SubscriptionListingActivity.this, UserProfileActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK); // Clear back stack
        startActivity(intent);
        finish(); // Finish this activity
    }

    public void setUpRatingNudge() {
        try {
            manager = ReviewManagerFactory.create(this);
            Task<ReviewInfo> request = manager.requestReviewFlow();
            request.addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    reviewInfo = task.getResult();
                    Log.d(TAG, "ReviewInfo requested successfully.");
                } else {
                    Log.e(TAG, "Error requesting review flow: ", task.getException());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error creating ReviewManager: ", e);
        }
    }

    public void launchRatingNudge() {
        if (manager == null || reviewInfo == null) {
            Log.w(TAG, "ReviewManager or ReviewInfo not ready. Cannot launch rating nudge.");
            return;
        }
        Log.d(TAG, "Attempting to launch review flow.");
        Task<Void> flow = manager.launchReviewFlow(this, reviewInfo);
        flow.addOnCompleteListener(task -> {
            // The flow has finished. It doesn't indicate if the dialog was shown or if the user reviewed.
            Log.d(TAG, "Review flow finished (regardless of outcome).");
            User user = EncryptedPreferences.getUserLoggedInInfo();
            String userId = (user != null) ? user.getId() : "UNKNOWN_USER";
            EventHelpers.triggerRatingNudgeEvent(userId, mFirebaseAnalytics);
        });
    }

    // --- ViewPager Adapter ---
    private class SubscriptionPagerAdapter extends FragmentPagerAdapter {
        private final List<Fragment> fragmentList = new ArrayList<>();
        private final List<String> fragmentTitleList = new ArrayList<>();

        // Use BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT for potentially better memory management
        public SubscriptionPagerAdapter(FragmentManager manager) {
            super(manager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return fragmentList.get(position);
        }

        @Override
        public int getCount() {
            return fragmentList.size();
        }

        public void addFragment(Fragment fragment, String title) {
            fragmentList.add(fragment);
            fragmentTitleList.add(title);
        }

        public void clearFragments() {
            fragmentList.clear();
            fragmentTitleList.clear();
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return fragmentTitleList.get(position);
        }
    }

    @Override
    public void onSubscriptionSelected(SubscriptionItem selectedItem) {
        Log.d(TAG, "Subscription selected: " + selectedItem);
        this.selectedItem = selectedItem;
        // When a new item is selected, reset any previously applied coupon state
        // because the coupon might not be valid for this newly selected item.
        resetCouponState();
    }


    // --- Firestore Data Fetching ---

    private List<String> fetchBenefitsFromFirestore(String productId) {
        Log.d(TAG, "Fetching benefits for productId: " + productId);
        List<String> benefits = new ArrayList<>();
        if (db == null || TextUtils.isEmpty(productId)) return benefits;

        try {
            // Run synchronously for simplicity as it's called during item creation background thread
            DocumentSnapshot documentSnapshot = Tasks.await(db.collection(SUBSCRIPTION_BENEFITS_COLLECTION)
                    .document(productId)
                    .get());

            if (documentSnapshot.exists()) {
                Map<String, Object> data = documentSnapshot.getData();
                if (data != null && data.containsKey("benefits")) {
                    Object benefitsObject = data.get("benefits");
                    // Handle both List<String> and Map<String, String> for flexibility
                    if (benefitsObject instanceof List) {
                        List<?> rawList = (List<?>) benefitsObject;
                        for(Object item : rawList) {
                            if (item instanceof String) {
                                benefits.add((String) item);
                            }
                        }
                        Log.d(TAG, "Benefits fetched as List: " + benefits.size());
                    } else if (benefitsObject instanceof Map) {
                        // Assuming map values are the benefits strings
                        Map<?, ?> benefitsMap = (Map<?, ?>) benefitsObject;
                        for(Object value : benefitsMap.values()) {
                            if (value instanceof String) {
                                benefits.add((String) value);
                            }
                        }
                        Log.d(TAG, "Benefits fetched as Map: " + benefits.size());
                    } else {
                        Log.w(TAG, "Firestore 'benefits' field for " + productId + " is neither a List nor a Map.");
                    }
                } else {
                    Log.w(TAG, "Firestore document for " + productId + " exists, but 'benefits' field is missing or null.");
                }
            } else {
                Log.w(TAG, "No Firestore benefits document found for product ID: " + productId);
            }
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "Error fetching benefits from Firestore for " + productId + ": " + e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt(); // Restore interrupt status
            }
            // Return empty list on error
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error fetching benefits from Firestore for " + productId + ": " + e.getMessage(), e);
            // Return empty list on error
        }
        return benefits;
    }

    private Map<String, String> fetchCouponFromFirestore(String couponCode) {
        Log.d(TAG, "Fetching coupon data for code: " + couponCode);
        Map<String, String> couponData = null; // Initialize as null, only populate if found
        if (db == null || TextUtils.isEmpty(couponCode)) return null;


        try {
            // Run synchronously for simplicity as it's called from a background thread already
            Task<QuerySnapshot> task = db.collection(PROMO_CODES)
                    .whereEqualTo("code", couponCode) // Ensure 'code' field is indexed in Firestore
                    .limit(1)
                    .get();

            QuerySnapshot querySnapshot = Tasks.await(task);

            if (!querySnapshot.isEmpty()) {
                DocumentSnapshot documentSnapshot = querySnapshot.getDocuments().get(0);
                Map<String, Object> data = documentSnapshot.getData();
                if (data != null) {
                    couponData = new HashMap<>(); // Create map only if data exists
                    // Safely get data, converting types as needed
                    couponData.put("code", String.valueOf(data.getOrDefault("code", "")));
                    couponData.put("package", String.valueOf(data.getOrDefault("package", ""))); // Product ID
                    couponData.put("discount", String.valueOf(data.getOrDefault("discount", "0"))); // Discount percentage/value
                    couponData.put("offer", String.valueOf(data.getOrDefault("offer", "")));     // Google Play Offer ID
                    // Add expiry or other fields if needed
                    // couponData.put("expiry", String.valueOf(data.get("expiry"))); // Example: Handle Timestamps appropriately

                    Log.d(TAG, "Coupon data found: " + couponData);

                    // Validate essential fields
                    if (TextUtils.isEmpty(couponData.get("package")) || TextUtils.isEmpty(couponData.get("offer"))) {
                        Log.e(TAG, "Firestore coupon document " + documentSnapshot.getId() + " is missing required fields ('package' or 'offer').");
                        return null; // Return null if essential data is missing
                    }

                } else {
                    Log.w(TAG, "Firestore document found for code " + couponCode + ", but data is null.");
                }
            } else {
                Log.w(TAG, "No Firestore document found for coupon code: " + couponCode);
                // Return null explicitly if not found
                return null;
            }
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "Error fetching coupon from Firestore for " + couponCode + ": " + e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt(); // Restore interrupt status
            }
            // Return null on error
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error fetching coupon from Firestore for " + couponCode + ": " + e.getMessage(), e);
            // Return null on error
            return null;
        }
        return couponData; // Return the map or null
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (billingClient != null && billingClient.isReady()) {
            Log.d(TAG, "Disconnecting BillingClient.");
            billingClient.endConnection();
        }
    }
}
