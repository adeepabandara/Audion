package com.example.audion;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
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

    public ProfileAdapter(List<HearingProfile> profiles, int selectedProfileId, OnItemClickListener listener) {
        this.profiles = profiles;
        this.selectedProfileId = selectedProfileId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_profile, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HearingProfile profile = profiles.get(position);
        Context ctx = holder.itemView.getContext();
        boolean isSelected = profile.getId() == selectedProfileId;

        // 1) Text + tick
        holder.tvProfileName.setText(profile.getName());
        holder.ivTick.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);

        // 2) Stroke color
        int strokeColor = ContextCompat.getColor(
            ctx,
            isSelected ? R.color.primary : R.color.outline
        );
        holder.cardProfile.setStrokeColor(strokeColor);

        // 3) Background color: selected gets your "background" color, others stay white
        int bgColor = ContextCompat.getColor(
            ctx,
            isSelected ? R.color.background : android.R.color.white
        );
        holder.cardProfile.setCardBackgroundColor(bgColor);

        holder.itemView.setOnClickListener(v -> {
            selectedProfileId = profile.getId();
            if (position != 0) {
                profiles.remove(position);
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
        final TextView tvProfileName;
        final ImageView ivTick;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardProfile   = itemView.findViewById(R.id.cardProfile);
            tvProfileName = itemView.findViewById(R.id.tvProfileName);
            ivTick        = itemView.findViewById(R.id.ivTick);
        }
    }
}
