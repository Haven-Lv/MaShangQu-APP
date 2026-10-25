package com.aggapp.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aggapp.app.di.PlatformRegistry
import com.aggapp.app.di.SessionRepository
import com.aggapp.core.model.Credential
import com.aggapp.core.model.IdentityCode
import com.aggapp.core.model.Platform
import com.aggapp.core.model.PlatformError
import com.aggapp.platform.api.PlatformAdapter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IdCodeUiState(
    /** 各平台身份码 */
    val codes: Map<Platform, IdentityCode> = emptyMap(),
    /** 该平台身份码是真实拉取（true）还是 Mock 演示（false） */
    val isReal: Map<Platform, Boolean> = emptyMap(),
    val selected: Platform = Platform.SF,
    val countdownSec: Long? = null,
    val loading: Boolean = false,
    /** 淘宝账号在菜鸟侧无档案（waybill-5026）→ 弹「激活菜鸟驿站」引导弹窗 */
    val needCainiaoActivate: Boolean = false,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    registry: PlatformRegistry,
    private val session: SessionRepository,
) : ViewModel() {

    private val adapters: Map<Platform, PlatformAdapter> = registry.adapters

    /** 有真实登录态返回凭据，否则 null（UI 提示未登录，不做 Mock 兜底） */
    private fun credentialFor(p: Platform): Credential? = session.credentials.value[p]

    /** 上次全量刷新时间，ON_RESUME 自动刷新用 3 分钟节流（静默全平台拉取频率过高是风控信号） */
    private var lastRefreshAt = 0L

    /** 平台失败退避：连续失败后静默自动刷新跳过该平台（手动 force 刷新不受限），成功即清零 */
    private val failStreak = mutableMapOf<Platform, Int>()
    private val skipUntil = mutableMapOf<Platform, Long>()

    private fun onFetchFail(p: Platform) {
        val n = (failStreak[p] ?: 0) + 1
        failStreak[p] = n
        // 1 分钟 → 2 分钟 → 8 分钟 → 32 分钟 → 64 分钟，封顶 2 小时
        val backoffMs = when (n) {
            1 -> 60_000L
            else -> minOf(120_000L shl ((n - 2).coerceIn(0, 5)), 2 * 3_600_000L)
        }
        skipUntil[p] = System.currentTimeMillis() + backoffMs
        com.aggapp.platform.api.AppLog.w("IdCode", "${p.label} 连续失败 ${n} 次，自动刷新退避 ${backoffMs / 1000}s")
    }

    private fun onFetchOk(p: Platform) {
        failStreak.remove(p)
        skipUntil.remove(p)
    }

    /**
     * 全量刷新。force=true（下拉/单击刷新）强制执行并显示 loading；
     * 默认（ON_RESUME 自动刷新）3 分钟内节流跳过（原先 30s，频繁切屏即全平台重拉，频率偏高），
     * 已有缓存时静默刷新不闪 loading；失败退避中的平台静默跳过，不对报错接口反复请求。
     */
    fun refreshAll(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRefreshAt < 180_000) return
        lastRefreshAt = now
        val silent = !force && _state.value.codes.isNotEmpty()
        viewModelScope.launch {
            if (!silent) _state.value = _state.value.copy(loading = true)
            val fetched = mutableMapOf<Platform, IdentityCode>()
            val real = mutableMapOf<Platform, Boolean>()
            for ((p, adapter) in adapters) {
                val cred = credentialFor(p)
                if (cred == null) {
                    real[p] = false // 未登录：不拉码
                    continue
                }
                if (!force && now < (skipUntil[p] ?: 0L)) {
                    real[p] = _state.value.isReal[p] ?: false // 失败退避中：静默跳过，保留旧码
                    continue
                }
                try {
                    fetched[p] = adapter.fetchIdentityCode(cred)
                    real[p] = true
                    onFetchOk(p)
                } catch (e: PlatformError.NeedRelogin) {
                    real[p] = false // 凭据失效：不展示码，退避到下次静默刷新再查
                    onFetchFail(p)
                } catch (e: PlatformError.NeedActivate) {
                    real[p] = false
                    com.aggapp.platform.api.AppLog.w("IdCode", "${p.label} fetchIdentityCode: ${e.detail}")
                    onFetchFail(p)
                    if (p == Platform.TAOBAO) {
                        _state.value = _state.value.copy(needCainiaoActivate = true)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    onFetchFail(p)
                }
            }
            _state.value = if (silent) {
                // 静默刷新：按平台合并，拉取失败的平台保留旧码
                _state.value.copy(codes = _state.value.codes + fetched, isReal = real)
            } else {
                _state.value.copy(
                    codes = fetched,
                    isReal = real,
                    loading = false,
                    selected = _state.value.selected,
                )
            }
        }
    }

    private val _state = MutableStateFlow(IdCodeUiState())
    val state: StateFlow<IdCodeUiState> = _state.asStateFlow()

    init {
        session.restore()
        refreshAll(force = true)
        // 每秒 tick 更新倒计时；到期自动重取该平台身份码
        viewModelScope.launch {
            while (true) {
                delay(1000)
                tick()
            }
        }
    }

    fun select(platform: Platform) {
        _state.value = _state.value.copy(selected = platform)
    }

    /** 关闭「激活菜鸟驿站」引导弹窗 */
    fun dismissCainiaoActivate() {
        _state.value = _state.value.copy(needCainiaoActivate = false)
    }

    private fun tick() {
        val s = _state.value
        val selected = s.codes[s.selected] ?: return
        val remain = selected.expireAt?.let { (it - System.currentTimeMillis()) / 1000 }
        _state.value = s.copy(countdownSec = remain?.coerceAtLeast(0))
        if (remain != null && remain <= 0) {
            refreshOne(s.selected)
        }
    }

    /** 到期重取在途标记：tick 每秒触发，防止网络慢时并发叠加多次重取 */
    private val refreshing = mutableSetOf<Platform>()

    private fun refreshOne(platform: Platform) {
        if (!refreshing.add(platform)) return // 上一次到期重取尚未返回，本秒不叠加
        viewModelScope.launch {
            try {
                val cred = credentialFor(platform) ?: return@launch
                val code = adapters.getValue(platform).fetchIdentityCode(cred)
                onFetchOk(platform)
                _state.value = _state.value.copy(
                    codes = _state.value.codes + (platform to code),
                    isReal = _state.value.isReal + (platform to true),
                )
            } catch (e: PlatformError.NeedActivate) {
                com.aggapp.platform.api.AppLog.w("IdCode", "${platform.label} fetchIdentityCode: ${e.detail}")
                if (platform == Platform.TAOBAO) {
                    _state.value = _state.value.copy(needCainiaoActivate = true)
                }
                dropExpired(platform)
            } catch (e: Exception) {
                e.printStackTrace()
                dropExpired(platform)
            } finally {
                refreshing.remove(platform)
            }
        }
    }

    /**
     * 到期重取失败：移除过期旧码并记失败退避。
     * 旧逻辑失败后仍保留过期码，tick() 每秒都看到「已过期」再次发起重取——
     * 对持续报错的平台等于每秒打一次接口（请求风暴，风控高危），必须掐断。
     */
    private fun dropExpired(platform: Platform) {
        onFetchFail(platform)
        _state.value = _state.value.copy(
            codes = _state.value.codes - platform,
            isReal = _state.value.isReal + (platform to false),
            countdownSec = null,
        )
    }
}
