// Game-owned RetroAchievements runtime. Softcore only until save-state and
// hardcore policy have been separately reviewed and accepted on hardware.
#include <jni.h>
#include <algorithm>
#include <atomic>
#include <cctype>
#include <cstdint>
#include <cstring>
#include <deque>
#include <map>
#include <mutex>
#include <string>
#include <utility>
#include <vector>
#include "rc_client.h"
#include "rc_libretro.h"
#include "libretrodroid.h"

namespace {
constexpr size_t kQueueLimit = 12;
constexpr size_t kEventLimit = 32;
constexpr size_t kMaxUrl = 2048;
constexpr size_t kMaxForm = 64 * 1024;
constexpr jsize kMaxResponse = 2 * 1024 * 1024;
std::mutex lifecycleMutex;
std::recursive_mutex clientMutex;
std::atomic<uint64_t> activeId{0};
// Only the observer, under coreLock, is allowed to read emulated NES RAM.
// rc_client_idle may run on Kotlin's IO thread, but must never access RAM.
thread_local uint64_t frameReadingSessionId = 0;
uint64_t issuedId = 0;
uint64_t requestSequence = 0;
rc_client_t* gameClient = nullptr;
rc_libretro_memory_regions_t regions{};
uint64_t coreGeneration = 0;
std::string expectedHash;
int sessionState = -2; // 0=login, 1=load, 3=map pending, 2=active, -1=failure, -2=closed
bool disposing = false;
bool memoryMapped = false;

struct Request {
    std::string url;
    std::string form;
    rc_client_server_callback_t callback = nullptr;
    void* userData = nullptr;
};
std::map<uint64_t, Request> inflight;
std::deque<uint64_t> requestOrder;
std::deque<std::string> userEvents;

bool validHash(const std::string& value) {
    if (value.size() != 32) return false;
    for (unsigned char c : value) {
        if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') ||
              (c >= 'A' && c <= 'F'))) return false;
    }
    return true;
}

std::string copyUtf(JNIEnv* env, jstring source) {
    if (!source) return {};
    const char* chars = env->GetStringUTFChars(source, nullptr);
    if (!chars) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(source, chars);
    return result;
}

bool validName(const std::string& value) {
    if (value.size() < 2 || value.size() > 64) return false;
    for (unsigned char c : value) {
        if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
              (c >= '0' && c <= '9') || c == '_' || c == '-')) return false;
    }
    return true;
}

bool validToken(const std::string& token) {
    if (token.size() < 16 || token.size() > 2048) return false;
    for (unsigned char c : token) {
        if (c <= 32 || c == 127) return false;
    }
    return true;
}

std::string safeLabel(const char* source, size_t limit) {
    if (!source) return {};
    const size_t size = strnlen(source, limit);
    std::string result(source, size);
    for (char& c : result) {
        if (static_cast<unsigned char>(c) < 32) c = ' ';
    }
    return result;
}

void enqueueEvent(std::string event) {
    if (userEvents.size() >= kEventLimit) userEvents.pop_front();
    userEvents.push_back(std::move(event));
}

void failCallback(rc_client_server_callback_t callback, void* userData) {
    if (!callback) return;
    rc_api_server_response_t response{};
    response.http_status_code = RC_API_SERVER_RESPONSE_CLIENT_ERROR;
    callback(&response, userData);
}

void RC_CCONV serverCall(
    const rc_api_request_t* request,
    rc_client_server_callback_t callback,
    void* userData,
    rc_client_t* client
) {
    std::lock_guard<std::recursive_mutex> lock(clientMutex);
    if (disposing || activeId.load() == 0 || client != gameClient ||
        !request || !request->url || !request->post_data ||
        inflight.size() >= kQueueLimit) {
        failCallback(callback, userData);
        return;
    }
    const size_t urlSize = strnlen(request->url, kMaxUrl + 1);
    const size_t formSize = strnlen(request->post_data, kMaxForm + 1);
    if (urlSize == 0 || urlSize > kMaxUrl || formSize == 0 || formSize > kMaxForm) {
        failCallback(callback, userData);
        return;
    }
    const uint64_t id = ++requestSequence;
    inflight.emplace(id, Request{
        std::string(request->url, urlSize),
        std::string(request->post_data, formSize),
        callback,
        userData
    });
    requestOrder.push_back(id);
}

void RC_CCONV onEvent(const rc_client_event_t* event, rc_client_t* client) {
    std::lock_guard<std::recursive_mutex> lock(clientMutex);
    if (!event || client != gameClient || disposing || sessionState != 2) return;
    if (event->type == RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED && event->achievement) {
        const rc_client_achievement_t* achievement = event->achievement;
        enqueueEvent(
            "unlock\t" + std::to_string(achievement->id) + "\t" +
            std::to_string(achievement->points) + "\t" +
            safeLabel(achievement->title, 220)
        );
    } else if (event->type == RC_CLIENT_EVENT_DISCONNECTED) {
        enqueueEvent("disconnected");
    } else if (event->type == RC_CLIENT_EVENT_RECONNECTED) {
        enqueueEvent("reconnected");
    }
}

void RC_CCONV onLoaded(int result, const char*, rc_client_t* client, void*) {
    std::lock_guard<std::recursive_mutex> lock(clientMutex);
    if (disposing || client != gameClient || activeId.load() == 0) return;
    const rc_client_game_t* game =
        (result == RC_OK && rc_client_is_game_loaded(client))
            ? rc_client_get_game_info(client) : nullptr;
    if (!game || game->id == 0 || !game->hash) {
        sessionState = -1;
        return;
    }
    std::string actualHash(game->hash);
    std::transform(actualHash.begin(), actualHash.end(), actualHash.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    if (actualHash != expectedHash) {
        sessionState = -1;
        return;
    }
    // A successful game response is not yet proof that the emulator RAM map
    // works. Only the next actual core frame can validate memory reads.
    sessionState = 3;
}

void RC_CCONV onLogin(int result, const char*, rc_client_t* client, void*) {
    std::lock_guard<std::recursive_mutex> lock(clientMutex);
    if (disposing || client != gameClient || activeId.load() == 0) return;
    if (result != RC_OK || !rc_client_get_user_info(client)) {
        sessionState = -1;
        return;
    }
    sessionState = 1;
    rc_client_begin_load_game(client, expectedHash.c_str(), onLoaded, nullptr);
}

void RC_CCONV coreRegion(uint32_t id, rc_libretro_core_memory_info_t* info) {
    // Called under coreLock by the emulation-thread observer.
    const auto region = libretrodroid::LibretroDroid::getInstance()
        .coreMemoryRegionWhileLocked(id);
    info->data = region.first;
    info->size = region.second;
}

uint32_t RC_CCONV readMemory(
    uint32_t address, uint8_t* buffer, uint32_t bytes, rc_client_t*
) {
    // Background memory reads are disabled, so this runs only while the
    // observer owns the emulator's coreLock and the game generation matches.
    if (frameReadingSessionId != activeId.load() || !memoryMapped || sessionState != 2 ||
        libretrodroid::LibretroDroid::getInstance().currentGameGeneration() != coreGeneration)
        return 0;
    return rc_libretro_memory_read(&regions, address, buffer, bytes);
}

void onEmulatedFrame(uint64_t id) {
    std::lock_guard<std::recursive_mutex> lock(clientMutex);
    if (activeId.load() != id || !gameClient || disposing) return;
    if (libretrodroid::LibretroDroid::getInstance().currentGameGeneration() != coreGeneration) {
        sessionState = -2;
        return;
    }
    if (sessionState != 2 && sessionState != 3) return;
    if (!memoryMapped) {
        // Console 7 is NES in pinned rcheevos; this is the tested memory map
        // used by the previous frame probe. Do not guess RAM addresses.
        if (!rc_libretro_memory_init(&regions, nullptr, coreRegion, 7) ||
            regions.total_size == 0) {
            rc_libretro_memory_destroy(&regions);
            sessionState = -1;
            return;
        }
        uint8_t firstByte = 0;
        if (rc_libretro_memory_read(&regions, 0, &firstByte, 1) != 1) {
            // Never process a frame against an unreadable map. Conditions
            // reading implicit zeroes could otherwise trigger false awards.
            rc_libretro_memory_destroy(&regions);
            sessionState = -1;
            return;
        }
        memoryMapped = true;
        // Never expose an Active session to Kotlin until the first NES RAM
        // read succeeded on the matching emulation core generation.
        sessionState = 2;
    }
    frameReadingSessionId = id;
    rc_client_do_frame(gameClient);
    frameReadingSessionId = 0;
}

void closeClient() {
    // Called with clientMutex after the frame observer has been detached.
    disposing = true;
    activeId.store(0);
    requestOrder.clear();
    userEvents.clear();
    while (!inflight.empty()) {
        auto it = inflight.begin();
        Request request = std::move(it->second);
        inflight.erase(it);
        failCallback(request.callback, request.userData);
    }
    if (gameClient) {
        rc_client_unload_game(gameClient);
        rc_client_logout(gameClient);
        rc_client_destroy(gameClient);
        gameClient = nullptr;
    }
    rc_libretro_memory_destroy(&regions);
    memoryMapped = false;
    expectedHash.clear();
    sessionState = -2;
    coreGeneration = 0;
    disposing = false;
}

jobjectArray stringArray(JNIEnv* env, const std::vector<std::string>& values) {
    jclass cls = env->FindClass("java/lang/String");
    if (!cls) return nullptr;
    jobjectArray out = env->NewObjectArray(static_cast<jsize>(values.size()), cls, nullptr);
    env->DeleteLocalRef(cls);
    if (!out) return nullptr;
    for (size_t i = 0; i < values.size(); ++i) {
        jstring value = env->NewStringUTF(values[i].c_str());
        if (!value) return nullptr;
        env->SetObjectArrayElement(out, static_cast<jsize>(i), value);
        env->DeleteLocalRef(value);
        if (env->ExceptionCheck()) return nullptr;
    }
    return out;
}
}

extern "C" bool OpenNESIsAchievementSessionActive() {
    return activeId.load() != 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_startGameSession(
    JNIEnv* env, jobject, jstring rawName, jstring rawToken, jstring rawHash
) {
    auto name = copyUtf(env, rawName);
    auto token = copyUtf(env, rawToken);
    auto hash = copyUtf(env, rawHash);
    if (!validName(name) || !validToken(token) || !validHash(hash)) {
        std::fill(token.begin(), token.end(), '\0');
        return 0;
    }
    std::transform(hash.begin(), hash.end(), hash.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });

    std::lock_guard<std::mutex> life(lifecycleMutex);
    // Consistent lock order: first coreLock (detach), then clientMutex.
    libretrodroid::LibretroDroid::getInstance().setEmulatedFrameObserver(nullptr);
    uint64_t id = 0;
    {
        std::lock_guard<std::recursive_mutex> lock(clientMutex);
        closeClient();
        gameClient = rc_client_create(readMemory, serverCall);
        if (!gameClient) {
            std::fill(token.begin(), token.end(), '\0');
            return 0;
        }
        id = ++issuedId;
        activeId.store(id);
        coreGeneration = libretrodroid::LibretroDroid::getInstance().currentGameGeneration();
        expectedHash = hash;
        sessionState = 0;
        rc_client_set_allow_background_memory_reads(gameClient, 0);
        rc_client_set_hardcore_enabled(gameClient, 0);
        rc_client_set_event_handler(gameClient, onEvent);
        rc_client_begin_login_with_token(gameClient, name.c_str(), token.c_str(), onLogin, nullptr);
    }
    std::fill(token.begin(), token.end(), '\0');
    libretrodroid::LibretroDroid::getInstance().setEmulatedFrameObserver(
        [id] { onEmulatedFrame(id); }
    );
    return static_cast<jlong>(id);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_takeGameRequest(
    JNIEnv* env, jobject, jlong session
) {
    std::lock_guard<std::recursive_mutex> lock(clientMutex);
    if (session <= 0 || activeId.load() != static_cast<uint64_t>(session)) return nullptr;
    while (!requestOrder.empty()) {
        uint64_t id = requestOrder.front();
        requestOrder.pop_front();
        auto it = inflight.find(id);
        if (it == inflight.end()) continue;
        return stringArray(env, {
            std::to_string(id), it->second.url, it->second.form
        });
    }
    return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_completeGameRequest(
    JNIEnv* env, jobject, jlong session, jlong requestId, jint status, jbyteArray bodyBytes
) {
    std::lock_guard<std::recursive_mutex> lock(clientMutex);
    if (session <= 0 || requestId <= 0 ||
        activeId.load() != static_cast<uint64_t>(session) || !gameClient)
        return JNI_FALSE;
    auto it = inflight.find(static_cast<uint64_t>(requestId));
    if (it == inflight.end()) return JNI_FALSE;
    Request request = std::move(it->second);
    inflight.erase(it);
    std::vector<char> body;
    if (bodyBytes) {
        const jsize length = env->GetArrayLength(bodyBytes);
        if (length >= 0 && length <= kMaxResponse) {
            body.assign(static_cast<size_t>(length) + 1, 0);
            if (length > 0) {
                env->GetByteArrayRegion(
                    bodyBytes, 0, length, reinterpret_cast<jbyte*>(body.data())
                );
            }
        }
    }
    if (env->ExceptionCheck()) return JNI_FALSE;
    rc_api_server_response_t response{};
    response.http_status_code =
        body.empty() ? RC_API_SERVER_RESPONSE_CLIENT_ERROR : status;
    if (!body.empty()) {
        response.body = body.data();
        response.body_length = body.size() - 1;
    }
    request.callback(&response, request.userData);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_idleGameSession(
    JNIEnv*, jobject, jlong session
) {
    // Lock ordering matches the per-frame observer and client teardown:
    // emulator coreLock, then clientMutex. rc_client_idle is allowed to
    // inspect RAM, so serializing *only* on clientMutex would be unsafe.
    auto& emulator = libretrodroid::LibretroDroid::getInstance();
    emulator.withCoreLock([&](bool coreReady) {
        std::lock_guard<std::recursive_mutex> lock(clientMutex);
        if (session <= 0 || activeId.load() != static_cast<uint64_t>(session) ||
            !gameClient || disposing || sessionState < 0) return;
        if (!coreReady || emulator.currentGameGeneration() != coreGeneration) {
            sessionState = -2;
            return;
        }
        // A game response may arrive before the first core frame maps RAM.
        // Never evaluate paused-game conditions against unreadable zeroes.
        if (sessionState == 3) return;
        frameReadingSessionId = static_cast<uint64_t>(session);
        rc_client_idle(gameClient);
        frameReadingSessionId = 0;
    });
}

extern "C" JNIEXPORT jint JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_gameSessionStatus(
    JNIEnv*, jobject, jlong session
) {
    std::lock_guard<std::recursive_mutex> lock(clientMutex);
    return (session > 0 && activeId.load() == static_cast<uint64_t>(session))
        ? sessionState : -2;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_loadedGameId(
    JNIEnv*, jobject, jlong session
) {
    std::lock_guard<std::recursive_mutex> lock(clientMutex);
    if (session <= 0 || activeId.load() != static_cast<uint64_t>(session) ||
        sessionState != 2 || !gameClient) return 0;
    const rc_client_game_t* game = rc_client_get_game_info(gameClient);
    return game && game->id > 0 && game->id <= INT32_MAX ? static_cast<jint>(game->id) : 0;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_gameAchievementRows(
    JNIEnv* env, jobject, jlong session
) {
    std::lock_guard<std::recursive_mutex> lock(clientMutex);
    if (session <= 0 || activeId.load() != static_cast<uint64_t>(session) ||
        sessionState != 2 || !gameClient) return nullptr;
    rc_client_achievement_list_t* list =
        rc_client_create_achievement_list(
            gameClient,
            RC_CLIENT_ACHIEVEMENT_CATEGORY_CORE,
            RC_CLIENT_ACHIEVEMENT_LIST_GROUPING_LOCK_STATE
        );
    if (!list) return nullptr;
    std::vector<std::string> rows;
    for (uint32_t b = 0; b < list->num_buckets && rows.size() < 512; ++b) {
        const rc_client_achievement_bucket_t& bucket = list->buckets[b];
        for (uint32_t i = 0; i < bucket.num_achievements && rows.size() < 512; ++i) {
            const rc_client_achievement_t* item = bucket.achievements[i];
            if (!item || item->id == 0) continue;
            rows.push_back(
                std::to_string(item->id) + "\t" +
                std::to_string(item->points) + "\t" +
                std::to_string(item->unlocked ? 1 : 0) + "\t" +
                safeLabel(item->title, 220) + "\t" +
                safeLabel(item->description, 640)
            );
        }
    }
    rc_client_destroy_achievement_list(list);
    return stringArray(env, rows);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_takeGameEvent(
    JNIEnv* env, jobject, jlong session
) {
    std::lock_guard<std::recursive_mutex> lock(clientMutex);
    if (session <= 0 || activeId.load() != static_cast<uint64_t>(session) ||
        userEvents.empty()) return nullptr;
    std::string value = std::move(userEvents.front());
    userEvents.pop_front();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_stopGameSession(
    JNIEnv*, jobject, jlong session
) {
    std::lock_guard<std::mutex> life(lifecycleMutex);
    if (session <= 0 || activeId.load() != static_cast<uint64_t>(session)) return;
    libretrodroid::LibretroDroid::getInstance().setEmulatedFrameObserver(nullptr);
    std::lock_guard<std::recursive_mutex> lock(clientMutex);
    if (activeId.load() == static_cast<uint64_t>(session)) closeClient();
}
