package io.tinychat.feature.settings

import androidx.activity.compose.BackHandler

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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
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
import io.tinychat.model.ProviderNaming
import io.tinychat.model.ProviderProtocol
import io.tinychat.model.ThinkingMode
import io.tinychat.ui.TcButton
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
                    onDelete = { pendingDelete = provider },
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
            text = { Text("该服务商下的会话仍可查看，换一个服务商就能继续对话。本机保存的密钥会一并删除。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(provider)
                }) { Text("删除", color = palette.danger) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消", color = TcTheme.palette.labelSecondary) } },
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
            color = palette.brand,
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
/** 离开保护用的一份表单快照（02 §10.2）。只比会落库的东西，状态文案不参与。 */
private data class FormSnapshot(
    val id: String,
    val name: String,
    val baseUrl: String,
    val protocol: ProviderProtocol,
    val models: List<Pair<String, String>>,
)
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
    // 目录里的预设可以重复添加（决策 48）：两块不同的 Key、两个网关、两个地区互不影响
    val catalogEntries = KnownProviders.catalog
    val existingIds = remember(existing) { existing.map { it.id }.toSet() }
    val existingNames = remember(existing) { existing.map { it.displayName }.toSet() }

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
    // 离开保护（02 §10.2）：打开这一页时记一份快照，任何一项与之不同就要问一句。
    // 取消按钮已经删掉，返回是唯一的出口，没有这道确认就会静默丢掉填了一半的密钥。
    val initialForm = remember {
        FormSnapshot(
            id = id,
            name = name,
            baseUrl = baseUrl,
            protocol = protocol,
            models = models.map { it.id to it.displayName },
        )
    }
    val dirty = FormSnapshot(
        id = id,
        name = name,
        baseUrl = baseUrl,
        protocol = protocol,
        models = models.map { it.id to it.displayName },
    ) != initialForm || key.isNotBlank()
    var confirmDiscard by remember { mutableStateOf(false) }

    fun leave() {
        if (busy) return
        if (dirty) confirmDiscard = true else onBack()
    }

    BackHandler(enabled = dirty && !busy) { confirmDiscard = true }

    // 预设解锁（决策 48）：任何服务商都能改地址、协议、模型，也能删；预设只负责预填
    val catalogMode = adding && tab == EditorTab.CATALOG
    val effective = pickedCatalog ?: provider
    val kindOf = effective?.kind ?: ProviderKind.OPENAI_COMPATIBLE
    // 模型始终以这一份可变状态为准（选目录服务商时已把它的模型灌进来）；
    // 第一版目录模式读的是 pickedCatalog.models，导致「拉取成功但界面不更新」
    val currentModels = models
    val canSave = when {
        busy -> false
        catalogMode -> pickedCatalog != null && name.isNotBlank() && baseUrl.isNotBlank() &&
            currentModels.isNotEmpty()
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
            leading = { TcIconButton(TcIcons.Back, "返回", ::leave) },
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
                    "从内置目录中选择一家，填入 API 密钥即可使用；同一家可以添加多次。",
                    style = TcTypography.labelMedium,
                    color = palette.labelTertiary,
                )
                Spacer(Modifier.height(12.dp))
                catalogEntries.forEach { item ->
                    CatalogRow(
                        provider = item,
                        picked = pickedCatalog?.id == item.id,
                        onClick = {
                            pickedCatalog = item
                            // 预设只预填基础信息与能力边界，不决定身份（决策 48）
                            id = ProviderNaming.uniqueId(item.id, existingIds)
                            name = ProviderNaming.uniqueName(item.displayName, existingNames)
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
                if (adding) {
                    EditorField(
                        label = "Provider ID",
                        value = id,
                        enabled = !busy,
                        placeholder = "acme-gateway",
                        onValueChange = { id = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(32) },
                    )
                    Text(
                        "以小写字母开头的标识，用于在本机唯一标识该服务商。",
                        style = TcTypography.labelMedium,
                        color = palette.labelTertiary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                }

                EditorField(
                    label = "显示名称",
                    value = name,
                    enabled = !busy,
                    placeholder = "例如：我的网关",
                    onValueChange = { name = it.take(24) },
                )
                Spacer(Modifier.height(16.dp))

                EditorField(
                    label = "API 地址",
                    value = baseUrl,
                    enabled = !busy,
                    placeholder = "https://gateway.example/v1",
                    onValueChange = { baseUrl = it.trim() },
                )
                Spacer(Modifier.height(16.dp))

                ProtocolPicker(
                    picked = protocol,
                    options = protocolOptions(kindOf),
                    enabled = !busy,
                    onSelect = { protocol = it },
                )
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
                                color = palette.brand,
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
                    editable = !busy,
                    canFetch = !busy,
                    busy = busy,
                    fetching = fetching,
                    protocol = protocol,
                    onAdd = { added -> models = models + added },
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
                                        if (kindOf == ProviderKind.OPENAI_COMPATIBLE) {
                                            // 自定义服务商：拉到的追加到现有目录，不删用户手填的
                                            val existingIds = currentModels.map { it.id }.toSet()
                                            models = models + fetched.filter { it.id !in existingIds }
                                        } else {
                                            // 目录服务商：模型以服务端为准，拉取即替换
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

        // 动作区（02 §10.2）：一屏一个主操作，取消由顶栏返回承担
        TcButton(
            text = if (busy) "保存中…" else "保存",
            onClick = { save() },
            enabled = canSave,
            fillWidth = true,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp),
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃修改？") },
            text = { Text("这一页有还没保存的改动。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onBack()
                }) { Text("放弃", color = TcTheme.palette.brand) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text("继续编辑", color = TcTheme.palette.labelSecondary)
                }
            },
        )
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
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消", color = TcTheme.palette.labelSecondary) } },
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
    onAdd: (ModelInfo) -> Unit,
    onChange: (Int, ModelInfo) -> Unit,
    onRemove: (Int) -> Unit,
    onFetch: () -> Unit,
) {
    val palette = TcTheme.palette
    // 模型详情弹层（决策 49）：index < 0 表示新增
    var editing by remember { mutableStateOf<Pair<Int, ModelInfo>?>(null) }

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

    if (models.isNotEmpty()) {
        // 表头：两列各有列名，右边留出删除位，扫一眼就知道哪列是 ID（决策 49）
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, bottom = 4.dp),
        ) {
            Text("模型", style = TcTypography.labelMedium, color = palette.labelTertiary, modifier = Modifier.weight(1.2f))
            Spacer(Modifier.width(8.dp))
            Text("名称", style = TcTypography.labelMedium, color = palette.labelTertiary, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(28.dp))
        }
    }

    models.forEachIndexed { index, model ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = editable && !busy) { editing = index to model }
                .padding(horizontal = 2.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.id.ifBlank { "未命名模型" },
                    style = TcTypography.bodyMedium,
                    color = if (model.id.isBlank()) palette.danger else palette.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.2f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = model.displayName.ifBlank { model.id }.ifBlank { "—" },
                    style = TcTypography.bodyMedium,
                    color = palette.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (editable) {
                    Text(
                        text = "✕",
                        fontSize = 13.sp,
                        color = palette.labelTertiary,
                        modifier = Modifier
                            .clickable(enabled = !busy) { onRemove(index) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text(
                text = modelSummary(model),
                style = TcTypography.labelMedium,
                color = palette.labelTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
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
                .clickable(enabled = !busy) {
                    editing = -1 to ModelInfo(
                        id = "",
                        displayName = "",
                        contextWindow = 128_000,
                        maxOutputTokens = 8_192,
                        capabilities = io.tinychat.model.Capabilities(thinking = false),
                    )
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }

    editing?.let { (index, model) ->
        ModelDetailDialog(
            model = model,
            isNew = index < 0,
            protocol = protocol,
            onDismiss = { editing = null },
            onConfirm = { updated ->
                editing = null
                if (index < 0) onAdd(updated) else onChange(index, updated)
            },
        )
    }
}

/** 模型行下面那行摘要：能力边界在列表里就能读到，不用点进去（决策 49）。 */
private fun modelSummary(model: ModelInfo): String = buildList {
    add("${shortTokens(model.contextWindow)} 上下文")
    add("${shortTokens(model.maxOutputTokens)} 输出")
    if (model.capabilities.thinking) {
        add(if (model.capabilities.effort) "思考，可调强度" else "思考")
    }
}.joinToString("，")

private fun shortTokens(value: Int): String = when {
    value <= 0 -> "未知"
    value % 1_000_000 == 0 -> "${value / 1_000_000}M"
    value % 1_000 == 0 -> "${value / 1_000}k"
    else -> value.toString()
}

/**
 * 模型详情（决策 49）。
 *
 * 上下文窗口与最大输出不是摆设：前者是输入区圆环的分母，后者直接进 Anthropic 的 `max_tokens`。
 * 拉取模型时给的 128k / 8192 只是占位，必须让用户能改。
 */
@Composable
private fun ModelDetailDialog(
    model: ModelInfo,
    isNew: Boolean,
    protocol: ProviderProtocol,
    onDismiss: () -> Unit,
    onConfirm: (ModelInfo) -> Unit,
) {
    val palette = TcTheme.palette
    var id by remember { mutableStateOf(model.id) }
    var name by remember { mutableStateOf(model.displayName) }
    var contextText by remember { mutableStateOf(model.contextWindow.takeIf { it > 0 }?.toString().orEmpty()) }
    var outputText by remember { mutableStateOf(model.maxOutputTokens.takeIf { it > 0 }?.toString().orEmpty()) }
    var thinking by remember { mutableStateOf(model.capabilities.thinking) }
    var effort by remember { mutableStateOf(model.capabilities.effort) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.bg,
        title = {
            Text(
                text = if (isNew) "添加模型" else "编辑模型",
                style = TcTypography.titleMedium,
                color = palette.labelPrimary,
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                EditorField(
                    label = "模型 ID",
                    value = id,
                    enabled = true,
                    placeholder = "gpt-4.1",
                    onValueChange = { id = it.trim() },
                )
                Spacer(Modifier.height(14.dp))
                EditorField(
                    label = "显示名称",
                    value = name,
                    enabled = true,
                    placeholder = "留空则与 ID 相同",
                    onValueChange = { name = it.take(40) },
                )
                Spacer(Modifier.height(14.dp))
                EditorField(
                    label = "上下文窗口",
                    value = contextText,
                    enabled = true,
                    placeholder = "128000",
                    onValueChange = { contextText = it.filter(Char::isDigit).take(9) },
                )
                Spacer(Modifier.height(14.dp))
                EditorField(
                    label = "最大输出",
                    value = outputText,
                    enabled = true,
                    placeholder = "8192",
                    onValueChange = { outputText = it.filter(Char::isDigit).take(9) },
                )
                Text(
                    text = "单位是 token，不是千 token：128k 写 128000。留空表示未知。",
                    style = TcTypography.labelMedium,
                    color = palette.labelTertiary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(6.dp))
                DialogSwitch(
                    title = "支持思考",
                    subtitle = "模型会返回思考过程",
                    checked = thinking,
                    enabled = true,
                    onCheckedChange = {
                        thinking = it
                        if (!it) effort = false
                    },
                )
                DialogSwitch(
                    title = "可调思考强度",
                    subtitle = if (thinking) "开启后底栏出现强度选择" else "先开启思考",
                    checked = effort && thinking,
                    enabled = thinking,
                    onCheckedChange = { effort = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = id.isNotBlank(),
                onClick = {
                    onConfirm(
                        model.copy(
                            id = id,
                            displayName = name.trim(),
                            contextWindow = contextText.toIntOrNull() ?: model.contextWindow,
                            maxOutputTokens = outputText.toIntOrNull() ?: model.maxOutputTokens,
                            thinking = when {
                                !thinking -> ThinkingMode.NONE
                                protocol == ProviderProtocol.ANTHROPIC_MESSAGES -> ThinkingMode.BUDGET
                                else -> ThinkingMode.EFFORT
                            },
                            capabilities = model.capabilities.copy(
                                thinking = thinking,
                                effort = effort && thinking,
                            ),
                        ),
                    )
                },
            ) {
                Text("保存", color = if (id.isNotBlank()) palette.brand else palette.labelTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = palette.labelSecondary) }
        },
    )
}

@Composable
private fun DialogSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = TcTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TcTypography.bodyMedium,
                color = if (enabled) palette.labelPrimary else palette.labelTertiary,
            )
            Text(subtitle, style = TcTypography.labelMedium, color = palette.labelTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = palette.brand,
                checkedThumbColor = palette.brandOn,
            ),
        )
    }
}

@Composable
private fun ProtocolPicker(
    picked: ProviderProtocol,
    options: List<ProviderProtocol>,
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
                options.forEach { item ->
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

/**
 * 协议可选项（决策 48）：官方那套 Messages 扩展只对 DeepSeek 官方有意义，
 * 其余服务商只给通用两种，避免选出「看起来更多、其实不发」的选项。
 */
private fun protocolOptions(kind: ProviderKind): List<ProviderProtocol> = when (kind) {
    ProviderKind.DEEPSEEK_OFFICIAL -> listOf(
        ProviderProtocol.DEEPSEEK_MESSAGES,
        ProviderProtocol.ANTHROPIC_MESSAGES,
        ProviderProtocol.OPENAI_CHAT,
    )
    else -> listOf(ProviderProtocol.OPENAI_CHAT, ProviderProtocol.ANTHROPIC_MESSAGES)
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

private val ID_REGEX = Regex("^[a-z][a-z0-9-]{1,31}$")
private val OK_WORDS = setOf("可用", "已保存")
