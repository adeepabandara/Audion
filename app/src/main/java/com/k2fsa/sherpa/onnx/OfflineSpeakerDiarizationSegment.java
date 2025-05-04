package com.k2fsa.sherpa.onnx;

/**
 * Represents a segment of audio with speaker information.
 */
public class OfflineSpeakerDiarizationSegment {
    private final float startTime;
    private final float endTime;
    private final int speakerId;
    private final float confidence;

    public OfflineSpeakerDiarizationSegment(float startTime, float endTime, int speakerId) {
        this(startTime, endTime, speakerId, 1.0f); // Default confidence to 1.0
    }

    public OfflineSpeakerDiarizationSegment(float startTime, float endTime, int speakerId, float confidence) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.speakerId = speakerId;
        this.confidence = confidence;
    }

    public float getStartTime() {
        return startTime;
    }

    public float getEndTime() {
        return endTime;
    }

    public int getSpeakerId() {
        return speakerId;
    }

    public float getConfidence() {
        return confidence;
    }

    @Override
    public String toString() {
        return String.format("Segment[start=%.2fs, end=%.2fs, speaker=%d, confidence=%.2f]",
                startTime, endTime, speakerId, confidence);
    }
} 