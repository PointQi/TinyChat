package io.tinychat.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcTypography
import io.tinychat.model.KnownProviders
import io.tinychat.model.ModelInfo
import io.tinychat.model.Provider
import io.tinychat.model.ProviderCheck
import io.tinychat.model.ProviderKind
import io.tinychat.model.ProviderProtocol
import io.tinychat.model.ThinkingMode
import io.tinychat.ui.TcIconButton
import io.tinychat.ui.TcTopBar
import io.tinychat.designsystem.TcIcons
import kotlinx.coroutines.launch

/**
 * 服务商与 API Key（M8-6，docs/02 §12.6 重写版）。
 *
 * 三个原则：
 * 1. 一屏只有一个主按钮（保存），校验是**状态**不是动作；
 * 2. 删除密钥/删除服务商分开：前者只删本机凭据，后者才影响会话；
 * 3. 「清除」只在真的存了密钥时出现，并且写清对象。
 */
@Composable
fun ProviderListScreen(
    providers: List<Provider>,
    keyedProviders: Set<String>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Provider) -> Unit,
    onDelete: (Provider) -> Unit,
) {
    val palette = TcTheme.palette
    var pendingDelete by remember { mutableStateOf<Provider?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg),
    ) {
        TcTopBar(
            title = "服务商与 API Key",
            leading = { TcIconButton(TcIcons.Back, "返回", onBack) },
        )

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "填入各服务商的 API 密钥即可使用其模型。",
                style = TcTypography.labelMedium,
                color = palette.labelTertiary,
                modifier = Modifier.padding(bottom = 14.dp),
            )

            providers.forEach { provider ->
                ProviderCard(
                    provider = provider,
                    hasKey = provider.id in keyedProviders,
                    onEdit = { onEdit(provider) },
                    onDelete = if (provider.builtin) null else ({ pendingDelete = provider }),
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.borderL2, RoundedCornerShape(12.dp))
                    .clickable(onClick = onAdd)
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "＋ 添加模型提供商",
                    style = TcTypography.bodyMedium,
                    color = palette.labelSecondary,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    pendingDelete?.let { provider ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除 ${provider.displayName}？") },
            text = { Text("该服务商下的会话仍可查看，但无法继续对话。本机保存的密钥会一并删除。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(provider)
                }) { Text("删除", color = palette.danger) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ProviderCard(
    provider: Provider,
    hasKey: Boolean,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val palette = TcTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .border(1.dp, palette.borderL2, RoundedCornerShape(12.dp))
            .clickable(onClick = onEdit)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = provider.displayName,
            style = TcTypography.bodyLarge,
            color = palette.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        // 「自定义」只标用户自己填的服务商；目录里的 OpenCode Go 不算
        if (provider.kind == ProviderKind.OPENAI_COMPATIBLE) {
            Text(
                text = "自定义",
                fontSize = 11.sp,
                color = palette.labelTertiary,
                modifier = Modifier
                    .background(palette.bgLayer2, RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        StatusDot(provider = provider, hasKey = hasKey)
        Spacer(Modifier.weight(1f))
        Text(
            text = "编辑",
            style = TcTypography.bodyMedium,
            color = palette.labelSecondary,
            modifier = Modifier
                .clickable(onClick = onEdit)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
        if (onDelete != null) {
            Text(
                text = "删除",
                style = TcTypography.bodyMedium,
                color = palette.danger,
                modifier = Modifier
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun StatusDot(provider: Provider, hasKey: Boolean) {
    val palette = TcTheme.palette
    val color = when {
        !hasKey -> palette.labelTertiary
        provider.check == ProviderCheck.OK -> palette.brand
        provider.check == ProviderCheck.FAILED -> palette.danger
        else -> palette.labelTertiary
    }
    Box(
        Modifier
            .size(8.dp)
            .background(color, RoundedCornerShape(4.dp)),
    )
}

/**
 * 添加 / 编辑同一个页面。
 *
 * `provider == null` 是新增（两个 tab）；否则是编辑（直接表单，不可改 ID）。
 */
@Composable
fun ProviderEditorScreen(
    provider: Provider?,
    existing: List<Provider>,
    hasKey: Boolean,
    onBack: () -> Unit,
    onSave: suspend (Provider, String?) -> Unit,
    onCheck: suspend (Provider, String?) -> String?,
    onClearKey: suspend (String) -> Unit,
    onFetchModels: suspend (Provider, String?) -> List<String>,
) {
    val palette = TcTheme.palette
    val scope = rememberCoroutineScope()
    val adding = provider == null
    val catalogEntries = remember(existing) {
        KnownProviders.catalog.filter { item -> existing.none { it.id == item.id } }
    }

    var tab by remember { mutableStateOf(EditorTab.CATALOG) }
    var pickedCatalog by remember { mutableStateOf<Provider?>(null) }
    var id by remember { mutableStateOf(provider?.id.orEmpty()) }
    var name by remember { mutableStateOf(provider?.displayName.orEmpty()) }
    var baseUrl by remember { mutableStateOf(provider?.baseUrl.orEmpty()) }
    var protocol by remember { mutableStateOf(provider?.protocol ?: ProviderProtocol.OPENAI_CHAT) }
    var models by remember { mutableStateOf(provider?.models.orEmpty()) }
    var key by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var fetching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember {
        mutableStateOf(
            when (provider?.check) {
                ProviderCheck.OK -> "可用"
                ProviderCheck.FAILED -> provider.checkMessage ?: "校验失败"
                else -> null
            },
        )
    }
    var confirmClear by remember { mutableStateOf(false) }

    val builtin = provider?.builtin == true
    val editableCustom = adding && tab == EditorTab.CUSTOM || (provider != null && provider.kind == ProviderKind.OPENAI_COMPATIBLE)
    val catalogMode = adding && tab == EditorTab.CATALOG
    val effective = pickedCatalog ?: provider
    // 模型始终以这一份可变状态为准（选目录服务商时已把它的模型灌进来）；
    // 第一版目录模式读的是 pickedCatalog.models，导致「拉取成功但界面不更新」
    val currentModels = models
    val canSave = when {
        busy -> false
        catalogMode -> pickedCatalog != null && (key.isNotBlank() || pickedCatalog?.id in existing.map { it.id })
        else -> name.isNotBlank() && baseUrl.isNotBlank() && currentModels.isNotEmpty() &&
            (provider != null || id.matches(ID_REGEX))
    }

    fun draftProvider(): Provider {
        val base = effective
        return (base ?: Provider(
            id = id,
            kind = ProviderKind.OPENAI_COMPATIBLE,
            displayName = name,
            baseUrl = baseUrl,
            models = models,
            protocol = protocol,
        )).copy(
            id = if (adding) id else provider!!.id,
            displayName = name,
            baseUrl = baseUrl,
            protocol = protocol,
            // 显示名留空 = 同 ID（DSH 的口径），省得列表里出现空行
            models = currentModels.map { it.copy(displayName = it.displayName.ifBlank { it.id }) },
            builtin = provider?.builtin ?: false,
            check = provider?.check ?: ProviderCheck.UNKNOWN,
            checkMessage = provider?.checkMessage,
        )
    }

    fun save() {
        val candidate = draftProvider()
        val localError = when {
            candidate.displayName.isBlank() -> "请填写显示名称"
            candidate.baseUrl.isBlank() -> "请填写 API 地址"
            !candidate.baseUrl.startsWith("https://") -> "API 地址需要以 https:// 开头"
            candidate.models.isEmpty() -> "至少添加一个模型"
            !adding -> null
            catalogMode -> null
            existing.any { it.id == candidate.id } -> "Provider ID 已被占用"
            !candidate.id.matches(ID_REGEX) -> "Provider ID 需要以小写字母开头"
            else -> null
        }
        if (localError != null) {
            error = localError
            return
        }
        scope.launch {
            busy = true
            error = null
            runCatching {
                onSave(candidate, key.trim().takeIf { it.isNotEmpty() })
                onBack()
            }.onFailure { error = it.message ?: "保存失败" }
            busy = false
        }
    }

    fun recheck() {
        val candidate = draftProvider()
        scope.launch {
            busy = true
            status = "校验中…"
            val message = runCatching { onCheck(candidate, key.trim().takeIf { it.isNotEmpty() }) }
                .getOrElse { it.message ?: "校验失败" }
            status = message ?: "可用"
            busy = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg),
    ) {
        TcTopBar(
            title = provider?.displayName ?: "添加模型提供商",
            leading = { TcIconButton(TcIcons.Back, "返回", onBack) },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            if (adding) {
                Spacer(Modifier.height(4.dp))
                EditorTabs(tab = tab, onSelect = { tab = it })
            }

            Spacer(Modifier.height(14.dp))

            if (catalogMode) {
                Text(
                    "从内置目录中选择 DeepSeek 或 OpenCode Go，填入其 API 密钥即可使用。",
                    style = TcTypography.labelMedium,
                    color = palette.labelTertiary,
                )
                Spacer(Modifier.height(12.dp))
                if (catalogEntries.isEmpty()) {
                    Text(
                        "目录里的服务商都已添加。要使用其他服务商，请选择「自定义模型 API」。",
                        style = TcTypography.bodyMedium,
                        color = palette.labelSecondary,
                    )
                }
                catalogEntries.forEach { item ->
                    CatalogRow(
                        provider = item,
                        picked = pickedCatalog?.id == item.id,
                        onClick = {
                            pickedCatalog = item
                            id = item.id
                            name = item.displayName
                            baseUrl = item.baseUrl
                            protocol = item.protocol
                            models = item.models
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            // 目录 tab 选中之后也要露出密钥与模型（否则只能选不能填）
            if (!catalogMode || pickedCatalog != null) {
                if (editableCustom) {
                    EditorField(
                        label = "Provider ID",
                        value = id,
                        enabled = adding,
                        placeholder = "acme-gateway",
                        onValueChange = { id = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(32) },
                    )
                    if (adding) {
                        Text(
                            "以小写字母开头的标识，用于在本机唯一标识该服务商。",
                            style = TcTypography.labelMedium,
                            color = palette.labelTertiary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                EditorField(
                    label = "显示名称",
                    value = name,
                    enabled = editableCustom,
                    placeholder = "例如：我的网关",
                    onValueChange = { name = it.take(24) },
                )
                Spacer(Modifier.height(16.dp))

                EditorField(
                    label = "API 地址",
                    value = baseUrl,
                    enabled = editableCustom,
                    placeholder = "https://gateway.example/v1",
                    onValueChange = { baseUrl = it.trim() },
                )
                Spacer(Modifier.height(16.dp))

                if (editableCustom || builtin) {
                    ProtocolPicker(
                        picked = protocol,
                        enabled = !builtin,
                        onSelect = { protocol = it },
                    )
                } else {
                    ReadOnlyField("API 协议", protocol.title())
                }
                Spacer(Modifier.height(16.dp))

                EditorField(
                    label = "API 密钥",
                    value = key,
                    enabled = !busy,
                    placeholder = if (hasKey) "留空使用已保存的凭据" else "输入 API 密钥",
                    secret = !reveal,
                    onValueChange = { key = it.trim() },
                    trailing = {
                        Text(
                            text = if (reveal) "隐藏" else "显示",
                            style = TcTypography.bodyMedium,
                            color = palette.labelSecondary,
                            modifier = Modifier
                                .clickable { reveal = !reveal }
                                .padding(start = 10.dp),
                        )
                    },
                )

                if (provider != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = status ?: "未校验",
                            style = TcTypography.labelMedium,
                            color = when {
                                status == null -> palette.labelTertiary
                                status == "校验中…" -> palette.labelSecondary
                                status in OK_WORDS -> palette.brand
                                else -> palette.danger
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (hasKey) {
                            Text(
                                text = "重新校验",
                                style = TcTypography.labelMedium,
                                color = palette.labelSecondary,
                                modifier = Modifier
                                    .clickable(enabled = !busy) { recheck() }
                                    .padding(6.dp),
                            )
                        }
                    }
                    if (hasKey) {
                        Text(
                            text = "清除已保存的密钥",
                            style = TcTypography.labelMedium,
                            color = palette.danger,
                            modifier = Modifier
                                .clickable { confirmClear = true }
                                .padding(vertical = 6.dp),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                ModelDirectory(
                    models = currentModels,
                    editable = editableCustom || (!builtin && provider != null),
                    canFetch = editableCustom || (pickedCatalog ?: provider)?.kind == ProviderKind.OPENCODE_GO,
                    busy = busy,
                    fetching = fetching,
                    protocol = protocol,
                    onAdd = { models = models + ModelInfo("", "", 128_000, 8_192, io.tinychat.model.Capabilities(thinking = false)) },
                    onChange = { index, updated -> models = models.toMutableList().also { it[index] = updated } },
                    onRemove = { index -> models = models.toMutableList().also { it.removeAt(index) } },
                    onFetch = {
                        val candidate = draftProvider()
                        scope.launch {
                            busy = true
                            fetching = true
                            error = null
                            runCatching { onFetchModels(candidate, key.trim().takeIf { it.isNotEmpty() }) }
                                .onSuccess { ids ->
                                    if (ids.isEmpty()) {
                                        error = "服务端没有返回模型"
                                    } else {
                                        val fetched = ids.map { modelId ->
                                            currentModels.firstOrNull { it.id == modelId } ?: ModelInfo(
                                                id = modelId,
                                                displayName = modelId,
                                                contextWindow = 128_000,
                                                maxOutputTokens = 8_192,
                                                capabilities = io.tinychat.model.Capabilities(
                                                    thinking = false,
                                                    reasoning = false,
                                                ),
                                            )
                                        }
                                        if (editableCustom) {
                                            // 自定义服务商：拉到的追加到现有目录，不删用户手填的
                                            val existingIds = currentModels.map { it.id }.toSet()
                                            models = models + fetched.filter { it.id !in existingIds }
                                        } else {
                                            // 目录服务商（DeepSeek / OpenCode Go）：模型只能来自服务端，拉取即替换
                                            models = fetched
                                        }
                                    }
                                }
                                .onFailure { error = it.message ?: "获取可用模型失败" }
                            fetching = false
                            busy = false
                        }
                    },
                )

                error?.let {
                    Text(
                        text = it,
                        style = TcTypography.labelMedium,
                        color = palette.danger,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, enabled = !busy) {
                Text("取消", color = palette.labelSecondary)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .height(44.dp)
                    .fillMaxWidth(0.55f)
                    .background(
                        if (canSave) palette.brand else palette.selector,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable(enabled = canSave) { save() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (busy) "保存中…" else "保存",
                    style = TcTypography.bodyMedium,
                    color = if (canSave) palette.brandOn else palette.labelTertiary,
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除已保存的密钥？") },
            text = { Text("清除后要重新填写才能继续对话。只删本机保存的密钥，历史记录和服务商账户不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        onClearKey(provider!!.id)
                        status = "已清除"
                    }
                }) { Text("清除", color = palette.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

private enum class EditorTab { CATALOG, CUSTOM }

@Composable
private fun EditorTabs(tab: EditorTab, onSelect: (EditorTab) -> Unit) {
    val palette = TcTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.selector, RoundedCornerShape(10.dp))
            .padding(3.dp),
    ) {
        listOf(EditorTab.CATALOG to "第三方模型提供商", EditorTab.CUSTOM to "自定义模型 API").forEach { (item, title) ->
            val picked = item == tab
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .background(
                        if (picked) palette.bgLayer2 else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(item) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    color = if (picked) palette.labelPrimary else palette.labelSecondary,
                )
            }
        }
    }
}

@Composable
private fun CatalogRow(provider: Provider, picked: Boolean, onClick: () -> Unit) {
    val palette = TcTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, if (picked) palette.brand else palette.borderL2, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(provider.displayName, style = TcTypography.bodyLarge, color = palette.labelPrimary)
            Text(
                text = if (provider.kind == ProviderKind.OPENCODE_GO) {
                    "opencode.ai/go"
                } else {
                    "platform.deepseek.com"
                },
                style = TcTypography.labelMedium,
                color = palette.labelTertiary,
                maxLines = 2,
            )
        }
        if (picked) {
            Text("已选择", fontSize = 12.sp, color = palette.brand)
        }
    }
}

@Composable
private fun ModelDirectory(
    models: List<ModelInfo>,
    editable: Boolean,
    canFetch: Boolean,
    busy: Boolean,
    fetching: Boolean,
    protocol: ProviderProtocol,
    onAdd: () -> Unit,
    onChange: (Int, ModelInfo) -> Unit,
    onRemove: (Int) -> Unit,
    onFetch: () -> Unit,
) {
    val palette = TcTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("模型目录", style = TcTypography.labelMedium, color = palette.labelSecondary, modifier = Modifier.weight(1f))
        if (canFetch) {
            Text(
                text = if (fetching) "获取中…" else "获取可用模型",
                style = TcTypography.labelMedium,
                color = if (busy) palette.labelTertiary else palette.brand,
                modifier = Modifier
                    .clickable(enabled = !busy, onClick = onFetch)
                    .padding(6.dp),
            )
        }
    }
    Spacer(Modifier.height(6.dp))

    models.forEachIndexed { index, model ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallField(
                    value = model.id,
                    placeholder = "模型 ID",
                    enabled = editable && !busy,
                    modifier = Modifier.weight(1.2f),
                    onValueChange = { onChange(index, model.copy(id = it.trim())) },
                )
                Spacer(Modifier.width(8.dp))
                SmallField(
                    value = model.displayName,
                    placeholder = "显示名称",
                    enabled = editable && !busy,
                    modifier = Modifier.weight(1f),
                    onValueChange = { onChange(index, model.copy(displayName = it)) },
                )
                if (editable) {
                    Text(
                        text = "✕",
                        fontSize = 13.sp,
                        color = palette.labelTertiary,
                        modifier = Modifier
                            .clickable(enabled = !busy) { onRemove(index) }
                            .padding(start = 8.dp),
                    )
                }
            }
            if (editable) {
                ThinkingPicker(
                    mode = model.thinking,
                    options = when (protocol) {
                        ProviderProtocol.ANTHROPIC_MESSAGES ->
                            listOf(ThinkingMode.NONE, ThinkingMode.BUDGET)
                        else -> listOf(ThinkingMode.NONE, ThinkingMode.EFFORT)
                    },
                    enabled = !busy,
                    onSelect = { mode ->
                        onChange(
                            index,
                            model.copy(
                                thinking = mode,
                                // 选了参数就显示思考强度；退回「不发送」时一起关掉
                                capabilities = model.capabilities.copy(effort = mode != ThinkingMode.NONE),
                            ),
                        )
                    },
                )
            }
        }
    }

    if (models.isEmpty()) {
        Text(
            text = if (editable) {
                "还没有模型。点击上面「获取可用模型」，或手动添加。"
            } else {
                "还没有模型。点击上面「获取可用模型」拉取。"
            },
            style = TcTypography.labelMedium,
            color = palette.labelTertiary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    if (editable) {
        Text(
            text = "＋ 添加模型",
            style = TcTypography.labelMedium,
            color = palette.labelSecondary,
            modifier = Modifier
                .border(1.dp, palette.borderL2, RoundedCornerShape(8.dp))
                .clickable(enabled = !busy, onClick = onAdd)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ThinkingPicker(
    mode: ThinkingMode,
    options: List<ThinkingMode>,
    enabled: Boolean,
    onSelect: (ThinkingMode) -> Unit,
) {
    val palette = TcTheme.palette
    var open by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Text(
            text = "思考",
            style = TcTypography.labelMedium,
            color = palette.labelTertiary,
        )
        Spacer(Modifier.width(8.dp))
        Box {
            Text(
                text = mode.title,
                style = TcTypography.labelMedium,
                color = if (mode == ThinkingMode.NONE) palette.labelTertiary else palette.brand,
                modifier = Modifier
                    .clickable(enabled = enabled) { open = true }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.title, color = palette.labelPrimary) },
                        onClick = {
                            open = false
                            onSelect(item)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtocolPicker(
    picked: ProviderProtocol,
    enabled: Boolean,
    onSelect: (ProviderProtocol) -> Unit,
) {
    val palette = TcTheme.palette
    var open by remember { mutableStateOf(false) }
    Column {
        Text("API 协议", style = TcTypography.labelMedium, color = palette.labelSecondary)
        Spacer(Modifier.height(6.dp))
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .border(1.dp, palette.borderL2, RoundedCornerShape(10.dp))
                    .clickable(enabled = enabled) { open = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = picked.title(),
                    style = TcTypography.bodyMedium,
                    color = if (enabled) palette.labelPrimary else palette.labelTertiary,
                    modifier = Modifier.weight(1f),
                )
                if (enabled) Text("▾", color = palette.labelTertiary, fontSize = 12.sp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                listOf(ProviderProtocol.OPENAI_CHAT, ProviderProtocol.ANTHROPIC_MESSAGES).forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.title(), color = palette.labelPrimary) },
                        onClick = {
                            open = false
                            onSelect(item)
                        },
                    )
                }
            }
        }
    }
}

private fun ProviderProtocol.title(): String = when (this) {
    ProviderProtocol.OPENAI_CHAT -> "OpenAI Chat Completions"
    ProviderProtocol.ANTHROPIC_MESSAGES -> "Anthropic Messages"
    ProviderProtocol.DEEPSEEK_MESSAGES -> "DeepSeek Messages"
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    val palette = TcTheme.palette
    Column {
        Text(label, style = TcTypography.labelMedium, color = palette.labelSecondary)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(palette.selector, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(value, style = TcTypography.bodyMedium, color = palette.labelTertiary)
        }
    }
}

@Composable
private fun EditorField(
    label: String,
    value: String,
    enabled: Boolean,
    placeholder: String,
    onValueChange: (String) -> Unit,
    secret: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = TcTheme.palette
    Column {
        Text(label, style = TcTypography.labelMedium, color = palette.labelSecondary)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .border(1.dp, palette.borderL2, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TcTypography.bodyMedium.copy(color = palette.labelPrimary),
                cursorBrush = SolidColor(palette.brand),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, style = TcTypography.bodyMedium, color = palette.labelTertiary)
                    }
                    inner()
                },
            )
            trailing?.invoke()
        }
    }
}

@Composable
private fun SmallField(
    value: String,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier,
    onValueChange: (String) -> Unit,
) {
    val palette = TcTheme.palette
    Box(
        modifier
            .height(38.dp)
            .border(1.dp, palette.borderL2, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TcTypography.bodyMedium.copy(color = palette.labelPrimary),
            cursorBrush = SolidColor(palette.brand),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, style = TcTypography.bodyMedium, color = palette.labelTertiary)
                }
                inner()
            },
        )
    }
}

private val ID_REGEX = Regex("^[a-z][a-z0-9-]{1,31}$")
private val OK_WORDS = setOf("可用", "已保存")
