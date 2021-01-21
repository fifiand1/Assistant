package com.wzf.assistant

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.wzf.assistant.databinding.ActivityMainBinding
import com.wzf.daemon.DaemonEnv
import com.wzf.assistant.service.AccessibilityServiceJD
import com.wzf.assistant.service.SlaveService
import com.wzf.assistant.utils.goSettingForAccessibility
import com.wzf.assistant.utils.isAccessibilityServiceON
import com.wzf.assistant.utils.loge

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        loge("MainActivity onCreate ")

        binding.goAccessibility.setOnClickListener {
            goSettingForAccessibility(this)
        }

        DaemonEnv.initialize(
            this,
            SlaveService::class.java,
            DaemonEnv.DEFAULT_WAKE_UP_INTERVAL
        )
        SlaveService.sShouldStopService = false
        DaemonEnv.startServiceMayBind(SlaveService::class.java)
    }

    override fun onResume() {
        super.onResume()

        val isOn = isAccessibilityServiceON(this, AccessibilityServiceJD::class.java.name)

        loge("AccessibilityService is $isOn")

        if(isOn){
            binding.accessibilityState.text = resources.getString(R.string.accessibility_on)
            binding.goAccessibility.visibility = View.INVISIBLE
        }else{
            binding.goAccessibility.visibility = View.VISIBLE
            binding.accessibilityState.text = resources.getString(R.string.accessibility_off)
        }

    }
}
