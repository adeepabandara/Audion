package com.example.audion;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.audion.data.HearingProfile;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(HearingProfile profile);
    }

    private final List<HearingProfile> profiles;
    private int selectedProfileId;
    private final OnItemClickListener listener;

    public ProfileAdapter(List<HearingProfile> profiles,
                          int selectedProfileId,
                          OnItemClickListener listener) {
        this.profiles = profiles;
        this.selectedProfileId = selectedProfileId;
        this.listener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_profile, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int pos) {
        HearingProfile profile = profiles.get(pos);
        Context ctx = holder.itemView.getContext();
        boolean isSelected = profile.getId() == selectedProfileId;

        // 1) name + tick
        holder.tvProfileName.setText(profile.getName());
        holder.ivTick.setVisibility(isSelected
            ? View.VISIBLE
            : View.INVISIBLE);

        // 2) icon lookup from the stored key
        holder.ivProfileIcon.setImageResource(
            iconResForKey(ctx, profile.getIcon())
        );

        // 3) stroke color
        int strokeColor = ContextCompat.getColor(
            ctx,
            isSelected
                ? R.color.primary
                : R.color.outline
        );
        holder.cardProfile.setStrokeColor(strokeColor);

        // 4) background
        int bgColor = ContextCompat.getColor(
            ctx,
            isSelected
                ? R.color.background
                : android.R.color.white
        );
        holder.cardProfile.setCardBackgroundColor(bgColor);

        holder.itemView.setOnClickListener(v -> {
            selectedProfileId = profile.getId();
            // move selected to top if you like
            if (pos != 0) {
                profiles.remove(pos);
                profiles.add(0, profile);
            }
            notifyDataSetChanged();
            if (listener != null) listener.onItemClick(profile);
        });
    }

    @Override
    public int getItemCount() {
        return profiles.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cardProfile;
        final ImageView ivProfileIcon;
        final TextView tvProfileName;
        final ImageView ivTick;

        ViewHolder(View itemView) {
            super(itemView);
            cardProfile     = itemView.findViewById(R.id.cardProfile);
            ivProfileIcon   = itemView.findViewById(R.id.ivProfileIcon);
            tvProfileName   = itemView.findViewById(R.id.tvProfileName);
            ivTick          = itemView.findViewById(R.id.ivTick);
        }
    }

    /** Should match your spinner’s array → drop "ic_" prefix. */
    @DrawableRes
    private static int iconResForKey(Context ctx, String key) {
        switch (key) {
            case "home":       return R.drawable.ic_home;
            case "school":     return R.drawable.ic_school;
            case "train":      return R.drawable.ic_train;
            case "palm_tree":  return R.drawable.ic_palm_tree;
            case "noodles":    return R.drawable.ic_noodles;
            case "glass_cocktail": return R.drawable.ic_glass_cocktail;
            default:           return R.drawable.ic_home;
        }
    }
}
