package project.witty.keys.app.countryselector;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import project.witty.keys.R;
import project.witty.keys.app.entities.Country;

public class CountryBottomSheetFragment extends BottomSheetDialogFragment implements CountryAdapter.OnCountryClickListener {
    private RecyclerView recyclerView;
    private CountryAdapter adapter;
    private List<Country> countryList;
    private OnCountrySelectedListener countrySelectedListener;

    public interface OnCountrySelectedListener {
        void onCountrySelected(Country country);
    }

    public CountryBottomSheetFragment() {
        // Required empty constructor
    }

    public void setOnCountrySelectedListener(OnCountrySelectedListener listener) {
        this.countrySelectedListener = listener;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_country_bottom_sheet, container, false);

        recyclerView = view.findViewById(R.id.countryRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        countryList = getCountriesWithPhoneCodes();
        adapter = new CountryAdapter(countryList, this);
        recyclerView.setAdapter(adapter);

        return view;
    }

    private List<Country> getCountriesWithPhoneCodes() {
        List<Country> countries = new ArrayList<>();
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        for (String regionCode : phoneUtil.getSupportedRegions()) {
            Locale locale = new Locale("", regionCode);
            int phoneCode = phoneUtil.getCountryCodeForRegion(regionCode);
            countries.add(new Country(locale.getDisplayCountry(), "+" + phoneCode));
        }
        countries.sort((c1, c2) -> c1.getName().compareTo(c2.getName())); // Sort alphabetically
        return countries;
    }

    @Override
    public void onCountryClick(Country country) {
        if (countrySelectedListener != null) {
            countrySelectedListener.onCountrySelected(country);
        }
        dismiss(); // Close BottomSheet after selection
    }
}