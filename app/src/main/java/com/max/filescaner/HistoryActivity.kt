package com.max.filescaner

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast // 添加此行导入Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import android.graphics.Color
import android.view.ViewGroup


class HistoryActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var imageAdapter: ImageAdapter
    private val imageFiles = mutableListOf<File>()

    // 添加选择模式相关变量
    private val selectedPositions = mutableSetOf<Int>() // 选中的图片位置
    private var isSelectionMode = false // 是否处于选择模式
    private lateinit var deleteBtn: Button // 删除按钮



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history) // 加载布局文件

        recyclerView = findViewById(R.id.historyRecyclerView)   // 获取RecyclerView实例
        recyclerView.layoutManager = GridLayoutManager(this, 2)     // 2列网格布局
        loadImageFiles()    // 从本地目录加载图片文件
        imageAdapter = ImageAdapter(imageFiles)     // 创建适配器并绑定数据源
        recyclerView.adapter = imageAdapter     // 将适配器设置给RecyclerView

        //  初始化删除按钮（需在activity_history.xml中添加Button，id为deleteBtn）
        deleteBtn = findViewById(R.id.deleteBtn)
        deleteBtn.setOnClickListener { deleteSelectedImages() }
        deleteBtn.visibility = View.GONE // 默认隐藏

    }

    // 从存储目录加载所有照片文件（过滤：仅包含"scanned_"开头的图片）
    private fun loadImageFiles() {
        val historyDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "FileScanner")
        if (historyDir.exists() && historyDir.isDirectory) {
            historyDir.listFiles { file ->
                file.name.startsWith("scanned_") && file.extension == "jpg"
            }?.sortedByDescending { it.lastModified() }?.let {
                imageFiles.addAll(it)
                // 添加调试信息
                Toast.makeText(this, "加载到 ${it.size} 张图片", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(this, "未找到图片文件", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "目录不存在", Toast.LENGTH_SHORT).show()
        }
    }

    // RecyclerView适配器：绑定照片到列表项
    inner class ImageAdapter(private val files: List<File>) :
        // 列表项视图持有者（缓存列表项中的ImageView）
        RecyclerView.Adapter<ImageAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: FrameLayout) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = ImageView(itemView.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )    // 用于显示图片的ImageView
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(8, 8, 8, 8)
            }
            // 添加选中状态指示器（可选：覆盖一层半透明背景+勾选图标）
            val selectOverlay = View(itemView.context).apply {
                setBackgroundColor(Color.parseColor("#80000000")) // 半透明黑色
                visibility = View.GONE // 默认隐藏
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            init {
                // 将ImageView和选择指示器添加到FrameLayout
                itemView.addView(imageView)
                itemView.addView(selectOverlay)
            }

        }

        // 创建列表项视图（修改为FrameLayout作为根容器）
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            // 创建FrameLayout作为根视图（容器）
            val frameLayout = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    800 // 固定高度
                )
            }
            return ViewHolder(frameLayout) // 将FrameLayout传入ViewHolder
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]  // 获取当前位置的图片文件
            // 加载图片并修正方向（复用MainActivity中的旋转逻辑）
            val bitmap = getBitmapFromFile(file)?.let { rotateIfRequired(it, file) }
            holder.imageView.setImageBitmap(bitmap) // 将处理后的图片设置到ImageView

            // 更新选中状态UI
            holder.selectOverlay.visibility = if (selectedPositions.contains(position)) {
                View.VISIBLE // 显示选中指示器
            } else {
                View.GONE // 隐藏选中指示器
            }

            // 长按进入选择模式
            holder.itemView.setOnLongClickListener {
                if (!isSelectionMode) {
                    isSelectionMode = true
                    selectedPositions.add(position)
                    updateSelectionModeUI() // 更新删除按钮和适配器
                    true
                } else false
            }

            // 点击切换选中状态（选择模式下）
            holder.itemView.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(position)
                } else {
                    // 原点击逻辑：跳转到详情页
                    val intent = Intent(holder.itemView.context, ImageDetailActivity::class.java)
                    intent.putExtra("IMAGE_PATH", file.absolutePath)
                    holder.itemView.context.startActivity(intent)
                }
            }
        }

        override fun getItemCount() = files.size // 列表项数量 = 图片文件数量
    }

    // 从文件加载Bitmap（需添加权限检查，但私有目录无需额外权限）
    private fun getBitmapFromFile(file: File): android.graphics.Bitmap? {
        return try {
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)    // 从文件路径解码Bitmap
        } catch (e: Exception) {
            e.printStackTrace() // 捕获异常（如文件损坏、内存不足）
            null
        }
    }

    // 修正图片方向（复用MainActivity逻辑，需将EXIF相关方法复制到此处）
    private fun rotateIfRequired(bitmap: android.graphics.Bitmap, file: File): android.graphics.Bitmap {
        return try {
            val exif = android.media.ExifInterface(file.absolutePath)   // 读取图片EXIF元数据
            // 获取拍摄时的方向信息（默认正常方向）
            val orientation = exif.getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
            // 根据方向旋转图片
            when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180)
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270)
                else -> bitmap  // 无需旋转
            }
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }
    // 执行图片旋转
    private fun rotateBitmap(bitmap: android.graphics.Bitmap, degree: Int): android.graphics.Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degree.toFloat())     // 设置旋转角度
        val rotatedBitmap = android.graphics.Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
        bitmap.recycle() // 释放原始Bitmap内存（避免内存泄漏）
        return rotatedBitmap
    }
    // 切换选中状态
    private fun toggleSelection(position: Int) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
        } else {
            selectedPositions.add(position)
        }
        updateSelectionModeUI()
    }
    // 更新选择模式UI
    private fun updateSelectionModeUI() {
        if (selectedPositions.isEmpty()) {
            // 无选中项时退出选择模式
            isSelectionMode = false
            deleteBtn.visibility = View.GONE

        } else {
            // 显示删除按钮和选中数量
            deleteBtn.visibility = View.VISIBLE
            deleteBtn.text = "删除 (${selectedPositions.size})"
        }
        imageAdapter.notifyDataSetChanged() // 刷新列表UI
    }
    // 删除选中图片
    private fun deleteSelectedImages() {
        // 按位置倒序删除（避免索引错乱）
        val sortedPositions = selectedPositions.sortedDescending()
        sortedPositions.forEach { position ->
            val file = imageFiles[position]
            if (file.delete()) { // 删除文件
                imageFiles.removeAt(position) // 从数据源移除
            }
        }
        // 重置选择状态
        selectedPositions.clear()
        isSelectionMode = false
        deleteBtn.visibility = View.GONE
        imageAdapter.notifyDataSetChanged() // 刷新列表
        Toast.makeText(this, "已删除${sortedPositions.size}张图片", Toast.LENGTH_SHORT).show()
    }

}