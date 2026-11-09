// OpenNES v12.5.0 native linking sanity test; no account or game session.
#include <jni.h>
#include "rc_client.h"

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
