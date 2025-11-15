package com.max.filescaner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale




class MainActivity : AppCompatActivity() {
    private var fromAlbum = 2
    lateinit var imageUri: Uri
    lateinit var outputImage: File

    // 注册相机ActivityResult回调
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {  // 相机返回成功
            try {
                // 启动图像处理Activity
                val intent = Intent(this, ImageProcessingActivity::class.java)
                intent.putExtra("IMAGE_PATH", outputImage.absolutePath)
                intent.putExtra("IS_FROM_CAMERA", true)
                startActivity(intent)

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "保存照片失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 注册权限请求回调
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraPermissionGranted = permissions[Manifest.permission.CAMERA] ?: false
        val storagePermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true
        } else {
            permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
        }

        if (cameraPermissionGranted && storagePermissionGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "需要相机和存储权限才能拍照", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val takePhotoBtn = findViewById<Button>(R.id.takePhotoBtn)
        val fromAlbumBtn = findViewById<Button>(R.id.fromAlbumBtn)
        val historyBtn = findViewById<Button>(R.id.historyBtn)

        takePhotoBtn.setOnClickListener{
            // 修复：调用权限请求方法，通过后自动打开相机
            requestPermissions()
        }

        // 从相册选择照片按钮的点击事件
        fromAlbumBtn.setOnClickListener{
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            startActivityForResult(intent, fromAlbum)
        }

        // 历史记录按钮点击事件
        historyBtn.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }
    }


    private fun requestPermissions() {
        // 1. 根据Android版本动态构建权限列表
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.CAMERA)  // Android Q（API 29）及以上：仅需相机权限
        } else {
            arrayOf(
                Manifest.permission.CAMERA,   // 相机权限（必需）
                Manifest.permission.WRITE_EXTERNAL_STORAGE   // 存储权限（低版本必需，Android Q以下）
            )
        }
        // 检查权限是否已授予
        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            openCamera()  // 权限已全部授予，直接打开相机
        } else {
            requestPermissionLauncher.launch(permissions)   // 权限未完全授予，向用户请求权限
        }
    }

    private fun openCamera() {
        // 创建File对象，用于存储拍照后的图片
        // 文件名生成：scanned_YYYYMMDD_HHMMSS.jpg
        val fileName = "scanned_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"

        // 历史记录目录：/storage/emulated/0/Android/data/com.max.filescaner/files/Pictures/FileScanner/
        val historyDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "FileScanner")

        // 确保历史记录目录存在（如果不存在则创建）
        if (!historyDir.exists() && !historyDir.mkdirs()) {
            Toast.makeText(this, "无法创建存储目录", Toast.LENGTH_SHORT).show()
            return
        }

        outputImage = File(historyDir, fileName)

        // 正确判断Android版本以选择Uri获取方式
        imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(this, "com.max.filescaner.fileprovider", outputImage)
        } else {
            Uri.fromFile(outputImage)
        }

        // 启动相机
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // 指定图片保存位置：imageUri
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)

        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        takePictureLauncher.launch(intent)
    }

    private fun getBitmapFromUri(uri: Uri) = contentResolver.openFileDescriptor(uri, "r")?.use {
        BitmapFactory.decodeFileDescriptor(it.fileDescriptor)
    }

    // 移除自动保存到相册的逻辑（如果不需要可删除此方法）
    private fun saveImageToGallery() {
        // 留空或删除此方法，避免自动保存到相册
        Toast.makeText(this, "手动保存功能已关闭", Toast.LENGTH_SHORT).show()
    }

    private fun rotateIfRequired(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            // 根据Android版本选择不同的EXIF信息读取方式
            val orientation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10及以上版本通过Uri获取EXIF信息
                contentResolver.openFileDescriptor(uri, "r")?.use {
                    val exif = ExifInterface(it.fileDescriptor)
                    exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } else {
                // Android 10以下版本通过文件路径获取EXIF信息
                val exif = ExifInterface(outputImage.path)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270)
                else -> bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap // 如果出现异常，返回原始bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degree: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        // 将不需要的Bitmap对象回收
        bitmap.recycle()
        return rotatedBitmap
    }

    // 添加相册选择结果处理
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == fromAlbum && resultCode == Activity.RESULT_OK) {
            // 获取选中图片的URI
            val imageUri = data?.data ?: run {
                Toast.makeText(this, "未选中图片", Toast.LENGTH_SHORT).show()
                return
            }

            // 保存图片到历史目录并获取路径
            val savedImagePath = saveAlbumImageToHistory(imageUri)
            savedImagePath?.let { path ->
                // 跳转到ImageDetailActivity，传入图片路径
                val intent = Intent(this, ImageDetailActivity::class.java)
                intent.putExtra("IMAGE_PATH", path)
                startActivity(intent)
            } ?: Toast.makeText(this, "图片保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    // 修改相册图片保存逻辑，启动图像处理Activity
    private fun saveAlbumImageToHistory(imageUri: Uri): String? {
        return try {
            val historyDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "FileScanner")
            if (!historyDir.exists() && !historyDir.mkdirs()) {
                Toast.makeText(this, "无法创建历史目录", Toast.LENGTH_SHORT).show()
                return null
            }

            val fileName = "temp_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
            val tempFile = File(historyDir, fileName)

            // 仅保存原始图片，不处理
            val originalBitmap = getBitmapFromUri(imageUri) ?: return null
            FileOutputStream(tempFile).use { out ->
                originalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out) // 不压缩保存原始图
            }
            originalBitmap.recycle()

            // 启动图像处理Activity
            val intent = Intent(this, ImageProcessingActivity::class.java)
            intent.putExtra("IMAGE_PATH", tempFile.absolutePath)
            intent.putExtra("IS_FROM_CAMERA", false)
            startActivity(intent)

            tempFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }




}