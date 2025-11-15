package com.max.filescaner

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class ImageDetailActivity : AppCompatActivity() {
    private lateinit var ivFullImage: ImageView
    private var currentBitmap: Bitmap? = null // 当前显示的图片
    private var imagePath: String? = null // 图片文件路径（从历史记录传递）

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_detail)

        // 初始化视图
        ivFullImage = findViewById(R.id.ivFullImage)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnRotateLeft = findViewById<Button>(R.id.btnRotateLeft)
        val btnRotateRight = findViewById<Button>(R.id.btnRotateRight)
        val btnProcess = findViewById<Button>(R.id.btnProcess)

        // 获取从历史记录页面传递的图片路径
        imagePath = intent.getStringExtra("IMAGE_PATH")
        if (imagePath.isNullOrEmpty()) {
            Toast.makeText(this, "图片路径无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 加载原图并显示（修正方向）
        loadImageAndCorrectOrientation()

        // 返回按钮：关闭当前Activity
        btnBack.setOnClickListener {
            // 创建跳转到历史记录页面的意图
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
            finish() // 关闭当前详情页，避免返回栈中残留
        }

        // 向左旋转（-90度）
        btnRotateLeft.setOnClickListener { rotateImage(-90) }

        // 向右旋转（90度）
        btnRotateRight.setOnClickListener { rotateImage(90) }

        // 处理图片（调用ImageProcessingActivity）
        btnProcess.setOnClickListener {
            if (imagePath.isNullOrEmpty()) {
                Toast.makeText(this, "图片路径无效", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 跳转到图片处理页面并传递当前图片路径
            val intent = Intent(this, ImageProcessingActivity::class.java)
            intent.putExtra("IMAGE_PATH", imagePath) // 传递当前图片路径
            intent.putExtra("IS_FROM_DETAIL", true) // 可选：标记来源为详情页
            startActivity(intent)
        }
    }

    // 加载图片并修正方向（复用历史记录页面逻辑）
    private fun loadImageAndCorrectOrientation() {
        val file = File(imagePath!!)
        currentBitmap = getBitmapFromFile(file)?.let { rotateIfRequired(it, file) }
        ivFullImage.setImageBitmap(currentBitmap)
    }

    // 旋转图片
    private fun rotateImage(degree: Int) {
        currentBitmap?.let { bitmap ->
            val rotatedBitmap = rotateBitmap(bitmap, degree)
            currentBitmap = rotatedBitmap // 更新当前图片
            ivFullImage.setImageBitmap(rotatedBitmap) // 刷新显示
            saveEditedImage(rotatedBitmap) // 保存编辑后的图片（覆盖原图）
        } ?: Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show()
    }


    // 保存编辑后的图片（覆盖原图）
    private fun saveEditedImage(bitmap: Bitmap) {
        runCatching {
            FileOutputStream(imagePath).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) // 90%质量压缩
            }
            Toast.makeText(this, "编辑已保存", Toast.LENGTH_SHORT).show()
        }.onFailure { e->
            e.printStackTrace()
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    // 从文件加载Bitmap（复用历史记录页面逻辑）
    private fun getBitmapFromFile(file: File): Bitmap? {
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 根据EXIF信息修正方向（复用历史记录页面逻辑）
    private fun rotateIfRequired(bitmap: Bitmap, file: File): Bitmap {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270)
                else -> bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }

    // 旋转Bitmap（复用历史记录页面逻辑）
    private fun rotateBitmap(bitmap: Bitmap, degree: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degree.toFloat()) }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
        bitmap.recycle() // 释放原始Bitmap内存
        return rotatedBitmap
    }
}