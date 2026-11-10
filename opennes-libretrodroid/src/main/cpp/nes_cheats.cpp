// OpenNES NES-only cheat bridge. No network, ROM mutation or hidden codes.
#include <jni.h>
#include <cctype>
#include <cstdint>
#include <string>
#include <vector>
#include "libretrodroid.h"

namespace {
bool isHex(unsigned char c) {
    return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F');
}

bool validCode(const std::string& code) {
    const size_t n = code.size();
    if (n == 6 || n == 8) {
        for (unsigned char c : code) {
            if (std::string("APZLGITYEOXUKSVN").find(c) == std::string::npos) return false;
        }
        return true;
    }
    if (n == 7 || n == 10) {
        for (size_t i = 0; i < 4; ++i) {
            if (!isHex(static_cast<unsigned char>(code[i]))) return false;
        }
        if (n == 7) {
            return code[4] == ':' && isHex(code[5]) && isHex(code[6]);
        }
        return code[4] == '?' && isHex(code[5]) && isHex(code[6]) &&
            code[7] == ':' && isHex(code[8]) && isHex(code[9]);
    }
    return false;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_swordfish_lemuroid_app_shared_cheats_NesCheatNative_currentGeneration(
    JNIEnv*, jobject
) {
    return static_cast<jlong>(
        libretrodroid::LibretroDroid::getInstance().currentGameGeneration()
    );
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_swordfish_lemuroid_app_shared_cheats_NesCheatNative_replaceCheats(
    JNIEnv* env, jobject, jlong generation, jobjectArray codes
) {
    if (generation <= 0 || !codes) return JNI_FALSE;
    const jsize count = env->GetArrayLength(codes);
    if (count < 0 || count > 64) return JNI_FALSE;
    std::vector<std::string> validated;
    validated.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        auto item = static_cast<jstring>(env->GetObjectArrayElement(codes, i));
        if (!item) return JNI_FALSE;
        const char* chars = env->GetStringUTFChars(item, nullptr);
        if (!chars) {
            env->DeleteLocalRef(item);
            return JNI_FALSE;
        }
        std::string code(chars);
        env->ReleaseStringUTFChars(item, chars);
        env->DeleteLocalRef(item);
        if (!validCode(code)) return JNI_FALSE;
        validated.push_back(std::move(code));
    }
    return libretrodroid::LibretroDroid::getInstance().replaceCoreCheats(
        validated, static_cast<uint64_t>(generation)
    ) ? JNI_TRUE : JNI_FALSE;
}
