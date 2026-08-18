package com.deepseek.lzjc.ui.screens

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepseek.lzjc.R
import com.deepseek.lzjc.data.provider.ProviderConfig
import com.deepseek.lzjc.data.provider.ProviderType
import com.deepseek.lzjc.data.repository.UsageRepository
import com.deepseek.lzjc.util.findLanguageByCode
import com.deepseek.lzjc.util.languageOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: UsageRepository
) : ViewModel() {

    private val _providers = MutableStateFlow<List<ProviderConfig>>(emptyList())
    val providers: StateFlow<List<ProviderConfig>> = _providers.asStateFlow()

    /** 正在编辑的供应商；null 表示新建 */
    private val _editingProvider = MutableStateFlow<ProviderConfig?>(null)
    val editingProvider: StateFlow<ProviderConfig?> = _editingProvider.asStateFlow()

    /** 是否显示编辑/新建对话框 */
    private val _showEditor = MutableStateFlow(false)
    val showEditor: StateFlow<Boolean> = _showEditor.asStateFlow()

    /** 待删除的供应商 */
    private val _pendingDelete = MutableStateFlow<ProviderConfig?>(null)
    val pendingDelete: StateFlow<ProviderConfig?> = _pendingDelete.asStateFlow()

    init {
        viewModelScope.launch {
            repository.providers.collect { _providers.value = it }
        }
    }

    fun openCreateDialog() {
        _editingProvider.value = ProviderConfig(
            id = "",
            name = "",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "",
            apiKey = "",
            chatModel = ""
        )
        _showEditor.value = true
    }

    fun openEditDialog(provider: ProviderConfig) {
        _editingProvider.value = provider.copy()
        _showEditor.value = true
    }

    fun closeEditor() {
        _showEditor.value = false
        _editingProvider.value = null
    }

    /** 保存供应商，返回 null 表示成功，否则返回错误信息 */
    fun saveProvider(provider: ProviderConfig): String? {
        if (provider.name.isBlank()) {
            return "name"
        }
        if (provider.apiKey.isBlank() && provider.userToken.isBlank()) {
            return "key"
        }
        if (provider.type == ProviderType.OPENAI_COMPATIBLE && provider.baseUrl.isBlank()) {
            return "url"
        }
        if (provider.baseUrl.isNotBlank()) {
            val valid = provider.baseUrl.trim().startsWith("http://") ||
                provider.baseUrl.trim().startsWith("https://")
            if (!valid) return "url"
        }

        val normalized = provider.copy(
            baseUrl = provider.baseUrl.ifBlank {
                if (provider.type == ProviderType.DEEPSEEK_OFFICIAL) "https://api.deepseek.com/" else ""
            }
        )

        viewModelScope.launch {
            val toSave = if (normalized.id.isBlank()) {
                normalized.copy(id = UUID.randomUUID().toString())
            } else {
                normalized
            }
            repository.saveProvider(toSave)
            closeEditor()
        }
        return null
    }

    fun requestDelete(provider: ProviderConfig) {
        _pendingDelete.value = provider
    }

    fun confirmDelete() {
        val target = _pendingDelete.value ?: return
        viewModelScope.launch {
            repository.deleteProvider(target.id)
            _pendingDelete.value = null
        }
    }

    fun cancelDelete() {
        _pendingDelete.value = null
    }

    fun toggleEnabled(provider: ProviderConfig, enabled: Boolean) {
        viewModelScope.launch {
            repository.saveProvider(provider.copy(enabled = enabled))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)?,
    onSaveSuccess: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val accent = Color(0xFF4D6BFE)

    val providers by viewModel.providers.collectAsState()
    val showEditor by viewModel.showEditor.collectAsState()
    val editingProvider by viewModel.editingProvider.collectAsState()
    val pendingDelete by viewModel.pendingDelete.collectAsState()

    var threshold by remember {
        mutableStateOf(
            context.getSharedPreferences("whale_prefs", Context.MODE_PRIVATE)
                .getString("balance_threshold", "") ?: ""
        )
    }
    var selectedLang by remember {
        mutableStateOf(
            findLanguageByCode(
                context.getSharedPreferences("whale_prefs", Context.MODE_PRIVATE)
                    .getString("app_language", "zh") ?: "zh"
            )
        )
    }
    var langExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.title_settings), tint = Color(0xFF333333))
                }
            }
            Text(
                stringResource(R.string.title_settings),
                color = Color(0xFF1A1A1A),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // ===== 供应商管理 =====
        SettingsPanel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.providers_title),
                    color = Color(0xFF1A1A1A),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { viewModel.openCreateDialog() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = accent
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.provider_add), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))

            if (providers.isEmpty()) {
                Text(
                    stringResource(R.string.providers_empty),
                    color = Color(0xFF999999),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                providers.forEach { provider ->
                    ProviderCard(
                        provider = provider,
                        accent = accent,
                        onEdit = { viewModel.openEditDialog(provider) },
                        onDelete = { viewModel.requestDelete(provider) },
                        onToggle = { enabled -> viewModel.toggleEnabled(provider, enabled) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        // ===== 余额提醒阈值 =====
        SettingsPanel {
            Text(
                stringResource(R.string.label_threshold),
                color = Color(0xFF1A1A1A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = threshold,
                onValueChange = { threshold = it },
                label = { Text(stringResource(R.string.label_threshold)) },
                placeholder = { Text(stringResource(R.string.threshold_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF000000),
                    unfocusedTextColor = Color(0xFF000000),
                    focusedLabelColor = accent,
                    unfocusedLabelColor = Color(0xFF666666),
                    focusedBorderColor = accent,
                    unfocusedBorderColor = Color(0xFFCCCCCC),
                    focusedPlaceholderColor = Color(0xFF999999),
                    unfocusedPlaceholderColor = Color(0xFF999999),
                    cursorColor = accent
                )
            )
            Text(
                stringResource(R.string.threshold_desc),
                color = Color(0xFF999999),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    context.getSharedPreferences("whale_prefs", Context.MODE_PRIVATE)
                        .edit().putString("balance_threshold", threshold).apply()
                    onSaveSuccess?.invoke()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color.White
                )
            ) {
                Text(stringResource(R.string.btn_save), fontWeight = FontWeight.SemiBold)
            }
        }

        // ===== 使用说明 =====
        SettingsPanel {
            Text(
                stringResource(R.string.title_usage),
                color = Color(0xFF1A1A1A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.usage_text_multi),
                color = Color(0xFF666666),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // ===== 语言 =====
        SettingsPanel {
            Text(
                stringResource(R.string.title_language),
                color = Color(0xFF1A1A1A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            ExposedDropdownMenuBox(
                expanded = langExpanded,
                onExpandedChange = { langExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedLang.displayName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF000000),
                        unfocusedTextColor = Color(0xFF000000),
                        focusedLabelColor = accent,
                        unfocusedLabelColor = Color(0xFF666666),
                        focusedBorderColor = accent,
                        unfocusedBorderColor = Color(0xFFCCCCCC),
                        cursorColor = accent
                    )
                )
                ExposedDropdownMenu(
                    expanded = langExpanded,
                    onDismissRequest = { langExpanded = false }
                ) {
                    languageOptions.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang.displayName) },
                            onClick = {
                                selectedLang = lang
                                langExpanded = false
                                context.getSharedPreferences("whale_prefs", Context.MODE_PRIVATE)
                                    .edit().putString("app_language", lang.localeCode).apply()
                                (context as? Activity)?.recreate()
                            }
                        )
                    }
                }
            }
        }

        // ===== 关于 =====
        SettingsPanel {
            Text(
                "关于",
                color = Color(0xFF1A1A1A),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "DeepSeek仪表盘 v2.1.0",
                color = Color(0xFF333333),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "基于 DavidBlon/SeekFlow 二次开发\n原项目采用 MIT 协议开源",
                color = Color(0xFF666666),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "原作者: DavidBlon\nGitHub: github.com/DavidBlon/SeekFlow",
                color = Color(0xFF999999),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    // ===== 编辑/新建对话框 =====
    if (showEditor && editingProvider != null) {
        ProviderEditorDialog(
            initial = editingProvider!!,
            accent = accent,
            onSave = { viewModel.saveProvider(it) },
            onDismiss = { viewModel.closeEditor() }
        )
    }

    // ===== 删除确认对话框 =====
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text(stringResource(R.string.provider_delete_confirm_title)) },
            text = { Text(stringResource(R.string.provider_delete_confirm_text, target.name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text(stringResource(R.string.btn_delete), color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderConfig,
    accent: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val typeLabel = when (provider.type) {
        ProviderType.DEEPSEEK_OFFICIAL -> stringResource(R.string.provider_type_deepseek)
        ProviderType.OPENAI_COMPATIBLE -> stringResource(R.string.provider_type_compatible)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    provider.name,
                    color = Color(0xFF1A1A1A),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "$typeLabel · ${provider.baseUrl.ifBlank { "-" }}",
                    color = Color(0xFF999999),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.btn_edit), tint = Color(0xFF666666))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_delete), tint = Color(0xFFFF5252))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (provider.enabled) stringResource(R.string.provider_enabled) else stringResource(R.string.provider_disabled),
                color = if (provider.enabled) Color(0xFF2E9E6B) else Color(0xFF999999),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = provider.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = accent
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderEditorDialog(
    initial: ProviderConfig,
    accent: Color,
    onSave: (ProviderConfig) -> String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial.name) }
    var type by remember { mutableStateOf(initial.type) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var userToken by remember { mutableStateOf(initial.userToken) }
    var chatModel by remember { mutableStateOf(initial.chatModel) }
    var showKey by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val typeOptions = listOf(
        ProviderType.DEEPSEEK_OFFICIAL to stringResource(R.string.provider_type_deepseek),
        ProviderType.OPENAI_COMPATIBLE to stringResource(R.string.provider_type_compatible)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial.id.isBlank()) stringResource(R.string.provider_add) else stringResource(R.string.provider_edit),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.provider_field_name)) },
                    placeholder = { Text("DeepSeek / MyRelay / OpenAI") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = editorFieldColors(accent)
                )

                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = typeOptions.first { it.first == type }.second,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.provider_field_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = editorFieldColors(accent)
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        typeOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    type = value
                                    typeExpanded = false
                                    if (value == ProviderType.DEEPSEEK_OFFICIAL) {
                                        if (baseUrl.isBlank()) baseUrl = "https://api.deepseek.com/"
                                    }
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.provider_field_base_url)) },
                    placeholder = { Text("https://api.deepseek.com/") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = editorFieldColors(accent)
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.provider_field_api_key)) },
                    placeholder = { Text("sk-...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(
                                if (showKey) stringResource(R.string.btn_hide) else stringResource(R.string.btn_show),
                                color = Color(0xFF333333)
                            )
                        }
                    },
                    colors = editorFieldColors(accent)
                )

                if (type == ProviderType.DEEPSEEK_OFFICIAL) {
                    OutlinedTextField(
                        value = userToken,
                        onValueChange = { userToken = it },
                        label = { Text(stringResource(R.string.provider_field_user_token)) },
                        placeholder = { Text("eyJ...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = editorFieldColors(accent)
                    )
                }

                OutlinedTextField(
                    value = chatModel,
                    onValueChange = { chatModel = it },
                    label = { Text(stringResource(R.string.provider_field_chat_model)) },
                    placeholder = { Text("deepseek-chat / gpt-4o") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = editorFieldColors(accent)
                )

                if (errorText != null) {
                    Text(
                        errorText!!,
                        color = Color(0xFFFF5252),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val error = onSave(
                        initial.copy(
                            name = name.trim(),
                            type = type,
                            baseUrl = baseUrl.trim(),
                            apiKey = apiKey.trim(),
                            userToken = userToken.trim(),
                            chatModel = chatModel.trim()
                        )
                    )
                    errorText = when (error) {
                        null -> null
                        "url" -> context.getString(R.string.provider_save_error_url)
                        else -> context.getString(R.string.provider_save_error)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color.White
                )
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
private fun editorFieldColors(accent: Color) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFF000000),
    unfocusedTextColor = Color(0xFF000000),
    focusedLabelColor = accent,
    unfocusedLabelColor = Color(0xFF666666),
    focusedBorderColor = accent,
    unfocusedBorderColor = Color(0xFFCCCCCC),
    focusedPlaceholderColor = Color(0xFF999999),
    unfocusedPlaceholderColor = Color(0xFF999999),
    cursorColor = accent
)

@Composable
private fun SettingsPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFF5F7FA))
            .padding(18.dp),
        content = content
    )
}
