#!/usr/bin/env python3
"""Apply OpenNES's *verified, reproducible* LibretroDroid 0.14.0 native bridge.

The vendored upstream stays pinned to the exact v0.14.0 commit. Apply this
script after 'git submodule update --init --recursive'. The build uses source
from vendor/LibretroDroid; never package its old AAR simultaneously.
"""
from __future__ import annotations

from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM = ROOT / "vendor" / "LibretroDroid"
RA_UPSTREAM = ROOT / "vendor" / "rcheevos"
SRC = UPSTREAM / "libretrodroid" / "src" / "main"
EXPECTED_COMMIT = "8835c3098514390a271e36983957f7bb5f40abf1"
EXPECTED_RA_COMMIT = "1433173220a7eaede6a9ed7a18e94117be1821e0"


def replace_once(source: str, old: str, new: str, file_name: str) -> str:
    if new in source:
        return source
    if source.count(old) != 1:
        raise RuntimeError(f"Unexpected upstream v0.14.0 source for {file_name}")
    return source.replace(old, new)


def main() -> int:
    if not (UPSTREAM / ".git").exists():
        raise RuntimeError("LibretroDroid submodule missing: git submodule update --init --recursive")
    actual = subprocess.check_output(["git", "-C", str(UPSTREAM), "rev-parse", "HEAD"], text=True).strip()
    if actual != EXPECTED_COMMIT:
        raise RuntimeError(f"Refusing to patch unreviewed LibretroDroid revision {actual}")
    if not (RA_UPSTREAM / ".git").exists():
        raise RuntimeError("rcheevos submodule missing: git submodule update --init --recursive")
    ra_actual = subprocess.check_output(
        ["git", "-C", str(RA_UPSTREAM), "rev-parse", "HEAD"], text=True,
    ).strip()
    if ra_actual != EXPECTED_RA_COMMIT:
        raise RuntimeError(f"Refusing to link unreviewed rcheevos revision {ra_actual}")
    cpp = SRC / "cpp" / "libretrodroid.cpp"
    header = SRC / "cpp" / "libretrodroid.h"
    jni = SRC / "cpp" / "libretrodroidjni.cpp"
    java = SRC / "java" / "com" / "swordfish" / "libretrodroid" / "LibretroDroid.java"
    javautils = SRC / "cpp" / "utils" / "javautils.h"
    rumble = SRC / "cpp" / "rumble.h"
    cmake = SRC / "cpp" / "CMakeLists.txt"
    files = {path: path.read_text() for path in (cpp, header, jni, java, javautils, rumble, cmake)}

    # The pinned v0.14.0 library relied on transitive C++ includes, which
    # modern Android NDK Clang correctly rejects when building from source.
    files[javautils] = replace_once(
        files[javautils],
        '#include "unordered_map"',
        '#include <functional>\n#include "unordered_map"',
        "javautils.h include",
    )
    files[rumble] = replace_once(
        files[rumble],
        "#include <array>",
        "#include <array>\n#include <functional>",
        "rumble.h include",
    )

    # Include the official pinned MIT C runtime rather than a home-grown
    # achievement evaluator or HTTP-only pseudo-unlock mechanism.
    cmake_addition = """# OpenNES: official rcheevos v12.5.0 (MIT), pinned.
set(OPENNES_RA_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../../../vendor/rcheevos")
file(GLOB OPENNES_RA_RUNTIME_SOURCES
    "${OPENNES_RA_ROOT}/src/rapi/*.c"
    "${OPENNES_RA_ROOT}/src/rcheevos/*.c"
    "${OPENNES_RA_ROOT}/src/rhash/*.c"
)
target_sources(libretrodroid PRIVATE
    "${OPENNES_RA_ROOT}/src/rc_client.c"
    "${OPENNES_RA_ROOT}/src/rc_client_external.c"
    "${OPENNES_RA_ROOT}/src/rc_compat.c"
    "${OPENNES_RA_ROOT}/src/rc_libretro.c"
    "${OPENNES_RA_ROOT}/src/rc_util.c"
    "${OPENNES_RA_ROOT}/src/rc_version.c"
    ${OPENNES_RA_RUNTIME_SOURCES}
    "${CMAKE_CURRENT_LIST_DIR}/../../../../../../opennes-libretrodroid/src/main/cpp/ra_preflight.cpp"
)
target_include_directories(libretrodroid PRIVATE
    "${OPENNES_RA_ROOT}/include"
    "${OPENNES_RA_ROOT}/src"
    "${CMAKE_CURRENT_LIST_DIR}"
)
target_link_libraries(libretrodroid z)

# add lib dependencies
target_link_libraries(libretrodroid
"""
    files[cmake] = replace_once(
        files[cmake],
        "# add lib dependencies\ntarget_link_libraries(libretrodroid\n",
        cmake_addition,
        "CMakeLists.txt rc_client linking",
    )
    h = files[header]
    h = replace_once(
        h, "#include <vector>", "#include <vector>\n#include <functional>\n#include <cstdint>\n#include <atomic>",
        "libretrodroid.h includes",
    )
    h = replace_once(
        h, "    void step();", """    void step();

    // OpenNES bridge: lock-protected *copies*, never dangling core pointers.
    std::vector<uint8_t> copyCoreMemory(unsigned memoryType);
    // Only call from the frame observer, while coreLock is already held.
    // Never retain the pointer past the current emulated frame.
    std::pair<uint8_t*, size_t> coreMemoryRegionWhileLocked(unsigned memoryType);
    uint64_t emulatedFrameCount();
    uint64_t currentGameGeneration() const {
        return gameGeneration.load(std::memory_order_acquire);
    }
    // Future rc_client hook runs synchronously after *every* retro_run().
    void setEmulatedFrameObserver(std::function<void()> callback);""",
        "libretrodroid.h bridge",
    )
    h = replace_once(
        h, "    std::mutex coreLock;", """    std::mutex coreLock;
    bool achievementsGameLoaded = false;
    uint64_t frameCounter = 0;
    // Never reset this counter: a new/removed core invalidates RAM pointers.
    std::atomic<uint64_t> gameGeneration{0};
    std::function<void()> emulatedFrameObserver;""",
        "libretrodroid.h state",
    )
    files[header] = h

    c = files[cpp]
    c = replace_once(
        c, "void LibretroDroid::resetGlobalVariables() {",
        """void LibretroDroid::resetGlobalVariables() {
    achievementsGameLoaded = false;
    frameCounter = 0;
    gameGeneration.fetch_add(1, std::memory_order_release);
    emulatedFrameObserver = nullptr;""",
        "libretrodroid.cpp reset",
    )
    c = replace_once(
        c, "void LibretroDroid::destroy() {\n    std::lock_guard<std::mutex> lock(coreLock);",
        """void LibretroDroid::destroy() {
    std::lock_guard<std::mutex> lock(coreLock);
    achievementsGameLoaded = false;
    frameCounter = 0;
    gameGeneration.fetch_add(1, std::memory_order_release);
    emulatedFrameObserver = nullptr;""",
        "libretrodroid.cpp destroy",
    )
    c = replace_once(
        c, """    for (size_t i = 0; i < frames * frameSpeed; i++)
        core->retro_run();""",
        """    for (size_t i = 0; i < frames * frameSpeed; i++) {
        core->retro_run();
        ++frameCounter;
        // Called on the core/GL thread while coreLock is held.
        // The observer must not call copyCoreMemory() (would deadlock);
        // native rc_client will read the core directly here.
        if (emulatedFrameObserver) emulatedFrameObserver();
    }""",
        "libretrodroid.cpp per emulated frame",
    )
    c = replace_once(
        c, "void LibretroDroid::afterGameLoad() {",
        """void LibretroDroid::afterGameLoad() {
    gameGeneration.fetch_add(1, std::memory_order_release);
    achievementsGameLoaded = true;
    frameCounter = 0;""",
        "libretrodroid.cpp after load",
    )
    c = replace_once(
        c, "void LibretroDroid::step() {",
        """std::vector<uint8_t> LibretroDroid::copyCoreMemory(unsigned memoryType) {
    std::lock_guard<std::mutex> lock(coreLock);
    if (!achievementsGameLoaded || !core) return {};
    if (memoryType != RETRO_MEMORY_SAVE_RAM &&
        memoryType != RETRO_MEMORY_SYSTEM_RAM &&
        memoryType != RETRO_MEMORY_VIDEO_RAM) return {};
    size_t length = core->retro_get_memory_size(memoryType);
    if (length == 0 || length > 16 * 1024 * 1024) return {};
    auto* address = static_cast<const uint8_t*>(core->retro_get_memory_data(memoryType));
    if (!address) return {};
    return std::vector<uint8_t>(address, address + length);
}

std::pair<uint8_t*, size_t> LibretroDroid::coreMemoryRegionWhileLocked(unsigned memoryType) {
    if (!achievementsGameLoaded || !core) return {nullptr, 0};
    if (memoryType != RETRO_MEMORY_SAVE_RAM &&
        memoryType != RETRO_MEMORY_SYSTEM_RAM &&
        memoryType != RETRO_MEMORY_VIDEO_RAM) return {nullptr, 0};
    auto length = core->retro_get_memory_size(memoryType);
    if (length == 0 || length > 16 * 1024 * 1024) return {nullptr, 0};
    auto* pointer = static_cast<uint8_t*>(core->retro_get_memory_data(memoryType));
    if (!pointer) return {nullptr, 0};
    return {pointer, length};
}

uint64_t LibretroDroid::emulatedFrameCount() {
    std::lock_guard<std::mutex> lock(coreLock);
    return achievementsGameLoaded ? frameCounter : 0;
}

void LibretroDroid::setEmulatedFrameObserver(std::function<void()> callback) {
    std::lock_guard<std::mutex> lock(coreLock);
    emulatedFrameObserver = std::move(callback);
}

void LibretroDroid::step() {""",
        "libretrodroid.cpp bridge functions",
    )
    files[cpp] = c

    j = files[jni]
    j = replace_once(
        j, "JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_step(",
        """JNIEXPORT jbyteArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_copyCoreMemory(
    JNIEnv* env, jclass, jint memoryType
) {
    if (memoryType < 0 || memoryType > 3) return nullptr;
    auto bytes = LibretroDroid::getInstance().copyCoreMemory(static_cast<unsigned>(memoryType));
    if (bytes.empty()) return nullptr;
    auto array = env->NewByteArray(static_cast<jsize>(bytes.size()));
    if (!array) return nullptr;
    env->SetByteArrayRegion(array, 0, static_cast<jsize>(bytes.size()),
                            reinterpret_cast<const jbyte*>(bytes.data()));
    return array;
}

JNIEXPORT jlong JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_emulatedFrameCount(
    JNIEnv*, jclass
) {
    return static_cast<jlong>(LibretroDroid::getInstance().emulatedFrameCount());
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_step(""",
        "libretrodroidjni.cpp bridge",
    )
    files[jni] = j
    a = files[java]
    a = replace_once(
        a, "    public static native void step(GLRetroView retroView);",
        """    public static native void step(GLRetroView retroView);

    // OpenNES: safe byte-array copy, null when unsupported or game unavailable.
    // Allowed types: 0 Save RAM, 2 System RAM, 3 Video RAM.
    public static native byte[] copyCoreMemory(int memoryType);
    public static native long emulatedFrameCount();""",
        "LibretroDroid.java API",
    )
    files[java] = a
    for path, body in files.items():
        path.write_text(body)
        print(f"Native bridge ready: {path.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, subprocess.CalledProcessError) as e:
        print(f"OpenNES native preparation failed: {e}", file=sys.stderr)
        raise SystemExit(1)
