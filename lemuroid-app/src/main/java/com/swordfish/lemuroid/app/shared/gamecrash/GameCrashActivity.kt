package com.swordfish.lemuroid.app.shared.gamecrash

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.settings.StorageFrameworkPickerLauncher

class GameCrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash)

        val message = intent.getStringExtra(EXTRA_MESSAGE)
        val messageDetail = intent.getStringExtra(EXTRA_MESSAGE_DETAIL)

        findViewById<TextView>(R.id.text1).apply {
            isVisible = !message.isNullOrEmpty()
            text = message
        }

        findViewById<TextView>(R.id.text2).apply {
            isVisible = !messageDetail.isNullOrEmpty()
            text = messageDetail
        }
        findViewById<MaterialButton>(R.id.back_to_library).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.choose_rom_folder).apply {
            isVisible = intent.getBooleanExtra(EXTRA_SHOW_ROM_FOLDER_ACTION, false)
            setOnClickListener {
                StorageFrameworkPickerLauncher.pickFolder(this@GameCrashActivity)
                finish()
            }
        }
    }

    companion object {
        private const val EXTRA_MESSAGE = "EXTRA_MESSAGE"
        private const val EXTRA_MESSAGE_DETAIL = "EXTRA_MESSAGE_DETAIL"
        private const val EXTRA_SHOW_ROM_FOLDER_ACTION = "EXTRA_SHOW_ROM_FOLDER_ACTION"

        fun launch(
            activity: Activity,
            message: String,
            messageDetail: String?,
            showRomFolderAction: Boolean = false,
        ) {
            val intent =
                Intent(activity, GameCrashActivity::class.java).apply {
                    putExtra(EXTRA_MESSAGE, message)
                    putExtra(EXTRA_MESSAGE_DETAIL, messageDetail)
                    putExtra(EXTRA_SHOW_ROM_FOLDER_ACTION, showRomFolderAction)
                }
            activity.startActivity(intent)
        }
    }
}
