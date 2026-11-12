/*
 * GameSystem.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.swordfish.lemuroid.lib.library

import androidx.annotation.StringRes
import com.swordfish.lemuroid.lib.R
import com.swordfish.lemuroid.lib.core.CoreVariable
import java.util.Locale

data class GameSystem(
    val id: SystemID,
    val libretroFullName: String,
    @StringRes
    val titleResId: Int,
    @StringRes
    val shortTitleResId: Int,
    val systemCoreConfigs: List<SystemCoreConfig>,
    val uniqueExtensions: List<String>,
    val scanOptions: ScanOptions = ScanOptions(),
    val supportedExtensions: List<String> = uniqueExtensions,
    val hasMultiDiskSupport: Boolean = false,
    val fastForwardSupport: Boolean = true,
    val hasTouchScreen: Boolean = false,
) {
    companion object {
        // OpenNES supports NES/Famicom cartridge ROMs only.
        private val SYSTEMS =
            listOf(
                GameSystem(
                    SystemID.NES,
                    "Nintendo - Nintendo Entertainment System",
                    R.string.game_system_title_nes,
                    R.string.game_system_abbr_nes,
                    listOf(
                        SystemCoreConfig(
                            CoreID.FCEUMM,
                            // Use actual viewport touches for Zapper-compatible NES games.
                            // A user's explicit advanced choice can override this default.
                            defaultSettings = listOf(CoreVariable("fceumm_zapper_mode", "touchscreen")),
                            exposedSettings =
                                listOf(
                                    ExposedSetting(
                                        "fceumm_overscan_h",
                                        R.string.setting_fceumm_overscan_h,
                                    ),
                                    ExposedSetting(
                                        "fceumm_overscan_v",
                                        R.string.setting_fceumm_overscan_v,
                                    ),
                                ),
                            exposedAdvancedSettings =
                                listOf(
                                    ExposedSetting(
                                        "fceumm_nospritelimit",
                                        R.string.setting_fceumm_nospritelimit,
                                    ),
                                    ExposedSetting(
                                        "fceumm_zapper_mode",
                                        R.string.setting_fceumm_zapper_mode,
                                    ),
                                ),
                            controllerConfigs =
                                hashMapOf(
                                    0 to arrayListOf(ControllerConfigs.NES),
                                ),
                        ),
                    ),
                    uniqueExtensions = listOf("nes"),
                    // Permits GLRetroView to forward touch down/up in the
                    // actual game viewport. Controller overlay still owns
                    // virtual buttons; non-Zapper games ignore pointer input.
                    hasTouchScreen = true,
                ),
            )

        private val byIdCache by lazy { mapOf(*SYSTEMS.map { it.id.dbname to it }.toTypedArray()) }
        private val byExtensionCache by lazy {
            val mutableMap = mutableMapOf<String, GameSystem>()
            for (system in SYSTEMS) {
                for (extension in system.uniqueExtensions) {
                    mutableMap[extension.lowercase(Locale.US)] = system
                }
            }
            mutableMap.toMap()
        }

        fun findById(id: String): GameSystem = byIdCache.getValue(id)

        fun all() = SYSTEMS

        fun getSupportedExtensions(): List<String> {
            return SYSTEMS.flatMap { it.supportedExtensions }
        }

        fun findSystemForCore(coreID: CoreID): List<GameSystem> {
            return all().filter { system -> system.systemCoreConfigs.any { it.coreID == coreID } }
        }

        fun findByUniqueFileExtension(fileExtension: String): GameSystem? =
            byExtensionCache[fileExtension.lowercase(Locale.US)]

        data class ScanOptions(
            val scanByFilename: Boolean = true,
            val scanByUniqueExtension: Boolean = true,
            val scanByPathAndFilename: Boolean = false,
            val scanByPathAndSupportedExtensions: Boolean = true,
            val scanBySimilarSerial: Boolean = false,
        )
    }
}
