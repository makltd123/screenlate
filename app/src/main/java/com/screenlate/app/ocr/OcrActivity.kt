package com.screenlate.app.ocr

import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.screenlate.app.services

/**
 * Invisible trampoline for the one-shot OCR capture: asks for the screen-capture permission
 * and hands the grant to [OcrCaptureService]. Android 14 re-asks on every capture, so no
 * attempt is made to keep a permission for the whole session.
 */
class OcrActivity : ComponentActivity() {
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            OcrCaptureService.start(this, result.resultCode, data)
        } else {
            OcrBus.post(OcrEvent.Cancelled)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (services.modelManager.readyDir() == null) {
            OcrBus.post(OcrEvent.NoModel)
            finish()
            return
        }
        consent.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }
}
