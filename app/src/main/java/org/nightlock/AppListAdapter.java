package org.nightlock;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.AppViewHolder> {

    private final List<AppInfo> fullList;
    private List<AppInfo> visibleList;

    public AppListAdapter(List<AppInfo> appList) {
        this.fullList = appList;
        this.visibleList = new ArrayList<>(appList);
    }

    public void filter(String query) {
        visibleList.clear();
        if (query == null || query.trim().isEmpty()) {
            visibleList.addAll(fullList);
        } else {
            String lower = query.toLowerCase(Locale.getDefault());
            for (AppInfo app : fullList) {
                if (app.name.toLowerCase(Locale.getDefault()).contains(lower)) {
                    visibleList.add(app);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.app_list_item, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppInfo app = visibleList.get(position);
        holder.nameText.setText(app.name);
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(app.isChecked);
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            app.isChecked = isChecked;
        });
    }

    @Override
    public int getItemCount() {
        return visibleList.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView nameText;

        AppViewHolder(View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.appCheckBox);
            nameText = itemView.findViewById(R.id.appNameText);
        }
    }
}