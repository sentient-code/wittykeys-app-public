package project.witty.keys.app;

import android.content.Context;
import android.os.Bundle;
// androidx imports are generally preferred now
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import project.witty.keys.R;

public class SubscriptionFragment extends Fragment {

    private static final String TAG = "SubscriptionFragment";
    private static final String ARG_SUBSCRIPTION_LIST = "subscription_list";
    private RecyclerView recyclerView;
    private SubscriptionAdapter adapter; // Ensure this is your adapter class
    private OnSubscriptionSelectedListener mListener;
    private List<SubscriptionItem> subscriptionDataList;

    // Listener interface implemented by the hosting Activity
    public interface OnSubscriptionSelectedListener {
        void onSubscriptionSelected(SubscriptionItem selectedItem);
    }

    /**
     * Factory method to create a new instance of this fragment.
     * @param subscriptionDataList List of items to display.
     * @param listener The listener for item selection events.
     * @return A new instance of fragment SubscriptionFragment.
     */
    public static SubscriptionFragment newInstance(List<SubscriptionItem> subscriptionDataList, OnSubscriptionSelectedListener listener) {
        Log.d(TAG, "newInstance called with " + (subscriptionDataList != null ? subscriptionDataList.size() : 0) + " items.");
        SubscriptionFragment fragment = new SubscriptionFragment();
        Bundle args = new Bundle();
        // Ensure SubscriptionItem implements Parcelable for this to work
        args.putParcelableArrayList(ARG_SUBSCRIPTION_LIST, new ArrayList<>(subscriptionDataList));
        fragment.setArguments(args);
        // We attach the listener in onAttach, but setting it here ensures it's available if needed early
        fragment.mListener = listener;
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called.");
        if (getArguments() != null) {
            // Retrieve the list passed via newInstance
            subscriptionDataList = getArguments().getParcelableArrayList(ARG_SUBSCRIPTION_LIST);
            Log.d(TAG, "Retrieved " + (subscriptionDataList != null ? subscriptionDataList.size() : "null") + " items from arguments.");
        } else {
            Log.w(TAG, "No arguments found in onCreate.");
            subscriptionDataList = new ArrayList<>(); // Initialize to avoid null pointer
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView called.");
        View view = inflater.inflate(R.layout.fragment_subscription_list, container, false);
        recyclerView = view.findViewById(R.id.subscription_recycler_view); // Ensure this ID exists in your layout
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setHasFixedSize(true); // Optimization if item sizes don't change

        // Initialize adapter *only if* we have the listener and data
        if (mListener != null && subscriptionDataList != null && getContext() != null) {
            Log.d(TAG, "Setting up adapter with " + subscriptionDataList.size() + " items.");
            // Pass the listener to the adapter if it needs it directly, or use the fragment's mListener
            adapter = new SubscriptionAdapter(subscriptionDataList, mListener); // Ensure your Adapter constructor matches
            recyclerView.setAdapter(adapter);
        } else {
            Log.e(TAG, "Cannot create adapter - Listener is null, data is null, or context is null.");
            // Handle error case - maybe show an empty state view?
        }

        return view;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Log.d(TAG, "onAttach called.");
        // Ensure the hosting activity implements the listener interface
        if (context instanceof OnSubscriptionSelectedListener) {
            mListener = (OnSubscriptionSelectedListener) context;
            Log.d(TAG, "Listener attached successfully.");
        } else {
            // If the direct parent isn't the listener, check the parent fragment or target fragment
            if (getParentFragment() instanceof OnSubscriptionSelectedListener) {
                mListener = (OnSubscriptionSelectedListener) getParentFragment();
                Log.d(TAG, "Listener attached successfully from Parent Fragment.");
            } else {
                throw new RuntimeException(context.toString()
                        + " or its parent must implement OnSubscriptionSelectedListener");
            }
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Log.d(TAG, "onDetach called.");
        mListener = null; // Clean up listener reference
    }

    /**
     * Provides access to the fragment's adapter, primarily for the Activity
     * to call methods like resetSelectedPosition.
     * @return The SubscriptionAdapter instance, or null if not initialized.
     */
    @Nullable
    public SubscriptionAdapter getAdapter() {
        return adapter;
    }
}