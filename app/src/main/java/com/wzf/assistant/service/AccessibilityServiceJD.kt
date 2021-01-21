package com.wzf.assistant.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.wzf.assistant.utils.loge

class AccessibilityServiceJD : AccessibilityService() {

    private val jdPackageName = "com.jingdong.app.mall"
    private val wxPackageName = "com.tencent.mm"

    override fun onInterrupt() {
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        loge("event : $event")
        when (rootInActiveWindow.packageName) {
            jdPackageName -> jingDongMaiTai(event)
            wxPackageName -> wxhb(event)
        }
    }

    private fun jingDongMaiTai(event: AccessibilityEvent) {

        val buyBtn =
            rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.jd.lib.productdetail.feature:id/add_2_car")
        if (buyBtn.size > 0) {
            buyBtn[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            loge("立即抢购->${buyBtn[0].text}")
        }
        val nodeInfo = event.source
        nodeInfo?.let {
            for (index in 0 until nodeInfo.childCount) {
                val indexNodeInfo = nodeInfo.getChild(index)
                loge("text : ${indexNodeInfo.text}")
                if ("立即抢购" == indexNodeInfo.text) {
                    indexNodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }

                if ("提交订单" == indexNodeInfo.text) {
                    indexNodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
        }
    }

    private fun wxhb(event: AccessibilityEvent) {

        val open =
            rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/den")
        if (open.size > 0) {
            open[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            loge("開->${open[0].contentDescription}")
        }

        val cover =
            rootInActiveWindow.findAccessibilityNodeInfosByText("微信红包")
        if (cover.size > 0) {
            loge("辣么多红包->${cover.size}")
            try {
                cover.forEachIndexed { index, it ->
                    val text = it.parent.getChild(1).text
                    if ("已领取" == text) {
                        loge("已领取 跳过这个 $index")
                        return@forEachIndexed
                    }
                    it.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
//                it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    loge("clicked :$index: ${it.text}")
                }
            } catch (e: Exception) {
            }

        }

    }
}
