package org.nightlock;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    // Packages that are ALWAYS blocked 10 PM - 9 AM, regardless of user settings
    public static final String[] ALWAYS_BLOCKED_PACKAGES = {
            "com.google.android.youtube",
            "com.vivaldi.browser",
            "com.instagram.android",
            "com.snapchat.android"
    };
    public static final String[] ALWAYS_BLOCKED_NAMES = {
            "YouTube", "Vivaldi", "Instagram", "Snapchat"
    };

    private int startHour = 22, startMinute = 0;
    private int endHour = 7, endMinute = 0;

    private Button btnStartTime;
    private Button btnEndTime;
    private Button btnSave;
    private Button btnEnableAccessibility;
    private LinearLayout mostUsedContainer;
    private EditText searchInput;

    private List<AppInfo> apps;
    private AppListAdapter adapter;

    private static final String PREF_SAVE_LOCKED_UNTIL = "save_locked_until";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStartTime = findViewById(R.id.btnStartTime);
        btnEndTime = findViewById(R.id.btnEndTime);
        btnSave = findViewById(R.id.btnSave);
        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility);
        mostUsedContainer = findViewById(R.id.mostUsedContainer);
        searchInput = findViewById(R.id.searchInput);

        loadSavedSettings();
        updateButtonLabels();
        buildMostUsedSection();

        btnStartTime.setOnClickListener(v -> {
            new TimePickerDialog(MainActivity.this, (view, hourOfDay, minute) -> {
                startHour = hourOfDay;
                startMinute = minute;
                updateButtonLabels();
            }, startHour, startMinute, false).show();
        });

        btnEndTime.setOnClickListener(v -> {
            new TimePickerDialog(MainActivity.this, (view, hourOfDay, minute) -> {
                endHour = hourOfDay;
                endMinute = minute;
                updateButtonLabels();
            }, endHour, endMinute, false).show();
        });

        btnSave.setOnClickListener(v -> confirmAndSave());

        btnEnableAccessibility.setOnClickListener(v -> openAccessibilitySettings());

        RecyclerView recyclerView = findViewById(R.id.appRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        apps = getInstalledApps();
        applySavedCheckedState();
        adapter = new AppListAdapter(apps);
        recyclerView.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        maybePromptForAccessibility();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityButtonState();
        updateSaveButtonState();
    }

    // Builds the fixed, non-editable "always blocked" rows above the search bar
    private void buildMostUsedSection() {
        mostUsedContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (String name : ALWAYS_BLOCKED_NAMES) {
            View row = inflater.inflate(R.layout.app_list_item, mostUsedContainer, false);
            CheckBox checkBox = row.findViewById(R.id.appCheckBox);
            TextView nameText = row.findViewById(R.id.appNameText);

            nameText.setText(name);
            checkBox.setChecked(true);
            checkBox.setEnabled(false);

            mostUsedContainer.addView(row);
        }
    }

    private void maybePromptForAccessibility() {
        SharedPreferences prefs = getSharedPreferences("NightlockPrefs", MODE_PRIVATE);
        boolean hasAskedBefore = prefs.getBoolean("has_asked_accessibility", false);

        if (!hasAskedBefore && !isAccessibilityServiceEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("One-Time Setup Needed")
                    .setMessage("Nightlock needs Accessibility permission to block apps overnight. " +
                            "You'll be taken to Settings \u2014 find \"Nightlock\" and turn it on.")
                    .setCancelable(false)
                    .setPositiveButton("Continue", (dialog, which) -> {
                        prefs.edit().putBoolean("has_asked_accessibility", true).apply();
                        openAccessibilitySettings();
                    })
                    .show();
        }
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private boolean isAccessibilityServiceEnabled() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabledServices)) return false;
        return enabledServices.contains(getPackageName() + "/" + getPackageName() + ".BlockerService");
    }

    private void updateAccessibilityButtonState() {
        if (isAccessibilityServiceEnabled()) {
            btnEnableAccessibility.setText("\u2713 Blocking Service Enabled");
            btnEnableAccessibility.setEnabled(false);
        } else {
            btnEnableAccessibility.setText("Enable Blocking Service");
            btnEnableAccessibility.setEnabled(true);
        }
    }

    private void updateButtonLabels() {
        btnStartTime.setText(String.format("Start: %02d:%02d", startHour, startMinute));
        btnEndTime.setText(String.format("End: %02d:%02d", endHour, endMinute));
    }

    private List<AppInfo> getInstalledApps() {
        List<AppInfo> appList = new ArrayList<>();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        Set<String> alwaysBlockedSet = new HashSet<>();
        Collections.addAll(alwaysBlockedSet, ALWAYS_BLOCKED_PACKAGES);

        for (ApplicationInfo appInfo : installedApps) {
            String packageName = appInfo.packageName;

            boolean isLaunchable = pm.getLaunchIntentForPackage(packageName) != null;
            if (!isLaunchable) continue;

            if (packageName.equals(getPackageName())) continue;

            String name = pm.getApplicationLabel(appInfo).toString();
            appList.add(new AppInfo(name, packageName));
        }

        Collections.sort(appList, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo a, AppInfo b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });

        return appList;
    }

        private void confirmAndSave() {
        boolean anyChecked = false;
        for (AppInfo app : apps) {
            if (app.isChecked) {
                anyChecked = true;
                break;
            }
        }

        if (!anyChecked) {
            Toast.makeText(this, "Please select at least one app to block", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Confirm Time Range")
                .setMessage("Once saved, this time range cannot be changed for 24 hours. Are you sure?")
                .setPositiveButton("Save", (dialog, which) -> saveSettings())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveSettings() {
        Set<String> blockedPackages = new HashSet<>();
        for (AppInfo app : apps) {
            if (app.isChecked) {
                blockedPackages.add(app.packageName);
            }
        }

        SharedPreferences prefs = getSharedPreferences("NightlockPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("start_hour", startHour);
        editor.putInt("start_minute", startMinute);
        editor.putInt("end_hour", endHour);
        editor.putInt("end_minute", endMinute);
        editor.putStringSet("blocked_packages", blockedPackages);

        long lockUntil = System.currentTimeMillis() + (24 * 60 * 60 * 1000L);
        editor.putLong(PREF_SAVE_LOCKED_UNTIL, lockUntil);
        editor.apply();

        Toast.makeText(this, "Saved: " + blockedPackages.size() + " apps blocked", Toast.LENGTH_SHORT).show();
        updateSaveButtonState();
    }

    private void updateSaveButtonState() {
        SharedPreferences prefs = getSharedPreferences("NightlockPrefs", MODE_PRIVATE);
        long lockUntil = prefs.getLong(PREF_SAVE_LOCKED_UNTIL, 0);
        long now = System.currentTimeMillis();

        if (now < lockUntil) {
            btnSave.setEnabled(false);
            long minutesLeft = (lockUntil - now) / 60000;
            btnSave.setText("Locked (" + (minutesLeft / 60) + "h " + (minutesLeft % 60) + "m left)");
        } else {
            btnSave.setEnabled(true);
            btnSave.setText("Save Time Range");
        }
    }

    private void loadSavedSettings() {
        SharedPreferences prefs = getSharedPreferences("NightlockPrefs", MODE_PRIVATE);
        startHour = prefs.getInt("start_hour", 22);
        startMinute = prefs.getInt("start_minute", 0);
        endHour = prefs.getInt("end_hour", 7);
        endMinute = prefs.getInt("end_minute", 0);
    }

    private void applySavedCheckedState() {
        SharedPreferences prefs = getSharedPreferences("NightlockPrefs", MODE_PRIVATE);
        Set<String> blockedPackages = prefs.getStringSet("blocked_packages", new HashSet<>());
        for (AppInfo app : apps) {
            app.isChecked = blockedPackages.contains(app.packageName);
        }
    }
}