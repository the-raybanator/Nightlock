package org.nightlock;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class BlockerService extends AccessibilityService {

    private static final Set<String> ALWAYS_BLOCKED = new HashSet<>();
    static {
        Collections.addAll(ALWAYS_BLOCKED, MainActivity.ALWAYS_BLOCKED_PACKAGES);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        String foregroundPackage = event.getPackageName().toString();

        // Rule 1: the 4 fixed apps are ALWAYS blocked 10 PM - 9 AM, no matter what
        if (ALWAYS_BLOCKED.contains(foregroundPackage) && isWithinWindow(22, 0, 9, 0)) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            return;
        }

        // Rule 2: user's own selected apps + custom time range
        if (isWithinUserLockWindow() && isUserBlockedApp(foregroundPackage)) {
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
    }

    private boolean isUserBlockedApp(String packageName) {
        if (packageName.equals(getPackageName())) return false;

        SharedPreferences prefs = getSharedPreferences("NightlockPrefs", MODE_PRIVATE);
        Set<String> blockedApps = prefs.getStringSet("blocked_packages", new HashSet<>());
        return blockedApps.contains(packageName);
    }

    private boolean isWithinUserLockWindow() {
        SharedPreferences prefs = getSharedPreferences("NightlockPrefs", MODE_PRIVATE);
        int startHour = prefs.getInt("start_hour", 22);
        int startMinute = prefs.getInt("start_minute", 0);
        int endHour = prefs.getInt("end_hour", 7);
        int endMinute = prefs.getInt("end_minute", 0);
        return isWithinWindow(startHour, startMinute, endHour, endMinute);
    }

    private boolean isWithinWindow(int startHour, int startMinute, int endHour, int endMinute) {
        Calendar now = Calendar.getInstance();
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int startMinutes = startHour * 60 + startMinute;
        int endMinutes = endHour * 60 + endMinute;

        if (startMinutes <= endMinutes) {
            return nowMinutes >= startMinutes && nowMinutes < endMinutes;
        } else {
            return nowMinutes >= startMinutes || nowMinutes < endMinutes;
        }
    }

    @Override
    public void onInterrupt() {
    }
}