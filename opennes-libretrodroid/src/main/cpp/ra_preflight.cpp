// OpenNES v12.5.0 native linking and per-emulated-frame smoke test.
// This probe never logs into an account or activates a game.
#include <jni.h>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>
#include "rc_client.h"
#include "rc_api_runtime.h"
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
std::atomic<uint64_t> successfulMemorySamples{0};
std::atomic<uint64_t> observedGameGeneration{0};
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
    // A core teardown removes the observer but does not call this JNI's
    // stop method. Always detach and reset before arming again, even if a
    // previous probe client still exists. Detach waits for a live frame.
    if (probeClient) {
        libretrodroid::LibretroDroid::getInstance().setEmulatedFrameObserver(nullptr);
        rc_client_destroy(probeClient);
        probeClient = nullptr;
        rc_libretro_memory_destroy(&probeRegions);
    }

    auto* client = rc_client_create(readMappedMemory, nullptr);
    if (!client) return JNI_FALSE;

    // setEmulatedFrameObserver serializes with retro_run() using coreLock.
    // The observer captures the same client, and removing it waits for an
    // in-progress emulated frame before that client can be destroyed.
    observedFrames.store(0, std::memory_order_relaxed);
    mappedMemoryBytes.store(0, std::memory_order_relaxed);
    successfulMemorySamples.store(0, std::memory_order_relaxed);
    rc_libretro_memory_destroy(&probeRegions);
    const auto currentGeneration =
        libretrodroid::LibretroDroid::getInstance().currentGameGeneration();
    observedGameGeneration.store(currentGeneration, std::memory_order_release);
    rc_client_set_allow_background_memory_reads(client, 0);
    libretrodroid::LibretroDroid::getInstance().setEmulatedFrameObserver([client] {
        // Called under the core lock after retro_run. When the ROM/core
        // changes, discard the old RAM region addresses BEFORE reading.
        const auto generation =
            libretrodroid::LibretroDroid::getInstance().currentGameGeneration();
        if (generation != observedGameGeneration.load(std::memory_order_acquire)) {
            rc_libretro_memory_destroy(&probeRegions);
            mappedMemoryBytes.store(0, std::memory_order_relaxed);
            successfulMemorySamples.store(0, std::memory_order_relaxed);
            observedGameGeneration.store(generation, std::memory_order_release);
        }

        // Use rcheevos' NES memory mapping, not guessed RAM offsets.
        if (mappedMemoryBytes.load(std::memory_order_relaxed) == 0) {
            if (rc_libretro_memory_init(&probeRegions, nullptr, coreRegion, 7)) {
                mappedMemoryBytes.store(
                    static_cast<uint64_t>(probeRegions.total_size),
                    std::memory_order_relaxed
                );
            }
        }
        // A successful mapping is not enough; verify that a byte at the
        // NES runtime's zero-page address is actually readable. Never
        // expose the value or send it over the network.
        if (mappedMemoryBytes.load(std::memory_order_relaxed) != 0) {
            uint8_t sample = 0;
            if (rc_libretro_memory_read(&probeRegions, 0, &sample, 1) == 1)
                successfulMemorySamples.fetch_add(1, std::memory_order_relaxed);
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
    observedGameGeneration.store(0, std::memory_order_relaxed);
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
    // A stopped/unloaded game must not display a stale positive memory map.
    if (observedGameGeneration.load(std::memory_order_acquire) !=
        libretrodroid::LibretroDroid::getInstance().currentGameGeneration())
        return 0;
    return static_cast<jlong>(mappedMemoryBytes.load(std::memory_order_relaxed));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_successfulMemorySamples(
    JNIEnv*, jobject
) {
    if (observedGameGeneration.load(std::memory_order_acquire) !=
        libretrodroid::LibretroDroid::getInstance().currentGameGeneration())
        return 0;
    return static_cast<jlong>(successfulMemorySamples.load(std::memory_order_relaxed));
}


/*
 * Native RetroAchievements game identity verification.
 *
 * These helpers use the exact pinned rcheevos 12.5.0 request encoder and
 * response parser. Java/Kotlin owns the bounded HTTPS transport; C++ never
 * handles credentials, follows redirects, or reads core memory here.
 * A matched game ID alone MUST NOT be used to report an active rc_client
 * session, achievement progress, or an unlock.
 */
namespace {
bool validMd5(const std::string& hash) {
    if (hash.size() != 32) return false;
    for (unsigned char ch : hash) {
        if (!((ch >= '0' && ch <= '9') ||
              (ch >= 'a' && ch <= 'f') ||
              (ch >= 'A' && ch <= 'F'))) return false;
    }
    return true;
}
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_prepareGameResolveRequest(
    JNIEnv* env, jobject, jstring rawMd5
) {
    if (!rawMd5) return nullptr;
    const char* utf = env->GetStringUTFChars(rawMd5, nullptr);
    if (!utf) return nullptr;
    const std::string hash(utf);
    env->ReleaseStringUTFChars(rawMd5, utf);
    if (!validMd5(hash)) return nullptr;

    rc_api_resolve_hash_request_t params{};
    params.game_hash = hash.c_str();
    rc_api_request_t request{};
    const int result = rc_api_init_resolve_hash_request(&request, &params);
    std::string url;
    std::string form;
    // Official rcheevos constructs a POST request. Preserve both parts
    // instead of accidentally issuing a GET with no hash parameters.
    if (result == RC_OK && request.url && request.post_data) {
        url.assign(request.url);
        form.assign(request.post_data);
    }
    rc_api_destroy_request(&request);
    if (url.empty() || form.empty()) return nullptr;

    jclass stringClass = env->FindClass("java/lang/String");
    if (!stringClass) return nullptr;
    jobjectArray parts = env->NewObjectArray(2, stringClass, nullptr);
    env->DeleteLocalRef(stringClass);
    if (!parts) return nullptr;
    jstring endpoint = env->NewStringUTF(url.c_str());
    jstring body = env->NewStringUTF(form.c_str());
    if (!endpoint || !body) {
        if (endpoint) env->DeleteLocalRef(endpoint);
        if (body) env->DeleteLocalRef(body);
        return nullptr;
    }
    env->SetObjectArrayElement(parts, 0, endpoint);
    env->SetObjectArrayElement(parts, 1, body);
    env->DeleteLocalRef(endpoint);
    env->DeleteLocalRef(body);
    return env->ExceptionCheck() ? nullptr : parts;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_parseGameResolveResponse(
    JNIEnv* env, jobject, jbyteArray rawBody, jint httpStatus
) {
    if (!rawBody || httpStatus < 200 || httpStatus >= 300) return 0;
    const jsize size = env->GetArrayLength(rawBody);
    if (size <= 0 || size > 2 * 1024 * 1024) return 0;
    // Ensure a NUL terminator for versions of the parser that expect it,
    // while also passing the exact content length to the official parser.
    std::vector<char> payload(static_cast<size_t>(size) + 1, 0);
    env->GetByteArrayRegion(
        rawBody, 0, size, reinterpret_cast<jbyte*>(payload.data())
    );
    if (env->ExceptionCheck()) return 0;

    rc_api_server_response_t server{};
    server.body = payload.data();
    server.body_length = static_cast<size_t>(size);
    server.http_status_code = httpStatus;
    rc_api_resolve_hash_response_t parsed{};
    const int result = rc_api_process_resolve_hash_server_response(&parsed, &server);
    const uint32_t id =
        (result == RC_OK && parsed.response.succeeded) ? parsed.game_id : 0;
    rc_api_destroy_resolve_hash_response(&parsed);
    // Java side represents only positive signed game IDs.
    return id > 0 && id <= 0x7fffffffU ? static_cast<jint>(id) : 0;
}
