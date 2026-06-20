package com.neko.music.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.ui.components.AppPageBackgroundImage
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.isAppDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBackClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val isDarkTheme = isAppDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)
    val glassTint = LiquidGlassDefaults.screenListCard
    val glassBg = glassTint.background(isDarkTheme)
    val glassBorder = glassTint.border(isDarkTheme)
    val glassHighlight = glassTint.highlight(isDarkTheme)
    val textColor = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface
    val mutedColor = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.82f) else scheme.onSurfaceVariant

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop)
        ) {
            AppPageBackgroundImage(modifier = Modifier.fillMaxSize())
            PlaylistPageDarkTintOverlay(enabled = isDarkTheme)
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "隐私政策",
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "返回",
                                tint = mutedColor,
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://music.cnmsb.xin/privacy")
                                    )
                                )
                            }
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = RoseRed,
                            )
                            Text(text = "网页版本", color = RoseRed)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                )
            },
        ) { paddingValues ->
            CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PrivacyCard(glassBg, glassBorder, glassHighlight) {
                        Text(
                            text = "Neko歌姬计划隐私政策",
                            color = textColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PrivacyParagraph("发布日期：2026 年 6 月 20 日\n更新日期：2026 年 6 月 21 日\n生效日期：2026 年 6 月 21 日", mutedColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        PrivacyParagraph("Neko歌姬计划由 Neko歌姬计划运营方（Fantasy Network「梦幻网络」）提供音乐搜索、在线播放、收藏、歌单、上传、客户端播放、会员及相关服务。", mutedColor)
                    }

                    PrivacySection(
                        title = "一、我们如何收集和使用您的个人信息",
                        body = listOf(
                            "注册、登录、找回密码：用户名、昵称、头像、电子邮箱地址、密码或密码加密摘要、登录 token、账号状态，用于创建账号、验证身份、维持登录状态和保障账号安全。",
                            "音乐搜索、播放、下载：搜索关键词、歌曲 ID、播放和下载请求、播放进度、播放列表、播放模式、访问时间、IP 地址、设备类型、客户端版本和错误日志，用于提供播放服务、故障排查和安全维护。",
                            "收藏、歌单和歌单迁入：收藏记录、歌单名称、歌单歌曲、第三方歌单链接或 ID、从网易云音乐或 QQ 音乐等来源返回的歌单名称和曲目信息，用于按您的操作保存和迁入歌单。",
                            "音乐上传和头像上传：您主动上传的音乐文件、歌词、封面、头像图片、歌曲信息、上传记录和审核状态，用于保存、展示、审核和管理您主动提交的内容。",
                            "会员和支付：用户 ID、会员套餐、订单号、支付方式、支付状态、支付回调结果和订单时间，用于创建订单、确认支付结果、开通权益和处理售后。我们不会收集完整银行卡号或支付账户密码。",
                            "Android 本地音乐、桌面歌词、通知和更新：本地音频文件信息、文件名、音频元数据或文件路径、通知状态、更新包下载状态、桌面歌词开关和显示状态，用于扫描本地音乐、后台播放、通知、桌面歌词和客户端更新。"
                        ),
                        textColor = textColor,
                        mutedColor = mutedColor,
                        glassBg = glassBg,
                        glassBorder = glassBorder,
                        glassHighlight = glassHighlight,
                        emphasizeWords = listOf("电子邮箱地址", "密码或密码加密摘要", "IP 地址", "您主动上传的音乐文件、歌词、封面、头像图片", "订单号、支付方式、支付状态", "本地音频文件信息、文件名、音频元数据或文件路径"),
                    )

                    PrivacySection(
                        title = "二、权限调用说明",
                        body = listOf(
                            "网络访问、网络状态：用于连接服务器，完成登录、搜索、播放、下载、上传、支付和更新检查。",
                            "通知权限：用于展示播放状态、下载或更新状态等必要通知。",
                            "前台服务、媒体播放前台服务、唤醒锁：用于在锁屏、后台或切换应用时维持音乐播放和媒体控制。",
                            "读取音频媒体或外部存储：仅在您使用本地音乐、文件选择或上传功能时，用于扫描、选择和播放本地音乐。",
                            "悬浮窗权限：仅在您主动开启桌面歌词或悬浮显示时，用于在其他应用上方显示歌词或控件。",
                            "安装未知应用、FileProvider 临时读取：仅在您下载并确认安装客户端更新时，用于调起系统安装流程。",
                            "查询微信、支付宝应用及相关 URL Scheme：仅在您选择对应支付方式时，用于判断是否可拉起支付应用或浏览器支付页面。"
                        ),
                        textColor = textColor,
                        mutedColor = mutedColor,
                        glassBg = glassBg,
                        glassBorder = glassBorder,
                        glassHighlight = glassHighlight,
                    )

                    PrivacySection(
                        title = "三、Cookie、本地存储及同类技术",
                        body = listOf(
                            "我们可能使用本地存储保存登录 token、用户基础资料、当前播放歌曲、播放队列、播放进度、播放模式、搜索状态、主题语言设置、缓存开关、桌面歌词设置和管理员登录状态等必要信息。",
                            "您可以通过系统设置清除应用数据。清除后，您可能需要重新登录，播放队列、偏好设置或页面状态也可能被重置。"
                        ),
                        textColor = textColor,
                        mutedColor = mutedColor,
                        glassBg = glassBg,
                        glassBorder = glassBorder,
                        glassHighlight = glassHighlight,
                    )

                    PrivacySection(
                        title = "四、委托处理、共享、转让和公开披露",
                        body = listOf(
                            "我们不会出售您的个人信息。为了实现特定功能，我们可能在必要范围内向支付服务、邮件或验证码服务、网易云音乐/QQ 音乐等歌单来源、文件存储/CDN/下载服务、应用商店或审核平台提供必要信息。",
                            "除取得您的明确同意、依法需要、保护用户或公众重大合法权益、公司合并分立或资产转让等法律允许情形外，我们不会转让或公开披露您的个人信息。"
                        ),
                        textColor = textColor,
                        mutedColor = mutedColor,
                        glassBg = glassBg,
                        glassBorder = glassBorder,
                        glassHighlight = glassHighlight,
                    )

                    PrivacySection(
                        title = "五、存储、保护和保存期限",
                        body = listOf(
                            "我们在中华人民共和国境内收集和产生的个人信息将存储在中华人民共和国境内。",
                            "账号资料、登录凭据和会员状态在账号存续期间保存；收藏、歌单、上传内容和审核记录保存至您主动删除、账号注销或为处理争议、安全、版权和合规问题所需期限届满；网络安全日志通常保存不超过 3 年；本地播放状态和偏好主要保存在您的设备本地。",
                            "我们会采取访问权限控制、身份校验、敏感凭据加密或摘要存储、传输加密、日志审计、异常访问排查、备份和安全事件响应等措施保护您的个人信息。"
                        ),
                        textColor = textColor,
                        mutedColor = mutedColor,
                        glassBg = glassBg,
                        glassBorder = glassBorder,
                        glassHighlight = glassHighlight,
                    )

                    PrivacySection(
                        title = "六、您的权利",
                        body = listOf(
                            "您有权依法访问、更正、补充、复制、删除您的个人信息，也可以撤回授权同意、关闭系统权限、注销或删除账号、要求解释个人信息处理规则。",
                            "您可以在个人中心查看和修改部分账号资料；如需注销账号或删除账号相关数据，可通过邮箱联系我们。我们可能会在处理请求前验证您的身份，并会在 15 个工作日内或法律法规要求的期限内回复。"
                        ),
                        textColor = textColor,
                        mutedColor = mutedColor,
                        glassBg = glassBg,
                        glassBorder = glassBorder,
                        glassHighlight = glassHighlight,
                    )

                    PrivacySection(
                        title = "七、未成年人、政策更新与联系我们",
                        body = listOf(
                            "未满 14 周岁的未成年人使用本服务，应事先取得父母或其他监护人的同意。如果监护人发现未成年人信息在未取得同意的情况下被处理，请联系我们。",
                            "我们可能根据产品功能、法律法规或运营情况更新本政策。重大变化会通过弹窗、公告、站内提示、邮件或其他显著方式通知您，并在法律法规要求时重新取得同意。",
                            "联系方式：support@cnmsb.xin；官网：https://www.cnmsb.xin/；QQ群：https://qm.qq.com/q/Q9HkDi6Ewk。"
                        ),
                        textColor = textColor,
                        mutedColor = mutedColor,
                        glassBg = glassBg,
                        glassBorder = glassBorder,
                        glassHighlight = glassHighlight,
                    )

                    Spacer(modifier = Modifier.height(120.dp))
                }
            }
        }
    }
}

@Composable
private fun PrivacyCard(
    glassBg: Float,
    glassBorder: Float,
    glassHighlight: Float,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundAlpha = glassBg,
        borderAlpha = glassBorder,
        highlightAlpha = glassHighlight,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

@Composable
private fun PrivacySection(
    title: String,
    body: List<String>,
    textColor: Color,
    mutedColor: Color,
    glassBg: Float,
    glassBorder: Float,
    glassHighlight: Float,
    emphasizeWords: List<String> = emptyList(),
) {
    PrivacyCard(glassBg, glassBorder, glassHighlight) {
        Text(
            text = title,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        body.forEachIndexed { index, paragraph ->
            PrivacyParagraph(
                text = paragraph,
                color = mutedColor,
                emphasizeWords = emphasizeWords,
            )
            if (index != body.lastIndex) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PrivacyParagraph(
    text: String,
    color: Color,
    emphasizeWords: List<String> = emptyList(),
) {
    val annotated = buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            val match = emphasizeWords
                .mapNotNull { word ->
                    val index = text.indexOf(word, cursor)
                    if (index >= 0) index to word else null
                }
                .minByOrNull { it.first }

            if (match == null) {
                append(text.substring(cursor))
                break
            }

            val (index, word) = match
            if (index > cursor) {
                append(text.substring(cursor, index))
            }
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    color = Color(0xFFFFE082),
                )
            ) {
                append(word)
            }
            cursor = index + word.length
        }
    }

    Text(
        text = annotated,
        color = color,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    )
}
