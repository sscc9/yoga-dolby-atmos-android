package com.yoga.dolbyatmos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yoga.dolbyatmos.data.DolbyRepository
import com.yoga.dolbyatmos.model.ActiveEndpoint
import com.yoga.dolbyatmos.model.DolbyProfile
import com.yoga.dolbyatmos.model.DolbyUiState
import com.yoga.dolbyatmos.model.EndpointOverride
import com.yoga.dolbyatmos.model.IeqPreset
import com.yoga.dolbyatmos.model.YogaDolbyTuningData
import com.yoga.dolbyatmos.ui.theme.DolbyBgDeep
import com.yoga.dolbyatmos.ui.theme.DolbyBlue
import com.yoga.dolbyatmos.ui.theme.DolbyBorder
import com.yoga.dolbyatmos.ui.theme.DolbyCardActive
import com.yoga.dolbyatmos.ui.theme.DolbyCardBg
import com.yoga.dolbyatmos.ui.theme.DolbyCyan
import com.yoga.dolbyatmos.ui.theme.DolbyTextPrimary
import com.yoga.dolbyatmos.ui.theme.DolbyTextSecondary
import com.yoga.dolbyatmos.ui.theme.DolbyViolet
import java.util.Locale

@Composable
fun DolbyAccessScreen(
    state: DolbyUiState,
    onMasterToggle: (Boolean) -> Unit,
    onRebindDsp: () -> Unit = {}
) {
    var showEndpointMenu by remember { mutableStateOf(false) }

    val compositeCurve = remember(state) {
        YogaDolbyTuningData.computeComposite20BandDb(state, state.effectiveEndpoint)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DolbyBgDeep)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Header + Master Power Button
        item {
            HeaderSection(
                state = state,
                onMasterToggle = onMasterToggle
            )
        }

        // 2. Endpoint & Audio Session Status Bar (Headphone vs Speaker Auto-Switch)
        item {
            EndpointStatusCard(
                state = state,
                expanded = showEndpointMenu,
                onToggleExpand = { showEndpointMenu = !showEndpointMenu },
                onSelectOverride = {
                    DolbyRepository.setEndpointOverride(it)
                },
                onToggleGlobalMix = {
                    DolbyRepository.setGlobalMixFallback(it)
                },
                onRebindDsp = onRebindDsp
            )
        }

        // 3. Real-Time 20-Band Frequency Response Visualizer (47Hz - 19.7kHz)
        item {
            SpectrumCurveCard(
                state = state,
                curve20Db = compositeCurve
            )
        }

        // 4. 6 Dolby Access Scene Profile Cards
        item {
            ProfileGridSection(
                currentProfile = state.currentProfile,
                enabled = state.masterEnabled,
                onSelectProfile = { DolbyRepository.selectProfile(it) },
                onResetProfile = { DolbyRepository.resetCurrentProfileToFactory() }
            )
        }

        // 5. Intelligent Equalizer (IEQ Tone Target: Off / Balanced / Warm / Detailed)
        if (state.currentProfile != DolbyProfile.CUSTOM) {
            item {
                IeqToneCard(
                    state = state,
                    onSelectIeq = { preset ->
                        val nextAmt = if (preset != IeqPreset.OFF && state.ieqAmount < 1f) 7f else state.ieqAmount
                        DolbyRepository.setIeq(preset, nextAmt)
                    },
                    onAmountChange = { amt ->
                        DolbyRepository.setIeq(state.ieqPreset, amt)
                    }
                )
            }
        }

        // 6. 10-Band Interactive Graphic Equalizer (Always shown in CUSTOM, optional overlay in presets)
        item {
            GraphicEqualizerCard(
                state = state,
                onSelectSlot = { DolbyRepository.selectCustomSlot(it) },
                onToggleOverlayInPreset = { DolbyRepository.setCustomEqEnabledInPreset(it) },
                onBandChange = { idx, db -> DolbyRepository.updateCustomBand(idx, db) },
                onFlatten = { DolbyRepository.flattenCurrentCustomSlot() }
            )
        }

        // 7. Four Core Dolby Acoustic Enhancement Modules
        item {
            EnhancementModulesSection(state = state)
        }
    }
}

@Composable
private fun HeaderSection(
    state: DolbyUiState,
    onMasterToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF141836), Color(0xFF1A1238), Color(0xFF0E233A))
                )
            )
            .border(1.dp, DolbyBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "DOLBY ATMOS",
                    color = DolbyTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.6.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DolbyCyan.copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "YOGA 83BU · 1:1原厂",
                        color = DolbyCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (state.masterEnabled) {
                    "电脑 XML 参数零删改直通 · ${state.currentProfile.titleCn}模式"
                } else {
                    "音效引擎已关闭 · 系统原生直通输出"
                },
                color = if (state.masterEnabled) DolbyCyan else DolbyTextSecondary,
                fontSize = 12.sp
            )
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (state.masterEnabled) {
                        Brush.linearGradient(listOf(DolbyCyan, DolbyBlue))
                    } else {
                        Brush.linearGradient(listOf(Color(0xFF252A42), Color(0xFF1B1F33)))
                    }
                )
                .clickable { onMasterToggle(!state.masterEnabled) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PowerSettingsNew,
                contentDescription = "总开关",
                tint = if (state.masterEnabled) Color(0xFF04131D) else DolbyTextSecondary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun EndpointStatusCard(
    state: DolbyUiState,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectOverride: (EndpointOverride) -> Unit,
    onToggleGlobalMix: (Boolean) -> Unit,
    onRebindDsp: () -> Unit
) {
    val isHeadphone = state.effectiveEndpoint == ActiveEndpoint.HEADPHONE
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DolbyCardBg)
            .border(1.dp, DolbyBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isHeadphone) DolbyCyan.copy(alpha = 0.15f)
                            else DolbyViolet.copy(alpha = 0.18f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isHeadphone) Icons.Rounded.Headphones else Icons.Rounded.Speaker,
                        contentDescription = null,
                        tint = if (isHeadphone) DolbyCyan else Color(0xFFB388FF),
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${state.effectiveEndpoint.labelCn} · ${state.endpointOverride.labelCn}",
                        color = DolbyTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${state.connectedDeviceName} · ${state.attachedSessionSummary}",
                        color = DolbyTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = if (expanded) "收起" else "切换 ▾",
                color = DolbyCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = "输出声学模型（1:1 复刻电脑 Headphone Atmos 3.7 与 ALC287 扬声器 XML）：",
                    color = DolbyTextSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EndpointOverride.entries.forEach { mode ->
                        val selected = state.endpointOverride == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) DolbyCardActive else Color(0xFF0B0E1D))
                                .border(
                                    width = 1.dp,
                                    color = if (selected) DolbyCyan else DolbyBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectOverride(mode) }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.labelCn,
                                color = if (selected) DolbyCyan else DolbyTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "全局混音兜底（Session 0 自动防重）",
                            color = DolbyTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "无独立播放器会话时自动挂载全局输出，有播放器会话时自动避让防双重叠加",
                            color = DolbyTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = state.globalMixFallback,
                        onCheckedChange = onToggleGlobalMix,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF04131D),
                            checkedTrackColor = DolbyCyan
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B1324))
                        .border(1.dp, DolbyBorder, RoundedCornerShape(10.dp))
                        .clickable { onRebindDsp() }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "重新扫描并刷新音频硬件挂载",
                        color = DolbyCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SpectrumCurveCard(
    state: DolbyUiState,
    curve20Db: FloatArray
) {
    val spec = remember(state.effectiveEndpoint, state.currentProfile) {
        YogaDolbyTuningData.getFactorySpec(state.effectiveEndpoint, state.currentProfile)
    }
    val gainBadge = buildString {
        append(String.format(Locale.US, "VolMax %+.1fdB", spec.volmaxBoostDb))
        if (spec.vlldpMbcEnabled) {
            append(String.format(Locale.US, " · MBC %+.1fdB", spec.vlldpMbcHighGroupMakeupDb))
        }
        if (spec.slidingBassEnabled) {
            append(" · 动态低音+4dB")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DolbyCardBg)
            .border(1.dp, DolbyBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "20 频段原厂响应曲线",
                color = DolbyTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = gainBadge,
                color = DolbyCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
        ) {
            val w = size.width
            val h = size.height
            val midY = h / 2f

            // Horizontal reference lines (+6dB, 0dB, -6dB)
            val gridLines = listOf(0.25f, 0.5f, 0.75f)
            gridLines.forEach { frac ->
                val y = h * frac
                drawLine(
                    color = if (frac == 0.5f) Color(0xFF323C6B) else Color(0xFF1C2240),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = if (frac == 0.5f) 1.5f else 1f
                )
            }

            if (curve20Db.isEmpty()) return@Canvas

            val points = curve20Db.mapIndexed { idx, db ->
                val x = (idx.toFloat() / (curve20Db.size - 1).coerceAtLeast(1)) * w
                val norm = (db.coerceIn(-12f, 12f) / 12f)
                val y = midY - norm * (h * 0.44f)
                Offset(x, y)
            }

            val strokePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val cx = (p0.x + p1.x) / 2f
                    cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }
            }

            val fillPath = Path().apply {
                addPath(strokePath)
                lineTo(points.last().x, h)
                lineTo(points.first().x, h)
                close()
            }

            val activeAlpha = if (state.masterEnabled) 1f else 0.3f

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DolbyCyan.copy(alpha = 0.34f * activeAlpha),
                        DolbyViolet.copy(alpha = 0.12f * activeAlpha),
                        Color.Transparent
                    )
                )
            )

            drawPath(
                path = strokePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        DolbyCyan.copy(alpha = activeAlpha),
                        DolbyBlue.copy(alpha = activeAlpha),
                        DolbyViolet.copy(alpha = activeAlpha)
                    )
                ),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            points.forEachIndexed { index, pt ->
                if (index % 2 == 0 || index == points.lastIndex) {
                    drawCircle(
                        color = DolbyCyan.copy(alpha = 0.9f * activeAlpha),
                        radius = 2.6.dp.toPx(),
                        center = pt
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("47Hz", "234Hz", "1kHz", "3.7kHz", "9kHz", "19.7kHz").forEach { lbl ->
                Text(text = lbl, color = DolbyTextSecondary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ProfileGridSection(
    currentProfile: DolbyProfile,
    enabled: Boolean,
    onSelectProfile: (DolbyProfile) -> Unit,
    onResetProfile: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "场景模式（源自电脑 Dolby Access）",
                color = DolbyTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onResetProfile() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = "重置当前模式",
                    tint = DolbyTextSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "恢复默认参数",
                    color = DolbyTextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        val rows = DolbyProfile.entries.chunked(3)
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { profile ->
                    val selected = profile == currentProfile
                    ProfileCardItem(
                        modifier = Modifier.weight(1f),
                        profile = profile,
                        selected = selected,
                        enabled = enabled,
                        onClick = { onSelectProfile(profile) }
                    )
                }
            }
        }

        Text(
            text = currentProfile.subtitleCn,
            color = DolbyTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
private fun ProfileCardItem(
    modifier: Modifier = Modifier,
    profile: DolbyProfile,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val icon: ImageVector = when (profile) {
        DolbyProfile.DYNAMIC -> Icons.Rounded.AutoAwesome
        DolbyProfile.MOVIE -> Icons.Rounded.Movie
        DolbyProfile.MUSIC -> Icons.Rounded.MusicNote
        DolbyProfile.GAME -> Icons.Rounded.SportsEsports
        DolbyProfile.VOICE -> Icons.Rounded.RecordVoiceOver
        DolbyProfile.CUSTOM -> Icons.Rounded.Tune
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) {
                    Brush.verticalGradient(listOf(DolbyCardActive, Color(0xFF141936)))
                } else {
                    Brush.verticalGradient(listOf(DolbyCardBg, DolbyCardBg))
                }
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected && enabled) DolbyCyan else DolbyBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = profile.titleCn,
            tint = if (selected && enabled) DolbyCyan else DolbyTextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = profile.titleCn,
            color = if (selected) DolbyTextPrimary else DolbyTextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun IeqToneCard(
    state: DolbyUiState,
    onSelectIeq: (IeqPreset) -> Unit,
    onAmountChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DolbyCardBg)
            .border(1.dp, DolbyBorder, RoundedCornerShape(16.dp))
            .padding(15.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "智能均衡器音色（IEQ 原厂 20 段靶向曲线）",
                color = DolbyTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = state.ieqPreset.labelCn,
                color = DolbyCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IeqPreset.entries.forEach { preset ->
                val selected = state.ieqPreset == preset
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) DolbyCardActive else Color(0xFF0A0D1B))
                        .border(
                            1.dp,
                            if (selected) DolbyCyan else DolbyBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectIeq(preset) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = preset.labelCn,
                        color = if (selected) DolbyCyan else DolbyTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        if (state.ieqPreset != IeqPreset.OFF) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "音色强度",
                    color = DolbyTextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.width(56.dp)
                )
                Slider(
                    value = state.ieqAmount.coerceIn(1f, 10f),
                    onValueChange = onAmountChange,
                    valueRange = 1f..10f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = DolbyCyan,
                        activeTrackColor = DolbyCyan,
                        inactiveTrackColor = Color(0xFF232946)
                    )
                )
                Text(
                    text = String.format(Locale.US, "%.0f", state.ieqAmount),
                    color = DolbyTextPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier.width(26.dp)
                )
            }
        }
    }
}

@Composable
private fun GraphicEqualizerCard(
    state: DolbyUiState,
    onSelectSlot: (Int) -> Unit,
    onToggleOverlayInPreset: (Boolean) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onFlatten: () -> Unit
) {
    val isCustomProfile = state.currentProfile == DolbyProfile.CUSTOM
    val active = isCustomProfile || state.customEqEnabledInPreset

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DolbyCardBg)
            .border(1.dp, DolbyBorder, RoundedCornerShape(16.dp))
            .padding(15.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "10 段自定义图形均衡器",
                    color = DolbyTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isCustomProfile) {
                        "上下滑动柱条调节（自动插值映射至底层 20 段滤波器）"
                    } else {
                        "可叠加在当前场景预设之上微调频响"
                    },
                    color = DolbyTextSecondary,
                    fontSize = 11.sp
                )
            }

            if (!isCustomProfile) {
                Switch(
                    checked = state.customEqEnabledInPreset,
                    onCheckedChange = onToggleOverlayInPreset,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF04131D),
                        checkedTrackColor = DolbyCyan
                    )
                )
            } else {
                Text(
                    text = "归零平直",
                    color = DolbyCyan,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onFlatten() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        AnimatedVisibility(visible = active) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("自定义 1", "自定义 2", "自定义 3").forEachIndexed { idx, name ->
                        val selected = state.customSlotIndex == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) DolbyCardActive else Color(0xFF0A0D1B))
                                .border(
                                    1.dp,
                                    if (selected) DolbyViolet else DolbyBorder,
                                    RoundedCornerShape(9.dp)
                                )
                                .clickable { onSelectSlot(idx) }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name,
                                color = if (selected) Color(0xFFD1B3FF) else DolbyTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    if (!isCustomProfile) {
                        Text(
                            text = "归零",
                            color = DolbyCyan,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onFlatten() }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val bands = state.activeCustomBands
                    YogaDolbyTuningData.UI_GEQ_LABELS.forEachIndexed { idx, freqLabel ->
                        val gain = bands.getOrElse(idx) { 0f }
                        VerticalEqBar(
                            modifier = Modifier.weight(1f),
                            gainDb = gain,
                            freqLabel = freqLabel,
                            onGainChange = { onBandChange(idx, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalEqBar(
    modifier: Modifier = Modifier,
    gainDb: Float,
    freqLabel: String,
    onGainChange: (Float) -> Unit
) {
    val currentOnGainChange by rememberUpdatedState(onGainChange)

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = String.format(Locale.US, "%+.1f", gainDb),
            color = if (gainDb != 0f) DolbyCyan else DolbyTextSecondary,
            fontSize = 9.sp,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val norm = 1f - (offset.y / h).coerceIn(0f, 1f)
                        val db = (norm * 24f) - 12f
                        currentOnGainChange((kotlin.math.round(db * 2f) / 2f).coerceIn(-12f, 12f))
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val norm = 1f - (change.position.y / h).coerceIn(0f, 1f)
                        val db = (norm * 24f) - 12f
                        currentOnGainChange((kotlin.math.round(db * 2f) / 2f).coerceIn(-12f, 12f))
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val h = size.height
                val midY = h / 2f
                val trackW = 6.dp.toPx()

                // Background rail
                drawRoundRect(
                    color = Color(0xFF1E2442),
                    topLeft = Offset(cx - trackW / 2f, 0f),
                    size = Size(trackW, h),
                    cornerRadius = CornerRadius(trackW / 2f, trackW / 2f)
                )

                // Center 0 dB tick
                drawLine(
                    color = Color(0xFF475385),
                    start = Offset(cx - 7.dp.toPx(), midY),
                    end = Offset(cx + 7.dp.toPx(), midY),
                    strokeWidth = 1.2.dp.toPx()
                )

                val thumbY = midY - (gainDb.coerceIn(-12f, 12f) / 12f) * (h * 0.46f)
                val barTop = minOf(midY, thumbY)
                val barHeight = kotlin.math.abs(thumbY - midY).coerceAtLeast(2f)

                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(DolbyCyan, DolbyViolet)),
                    topLeft = Offset(cx - trackW / 2f, barTop),
                    size = Size(trackW, barHeight),
                    cornerRadius = CornerRadius(trackW / 2f, trackW / 2f)
                )

                drawCircle(
                    color = DolbyCyan,
                    radius = 6.5.dp.toPx(),
                    center = Offset(cx, thumbY)
                )
                drawCircle(
                    color = Color(0xFF081022),
                    radius = 2.8.dp.toPx(),
                    center = Offset(cx, thumbY)
                )
            }
        }

        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = freqLabel,
            color = DolbyTextSecondary,
            fontSize = 9.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun EnhancementModulesSection(state: DolbyUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "杜比核心声学增强组件",
            color = DolbyTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        DspModuleCard(
            icon = Icons.Rounded.SurroundSound,
            title = "环绕声虚拟器（Surround Virtualizer）",
            desc = "对应 XML surround-decoder + surround-boost（游戏=0基础解码，动态/电影=6 [+6dB]）",
            enabled = state.surroundEnabled,
            amount = state.surroundAmount,
            onToggle = { DolbyRepository.setSurround(it) },
            onAmountChange = { DolbyRepository.setSurround(true, it) }
        )

        DspModuleCard(
            icon = Icons.Rounded.RecordVoiceOver,
            title = "对白增强器（Dialog Enhancer）",
            desc = "对应 XML dialog-enhancer-amount（动态/电影=5，语音=8/10，无低频抽吸裁剪）",
            enabled = state.dialogEnhancerEnabled,
            amount = state.dialogEnhancerAmount,
            onToggle = { DolbyRepository.setDialogEnhancer(it) },
            onAmountChange = { DolbyRepository.setDialogEnhancer(true, it) }
        )

        DspModuleCard(
            icon = Icons.Rounded.VolumeUp,
            title = "音量均衡器（Volume Leveler）",
            desc = "对应 XML volume-leveler（靶向 -20dBFS 提拉小声细节，配合 VolMax +4/+6dB 响度增益）",
            enabled = state.volumeLevelerEnabled,
            amount = state.volumeLevelerAmount,
            onToggle = { DolbyRepository.setVolumeLeveler(it) },
            onAmountChange = { DolbyRepository.setVolumeLeveler(true, it) }
        )

        DspModuleCard(
            icon = Icons.Rounded.GraphicEq,
            title = "超重低音增强（Bass Enhancer）",
            desc = if (state.effectiveEndpoint == ActiveEndpoint.HEADPHONE) {
                "电脑原厂默认关闭（开启时按 XML 施加 100Hz 频点最高 +9.0dB 低音推进）"
            } else {
                "电脑原厂默认关闭（开启时按 XML 施加 200Hz 频点低音补偿与原厂限幅保护）"
            },
            enabled = state.bassEnhancerEnabled,
            amount = state.bassEnhancerAmount,
            onToggle = { DolbyRepository.setBassEnhancer(it) },
            onAmountChange = { DolbyRepository.setBassEnhancer(true, it) }
        )
    }
}

@Composable
private fun DspModuleCard(
    icon: ImageVector,
    title: String,
    desc: String,
    enabled: Boolean,
    amount: Float,
    onToggle: (Boolean) -> Unit,
    onAmountChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DolbyCardBg)
            .border(1.dp, DolbyBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) DolbyCyan else DolbyTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        color = DolbyTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = desc,
                        color = DolbyTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF04131D),
                    checkedTrackColor = DolbyCyan
                )
            )
        }

        AnimatedVisibility(visible = enabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "强度",
                    color = DolbyTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.width(36.dp)
                )
                Slider(
                    value = amount.coerceIn(0f, 10f),
                    onValueChange = onAmountChange,
                    valueRange = 0f..10f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = DolbyCyan,
                        activeTrackColor = DolbyCyan,
                        inactiveTrackColor = Color(0xFF232946)
                    )
                )
                Text(
                    text = String.format(Locale.US, "%.1f", amount.coerceIn(0f, 10f)),
                    color = DolbyCyan,
                    fontSize = 11.sp,
                    modifier = Modifier.width(30.dp)
                )
            }
        }
    }
}
