// Isolated, authenticated rc_client token-verification transport.
// This does NOT load a game, observe RAM, or enable achievement awards.
#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <cstring>
#include <deque>
#include <map>
#include <mutex>
#include <string>
#include <utility>
#include <vector>
#include "rc_client.h"

namespace {
constexpr size_t kMaxRequests = 8;
constexpr size_t kMaxUrlBytes = 2048;
constexpr size_t kMaxFormBytes = 64 * 1024;
constexpr jsize kMaxReplyBytes = 2 * 1024 * 1024;

struct PendingRequest {
    std::string url;
    std::string form;
    rc_client_server_callback_t callback;
    void* callback_data;
};

std::recursive_mutex stateMutex;
rc_client_t* validationClient = nullptr;
uint64_t lastSession = 0;
uint64_t activeSession = 0;
uint64_t lastRequest = 0;
int validationState = -2; // 0 pending, 1 accepted, -1 rejected, -2 closed
bool closing = false;
std::map<uint64_t, PendingRequest> inFlight;
std::deque<uint64_t> pendingIds;

void rejectRequest(rc_client_server_callback_t callback, void* data) {
    rc_api_server_response_t response{};
    response.http_status_code = RC_API_SERVER_RESPONSE_CLIENT_ERROR;
    callback(&response, data);
}

void RC_CCONV serverCall(
    const rc_api_request_t* request,
    rc_client_server_callback_t callback,
    void* data,
    rc_client_t* client
) {
    std::lock_guard<std::recursive_mutex> guard(stateMutex);
    if (!request || !callback || closing || !activeSession ||
        client != validationClient || inFlight.size() >= kMaxRequests ||
        !request->url) {
        if (callback) rejectRequest(callback, data);
        return;
    }
    const size_t urlSize = strnlen(request->url, kMaxUrlBytes + 1);
    if (urlSize == 0 || urlSize > kMaxUrlBytes) {
        rejectRequest(callback, data);
        return;
    }
    const char* post = request->post_data;
    if (!post) {
        // Token validation must use the official POST API; reject GET.
        rejectRequest(callback, data);
        return;
    }
    const size_t formSize = strnlen(post, kMaxFormBytes + 1);
    if (formSize == 0 || formSize > kMaxFormBytes) {
        rejectRequest(callback, data);
        return;
    }
    const uint64_t id = ++lastRequest;
    inFlight.emplace(id, PendingRequest{
        std::string(request->url, urlSize),
        std::string(post, formSize),
        callback,
        data
    });
    pendingIds.push_back(id);
}

void RC_CCONV loginFinished(
    int result, const char*, rc_client_t* client, void*
) {
    std::lock_guard<std::recursive_mutex> guard(stateMutex);
    if (!closing && validationClient == client && activeSession) {
        validationState =
            (result == RC_OK && rc_client_get_user_info(client)) ? 1 : -1;
    }
}

void closeLocked() {
    closing = true;
    activeSession = 0;
    validationState = -2;
    pendingIds.clear();

    // rc_client_destroy marks unfinished requests destroyed but cannot
    // release caller-owned asynchronous callback data. Deliver a bounded
    // explicit error while the client is still alive to free those handles.
    while (!inFlight.empty()) {
        auto it = inFlight.begin();
        PendingRequest request = std::move(it->second);
        inFlight.erase(it);
        rejectRequest(request.callback, request.callback_data);
    }
    if (validationClient) {
        rc_client_logout(validationClient);
        rc_client_destroy(validationClient);
        validationClient = nullptr;
    }
    closing = false;
}

std::string readUtf8(JNIEnv* env, jstring input) {
    if (!input) return {};
    const char* value = env->GetStringUTFChars(input, nullptr);
    if (!value) return {};
    std::string copy(value);
    env->ReleaseStringUTFChars(input, value);
    return copy;
}

bool validUsername(const std::string& username) {
    if (username.size() < 2 || username.size() > 64) return false;
    for (char ch : username) {
        if (!((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') ||
            (ch >= '0' && ch <= '9') || ch == '_' || ch == '-')) return false;
    }
    return true;
}

bool validToken(const std::string& token) {
    if (token.size() < 16 || token.size() > 2048) return false;
    for (unsigned char ch : token) {
        if (ch <= 32 || ch == 127) return false;
    }
    return true;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_beginTokenValidation(
    JNIEnv* env, jobject, jstring rawUsername, jstring rawToken
) {
    auto username = readUtf8(env, rawUsername);
    auto token = readUtf8(env, rawToken);
    const bool valid = validUsername(username) && validToken(token);
    if (!valid) {
        std::fill(token.begin(), token.end(), '\0');
        return 0;
    }

    std::lock_guard<std::recursive_mutex> guard(stateMutex);
    closeLocked();
    auto* client = rc_client_create(nullptr, serverCall);
    if (!client) {
        std::fill(token.begin(), token.end(), '\0');
        return 0;
    }
    validationClient = client;
    activeSession = ++lastSession;
    validationState = 0;
    rc_client_set_allow_background_memory_reads(client, 0);
    // rc_client constructs a real login2 request and calls serverCall().
    rc_client_begin_login_with_token(
        client, username.c_str(), token.c_str(), loginFinished, nullptr
    );
    std::fill(token.begin(), token.end(), '\0');
    return static_cast<jlong>(activeSession);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_takeValidationRequest(
    JNIEnv* env, jobject, jlong session
) {
    std::lock_guard<std::recursive_mutex> guard(stateMutex);
    if (session <= 0 || static_cast<uint64_t>(session) != activeSession) return nullptr;
    while (!pendingIds.empty()) {
        const uint64_t id = pendingIds.front();
        pendingIds.pop_front();
        const auto it = inFlight.find(id);
        if (it == inFlight.end()) continue;
        jclass stringClass = env->FindClass("java/lang/String");
        if (!stringClass) return nullptr;
        jobjectArray parts = env->NewObjectArray(3, stringClass, nullptr);
        env->DeleteLocalRef(stringClass);
        if (!parts) return nullptr;
        const std::string requestId = std::to_string(id);
        const char* values[] = {
            requestId.c_str(), it->second.url.c_str(), it->second.form.c_str()
        };
        for (int index = 0; index < 3; ++index) {
            jstring item = env->NewStringUTF(values[index]);
            if (!item) return nullptr;
            env->SetObjectArrayElement(parts, index, item);
            env->DeleteLocalRef(item);
            if (env->ExceptionCheck()) return nullptr;
        }
        return parts;
    }
    return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_completeValidationRequest(
    JNIEnv* env, jobject, jlong session, jlong requestId, jint status, jbyteArray rawBody
) {
    std::lock_guard<std::recursive_mutex> guard(stateMutex);
    if (session <= 0 || requestId <= 0 || !validationClient ||
        static_cast<uint64_t>(session) != activeSession) return JNI_FALSE;

    auto it = inFlight.find(static_cast<uint64_t>(requestId));
    if (it == inFlight.end()) return JNI_FALSE;
    PendingRequest request = std::move(it->second);
    inFlight.erase(it); // a late or duplicated response cannot be delivered twice

    std::vector<char> body;
    if (rawBody) {
        const jsize size = env->GetArrayLength(rawBody);
        if (size >= 0 && size <= kMaxReplyBytes) {
            body.resize(static_cast<size_t>(size) + 1, 0);
            if (size > 0) {
                env->GetByteArrayRegion(
                    rawBody, 0, size, reinterpret_cast<jbyte*>(body.data())
                );
            }
        }
    }
    if (env->ExceptionCheck()) return JNI_FALSE;

    rc_api_server_response_t reply{};
    if (!body.empty()) {
        reply.body = body.data();
        reply.body_length = body.size() - 1;
    }
    reply.http_status_code =
        body.empty() ? RC_API_SERVER_RESPONSE_CLIENT_ERROR : status;
    request.callback(&reply, request.callback_data);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_tokenValidationState(
    JNIEnv*, jobject, jlong session
) {
    std::lock_guard<std::recursive_mutex> guard(stateMutex);
    if (session <= 0 || static_cast<uint64_t>(session) != activeSession) return -2;
    return validationState;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_validatedTokenUsername(
    JNIEnv* env, jobject, jlong session
) {
    std::lock_guard<std::recursive_mutex> guard(stateMutex);
    if (session <= 0 || static_cast<uint64_t>(session) != activeSession ||
        validationState != 1 || !validationClient) return nullptr;
    const rc_client_user_t* user = rc_client_get_user_info(validationClient);
    if (!user || !user->username) return nullptr;
    return env->NewStringUTF(user->username);
}

extern "C" JNIEXPORT void JNICALL
Java_com_swordfish_lemuroid_app_shared_achievements_RetroAchievementsNative_stopTokenValidation(
    JNIEnv*, jobject, jlong session
) {
    std::lock_guard<std::recursive_mutex> guard(stateMutex);
    if (session > 0 && static_cast<uint64_t>(session) == activeSession) closeLocked();
}
