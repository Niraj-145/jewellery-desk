package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.LuxuryCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val jsonStr = viewModel.exportBackupJson()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(jsonStr.toByteArray(Charsets.UTF_8))
                        }
                    }
                    viewModel.showStatus("Backup exported successfully.")
                } catch (e: Exception) {
                    viewModel.showStatus("Export failed: ${e.localizedMessage}", isError = true)
                }
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val content = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }
                    if (!content.isNullOrBlank()) {
                        pendingRestoreJson = content
                    } else {
                        viewModel.showStatus("Selected backup file is empty.", isError = true)
                    }
                } catch (e: Exception) {
                    viewModel.showStatus("Could not read file: ${e.localizedMessage}", isError = true)
                }
            }
        }
    }

    if (pendingRestoreJson != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreJson = null },
            title = { Text("Restore Backup Data?") },
            text = {
                Text("Restoring from a backup replaces all current customers, bills, payments, and settings on this device. Continue?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val raw = pendingRestoreJson
                        pendingRestoreJson = null
                        if (raw != null) viewModel.restoreBackupJson(raw)
                    },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRestoreJson = null },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Settings & Backup",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Configure business invoice details, default billing rates, and local backups.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.statusMessage != null) {
            item {
                Surface(
                    color = if (state.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.statusMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        item {
            LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Business Profile", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = state.businessName,
                        onValueChange = { v -> viewModel.updateField { it.copy(businessName = v) } },
                        label = { Text("Business Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_business_name_input")
                    )
                    OutlinedTextField(
                        value = state.businessAddress,
                        onValueChange = { v -> viewModel.updateField { it.copy(businessAddress = v) } },
                        label = { Text("Business Address") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.businessPhone,
                        onValueChange = { v -> viewModel.updateField { it.copy(businessPhone = v) } },
                        label = { Text("Business Phone") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.gstNumber,
                        onValueChange = { v -> viewModel.updateField { it.copy(gstNumber = v.uppercase()) } },
                        label = { Text("GST Number (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Billing & Invoice Defaults", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = state.defaultRateInput,
                            onValueChange = { v -> viewModel.updateField { it.copy(defaultRateInput = v) } },
                            label = { Text("Default Rate / g") },
                            placeholder = { Text("14000") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = state.defaultPercentageInput,
                            onValueChange = { v -> viewModel.updateField { it.copy(defaultPercentageInput = v) } },
                            label = { Text("Default %") },
                            placeholder = { Text("72") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(0.9f)
                        )
                        OutlinedTextField(
                            value = state.currencySymbol,
                            onValueChange = { v -> viewModel.updateField { it.copy(currencySymbol = v) } },
                            label = { Text("Currency") },
                            singleLine = true,
                            modifier = Modifier.weight(0.7f)
                        )
                    }

                    OutlinedTextField(
                        value = state.invoiceFooter,
                        onValueChange = { v -> viewModel.updateField { it.copy(invoiceFooter = v) } },
                        label = { Text("Invoice Footer") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = viewModel::saveSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_settings_button")
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Business Settings")
                    }
                }
            }
        }

        item {
            LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Data Backup & Restore", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Export your entire wholesale database (customers, bills, items, payments, and settings) as a JSON file, or restore from a previous backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                exportBackupLauncher.launch("jewellery_desk_backup_${System.currentTimeMillis()}.json")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_backup_button")
                        ) {
                            Icon(Icons.Outlined.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export JSON")
                        }
                        OutlinedButton(
                            onClick = {
                                importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("restore_backup_button")
                        ) {
                            Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore JSON")
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
