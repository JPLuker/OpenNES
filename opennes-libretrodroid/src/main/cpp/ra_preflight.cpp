// OpenNES v12.5.0 native linking and per-emulated-frame smoke test.
// This probe never logs into an account or activates a game.
#include <jni.h>
#include <atomic>
#include <mutex>
#include "rc_client.h"
#include "libretrodroid.h"

extern "C" JNIEXPORT jboolean JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_preflight(
    JNIEnv*, jobject
) {
    // A dormant client issues no requests and cannot trigger achievements.
    rc_client_t* client = rc_client_create(nullptr, nullptr);
    if (!client) return JNI_FALSE;
    rc_client_destroy(client);
    return JNI_TRUE;
}

namespace {
std::mutex probeMutex;
rc_client_t* probeClient = nullptr;
std::atomic<uint64_t> observedFrames{0};
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_startFrameProbe(
    JNIEnv*, jobject
) {
    std::lock_guard<std::mutex> guard(probeMutex);
    if (probeClient) return JNI_TRUE;

    auto* client = rc_client_create(nullptr, nullptr);
    if (!client) return JNI_FALSE;

    // setEmulatedFrameObserver serializes with retro_run() using coreLock.
    // The observer captures the same client, and removing it waits for an
    // in-progress emulated frame before that client can be destroyed.
    observedFrames.store(0, std::memory_order_relaxed);
    libretrodroid::LibretroDroid::getInstance().setEmulatedFrameObserver([client] {
        rc_client_do_frame(client);
        observedFrames.fetch_add(1, std::memory_order_relaxed);
    });
    probeClient = client;
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_stopFrameProbe(
    JNIEnv*, jobject
) {
    std::lock_guard<std::mutex> guard(probeMutex);
    if (!probeClient) return;
    libretrodroid::LibretroDroid::getInstance().setEmulatedFrameObserver(nullptr);
    rc_client_destroy(probeClient);
    probeClient = nullptr;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_observedProbeFrames(
    JNIEnv*, jobject
) {
    return static_cast<jlong>(observedFrames.load(std::memory_order_relaxed));
}
