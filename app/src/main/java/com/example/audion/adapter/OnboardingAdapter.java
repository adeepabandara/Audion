package com.example.audion.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.audion.psap.R;
import com.example.audion.model.OnboardingSlide;

import java.util.List;

public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder> {

    private final List<OnboardingSlide> slides;

    public OnboardingAdapter(List<OnboardingSlide> slides) {
        this.slides = slides;
    }

    @NonNull
    @Override
    public OnboardingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding_slide, parent, false);
        return new OnboardingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OnboardingViewHolder holder, int position) {
        OnboardingSlide slide = slides.get(position);
        holder.bind(slide);
    }

    @Override
    public int getItemCount() {
        return slides.size();
    }

    static class OnboardingViewHolder extends RecyclerView.ViewHolder {
        private final TextView slideTitle;
        private final TextView slideDescription;
        private final ImageView slideImage;

        public OnboardingViewHolder(@NonNull View itemView) {
            super(itemView);
            slideTitle = itemView.findViewById(R.id.slideTitle);
            slideDescription = itemView.findViewById(R.id.slideDescription);
            slideImage = itemView.findViewById(R.id.slideImage);
        }

        public void bind(OnboardingSlide slide) {
            slideTitle.setText(slide.getTitle());
            slideDescription.setText(slide.getDescription());
            slideImage.setImageResource(slide.getImageResId());
        }
    }
}
