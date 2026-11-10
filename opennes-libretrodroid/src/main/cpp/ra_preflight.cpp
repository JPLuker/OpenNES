// OpenNES v12.5.0 native linking and per-emulated-frame smoke test.
// This probe never logs into an account or activates a game.
#include <jni.h>
#include <atomic>
#include <mutex>
#include "rc_client.h"
#include "rc_libretro.h"
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
std::atomic<uint64_t> mappedMemoryBytes{0};
rc_libretro_memory_regions_t probeRegions{};

void RC_CCONV coreRegion(uint32_t id, rc_libretro_core_memory_info_t* info) {
    // Invoked by rc_libretro_memory_init in the emulation observer, which
    // already holds LibretroDroid::coreLock. Do NOT lock recursively.
    const auto data = libretrodroid::LibretroDroid::getInstance()
        .coreMemoryRegionWhileLocked(id);
    info->data = data.first;
    info->size = data.second;
}

uint32_t RC_CCONV readMappedMemory(
    uint32_t address, uint8_t* buffer, uint32_t bytes, rc_client_t*
) {
    // rc_client is strictly disabled for background reads: this callback
    // runs only while the native emulation thread owns coreLock.
    return rc_libretro_memory_read(&probeRegions, address, buffer, bytes);
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_startFrameProbe(
    JNIEnv*, jobject
) {
    std::lock_guard<std::mutex> guard(probeMutex);
    if (probeClient) return JNI_TRUE;

    auto* client = rc_client_create(readMappedMemory, nullptr);
    if (!client) return JNI_FALSE;

    // setEmulatedFrameObserver serializes with retro_run() using coreLock.
    // The observer captures the same client, and removing it waits for an
    // in-progress emulated frame before that client can be destroyed.
    observedFrames.store(0, std::memory_order_relaxed);
    mappedMemoryBytes.store(0, std::memory_order_relaxed);
    rc_libretro_memory_destroy(&probeRegions);
    rc_client_set_allow_background_memory_reads(client, 0);
    libretrodroid::LibretroDroid::getInstance().setEmulatedFrameObserver([client] {
        // Lazily initialize mapping only after a real game has been loaded.
        // Use rcheevos' official NES memory regions, not a guessed 2-KiB
        // pointer layout. No map is retained across stop or core destroy.
        if (mappedMemoryBytes.load(std::memory_order_relaxed) == 0) {
            if (rc_libretro_memory_init(&probeRegions, nullptr, coreRegion, 7)) {
                mappedMemoryBytes.store(
                    static_cast<uint64_t>(probeRegions.total_size),
                    std::memory_order_relaxed
                );
            }
        }
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
    rc_libretro_memory_destroy(&probeRegions);
    mappedMemoryBytes.store(0, std::memory_order_relaxed);
    probeClient = nullptr;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_observedProbeFrames(
    JNIEnv*, jobject
) {
    return static_cast<jlong>(observedFrames.load(std::memory_order_relaxed));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_mappedProbeBytes(
    JNIEnv*, jobject
) {
    return static_cast<jlong>(mappedMemoryBytes.load(std::memory_order_relaxed));
}
