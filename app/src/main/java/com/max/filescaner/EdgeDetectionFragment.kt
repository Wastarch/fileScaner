package com.max.filescaner

import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.android.OpenCVLoader
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.InstallCallbackInterface
import android.view.MotionEvent
import org.opencv.core.Point
import org.opencv.core.MatOfPoint2f
import android.view.ViewTreeObserver
import android.graphics.Rect

class EdgeDetectionFragment : Fragment() {

    companion object {
        private const val TAG = "DocEdgeDetection"
        private const val ARG_IMAGE_PATH = "IMAGE_PATH"
        private const val MAX_IMAGE_DIMENSION = 1024

        fun newInstance(imagePath: String): EdgeDetectionFragment {
            return EdgeDetectionFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_IMAGE_PATH, imagePath)
                }
            }
        }
    }

    private var imagePath: String? = null
    private var isOpenCVInitialized = false
    private var originalBitmap: Bitmap? = null
    private var processedBitmap: Bitmap? = null
    private var currentCorners: Array<Point>? = null
    private var scaleFactor: Double = 1.0

    // UI组件
    private lateinit var imageView: ImageView
    private lateinit var nextButton: Button // 改为下一步按钮
    private lateinit var adjustButton: Button
    private lateinit var resetButton: Button
    private lateinit var thresholdSeekBar: SeekBar
    private lateinit var thresholdText: TextView
    private lateinit var adjustLayout: LinearLayout
    private lateinit var edgePointsLayout: FrameLayout
    private lateinit var progressBar: ProgressBar

    // 边缘检测参数
    private var cannyThreshold = 100

    // 触摸点相关
    private val cornerPoints = mutableListOf<Point>()
    private val touchPoints = mutableListOf<View>()
    private var selectedPointIndex = -1

    // 回调接口，用于与Activity通信
    interface OnEdgeDetectionCompleteListener {
        fun onEdgeDetectionComplete(originalImagePath: String, corners: Array<Point>)
    }

    private var listener: OnEdgeDetectionCompleteListener? = null

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        if (context is OnEdgeDetectionCompleteListener) {
            listener = context
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getString(ARG_IMAGE_PATH)?.let {
            imagePath = it
            Log.d(TAG, "接收到的图片路径: $it")
        }
        initOpenCV()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "创建视图")
        return inflater.inflate(R.layout.fragment_edge_detection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "视图创建完成")
        initViews(view)
        setupClickListeners()
        setupSeekBar()

        imageView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                imageView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                Log.d(TAG, "ImageView完成布局，尺寸: ${imageView.width}x${imageView.height}")

                if (isOpenCVInitialized) {
                    loadImage()
                } else {
                    Log.w(TAG, "OpenCV尚未初始化，等待初始化完成")
                }
            }
        })
    }

    private fun initViews(view: View) {
        Log.d(TAG, "初始化视图组件")
        try {
            imageView = view.findViewById(R.id.iv_edge_detection)
            nextButton = view.findViewById(R.id.btn_save) // 重用保存按钮作为下一步按钮
            adjustButton = view.findViewById(R.id.btn_adjust)
            resetButton = view.findViewById(R.id.btn_reset)
            thresholdSeekBar = view.findViewById(R.id.seekbar_threshold)
            thresholdText = view.findViewById(R.id.tv_threshold_value)
            adjustLayout = view.findViewById(R.id.layout_adjust_controls)
            edgePointsLayout = view.findViewById(R.id.layout_edge_points)
            progressBar = view.findViewById(R.id.progress_bar) ?: ProgressBar(context)

            // 修改按钮文本
            nextButton.text = "下一步：透视矫正"

            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            initTouchPoints()
            Log.d(TAG, "视图组件初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "初始化视图组件失败", e)
            showToast("初始化界面失败")
        }
    }

    private fun initTouchPoints() {
        Log.d(TAG, "初始化触摸点")
        for (i in 0 until 4) {
            val pointView = View(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(60, 60) // 增大触摸区域
                setBackgroundColor(0xFFFF0000.toInt())
                visibility = View.GONE

                // 确保可点击和触摸
                isClickable = true
                isFocusable = true
                isEnabled = true
            }

            pointView.setOnTouchListener { v, event ->
                Log.d(TAG, "触摸点 $i 被触摸，动作: ${event.action}")
                handlePointTouch(i, event)
            }

            edgePointsLayout.addView(pointView)
            touchPoints.add(pointView)
        }
    }

    private fun setupClickListeners() {
        nextButton.setOnClickListener {
            Log.d(TAG, "下一步按钮点击")
            proceedToPerspectiveCorrection()
        }

        adjustButton.setOnClickListener {
            Log.d(TAG, "调整按钮点击")
            toggleAdjustMode()
        }

        resetButton.setOnClickListener {
            Log.d(TAG, "重置按钮点击")
            resetCorners()
        }
    }

    private fun setupSeekBar() {
        thresholdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                cannyThreshold = progress
                thresholdText.text = "阈值: $progress"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                Log.d(TAG, "阈值调整为: $cannyThreshold")
                processImageWithCurrentSettings()
            }
        })
    }

    private fun initOpenCV() {
        Log.d(TAG, "初始化OpenCV")
        val loaderCallback = object : LoaderCallbackInterface {
            override fun onManagerConnected(status: Int) {
                when (status) {
                    LoaderCallbackInterface.SUCCESS -> {
                        Log.i(TAG, "OpenCV初始化成功")
                        isOpenCVInitialized = true
                        if (view != null) {
                            loadImage()
                        }
                    }
                    else -> {
                        Log.e(TAG, "OpenCV初始化失败，状态码: $status")
                        showToast("OpenCV初始化失败")
                    }
                }
            }

            override fun onPackageInstall(reason: Int, installerPackageName: InstallCallbackInterface?) {
                Log.w(TAG, "需要安装OpenCV Manager")
                showToast("需要安装OpenCV Manager")
            }
        }

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "使用OpenCV Manager初始化")
            OpenCVLoader.initAsync("4.5.3", context, loaderCallback)
        } else {
            Log.d(TAG, "使用本地OpenCV库初始化")
            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }

    private fun loadImage() {
        Log.d(TAG, "开始加载图片: $imagePath")
        imagePath?.let { path ->
            showProgress(true)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(path, options)

                    val scale = calculateScaleFactor(options.outWidth, options.outHeight)
                    Log.d(TAG, "原图尺寸: ${options.outWidth}x${options.outHeight}, 缩放因子: $scale")

                    val decodeOptions = BitmapFactory.Options().apply {
                        inJustDecodeBounds = false
                        inSampleSize = scale
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }

                    val bitmap = BitmapFactory.decodeFile(path, decodeOptions)
                    if (bitmap == null) {
                        Log.e(TAG, "无法解码图片: $path")
                        withContext(Dispatchers.Main) {
                            showProgress(false)
                            showToast("无法加载图片")
                        }
                        return@launch
                    }

                    Log.d(TAG, "缩放后图片尺寸: ${bitmap.width}x${bitmap.height}")
                    scaleFactor = options.outWidth.toDouble() / bitmap.width

                    withContext(Dispatchers.Main) {
                        originalBitmap = bitmap
                        processImageWithCurrentSettings()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "加载图片失败", e)
                    withContext(Dispatchers.Main) {
                        showProgress(false)
                        showToast("加载图片失败: ${e.message}")
                    }
                }
            }
        } ?: run {
            Log.e(TAG, "图片路径为空")
            showToast("图片路径无效")
        }
    }

    private fun calculateScaleFactor(width: Int, height: Int): Int {
        var scale = 1
        val maxDimension = maxOf(width, height)

        if (maxDimension > MAX_IMAGE_DIMENSION) {
            scale = Math.ceil(maxDimension.toDouble() / MAX_IMAGE_DIMENSION).toInt()
        }

        Log.d(TAG, "计算缩放因子: 原图最大尺寸=$maxDimension, 缩放因子=$scale")
        return scale
    }


    private fun processImageWithCurrentSettings() {
        Log.d(TAG, "处理图片，阈值: $cannyThreshold")
        originalBitmap?.let { bitmap ->
            showProgress(true)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = detectDocumentEdges(bitmap)
                    withContext(Dispatchers.Main) {
                        processedBitmap = result
                        imageView.setImageBitmap(result)

                        imageView.post {
                            updateCornerPointsDisplay()
                        }

                        showProgress(false)
                        showToast("边缘检测完成，可调整角点")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理图片失败", e)
                    withContext(Dispatchers.Main) {
                        showProgress(false)
                        showToast("处理失败: ${e.message}")
                    }
                }
            }
        }
    }

    private fun detectDocumentEdges(bitmap: Bitmap): Bitmap {
        Log.d(TAG, "开始边缘检测，图片尺寸: ${bitmap.width}x${bitmap.height}")
        val srcMat = Mat()
        val grayMat = Mat()
        val blurredMat = Mat()
        val edgesMat = Mat()
        val resultMat = Mat()
        val adaptiveMat = Mat()
        val hierarchy = Mat()

        try {
            // 转换为OpenCV Mat
            Utils.bitmapToMat(bitmap, srcMat)
            Log.d(TAG, "原图Mat信息 - 尺寸: ${srcMat.cols()}x${srcMat.rows()}")

            // 转为灰度图
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_BGR2GRAY)

            // 自适应阈值处理 - 提高对比度
            Imgproc.adaptiveThreshold(grayMat, adaptiveMat, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 2.0)

            // 高斯模糊
            Imgproc.GaussianBlur(adaptiveMat, blurredMat, Size(5.0, 5.0), 0.0)

            // Canny边缘检测 - 使用更宽松的阈值
            val threshold1 = cannyThreshold * 0.3
            val threshold2 = cannyThreshold.toDouble()
            Log.d(TAG, "Canny阈值: $threshold1, $threshold2")
            Imgproc.Canny(blurredMat, edgesMat, threshold1, threshold2)

            // 形态学操作 - 连接断开的边缘
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            Imgproc.dilate(edgesMat, edgesMat, kernel)
            Imgproc.erode(edgesMat, edgesMat, kernel)

            // 查找轮廓 - 使用外部轮廓
            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(edgesMat, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            Log.d(TAG, "找到 ${contours.size} 个轮廓")

            // 查找最大的四边形轮廓
            val documentContour = findDocumentContour(contours, bitmap.width * bitmap.height)

            // 绘制结果
            srcMat.copyTo(resultMat)

            if (documentContour != null) {
                Log.d(TAG, "找到文档轮廓")
                // 绘制轮廓
                Imgproc.drawContours(resultMat, listOf(documentContour), -1, Scalar(0.0, 255.0, 0.0), 3)

                // 获取角点坐标
                val corners = getCornersFromContour(documentContour)
                currentCorners = corners
                Log.d(TAG, "检测到的角点: ${corners.joinToString()}")

                // 绘制角点
                corners.forEach { point ->
                    Imgproc.circle(resultMat, point, 15, Scalar(255.0, 0.0, 0.0), -1)
                    Imgproc.circle(resultMat, point, 15, Scalar(255.0, 255.0, 255.0), 2)
                }

                // 绘制连接线
                for (i in 0 until 4) {
                    val startPoint = corners[i]
                    val endPoint = corners[(i + 1) % 4]
                    Imgproc.line(resultMat, startPoint, endPoint, Scalar(0.0, 255.0, 255.0), 2)
                }

                // 更新触摸点位置
                updateCornerPoints(corners, bitmap.width, bitmap.height)
            } else {
                Log.w(TAG, "未找到文档轮廓，使用默认角点")
                currentCorners = getDefaultCorners(bitmap.width, bitmap.height)
                updateCornerPoints(currentCorners!!, bitmap.width, bitmap.height)

                currentCorners!!.forEach { point ->
                    Imgproc.circle(resultMat, point, 15, Scalar(255.0, 0.0, 0.0), -1)
                    Imgproc.circle(resultMat, point, 15, Scalar(255.0, 255.0, 255.0), 2)
                }

                Imgproc.putText(resultMat, "未检测到文档边缘",
                    Point(bitmap.width * 0.1, bitmap.height * 0.1),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, Scalar(0.0, 0.0, 255.0), 2)
            }

            val resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultMat, resultBitmap)

            Log.d(TAG, "边缘检测完成，结果尺寸: ${resultBitmap.width}x${resultBitmap.height}")
            return resultBitmap

        } catch (e: Exception) {
            Log.e(TAG, "边缘检测过程中出错", e)
            return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        } finally {
            // 安全释放所有Mat资源
            releaseMatSafely(srcMat)
            releaseMatSafely(grayMat)
            releaseMatSafely(blurredMat)
            releaseMatSafely(edgesMat)
            releaseMatSafely(resultMat)
            releaseMatSafely(adaptiveMat)
            releaseMatSafely(hierarchy)
        }
    }

    // 辅助方法：安全释放Mat资源
    private fun releaseMatSafely(mat: Mat) {
        try {
            if (!mat.empty()) {
                mat.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "释放Mat资源时出错", e)
        }
    }

    private fun findDocumentContour(contours: List<MatOfPoint>, imageArea: Int): MatOfPoint? {
        var maxContour: MatOfPoint? = null
        var maxArea = 0.0

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            // 降低面积阈值，提高检测灵敏度
            if (area > imageArea * 0.05) { // 从10%降低到5%
                val epsilon = 0.02 * Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, epsilon, true)

                // 允许近似四边形（4-6个点都算）
                if (approx.total() in 4..6) {
                    if (area > maxArea) {
                        maxArea = area
                        maxContour = MatOfPoint(*approx.toArray())
                    }
                }
            }
        }

        return maxContour
    }

    private fun getCornersFromContour(contour: MatOfPoint): Array<Point> {
        val points = contour.toArray()
        if (points.size != 4) {
            return getDefaultCorners(1000, 1000)
        }

        // 简单的按坐标排序
        val sortedByY = points.sortedBy { it.y }
        val topPoints = sortedByY.take(2).sortedBy { it.x } // 左上、右上
        val bottomPoints = sortedByY.takeLast(2).sortedBy { it.x } // 左下、右下

        return arrayOf(
            topPoints[0], // 左上
            topPoints[1], // 右上
            bottomPoints[1], // 右下
            bottomPoints[0]  // 左下
        )
    }

    private fun getDefaultCorners(width: Int, height: Int): Array<Point> {
        return arrayOf(
            Point(0.1 * width, 0.1 * height),
            Point(0.9 * width, 0.1 * height),
            Point(0.9 * width, 0.9 * height),
            Point(0.1 * width, 0.9 * height)
        )
    }

    private fun updateCornerPoints(corners: Array<Point>, imageWidth: Int, imageHeight: Int) {
        cornerPoints.clear()
        cornerPoints.addAll(corners)

        requireActivity().runOnUiThread {
            // 强制测量ImageView尺寸
            imageView.measure(
                View.MeasureSpec.makeMeasureSpec(imageView.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(imageView.height, View.MeasureSpec.EXACTLY)
            )

            val viewWidth = imageView.measuredWidth
            val viewHeight = imageView.measuredHeight

            Log.d(TAG, "ImageView测量尺寸: ${viewWidth}x${viewHeight}, 图像尺寸: ${imageWidth}x${imageHeight}")

            if (viewWidth == 0 || viewHeight == 0) {
                Log.w(TAG, "ImageView尺寸为0，使用备用方案")
                // 备用方案：直接使用父容器尺寸
                val parentWidth = imageView.parent.toString().length // 临时方案
                updateCornerPointsDirect(corners, imageWidth, imageHeight)
                return@runOnUiThread
            }

            updateCornerPointsWithSize(corners, imageWidth, imageHeight, viewWidth, viewHeight)
        }
    }
    private fun updateCornerPointsDirect(corners: Array<Point>, imageWidth: Int, imageHeight: Int) {
        // 直接使用固定尺寸显示角点（临时方案）
        val displayWidth = resources.displayMetrics.widthPixels - 32 // 减去padding
        val displayHeight = (displayWidth * imageHeight) / imageWidth

        Log.d(TAG, "使用直接计算尺寸: ${displayWidth}x${displayHeight}")

        for (i in corners.indices) {
            val point = corners[i]
            val view = touchPoints[i]

            // 直接按比例计算位置
            val x = (point.x / imageWidth * displayWidth).toFloat()
            val y = (point.y / imageHeight * displayHeight).toFloat()

            Log.d(TAG, "角点 $i: 图像位置(${point.x}, ${point.y}) -> 直接计算位置($x, $y)")

            view.x = x - view.width / 2
            view.y = y - view.height / 2
            view.visibility = View.VISIBLE
        }
    }

    private fun updateCornerPointsWithSize(corners: Array<Point>, imageWidth: Int, imageHeight: Int, viewWidth: Int, viewHeight: Int) {
        // 计算图像在ImageView中的实际显示区域（考虑scaleType=FIT_CENTER）
        val scale = minOf(
            viewWidth.toFloat() / imageWidth,
            viewHeight.toFloat() / imageHeight
        )
        val scaledImageWidth = (imageWidth * scale).toInt()
        val scaledImageHeight = (imageHeight * scale).toInt()
        val offsetX = (viewWidth - scaledImageWidth) / 2
        val offsetY = (viewHeight - scaledImageHeight) / 2

        Log.d(TAG, "缩放比例: $scale, 显示区域: ${scaledImageWidth}x${scaledImageHeight}, 偏移: ($offsetX, $offsetY)")

        for (i in corners.indices) {
            val point = corners[i]
            val view = touchPoints[i]

            // 计算在ImageView中的实际显示位置
            val x = offsetX + (point.x * scale).toFloat()
            val y = offsetY + (point.y * scale).toFloat()

            Log.d(TAG, "角点 $i: 图像位置(${point.x}, ${point.y}) -> 视图位置($x, $y)")

            view.x = x - view.width / 2
            view.y = y - view.height / 2
            view.visibility = View.VISIBLE
        }
    }


    private fun updateCornerPointsDisplay() {
        processedBitmap?.let { bitmap ->
            currentCorners?.let { corners ->
                updateCornerPoints(corners, bitmap.width, bitmap.height)
            }
        }
    }

    private fun toggleAdjustMode() {
        val isAdjustMode = adjustLayout.visibility == View.VISIBLE
        adjustLayout.visibility = if (isAdjustMode) View.GONE else View.VISIBLE

        // 显示/隐藏触摸点
        touchPoints.forEach { pointView ->
            pointView.visibility = if (isAdjustMode) View.GONE else View.VISIBLE
        }

        adjustButton.text = if (isAdjustMode) "调整边缘" else "完成调整"
    }

    private fun handlePointTouch(index: Int, event: MotionEvent): Boolean {
        val view = touchPoints[index]
        Log.d(TAG, "触摸点 $index - 动作: ${event.action}, 坐标: (${event.rawX}, ${event.rawY})")

        // 处理触摸事件
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                selectedPointIndex = index
                Log.d(TAG, "开始拖动角点 $index")
                // 记录初始触摸位置和视图位置
                view.tag = Pair(event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (selectedPointIndex == index) {
                    val initialTouch = view.tag as? Pair<Float, Float>
                    val initialTouchX = initialTouch?.first ?: event.rawX
                    val initialTouchY = initialTouch?.second ?: event.rawY

                    // 计算移动距离
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    // 应用移动
                    val newX = view.x + deltaX
                    val newY = view.y + deltaY

                    // 限制在父容器内
                    val clampedX = newX.coerceIn(0f, edgePointsLayout.width - view.width.toFloat())
                    val clampedY = newY.coerceIn(0f, edgePointsLayout.height - view.height.toFloat())

                    view.x = clampedX
                    view.y = clampedY

                    // 更新触摸位置记录
                    view.tag = Pair(event.rawX, event.rawY)

                    updateCornerFromTouchPoint(index, clampedX, clampedY)
                    Log.d(TAG, "拖动角点 $index 到位置: ($view.x, $view.y)")
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                selectedPointIndex = -1
                view.tag = null
                Log.d(TAG, "结束拖动角点 $index")
                return true
            }
        }
        return false
    }

    private fun updateCornerFromTouchPoint(index: Int, viewX: Float, viewY: Float) {
        processedBitmap?.let { bitmap ->
            // 简单转换：假设ImageView完全显示图像
            val imageX = (viewX / imageView.width * bitmap.width).toDouble()
            val imageY = (viewY / imageView.height * bitmap.height).toDouble()

            // 确保坐标在图像范围内
            val clampedImageX = imageX.coerceIn(0.0, bitmap.width.toDouble())
            val clampedImageY = imageY.coerceIn(0.0, bitmap.height.toDouble())

            if (index < cornerPoints.size) {
                cornerPoints[index] = Point(clampedImageX, clampedImageY)
                currentCorners = cornerPoints.toTypedArray()
                Log.d(TAG, "更新角点 $index: ($clampedImageX, $clampedImageY)")

                // 实时更新显示
                updateEdgeDisplay()
            }
        }
    }

    /**
     * 获取图像在ImageView中的实际显示区域
     */
    private fun getImageDisplayRect(): Rect {
        val bitmap = processedBitmap ?: return Rect(0, 0, imageView.width, imageView.height)

        val viewWidth = imageView.width.toFloat()
        val viewHeight = imageView.height.toFloat()
        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()

        val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale
        val offsetX = (viewWidth - scaledWidth) / 2
        val offsetY = (viewHeight - scaledHeight) / 2

        return Rect(
            offsetX.toInt(),
            offsetY.toInt(),
            (offsetX + scaledWidth).toInt(),
            (offsetY + scaledHeight).toInt()
        )
    }

    /**
     * 实时更新边缘显示（不重新进行完整的边缘检测）
     */
    private fun updateEdgeDisplay() {
        processedBitmap?.let { bitmap ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = drawCurrentEdges(bitmap)
                    withContext(Dispatchers.Main) {
                        imageView.setImageBitmap(result)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "更新边缘显示失败", e)
                }
            }
        }
    }

    /**
     * 绘制当前边缘（不重新检测，只绘制当前角点）
     */
    private fun drawCurrentEdges(bitmap: Bitmap): Bitmap {
        val srcMat = Mat()
        val resultMat = Mat()

        try {
            Utils.bitmapToMat(bitmap, srcMat)
            srcMat.copyTo(resultMat)

            currentCorners?.let { corners ->
                // 绘制连接线
                for (i in corners.indices) {
                    val startPoint = corners[i]
                    val endPoint = corners[(i + 1) % corners.size]
                    Imgproc.line(resultMat, startPoint, endPoint, Scalar(0.0, 255.0, 0.0), 3)
                }

                // 绘制角点
                corners.forEach { point ->
                    Imgproc.circle(resultMat, point, 15, Scalar(255.0, 0.0, 0.0), -1)
                    Imgproc.circle(resultMat, point, 15, Scalar(255.0, 255.0, 255.0), 2)
                }
            }

            val resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultMat, resultBitmap)
            return resultBitmap

        } finally {
            srcMat.release()
            resultMat.release()
        }
    }

    private fun proceedToPerspectiveCorrection() {
        imagePath?.let { path ->
            currentCorners?.let { corners ->
                // 将角点坐标缩放到原图尺寸
                val originalCorners = corners.map { point ->
                    Point(point.x * scaleFactor, point.y * scaleFactor)
                }.toTypedArray()

                Log.d(TAG, "传递到透视矫正的角点: ${originalCorners.joinToString()}")

                // 通过回调传递数据给Activity
                listener?.onEdgeDetectionComplete(path, originalCorners)

                showToast("正在跳转到透视矫正...")
            } ?: run {
                showToast("请先完成边缘检测")
            }
        } ?: run {
            showToast("图片路径无效")
        }
    }

    // 提供给外部调用的方法，获取边缘数据
    fun getEdgeDetectionResult(): EdgeDetectionResult? {
        return imagePath?.let { path ->
            currentCorners?.let { corners ->
                val originalCorners = corners.map { point ->
                    Point(point.x * scaleFactor, point.y * scaleFactor)
                }.toTypedArray()
                EdgeDetectionResult(path, originalCorners)
            }
        }
    }

    data class EdgeDetectionResult(
        val imagePath: String,
        val documentCorners: Array<Point>
    )


    private fun resetCorners() {
        processedBitmap?.let { bitmap ->
            currentCorners = getDefaultCorners(bitmap.width, bitmap.height)
            processImageWithCurrentSettings()
        }
    }

    private fun showProgress(show: Boolean) {
        activity?.runOnUiThread {
            progressBar.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    private fun showToast(message: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
}