#include <jni.h>
#include <string>
#include <android/log.h>
#include "rnnoise/include/rnnoise.h"

#define FRAME_SIZE 480  // RNNoise works with 48kHz audio, 10ms frames

// Store the last VAD probability for each state
static thread_local float last_vad_probability = 0.0f;

extern "C" {

// Log macros
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "RNNoiseJNI", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "RNNoiseJNI", __VA_ARGS__)

JNIEXPORT jlong JNICALL
Java_com_example_androidapp_RNNoise_createState(JNIEnv *env, jobject /* this */) {
    DenoiseState *st = rnnoise_create(nullptr);
    return reinterpret_cast<jlong>(st);
}

JNIEXPORT void JNICALL
Java_com_example_androidapp_RNNoise_destroyState(JNIEnv *env, jobject /* this */, jlong state) {
    if (state != 0) {
        DenoiseState *st = reinterpret_cast<DenoiseState *>(state);
        rnnoise_destroy(st);
    }
}

JNIEXPORT jobject JNICALL
Java_com_example_androidapp_RNNoise_processFrame(JNIEnv *env, jobject /* this */,
                                               jlong state, jfloatArray input) {
    if (state == 0) {
        LOGE("Invalid state pointer");
        return nullptr;
    }

    DenoiseState *st = reinterpret_cast<DenoiseState *>(state);
    
    // Get input array
    jsize inputLength = env->GetArrayLength(input);
    if (inputLength != FRAME_SIZE) {
        LOGE("Invalid input size: %d (expected %d)", inputLength, FRAME_SIZE);
        return nullptr;
    }

    // Get input data
    jfloat *inputBuffer = env->GetFloatArrayElements(input, nullptr);
    if (inputBuffer == nullptr) {
        LOGE("Failed to get input buffer");
        return nullptr;
    }

    // Create output array
    jfloatArray output = env->NewFloatArray(FRAME_SIZE);
    if (output == nullptr) {
        LOGE("Failed to create output array");
        env->ReleaseFloatArrayElements(input, inputBuffer, JNI_ABORT);
        return nullptr;
    }

    // Get output buffer
    jfloat *outputBuffer = env->GetFloatArrayElements(output, nullptr);
    if (outputBuffer == nullptr) {
        LOGE("Failed to get output buffer");
        env->ReleaseFloatArrayElements(input, inputBuffer, JNI_ABORT);
        return nullptr;
    }

    // Process frame and get VAD probability
    float vad = rnnoise_process_frame(st, outputBuffer, inputBuffer);
    LOGD("VAD probability: %f", vad);

    // Create ProcessResult object to return both the processed audio and VAD probability
    jclass processResultClass = env->FindClass("com/example/androidapp/RNNoise$ProcessResult");
    if (processResultClass == nullptr) {
        LOGE("Failed to find ProcessResult class");
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(processResultClass, "<init>", "([FF)V");
    if (constructor == nullptr) {
        LOGE("Failed to find ProcessResult constructor");
        return nullptr;
    }

    // Release buffers
    env->ReleaseFloatArrayElements(input, inputBuffer, JNI_ABORT);
    env->ReleaseFloatArrayElements(output, outputBuffer, 0);

    // Create and return ProcessResult object
    return env->NewObject(processResultClass, constructor, output, vad);
}

} // extern "C"