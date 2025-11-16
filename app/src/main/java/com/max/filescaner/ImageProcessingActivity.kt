package com.max.filescaner


import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.appcompat.widget.AppCompatImageView
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.android.OpenCVLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.core.Mat
import android.widget.SeekBar
import org.opencv.core.Core
import android.view.MotionEvent
import android.content.Context
import android.util.AttributeSet
import androidx.lifecycle.lifecycleScope
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.InstallCallbackInterface



class ImageProcessingActivity : AppCompatActivity() {
    private lateinit var progressBar: ProgressBar
    private lateinit var originalImageView: ImageView
    private lateinit var scannedImageView: ImageView // 处理后图片预览
    private lateinit var buttonContainer: LinearLayout  // 新增：按钮容器
    private lateinit var btnSave: Button               // 新增：保留按钮
    private lateinit var btnCancel: Button             // 新增：取消按钮
    private lateinit var btnCrop: Button  // 新增：裁剪按钮
    private var imagePath: String? = null
    private var isFromCamera: Boolean = false
    private var scannedBitmap: Bitmap? = null // 暂存处理后图片，用于用户确认

    private var currentThreshold = 150.0
    private var currentContrast = 2.0
    private var currentBrightness = 30.0
    private var currentBlurSize = 3

    private lateinit var edgePreviewImageView: EdgePreviewImageView
    private var edgePreviewBitmap: Bitmap? = null
    private var edgePoints: MutableList<Point> = mutableListOf()

    private var selectedPointIndex: Int = -1
    private var isDragging: Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 添加OpenCV初始化检查
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "OpenCV初始化失败，尝试异步初始化")
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, object : LoaderCallbackInterface {
                override fun onManagerConnected(status: Int) {
                    if (status != LoaderCallbackInterface.SUCCESS) {
                        Toast.makeText(this@ImageProcessingActivity, "OpenCV初始化失败", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        // 初始化成功后继续处理
                        processImageAfterOpenCVInit()
                    }
                }

                override fun onPackageInstall(operation: Int, callback: InstallCallbackInterface?) {}
            })
            return
        }

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
        edgePreviewImageView = findViewById(R.id.edgePreviewImageView)
        edgePreviewImageView.setEdgePointListeners(
            onSelected = { x, y ->
                selectedPointIndex = findNearestEdgePoint(x, y)
                isDragging = selectedPointIndex != -1
            },
            onMoved = { x, y ->
                if (isDragging && selectedPointIndex != -1) {
                    adjustEdgePoint(selectedPointIndex, x, y)
                }
            },
            onReleased = {
                isDragging = false
            }
        )
        val seekBarThreshold = findViewById<SeekBar>(R.id.seekBarThreshold)
        val seekBarContrast = findViewById<SeekBar>(R.id.seekBarContrast)
        val seekBarBrightness = findViewById<SeekBar>(R.id.seekBarBrightness)
        val seekBarBlur = findViewById<SeekBar>(R.id.seekBarBlur)

        seekBarThreshold.setOnSeekBarChangeListener(createParamChangeListener { progress ->
            currentThreshold = progress.toDouble()
            updatePreview()
        })
        seekBarContrast.setOnSeekBarChangeListener(createParamChangeListener { progress ->
            currentContrast = progress.toDouble() / 10
            updatePreview()
        })
        seekBarBrightness.setOnSeekBarChangeListener(createParamChangeListener { progress ->
            currentBrightness = progress.toDouble()
            updatePreview()
        })
        seekBarBlur.setOnSeekBarChangeListener(createParamChangeListener { progress ->
            currentBlurSize = progress * 2 + 1 // 确保是奇数
            updatePreview()
        })
        if (imagePath.isNullOrEmpty()) {
            Toast.makeText(this, "无效的图片路径", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        btnCrop = findViewById(R.id.btnCrop)
        btnCrop.setOnClickListener {
            val originalBitmap = BitmapFactory.decodeFile(imagePath)
            originalImageView.setImageBitmap(originalBitmap)
            processImage(originalBitmap)
            edgePreviewImageView = findViewById(R.id.edgePreviewImageView)
            lifecycleScope.launch(Dispatchers.IO){
                try {
                    val croppedBitmap = cropDocument(originalBitmap)
                    withContext(Dispatchers.Main) {
                        scannedImageView.setImageBitmap(croppedBitmap)
                        scannedBitmap = croppedBitmap
                    }
                } catch (e: Exception) {
                    Log.e("ImageProcessing", "裁剪失败", e)
                }
            }
        }
    }
    // 添加缺失的方法
    private fun processImageAfterOpenCVInit() {
        val originalBitmap = BitmapFactory.decodeFile(imagePath)
        originalImageView.setImageBitmap(originalBitmap)
        processImage(originalBitmap)
    }
    // 添加裁剪方法
    private fun cropDocument(originalBitmap: Bitmap): Bitmap {
        val srcMat = Mat()
        Utils.bitmapToMat(originalBitmap, srcMat)

        // 灰度转换
        val grayMat = Mat()
        Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_BGR2GRAY)

        // 边缘检测
        val edgesMat = Mat()
        Imgproc.Canny(grayMat, edgesMat, currentThreshold, currentThreshold * 2)

        // 查找轮廓
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edgesMat, contours, hierarchy,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        if (contours.isNotEmpty()) {
            val largestContour = contours.maxByOrNull { Imgproc.contourArea(it) }
            largestContour?.let { contour ->
                // 获取边界矩形
                val rect = Imgproc.boundingRect(contour)

                // 裁剪图像
                val croppedMat = Mat(srcMat, rect)
                val resultBitmap = Bitmap.createBitmap(croppedMat.cols(), croppedMat.rows(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(croppedMat, resultBitmap)

                // 释放资源
                croppedMat.release()
                return resultBitmap
            }
        }

        // 如果没有找到轮廓，返回原图
        return originalBitmap
    }

    // 查找最近的边缘点
    private fun findNearestEdgePoint(x: Int, y: Int): Int {
        var minDistance = Float.MAX_VALUE
        var nearestIndex = -1
        edgePoints.forEachIndexed { index, point ->
            val distance = Math.sqrt(Math.pow(point.x - x, 2.0) + Math.pow(point.y - y, 2.0))
            if (distance < 20 && distance < minDistance) { // 20像素范围内
                minDistance = distance.toFloat()
                nearestIndex = index
            }
        }
        return nearestIndex
    }

    // 添加参数变化监听器创建方法
    private fun createParamChangeListener(onChange: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                onChange(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }
    // 更新预览方法
    private fun updatePreview() {
        val originalBitmap = originalImageView.drawable?.let { (it as BitmapDrawable).bitmap } ?: return
        lifecycleScope.launch(Dispatchers.IO){
            try {
                val processedBitmap = convertToDocumentStyle(originalBitmap)
                withContext(Dispatchers.Main) {
                    scannedImageView.setImageBitmap(processedBitmap)
                }
            } catch (e: Exception) {
                Log.e("ImageProcessing", "更新预览失败", e)
            }
        }
    }
    private fun processImage(originalBitmap: Bitmap) {
        progressBar.visibility = ProgressBar.VISIBLE

        // 使用子线程处理避免ANR
        lifecycleScope.launch(Dispatchers.IO){
            try {

                val processedBitmap = convertToDocumentStyle(originalBitmap)

                withContext(Dispatchers.Main) {
                    scannedBitmap = processedBitmap
                    originalImageView.setImageBitmap(originalBitmap)
                    scannedImageView.setImageBitmap(processedBitmap)
                    progressBar.visibility = ProgressBar.GONE
                    buttonContainer.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ImageProcessingActivity,
                        "处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
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
            finish()
        }
    }
    // 新增：取消保存并返回
    private fun cancelAndFinish() {
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

    private fun convertToDocumentStyle(originalBitmap: Bitmap): Bitmap {
        return try {
            Log.d("ImageProcessing", "开始处理图片，尺寸: ${originalBitmap.width}x${originalBitmap.height}")
            // 将Bitmap转换为OpenCV的Mat格式
            val srcMat = Mat()
            Utils.bitmapToMat(originalBitmap, srcMat)

            // 1. 灰度转换
            val grayMat = Mat()
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_BGR2GRAY)

            // 2. 边缘检测
            val edgesMat = Mat()
            Imgproc.Canny(grayMat, edgesMat, currentThreshold, currentThreshold * 2)

            // 生成边缘预览
            val previewMat = Mat()
            Imgproc.cvtColor(edgesMat, previewMat, Imgproc.COLOR_GRAY2BGR)

            // 3. 查找文档轮廓
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(edgesMat, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            // 存储轮廓点
            edgePoints = contours.flatMap { it.toList() }.toMutableList()

            // 绘制轮廓点
            for (point in edgePoints) {
                Imgproc.circle(previewMat, point, 5, Scalar(0.0, 255.0, 0.0), -1)
            }

            // 显示边缘预览
            edgePreviewBitmap = Bitmap.createBitmap(previewMat.cols(), previewMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(previewMat, edgePreviewBitmap)
            runOnUiThread {
                edgePreviewImageView.setImageBitmap(edgePreviewBitmap)
            }

            // 修改对比度和亮度部分
            val colorMatrix = Mat()
            Core.convertScaleAbs(grayMat, colorMatrix, currentContrast, currentBrightness)

            // 添加模糊处理
            if (currentBlurSize > 1) {
                Imgproc.GaussianBlur(colorMatrix, colorMatrix,
                    Size(currentBlurSize.toDouble(), currentBlurSize.toDouble()), 0.0)
            }

            // 4. 透视变换（文档矫正）
            val docMat = if (contours.isNotEmpty()) {
                val largestContour = contours.maxByOrNull { Imgproc.contourArea(it) }
                largestContour?.let { contour ->
                    val approxCurve = MatOfPoint2f()
                    val contour2f = MatOfPoint2f(*contour.toArray())
                    val peri = Imgproc.arcLength(contour2f, true)
                    Imgproc.approxPolyDP(contour2f, approxCurve, 0.02 * peri, true)

                    if (approxCurve.toArray().size == 4) {
                        val srcPoints = sortPoints(approxCurve)
                        val dstPoints = getDestinationPoints(srcPoints)
                        val perspectiveTransform = Imgproc.getPerspectiveTransform(
                            srcPoints, dstPoints)
                        val warpedMat = Mat()

                        // 计算目标尺寸
                        val width = Math.sqrt(Math.pow(dstPoints.toArray()[1].x - dstPoints.toArray()[0].x, 2.0) +
                                Math.pow(dstPoints.toArray()[1].y - dstPoints.toArray()[0].y, 2.0))
                        val height = Math.sqrt(Math.pow(dstPoints.toArray()[2].x - dstPoints.toArray()[1].x, 2.0) +
                                Math.pow(dstPoints.toArray()[2].y - dstPoints.toArray()[1].y, 2.0))

                        // 执行透视变换
                        Imgproc.warpPerspective(colorMatrix, warpedMat,
                            perspectiveTransform, Size(width, height))
                        warpedMat
                    } else {
                        // 如果不是四边形，使用矩形边界裁剪
                        val rect = Imgproc.boundingRect(contour)
                        val croppedMat = Mat(colorMatrix, rect)
                        croppedMat.clone()
                    }
                } ?: colorMatrix
            } else {
                colorMatrix
            }

            // 5. 自适应阈值二值化
            val binaryMat = Mat()
            Imgproc.adaptiveThreshold(docMat, binaryMat, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 2.0)

            // 将处理后的Mat转换回Bitmap
            val resultBitmap = Bitmap.createBitmap(binaryMat.cols(), binaryMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(binaryMat, resultBitmap)

            // 释放Mat内存
            srcMat.release()
            grayMat.release()
            edgesMat.release()
            previewMat.release()
            colorMatrix.release()
            docMat.release()
            binaryMat.release()

            resultBitmap
        } catch (e: Exception) {
            Log.e("ImageProcessing", "处理失败", e)
            throw e
        }
    }

    // 边缘点调整方法
    private fun adjustEdgePoint(index: Int, newX: Int, newY: Int) {
        if (index in 0 until edgePoints.size) {
            val bitmap = originalImageView.drawable?.let { (it as BitmapDrawable).bitmap } ?: return
            // 限制点在图片范围内
            edgePoints[index].x = newX.coerceIn(0, bitmap.width - 1).toDouble()
            edgePoints[index].y = newY.coerceIn(0, bitmap.height - 1).toDouble()
            updateEdgePreviewWithPoints()
        }
    }

    // 更新边缘预览方法（带点绘制）
    private fun updateEdgePreviewWithPoints() {
        val originalBitmap = originalImageView.drawable?.let { (it as BitmapDrawable).bitmap } ?: return
        lifecycleScope.launch(Dispatchers.IO){
            try {
                val srcMat = Mat()
                Utils.bitmapToMat(originalBitmap, srcMat)

                // 边缘检测
                val grayMat = Mat()
                Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_BGR2GRAY)

                val edgesMat = Mat()
                Imgproc.Canny(grayMat, edgesMat, currentThreshold, currentThreshold * 2)

                // 创建预览图
                val previewMat = Mat()
                Imgproc.cvtColor(edgesMat, previewMat, Imgproc.COLOR_GRAY2BGR)

                // 绘制边缘点
                for (point in edgePoints) {
                    Imgproc.circle(previewMat, point, 8, Scalar(0.0, 0.0, 255.0), -1)
                }

                // 显示预览
                val previewBitmap = Bitmap.createBitmap(previewMat.cols(), previewMat.rows(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(previewMat, previewBitmap)

                withContext(Dispatchers.Main) {
                    edgePreviewImageView.setImageBitmap(previewBitmap)
                }

                // 释放资源
                srcMat.release()
                grayMat.release()
                edgesMat.release()
                previewMat.release()
            } catch (e: Exception) {
                Log.e("ImageProcessing", "更新预览失败", e)
            }
        }
    }
    // 辅助方法：对检测到的角点进行排序
    private fun sortPoints(points: MatOfPoint2f): MatOfPoint2f {
        val pts = points.toArray()
        val sorted = pts.sortedWith(compareBy({ it.x + it.y })).toTypedArray()
        return MatOfPoint2f(*sorted)
    }

    // 辅助方法：获取透视变换的目标点
    private fun getDestinationPoints(srcPoints: MatOfPoint2f): MatOfPoint2f {
        val pts = srcPoints.toArray()
        val tl = pts[0]
        val tr = pts[1]
        val br = pts[2]
        val bl = pts[3]

        val widthA = Math.sqrt(Math.pow(br.x - bl.x, 2.0) + Math.pow(br.y - bl.y, 2.0))
        val widthB = Math.sqrt(Math.pow(tr.x - tl.x, 2.0) + Math.pow(tr.y - tl.y, 2.0))
        val maxWidth = maxOf(widthA, widthB)

        val heightA = Math.sqrt(Math.pow(tr.x - br.x, 2.0) + Math.pow(tr.y - br.y, 2.0))
        val heightB = Math.sqrt(Math.pow(tl.x - bl.x, 2.0) + Math.pow(tl.y - bl.y, 2.0))
        val maxHeight = maxOf(heightA, heightB)

        return MatOfPoint2f(
            Point(0.0, 0.0),
            Point(maxWidth - 1.0, 0.0),
            Point(maxWidth - 1.0, maxHeight - 1.0),
            Point(0.0, maxHeight - 1.0)
        )
    }
}

class EdgePreviewImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {  // 修改这里

    private var onEdgePointSelected: ((x: Int, y: Int) -> Unit)? = null
    private var onEdgePointMoved: ((x: Int, y: Int) -> Unit)? = null
    private var onEdgePointReleased: (() -> Unit)? = null

    fun setEdgePointListeners(
        onSelected: (x: Int, y: Int) -> Unit,
        onMoved: (x: Int, y: Int) -> Unit,
        onReleased: () -> Unit
    ) {
        this.onEdgePointSelected = onSelected
        this.onEdgePointMoved = onMoved
        this.onEdgePointReleased = onReleased
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                performClick()
                onEdgePointSelected?.invoke(event.x.toInt(), event.y.toInt())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                onEdgePointMoved?.invoke(event.x.toInt(), event.y.toInt())
                return true
            }
            MotionEvent.ACTION_UP -> {
                onEdgePointReleased?.invoke()
                return true
            }
            else -> return super.onTouchEvent(event)
        }
    }
}