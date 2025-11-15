package com.max.filescaner


import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Bundle
import android.os.Environment
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.appcompat.app.AlertDialog
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout


class ImageProcessingActivity : AppCompatActivity() {
    private lateinit var progressBar: ProgressBar
    private lateinit var originalImageView: ImageView
    private lateinit var scannedImageView: ImageView // 处理后图片预览
    private lateinit var buttonContainer: LinearLayout  // 新增：按钮容器
    private lateinit var btnSave: Button               // 新增：保留按钮
    private lateinit var btnCancel: Button             // 新增：取消按钮
    private var imagePath: String? = null
    private var isFromCamera: Boolean = false
    private var scannedBitmap: Bitmap? = null // 暂存处理后图片，用于用户确认

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_processing)

        // 初始化视图
        progressBar = findViewById(R.id.progressBar)
        originalImageView = findViewById(R.id.originalImageView)
        scannedImageView = findViewById(R.id.scannedImageView)

        buttonContainer = findViewById(R.id.buttonContainer)  // 新增
        btnSave = findViewById(R.id.btnSave)                  // 新增
        btnCancel = findViewById(R.id.btnCancel)
        // 新增
        btnSave.setOnClickListener { saveAndProceed() }
        btnCancel.setOnClickListener { cancelAndFinish() }
        // 获取传入的图片路径和来源标记
        imagePath = intent.getStringExtra("IMAGE_PATH")
        isFromCamera = intent.getBooleanExtra("IS_FROM_CAMERA", false)

        if (imagePath.isNullOrEmpty()) {
            Toast.makeText(this, "无效的图片路径", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 显示原始图片并开始处理
        val originalBitmap = BitmapFactory.decodeFile(imagePath)
        originalImageView.setImageBitmap(originalBitmap)
        processImage(originalBitmap)
    }

    // 修改图片处理逻辑：处理后显示预览，等待用户确认
    private fun processImage(originalBitmap: Bitmap) {
        progressBar.visibility = ProgressBar.VISIBLE

        try {
            // 应用文档扫描处理
            scannedBitmap = convertToDocumentStyle(originalBitmap)

            // 显示处理前后对比
            originalImageView.setImageBitmap(originalBitmap)
            scannedImageView.setImageBitmap(scannedBitmap) // 显示处理后图片

            // 处理完成：隐藏进度条，显示按钮容器（修改）
            progressBar.visibility = ProgressBar.GONE
            buttonContainer.visibility = View.VISIBLE  // 显示按钮

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "处理图片时出错: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
        // 注意：不再立即回收bitmap，等待用户确认
    }
    // 显示保存确认对话框
    private fun showSaveConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("保存扫描结果")
            .setMessage("是否保留这张扫描后的图片？")
            .setPositiveButton("保留") { _, _ ->
                saveAndProceed() // 用户确认保留
            }
            .setNegativeButton("取消") { _, _ ->
                cancelAndFinish() // 用户取消
            }
            .setCancelable(false) // 强制用户选择
            .show()
    }
    // 新增：保存图片并继续流程
    private fun saveAndProceed() {
        val bitmap = scannedBitmap ?: return
        val originalBitmap = originalImageView.drawable?.let { it as BitmapDrawable }?.bitmap

        try {
            val savedPath = saveScannedImage(bitmap)
            if (savedPath != null) {
                Toast.makeText(this, "文档已保存", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, HistoryActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
            }
        } finally {
            // 释放所有bitmap
            originalBitmap?.recycle()
            bitmap.recycle()
            finish()
        }
    }
    // 新增：取消保存并返回
    private fun cancelAndFinish() {
        val originalBitmap = originalImageView.drawable?.let { it as BitmapDrawable }?.bitmap
        originalBitmap?.recycle()
        scannedBitmap?.recycle()
        finish() // 返回上一页
    }

    // 保存扫描后的图片
    private fun saveScannedImage(bitmap: Bitmap): String? {
        return try {
            val historyDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "FileScanner")
            if (!historyDir.exists() && !historyDir.mkdirs()) {
                Toast.makeText(this, "无法创建存储目录", Toast.LENGTH_SHORT).show()
                return null
            }

            val fileName = "scanned_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
            val outputFile = File(historyDir, fileName)

            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 文档扫描核心处理方法（从MainActivity迁移）
    private fun convertToDocumentStyle(originalBitmap: Bitmap): Bitmap {
        val grayBitmap = toGrayscale(originalBitmap)
        val enhancedBitmap = enhanceContrast(grayBitmap)
        val binaryBitmap = binarize(enhancedBitmap)

        grayBitmap.recycle()
        enhancedBitmap.recycle()
        return binaryBitmap
    }

    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        return applyColorMatrix(bitmap, colorMatrix)
    }

    private fun enhanceContrast(bitmap: Bitmap): Bitmap {
        val contrast = 2.0f
        val brightness = 30f
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        return applyColorMatrix(bitmap, colorMatrix)
    }

    private fun binarize(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val threshold = 150
        for (i in pixels.indices) {
            val gray = Color.red(pixels[i])
            pixels[i] = if (gray > threshold) Color.WHITE else Color.BLACK
        }

        val binaryBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        binaryBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return binaryBitmap
    }

    private fun applyColorMatrix(bitmap: Bitmap, colorMatrix: ColorMatrix): Bitmap {
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(resultBitmap).drawBitmap(bitmap, 0f, 0f, paint)
        return resultBitmap
    }
}