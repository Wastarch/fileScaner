package com.max.filescaner

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator


class ImageProcessingActivity : AppCompatActivity() {
    // 定义ViewPager2适配器
    private inner class ViewPagerAdapter(
        fragmentActivity: FragmentActivity,
        private val imagePath: String
    ) : FragmentStateAdapter(fragmentActivity) {

        // 返回Tab数量
        override fun getItemCount(): Int = 2

        // 根据位置创建对应Fragment
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> EdgeDetectionFragment.newInstance(imagePath)
                1 -> PerspectiveCorrectionFragment.newInstance(imagePath)
                else -> throw IllegalArgumentException("Invalid position")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_processing)

        val imagePath = intent.getStringExtra("IMAGE_PATH") ?: run {
            Toast.makeText(this, "图片路径无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 初始化ViewPager2和TabLayout
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)

        // 设置ViewPager2适配器
        viewPager.adapter = ViewPagerAdapter(this, imagePath)

        // 关联TabLayout和ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "边缘检测"
                1 -> "透视修正"
                else -> null
            }
        }.attach()
    }
}

