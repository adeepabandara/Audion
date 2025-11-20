//package com.example.audion;

import com.audion.psap.R;
//
//import android.content.Intent;
//import android.os.Bundle;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.TextView;
//
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.recyclerview.widget.LinearLayoutManager;
//import androidx.recyclerview.widget.RecyclerView;
//
//import com.google.android.material.bottomnavigation.BottomNavigationView;
//
//import java.util.ArrayList;
//import java.util.List;
//
//public class HelpActivity extends AppCompatActivity {
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.help_activity);
//
//        // 1. Play Button Tour
//        findViewById(R.id.guideCard1).setOnClickListener(v -> {
//            launchHomeWithTour("PLAY");
//        });
//
//        // 2. Amplification Tour
//        findViewById(R.id.guideCard2).setOnClickListener(v -> {
//            launchHomeWithTour("AMPLIFY");
//        });
//
//        // 3. Noise Cancellation Tour
//        findViewById(R.id.guideCard3).setOnClickListener(v -> {
//            launchHomeWithTour("NOISE");
//        });
//
//        // Bottom navigation
//        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
//        bottomNav.setSelectedItemId(R.id.navigation_help);
//        bottomNav.setOnItemSelectedListener(item -> {
//            int id = item.getItemId();
//            if (id == R.id.navigation_home)        startActivity(new Intent(this, MainActivity.class));
//            else if (id == R.id.navigation_frequencies) startActivity(new Intent(this, FrequencyActivity.class));
//            else if (id == R.id.navigation_music)   startActivity(new Intent(this, MusicPlayerActivity.class));
//            else if (id == R.id.navigation_settings)startActivity(new Intent(this, SettingsActivity.class));
//            overridePendingTransition(0, 0);
//            return true;
//        });
//
//        // FAQ list setup
//        RecyclerView faqRv = findViewById(R.id.faqRecyclerView);
//        faqRv.setLayoutManager(new LinearLayoutManager(this));
//        List<FaqItem> faqs = new ArrayList<>();
//        faqs.add(new FaqItem("How do I adjust volume?", "Use the slider under Volume."));
//        faqs.add(new FaqItem("How to enable captions?", "Tap the 'Open Captions' button."));
//        faqRv.setAdapter(new FaqAdapter(faqs));
//    }
//
//    private void launchHomeWithTour(String tourType) {
//        Intent intent = new Intent(this, HomeActivity.class);
//        intent.putExtra("START_TOUR", true);
//        intent.putExtra("TOUR_TYPE", tourType);
//        startActivity(intent);
//        overridePendingTransition(0, 0);
//    }
//
//    // FAQ data model
//    private static class FaqItem {
//        final String question, answer;
//        FaqItem(String q, String a) { question = q; answer = a; }
//    }
//
//    // RecyclerView adapter
//    private class FaqAdapter extends RecyclerView.Adapter<FaqAdapter.VH> {
//        private final List<FaqItem> data;
//        private int expandedPos = -1;
//        FaqAdapter(List<FaqItem> items) { data = items; }
//
//        @NonNull @Override
//        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
//            View v = getLayoutInflater().inflate(R.layout.item_faq_layout, parent, false);
//            return new VH(v);
//        }
//
//        @Override
//        public void onBindViewHolder(@NonNull VH holder, int position) {
//            FaqItem item = data.get(position);
//            holder.question.setText(item.question);
//            holder.answer.setText(item.answer);
//            boolean isExpanded = (position == expandedPos);
//            holder.answer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
//            holder.itemView.setOnClickListener(v -> {
//                int previous = expandedPos;
//                expandedPos = isExpanded ? -1 : position;
//                notifyItemChanged(previous);
//                notifyItemChanged(expandedPos);
//            });
//        }
//
//        @Override public int getItemCount() { return data.size(); }
//
//        class VH extends RecyclerView.ViewHolder {
//            final TextView question, answer;
//            VH(View itemView) {
//                super(itemView);
//                question = itemView.findViewById(R.id.tvQuestion);
//                answer   = itemView.findViewById(R.id.tvAnswer);
//            }
//        }
//    }
//}
