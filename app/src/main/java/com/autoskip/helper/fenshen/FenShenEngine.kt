package com.autoskip.helper.fenshen

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 分身自动化引擎。逐条翻译自 Auto.js 脚本，逻辑保持一致。
 */
class FenShenEngine(private val context: Context) {

    private val _state = MutableStateFlow(FenShenState())
    val state: StateFlow<FenShenState> = _state.asStateFlow()

    private var config = FenShenConfig()
    private var floatConsole: FloatConsole? = null
    private var logBuilder: StringBuilder? = null

    private val service get() = FenShenAccessibilityService.instance
    private val root get() = service?.root()

    companion object {
        private const val TAG = "FenShen"
        const val TARGET_PKG = "com.qihoo.magic"
    }

    fun attachConsole(fc: FloatConsole) { floatConsole = fc }

    /** 日志输出 */
    private fun log(msg: String) {
        Log.i(TAG, msg)
        floatConsole?.appendLog(msg)
        _state.value = _state.value.copy(lastLog = msg)
        logBuilder?.append(msg).append("\n")
    }

    private fun updateStatus(s: String) {
        _state.value = _state.value.copy(statusText = s)
        floatConsole?.updateStatus(s)
    }

    /** 检查暂停/终止 */
    private suspend fun checkState() {
        if (_state.value.stopped) throw StopException()
        while (_state.value.paused && !_state.value.stopped) {
            delay(500)
        }
        if (_state.value.stopped) throw StopException()
    }

    class StopException : Exception("STOPPED")

    suspend fun run(cfg: FenShenConfig) {
        config = cfg
        val svc = service ?: throw IllegalStateException("无障碍服务未连接")
        if (logBuilder == null) logBuilder = StringBuilder()

        _state.value = FenShenState(running = true, statusText = "初始化...")
        log("任务开始")
        log("总数: ${cfg.totalCount}，后缀格式: ${cfg.suffixFmt}")

        var consecutiveFail = 0
        for (i in 0 until cfg.totalCount) {
            try { checkState() } catch (e: StopException) { log("脚本被用户终止"); break }

            _state.value = _state.value.copy(currentIndex = i + 1)
            updateStatus("进度 ${i + 1}/${cfg.totalCount} | 成功 ${_state.value.successCount} 失败 ${_state.value.failCount}")

            var ok = false
            val attempts = cfg.retryTimes + 1
            for (a in 0 until attempts) {
                if (a > 0) log("第 $a 次重试...")
                try {
                    ok = doOneTask()
                } catch (e: StopException) {
                    log("脚本被用户终止"); ok = false; break
                } catch (e: Exception) {
                    log("异常: ${e.message}"); ok = false
                }
                if (ok) break
                delay(2000)
            }

            if (ok) {
                _state.value = _state.value.copy(successCount = _state.value.successCount + 1)
                consecutiveFail = 0
                log("第 ${_state.value.currentIndex} 个完成")
            } else {
                _state.value = _state.value.copy(failCount = _state.value.failCount + 1)
                consecutiveFail++
                log("第 ${_state.value.currentIndex} 个失败（连续失败 $consecutiveFail）")
            }

            if (i < cfg.totalCount - 1) {
                try { clearAllTasks() } catch (_: Exception) {}
                delay(2000)
            } else {
                log("已是最后一个任务，跳过清理后台")
            }

            if (consecutiveFail >= cfg.maxFail) {
                log("连续失败 ${cfg.maxFail} 次，自动停止"); break
            }
            if (_state.value.stopped) break
        }

        log("任务结束")
        log("成功: ${_state.value.successCount} / 失败: ${_state.value.failCount}")
        updateStatus("完成 成功${_state.value.successCount} 失败${_state.value.failCount}")
        _state.value = _state.value.copy(running = false)
    }

    private fun buildSuffix(): String {
        val d = Date()
        return config.suffixFmt
            .replace("{date}", d.date.toString())
            .replace("{date2}", String.format("%02d", d.date))
            .replace("{time}", SimpleDateFormat("HHmm", Locale.getDefault()).format(d))
    }

    /** 弹窗字典 */
    private suspend fun handleDictPopups(): Boolean {
        val r = root ?: return false
        // id 优先
        NodeHelper.findById(r, "pzq")?.let { log("字典拦截: 跳过按钮(ID)"); NodeHelper.clickNode(it); delay(500); return true }
        NodeHelper.findByText(r, "跳过")?.let { log("字典拦截: 跳过按钮(文字)"); NodeHelper.clickNode(it); delay(500); return true }
        NodeHelper.findById(r, "cnq")?.let { log("字典拦截: 更新弹窗(ID)"); NodeHelper.clickNode(it); delay(500); return true }
        NodeHelper.findByText(r, "以后再说")?.let { log("字典拦截: 更新弹窗(文字)"); NodeHelper.clickNode(it); delay(500); return true }
        NodeHelper.findByText(r, "下次再说")?.let { log("字典拦截: 下次再说"); NodeHelper.clickNode(it); delay(500); return true }
        NodeHelper.findByDesc(r, "关闭")?.let { log("字典拦截: 关闭按钮(描述)"); NodeHelper.clickNode(it); delay(500); return true }
        NodeHelper.findById(r, "close")?.let { log("字典拦截: 关闭按钮(ID)"); NodeHelper.clickNode(it); delay(500); return true }
        NodeHelper.findByText(r, "取消")?.let { log("字典拦截: 取消按钮"); NodeHelper.clickNode(it); delay(500); return true }
        NodeHelper.findByText(r, "我知道了")?.let { log("字典拦截: 我知道了"); NodeHelper.clickNode(it); delay(500); return true }
        return false
    }

    private suspend fun handlePopups(): Boolean {
        val r = root ?: return false
        for (t in listOf("确定", "允许", "同意", "我知道了", "始终允许", "继续")) {
            val n = NodeHelper.findByText(r, t)
            if (n != null) { NodeHelper.clickNode(n); delay(500); return true }
        }
        return false
    }

    private suspend fun smartSwipe() {
        val svc = service ?: return
        val w = context.resources.displayMetrics.widthPixels.toFloat()
        val h = context.resources.displayMetrics.heightPixels.toFloat()
        svc.swipe(w / 2, h * 0.8f, w / 2, h * 0.3f, 400)
    }

    private suspend fun tap(x: Float, y: Float) {
        service?.tap(x, y)
    }

    private suspend fun waitFor(timeoutSec: Int, fn: () -> Boolean): Boolean {
        val max = timeoutSec * 1000 / 500
        for (i in 0 until max) {
            checkState()
            if (fn()) return true
            delay(500)
        }
        return false
    }

    private suspend fun clearAllTasks() {
        log("准备清理后台任务...")
        service?.globalRecents()
        delay(2000)
        val r = root
        val clearBtn = NodeHelper.findById(r, "clear_all")
            ?: NodeHelper.findByText(r, "全部关闭")
            ?: NodeHelper.findByText(r, "清除全部")
            ?: NodeHelper.findByText(r, "关闭全部")
        if (clearBtn != null) {
            log("找到全部关闭，点击清理")
            NodeHelper.clickNode(clearBtn)
            delay(3000)
            return
        }
        log("未找到清理按钮，回到桌面")
        service?.globalHome()
        delay(1500)
    }

    private suspend fun launchApp(pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun currentPkg(): String? {
        // 通过 root 节点的 packageName 判断
        return try { root?.packageName?.toString() } catch (e: Exception) { null }
    }

    private suspend fun doOneTask(): Boolean {
        val suffix = buildSuffix()
        log("当前后缀: $suffix")

        log("启动分身大师...")
        launchApp(TARGET_PKG)
        waitFor(10) { currentPkg() == TARGET_PKG }
        delay(3000)
        handlePopups()
        checkState()

        // 查找添加分身/再分一个
        log("查找添加分身/再分一个...")
        var addText: android.view.accessibility.AccessibilityNodeInfo? = null
        var reAdd: android.view.accessibility.AccessibilityNodeInfo? = null
        for (tryCount in 0 until 3) {
            checkState()
            val r = root
            addText = NodeHelper.findByText(r, "添加分身")
            if (addText != null) break
            reAdd = NodeHelper.findByText(r, "再分一个")
            if (reAdd != null) break
            if (tryCount < 2) {
                log("第 ${tryCount + 1} 次未找到按钮，返回")
                service?.globalBack()
                delay(1500)
                handlePopups()
            }
        }

        when {
            addText != null -> { NodeHelper.clickNode(addText); log("点击添加分身"); delay(1000) }
            reAdd != null -> { NodeHelper.clickNode(reAdd); log("点击再分一个"); delay(1000) }
            else -> {
                log("未找到添加按钮，盲点兜底")
                val w = context.resources.displayMetrics.widthPixels.toFloat()
                val h = context.resources.displayMetrics.heightPixels.toFloat()
                tap(w * 0.2f, h * 0.22f)
                delay(1000)
            }
        }
        checkState()

        // 查找抖音
        log("查找抖音...")
        handlePopups()
        var r = root
        var douyin = NodeHelper.findByTextMatches(r, Regex("^抖音$"))
        if (douyin == null) {
            val d = NodeHelper.findByText(r, "D")
            if (d != null) { NodeHelper.clickNode(d); delay(800) }
            r = root
            douyin = NodeHelper.findByTextMatches(r, Regex("^抖音$"))
        }
        if (douyin == null) { log("未找到抖音"); return false }

        val bounds = android.graphics.Rect()
        douyin.getBoundsInScreen(bounds)
        val targetY = bounds.centerY()
        val addBtns = NodeHelper.findAllByText(root, "添加")
        var best: android.view.accessibility.AccessibilityNodeInfo? = null
        var minDist = 99999
        for (b in addBtns) {
            val rb = android.graphics.Rect(); b.getBoundsInScreen(rb)
            val dist = Math.abs(rb.centerY() - targetY)
            if (dist < minDist && dist < 150) { minDist = dist; best = b }
        }
        if (best != null) NodeHelper.clickNode(best)
        else tap(bounds.right + 300f, targetY.toFloat())
        log("已点击抖音的添加")
        delay(2000)
        checkState()

        // 改名
        log("修改分身名称，追加后缀: $suffix")
        r = root
        val nameInput = NodeHelper.collectAll(r).firstOrNull {
            val cls = it.className?.toString() ?: ""
            cls.contains("EditText") && (it.text?.toString() ?: "").contains("抖音")
        }
        if (nameInput != null) {
            val oldName = nameInput.text?.toString() ?: ""
            if (!oldName.contains(suffix)) {
                val newName = oldName + suffix
                NodeHelper.setText(nameInput, newName)
                log("名称: $newName")
            } else log("名称已含后缀，跳过")
        } else log("未找到输入框，跳过改名")
        delay(1000)
        checkState()

        // 开始制作
        log("查找开始制作...")
        handlePopups()
        r = root
        val startBtn = NodeHelper.findById(r, "btn_start")
        if (startBtn != null) { NodeHelper.clickNode(startBtn); log("点击开始制作") }
        else {
            val tvOpen = NodeHelper.findById(r, "tv_open_disguise")
            if (tvOpen != null) { NodeHelper.clickNode(tvOpen); log("点击备用按钮") }
            else { log("未找到开始制作按钮"); return false }
        }
        delay(1500)
        checkState()

        // 等待安装弹窗
        log("等待系统安装弹窗（最多 ${config.installTimeoutSec} 秒）...")
        val isInstalled = waitFor(config.installTimeoutSec) {
            val rr = root
            NodeHelper.existsByText(rr, "要安装此应用吗？") ||
                ((currentPkg() ?: "").contains("packageinstaller") && NodeHelper.existsByText(rr, "安装"))
        }
        if (!isInstalled) { log("未检测到系统安装弹窗"); return false }
        log("检测到系统安装弹窗")

        val w = context.resources.displayMetrics.widthPixels.toFloat()
        val h = context.resources.displayMetrics.heightPixels.toFloat()
        val installX = w * 760f / config.testW
        val installY = h * 1620f / config.testH

        val warn = NodeHelper.findByText(root, "确定") ?: NodeHelper.findByText(root, "允许")
        if (warn != null) { NodeHelper.clickNode(warn); delay(500) }

        floatConsole?.setVisible(false)
        delay(800)
        log("坐标点击安装: ${installX.toInt()}, ${installY.toInt()}")
        tap(installX, installY)
        delay(2000)
        floatConsole?.setVisible(true)
        checkState()

        // 等待安装完成
        log("等待安装完成并出现打开按钮（最多 180 秒）...")
        var opened = false
        for (iw in 0 until 180) {
            checkState()
            val rr = root
            val installBtn = NodeHelper.findByText(rr, "安装")
            val openBtn = NodeHelper.findByText(rr, "打开") ?: NodeHelper.findByTextContains(rr, "打开")
            val doneBtn = NodeHelper.findByText(rr, "完成")
            if (installBtn == null && (openBtn != null || doneBtn != null)) {
                opened = true; log("检测到安装完成（已等待 $iw 秒）"); break
            }
            if (iw > 0 && iw % 15 == 0) log("已等待 $iw 秒，安装尚未完成...")
            delay(1000)
        }
        if (!opened) { log("等待安装完成超时（180 秒），跳过本次任务"); return false }

        // 点击打开
        log("安装完成，点击打开")
        val rr = root
        val openNode = NodeHelper.findByText(rr, "打开") ?: NodeHelper.findByTextContains(rr, "打开") ?: NodeHelper.findByText(rr, "完成")
        if (openNode != null) {
            floatConsole?.setVisible(false)
            delay(500)
            NodeHelper.clickNode(openNode)
            delay(1000)
            floatConsole?.setVisible(true)
        } else { log("未找到打开按钮节点，跳过"); return false }

        delay(2000)

        // 等待进入主界面
        val permX = w * 540f / config.testW
        val permY = h * 1465f / config.testH
        var waitCount = 0
        for (step in 0 until 180) {
            checkState()
            val r2 = root
            val agree = NodeHelper.findById(r2, "fj6")
            if (agree != null) { log("点击抖音同意(id=fj6)"); NodeHelper.clickNode(agree); delay(1000); break }

            val isMain = NodeHelper.findById(r2, "fj7") != null ||
                NodeHelper.existsByText(r2, "验证并登录") ||
                NodeHelper.existsByText(r2, "+86") ||
                NodeHelper.existsByText(r2, "首页") ||
                NodeHelper.existsByText(r2, "推荐")
            if (isMain) { log("已进入抖音主界面"); break }

            if (currentPkg() == TARGET_PKG) {
                waitCount++
                val openApp = NodeHelper.findById(r2, "rl_open_disguise") ?: NodeHelper.findByText(r2, "打开应用")
                if (openApp != null) {
                    log("点击打开应用")
                    NodeHelper.clickNode(openApp)
                    delay(3000); continue
                } else {
                    if (waitCount > 15 && waitCount % 5 == 0) tap(w / 2, h * 0.75f)
                    delay(2000); continue
                }
            }

            val btnAgree = NodeHelper.findByText(r2, "允许") ?: NodeHelper.findByText(r2, "始终允许") ?: NodeHelper.findByText(r2, "我知道了")
            if (btnAgree != null) { NodeHelper.clickNode(btnAgree); delay(1000); continue }

            if (currentPkg() != TARGET_PKG) { tap(permX, permY); delay(1000) }
            else delay(2000)
        }

        // 等待页面稳定
        log("等待抖音页面稳定...")
        for (ww in 0 until 120) {
            checkState()
            val r2 = root
            val agreeNow = NodeHelper.findById(r2, "fj6")
            if (agreeNow != null) { log("页面出现同意按钮，点击"); NodeHelper.clickNode(agreeNow); delay(1500); break }
            if (NodeHelper.existsByText(r2, "首页") || NodeHelper.existsByText(r2, "推荐") ||
                NodeHelper.existsByText(r2, "关注") || NodeHelper.existsByText(r2, "我") ||
                NodeHelper.findById(r2, "fj7") != null) { log("抖音页面已稳定"); break }
            delay(600)
        }
        delay(1500)

        // 等待发布者
        log("等待视频发布者(@发布者)出现（最多 120 秒）...")
        var pubLoaded = false
        var refreshCount = 0
        for (pw in 0 until 150) {
            checkState()
            if (handleDictPopups()) { delay(1000); continue }

            val r2 = root
            val agreeInWait = NodeHelper.findById(r2, "fj6") ?: NodeHelper.findByTextMatches(r2, Regex("^同意$"))
            if (agreeInWait != null) { log("等待期间检测到同意按钮，点击"); NodeHelper.clickNode(agreeInWait); delay(1500); continue }

            val netErr = NodeHelper.findByTextContains(r2, "网络错误") != null ||
                NodeHelper.findByTextContains(r2, "当前无网络") != null ||
                NodeHelper.findByTextContains(r2, "请检查后重试") != null ||
                NodeHelper.findByTextContains(r2, "网络异常") != null
            val refreshBtn = NodeHelper.findByText(r2, "刷新") ?: NodeHelper.findByTextContains(r2, "刷新")
            if (refreshBtn != null || netErr) {
                refreshCount++
                if (refreshCount <= 15) {
                    log("检测到网络错误/刷新，点击刷新（第 $refreshCount 次）")
                    if (refreshBtn != null) NodeHelper.clickNode(refreshBtn)
                    else tap(w / 2, h * 0.68f)
                    delay(2500); continue
                } else { log("已刷新 $refreshCount 次仍无网络，放弃本次"); break }
            }

            val pubNode = NodeHelper.findByTextStartsWith(r2, "@") ?: NodeHelper.findByTextContains(r2, "@") ?:
                NodeHelper.findByIdContains(r2, "user_name") ?: NodeHelper.findByIdContains(r2, "author") ?:
                NodeHelper.findByIdContains(r2, "publisher")
            if (pubNode != null) { pubLoaded = true; log("检测到发布者，视频流已加载（已等待 $pw 秒）"); break }

            if (pw > 0 && pw % 15 == 0) log("已等待发布者 $pw 秒，视频流仍未加载...")
            delay(800)
        }
        if (!pubLoaded) log("等待发布者超时，视频流可能未加载，仍继续滑动")
        delay(1000)

        // 滑动触发定位权限
        log("滑动触发定位权限弹窗...")
        for (s in 0 until 3) {
            checkState()
            log("第 ${s + 1} 次滑动")
            smartSwipe()
            delay(1500)
            handleDictPopups()
        }
        delay(1000)

        val locX = w * 523f / config.testW
        val locY = h * 1308f / config.testH
        log("坐标点击位置权限: ${locX.toInt()}, ${locY.toInt()}")
        delay(1500)
        tap(locX, locY)
        delay(2000)

        val locBtn = NodeHelper.findByTextMatches(root, Regex(".*仅在使用该应用时允许.*"))
        if (locBtn != null) { log("弹窗还在，补点一次"); tap(locX, locY); delay(1500) }

        log("位置权限流程结束")
        return true
    }

    fun requestStop() {
        _state.value = _state.value.copy(stopped = true, paused = false)
    }

    fun setPaused(p: Boolean) {
        _state.value = _state.value.copy(paused = p)
    }
}
