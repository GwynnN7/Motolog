package com.gwynn7.motolog.photos

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.canhub.cropper.CropImageView
import com.google.android.material.appbar.MaterialToolbar
import com.gwynn7.motolog.LocaleHelper
import com.gwynn7.motolog.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Private crop screen: no camera intents, external storage, or exported editing endpoint. */
class PhotoCropActivity : AppCompatActivity() {
    private lateinit var cropView: CropImageView
    private lateinit var output: Uri
    private var cropping = false
    private var work: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.photo_crop)
        val root = findViewById<android.view.View>(R.id.photo_crop_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.photo_crop_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.photo_crop)
        cropView = findViewById(R.id.photo_crop_view)
        val source = intent.data
        val target = intent.getStringExtra(OUTPUT)?.let(Uri::parse)
        if (source?.scheme != "content" || target == null || PhotoStore(this).resolve(target, true) == null) {
            fail(); return
        }
        output = target
        cropView.setOnSetImageUriCompleteListener { _, _, error -> if (error != null) fail() }
        cropView.setOnCropImageCompleteListener { _, result ->
            if (isFinishing || isDestroyed) return@setOnCropImageCompleteListener
            if (result.isSuccessful && result.uriContent != null) {
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) { PhotoStore(this@PhotoCropActivity).promoteDraft(requireNotNull(work), output) }
                        setResult(RESULT_OK); finish()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { fail() }
                }
            } else fail()
        }
        // CropImageView restores the image URI, crop rectangle and rotation itself.
        if (savedInstanceState == null || !savedInstanceState.getBoolean("hadImage")) cropView.setImageUriAsync(source)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("hadImage", cropView.wholeImageRect != null)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.photo_crop, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        for (i in 0 until menu.size()) menu.getItem(i).isEnabled = !cropping
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.photo_rotate -> { if (!cropping) cropView.rotateImage(90); true }
        R.id.photo_done -> {
            if (!cropping && cropView.wholeImageRect != null) {
                cropping = true
                invalidateOptionsMenu()
                try {
                    // Each Activity instance writes a different temporary file. A crop finishing
                    // after rotation cannot overwrite the result of the replacement screen.
                    work = PhotoStore(this).newDraft()
                    cropView.croppedImageAsync(Bitmap.CompressFormat.JPEG, 90, 1200, 1200,
                        CropImageView.RequestSizeOptions.RESIZE_INSIDE, work)
                }
                catch (_: Exception) { fail() }
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun fail() { setResult(RESULT_FIRST_USER); finish() }

    override fun onDestroy() {
        if (::cropView.isInitialized) {
            cropView.setOnSetImageUriCompleteListener(null)
            cropView.setOnCropImageCompleteListener(null)
        }
        PhotoStore(this).deleteDraft(work)
        super.onDestroy()
    }

    override fun attachBaseContext(base: Context) { super.attachBaseContext(LocaleHelper.onAttach(base)) }

    companion object { const val OUTPUT = "photoOutput" }
}
