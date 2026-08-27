package org.nightlock;

public class AppInfo {
    public String name;
    public String packageName;
    public boolean isChecked;

    public AppInfo(String name, String packageName) {
        this.name = name;
        this.packageName = packageName;
        this.isChecked = false;
    }
}