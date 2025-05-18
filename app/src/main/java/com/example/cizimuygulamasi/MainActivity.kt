package com.example.cizimuygulamasi // TODO: KENDİ PAKET ADINIZI YAZIN

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.cizimuygulamasi.databinding.ActivityMainBinding
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

const val SERVER_BASE_URL = "http://192.168.1.8:5000" // TODO: Kendi Flask IP adresinizi doğrulayın

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(DrawingActionElement::class.java, DrawingActionElementAdapter())
        .create()
    private val client = OkHttpClient()
    private var lastEraserClickTime: Long = 0
    private val DOUBLE_CLICK_TIME_DELTA: Long = 300

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupControlListeners()

        binding.buttonLoad.setOnClickListener {
            val userId = binding.editTextUserId.text.toString().trim()
            if (userId.isNotEmpty()) fetchDrawing(userId)
            else Toast.makeText(this, "Lütfen bir User ID girin.", Toast.LENGTH_SHORT).show()
        }
        binding.buttonSave.setOnClickListener {
            val userId = binding.editTextUserId.text.toString().trim()
            if (userId.isNotEmpty()) {
                val drawingDataContainer = binding.drawingView.getDrawingDataForUpload()
                if (drawingDataContainer.elements.isEmpty()) {
                    Toast.makeText(this, "Kaydedilecek bir çizim yok.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val jsonData = gson.toJson(drawingDataContainer)
                sendDrawing(userId, jsonData)
            } else Toast.makeText(this, "Lütfen bir User ID girin.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupControlListeners() {
        binding.buttonColorBlack.setOnClickListener { view -> binding.drawingView.setCurrentColor(Color.BLACK); binding.drawingView.setPenMode(); updateButtonHighlights(view) }
        binding.buttonColorRed.setOnClickListener { view -> binding.drawingView.setCurrentColor(Color.RED); binding.drawingView.setPenMode(); updateButtonHighlights(view) }
        binding.buttonColorBlue.setOnClickListener { view -> binding.drawingView.setCurrentColor(Color.BLUE); binding.drawingView.setPenMode(); updateButtonHighlights(view) }
        binding.buttonColorGreen.setOnClickListener { view -> binding.drawingView.setCurrentColor(Color.GREEN); binding.drawingView.setPenMode(); updateButtonHighlights(view) }

        binding.buttonEraser.setOnClickListener { view ->
            val clickTime = System.currentTimeMillis()
            if (clickTime - lastEraserClickTime < DOUBLE_CLICK_TIME_DELTA) {
                AlertDialog.Builder(this).setTitle("Tüm Çizimi Sil").setMessage("Sayfadaki tüm çizimi silmek istediğinizden emin misiniz?")
                    .setPositiveButton("Evet, Sil") { _, _ -> binding.drawingView.clearCanvas(); Toast.makeText(this, "Tüm çizim silindi", Toast.LENGTH_SHORT).show() }
                    .setNegativeButton("Hayır", null).setIcon(android.R.drawable.ic_dialog_alert).show()
                lastEraserClickTime = 0
            } else { binding.drawingView.setEraserMode(); updateButtonHighlights(view) }
            lastEraserClickTime = clickTime
        }

        binding.buttonAddText.setOnClickListener { view -> showAddTextDialog(); updateButtonHighlights(view) }

        binding.buttonExportPdf.setOnClickListener { view ->
            exportDrawingToPdf()
            updateButtonHighlights(view)
        }

        binding.seekBarStrokeWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = if (progress > 0) progress.toFloat() else 1f
                binding.drawingView.setStrokeWidthValue(value)
                binding.drawingView.setTextSizeValue(value * 3 + 30)
                binding.textViewStrokeValue.text = value.toInt().toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val initialProgress = 15
        binding.seekBarStrokeWidth.progress = initialProgress
        binding.textViewStrokeValue.text = initialProgress.toString()
        binding.drawingView.setStrokeWidthValue(initialProgress.toFloat())
        binding.drawingView.setTextSizeValue(initialProgress.toFloat() * 3 + 30)

        binding.drawingView.setCurrentColor(Color.BLACK); binding.drawingView.setPenMode(); updateButtonHighlights(binding.buttonColorBlack)
    }

    private fun showAddTextDialog() {
        val editText = EditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT; hint = "Metni buraya girin"; setPadding(30,30,30,30) }
        AlertDialog.Builder(this).setTitle("Metin Ekle").setView(editText)
            .setPositiveButton("Ekle") { _, _ ->
                val text = editText.text.toString().trim()
                if (text.isNotEmpty()) binding.drawingView.prepareForTextPlacement(text)
                else Toast.makeText(this, "Lütfen bir metin girin.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("İptal", null).show()
    }

    private fun updateButtonHighlights(selectedButton: View?) {
        val buttons = listOf(binding.buttonColorBlack, binding.buttonColorRed, binding.buttonColorBlue,
            binding.buttonColorGreen, binding.buttonEraser, binding.buttonAddText, binding.buttonExportPdf)
        buttons.forEach { it.alpha = 0.6f }
        selectedButton?.alpha = 1.0f
        if (selectedButton == binding.buttonExportPdf || selectedButton == binding.buttonAddText) { // AddText için de geçici vurgu
            selectedButton?.postDelayed({ selectedButton.alpha = 0.6f }, 300) // Vurguyu kısa süre sonra kaldır
        }
    }

    private fun exportDrawingToPdf() {
        val bitmap = binding.drawingView.getBitmap()
        if (bitmap.width == 0 || bitmap.height == 0) {
            Toast.makeText(this, "Çizim alanı boş, PDF oluşturulamadı.", Toast.LENGTH_SHORT).show()
            return
        }

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
        pdfDocument.finishPage(page)

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val pdfFileName = "CizimTahtasi_$timeStamp.pdf"
        val pdfDir = File(externalCacheDir, "pdf_exports")
        if (!pdfDir.exists()) {
            pdfDir.mkdirs()
        }
        val pdfFile = File(pdfDir, pdfFileName)

        try {
            FileOutputStream(pdfFile).use { fos ->
                pdfDocument.writeTo(fos)
                Toast.makeText(this, "PDF oluşturuldu: ${pdfFile.name}", Toast.LENGTH_LONG).show()
                Log.d("PDFExport", "PDF oluşturuldu: ${pdfFile.absolutePath}")
                sharePdf(pdfFile) // PDF oluşturulduktan sonra paylaş
            }
        } catch (e: IOException) {
            Log.e("PDFExport", "PDF kaydetme hatası", e)
            Toast.makeText(this, "PDF kaydedilemedi: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            pdfDocument.close()
        }
    }

    private fun sharePdf(pdfFile: File) {
        val fileUri: Uri? = try {
            FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", pdfFile)
        } catch (e: IllegalArgumentException) {
            Log.e("FileProvider", "FileProvider URI hatası: ${e.message}")
            Toast.makeText(this, "Dosya paylaşılamadı (URI hatası).", Toast.LENGTH_LONG).show()
            null
        }

        if (fileUri != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(Intent.createChooser(shareIntent, "PDF'i Paylaş..."))
            } catch (e: android.content.ActivityNotFoundException) {
                Toast.makeText(this, "PDF paylaşmak için uygun bir uygulama bulunamadı.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun fetchDrawing(userId: String) {
        Toast.makeText(this, "$userId için çizim yükleniyor...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder().url("$SERVER_BASE_URL/drawing/$userId").get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorMsg = "Sunucudan çizim alınamadı. Kod: ${response.code}, Mesaj: ${response.message}"
                        Log.e("MainActivity_Fetch", errorMsg)
                        withContext(Dispatchers.Main) { Toast.makeText(applicationContext, "Çizim yüklenemedi: ${response.code}", Toast.LENGTH_LONG).show(); binding.drawingView.clearCanvas() }
                        return@launch
                    }
                    val responseBody = response.body?.string()
                    withContext(Dispatchers.Main) {
                        if (!responseBody.isNullOrEmpty()) {
                            Log.d("MainActivity_Fetch", "Sunucudan gelen ($userId): $responseBody")
                            binding.drawingView.loadDrawingFromJson(responseBody)
                            Toast.makeText(applicationContext, "$userId için çizim yüklendi.", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.d("MainActivity_Fetch", "$userId için kayıtlı çizim bulunamadı.")
                            binding.drawingView.clearCanvas()
                            Toast.makeText(applicationContext, "$userId için kayıtlı çizim bulunamadı.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity_Fetch", "Yükleme hatası", e)
                withContext(Dispatchers.Main) { binding.drawingView.clearCanvas(); Toast.makeText(applicationContext, "Ağ/Veri Hatası: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun sendDrawing(userId: String, jsonData: String) {
        Toast.makeText(this, "$userId için kaydediliyor...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = jsonData.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val request = Request.Builder().url("$SERVER_BASE_URL/drawing/$userId").post(body).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorMsg = "Sunucuya gönderilemedi. Kod: ${response.code}, Mesaj: ${response.message}"
                        Log.e("MainActivity_Send", errorMsg)
                        withContext(Dispatchers.Main) { Toast.makeText(applicationContext, "Kaydedilemedi: ${response.code}", Toast.LENGTH_LONG).show() }
                        return@launch
                    }
                    Log.d("MainActivity_Send", "Kaydetme yanıtı ($userId): ${response.body?.string()}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "$userId için kaydedildi!", Toast.LENGTH_SHORT).show()
                        binding.drawingView.clearCanvas(); binding.editTextUserId.text.clear()
                        binding.drawingView.setPenMode(); updateButtonHighlights(binding.buttonColorBlack)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity_Send", "Gönderme hatası", e)
                withContext(Dispatchers.Main) { Toast.makeText(applicationContext, "Ağ Hatası: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }
}