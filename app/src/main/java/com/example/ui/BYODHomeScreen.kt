package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.GeneratedWordItem
import com.example.data.database.DailyReport
import com.example.data.database.Word
import com.example.viewmodel.BYODViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BYODHomeScreen(
    viewModel: BYODViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentProfile by viewModel.currentProfile.collectAsStateWithLifecycle()
    val allWords by viewModel.allWords.collectAsStateWithLifecycle()
    val activeWords by viewModel.activeWords.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) } // 0: Quiz, 1: AI Extractions, 2: Monthly Report, 3: Wordbook
    var showSyncDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Listen to UI events for toast notifications
    LaunchedEffect(key1 = true) {
        viewModel.uiEvents.collect { event ->
            if (event == "API_KEY_REQUIRED") {
                Toast.makeText(context, "⚠️ Gemini API Key가 설정되지 않았습니다.\n우측 상단 설정 아이콘(⚙️)을 눌러 직접 Key를 입력해 주세요!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, event, Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showSyncDialog) {
        var inputUrl by remember { mutableStateOf(viewModel.googleSheetsUrl) }
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

        AlertDialog(
            onDismissRequest = { showSyncDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "클라우드 동기화",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("구글 시트 실시간 동기화")
                }
            },
            shape = RoundedCornerShape(26.dp),
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Text(
                            text = "구글 시트(Spreadsheet)를 로컬 DB로 사용하여 커플 간의 단어장을 중복 없이 완벽히 양방향 동기화합니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            label = { Text("구글 앱스 스크립트 웹앱 URL") },
                            placeholder = { Text("https://script.google.com/...") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = viewModel.lastSyncTime,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            Button(
                                onClick = {
                                    viewModel.saveGoogleSheetsUrl(inputUrl)
                                    Toast.makeText(context, "구글 시트 URL 주소가 저장되었습니다.", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("주소 저장", fontSize = 12.sp)
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Text(
                            text = "💡 5초 설정하는 간단한 방법",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "1. 구글 스프레드시트를 하나 새로 만듭니다.\n2. 상단 메뉴 [확장 프로그램] -> [Apps Script]를 클릭합니다.\n3. 아래 버튼을 눌러 스크립트 코드를 복사 한 뒤 붙여넣어 줍니다.",
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                val scriptCode = """
                                    function doPost(e) {
                                      try {
                                        var requestData = JSON.parse(e.postData.contents);
                                        var action = requestData.action;
                                        if (action === "sync") {
                                          var words = requestData.words || [];
                                          var chatLogs = requestData.chatLogs || [];
                                          
                                          // 1. Sync Words
                                          var wordSheet = getOrCreateSheet("Words", ["item_type", "target_english", "target_meaning", "context_kr", "target_hint", "native_example", "native_example_kr", "status", "created_at"]);
                                          var existingWords = getWordsFromSheet(wordSheet);
                                          var mergedCount = 0;
                                          
                                          words.forEach(function(localWord) {
                                            var existingIdx = existingWords.findIndex(function(w) {
                                              return w.targetEnglish.trim().toLowerCase() === localWord.targetEnglish.trim().toLowerCase();
                                            });
                                            if (existingIdx === -1) {
                                              appendWordToSheet(wordSheet, localWord);
                                              mergedCount++;
                                            } else {
                                              var existingWord = existingWords[existingIdx];
                                              if (localWord.status === "ACTIVE" && existingWord.status === "PASSIVE") {
                                                updateWordStatusInSheet(wordSheet, existingIdx + 2, "ACTIVE");
                                              }
                                            }
                                          });
                                          
                                          // 2. Sync Chat Logs (Writings)
                                          var logSheet = getOrCreateSheet("ChatLogs", ["target_english", "user_typed_sentence", "created_at"]);
                                          var existingLogs = getLogsFromSheet(logSheet);
                                          var mergedLogsCount = 0;
                                          
                                          chatLogs.forEach(function(localLog) {
                                            var exist = existingLogs.some(function(l) {
                                              return l.targetEnglish.trim().toLowerCase() === localLog.targetEnglish.trim().toLowerCase() &&
                                                     l.userTypedSentence.trim().toLowerCase() === localLog.userTypedSentence.trim().toLowerCase();
                                            });
                                            if (!exist) {
                                              appendLogToSheet(logSheet, localLog);
                                              mergedLogsCount++;
                                            }
                                          });
                                          
                                          // 3. Read up-to-date data lists to return
                                          var updatedWords = getWordsFromSheet(wordSheet);
                                          var updatedLogs = getLogsFromSheet(logSheet);
                                          
                                          return ContentService.createTextOutput(JSON.stringify({
                                            status: "success",
                                            message: "Merged words: " + mergedCount + ", logs: " + mergedLogsCount,
                                            words: updatedWords,
                                            chatLogs: updatedLogs
                                          })).setMimeType(ContentService.MimeType.JSON);
                                        }
                                        return ContentService.createTextOutput(JSON.stringify({status: "error", message: "Unknown action"})).setMimeType(ContentService.MimeType.JSON);
                                      } catch (error) {
                                        return ContentService.createTextOutput(JSON.stringify({status: "error", message: error.toString()})).setMimeType(ContentService.MimeType.JSON);
                                      }
                                    }
                                    function doGet(e) {
                                      try {
                                        var wordSheet = getOrCreateSheet("Words", ["item_type", "target_english", "target_meaning", "context_kr", "target_hint", "native_example", "native_example_kr", "status", "created_at"]);
                                        var logSheet = getOrCreateSheet("ChatLogs", ["target_english", "user_typed_sentence", "created_at"]);
                                        var words = getWordsFromSheet(wordSheet);
                                        var logs = getLogsFromSheet(logSheet);
                                        return ContentService.createTextOutput(JSON.stringify({
                                          status: "success",
                                          words: words,
                                          chatLogs: logs
                                        })).setMimeType(ContentService.MimeType.JSON);
                                      } catch (error) {
                                        return ContentService.createTextOutput(JSON.stringify({status: "error", message: error.toString()})).setMimeType(ContentService.MimeType.JSON);
                                      }
                                    }
                                    function getOrCreateSheet(name, headers) {
                                      var ss = SpreadsheetApp.getActiveSpreadsheet();
                                      var sheet = ss.getSheetByName(name);
                                      if (!sheet) {
                                        sheet = ss.insertSheet(name);
                                        sheet.appendRow(headers);
                                      }
                                      return sheet;
                                    }
                                    function getWordsFromSheet(sheet) {
                                      var rows = sheet.getDataRange().getValues();
                                      if (rows.length <= 1) return [];
                                      var words = [];
                                      for (var i = 1; i < rows.length; i++) {
                                        var row = rows[i];
                                        words.push({
                                          itemType: String(row[0] || ""),
                                          targetEnglish: String(row[1] || ""),
                                          targetMeaning: String(row[2] || ""),
                                          contextKr: String(row[3] || ""),
                                          targetHint: String(row[4] || ""),
                                          nativeExample: String(row[5] || ""),
                                          nativeExampleKr: String(row[6] || ""),
                                          status: String(row[7] || "PASSIVE"),
                                          createdAt: Number(row[8] || Date.now())
                                        });
                                      }
                                      return words;
                                    }
                                    function getLogsFromSheet(sheet) {
                                      var rows = sheet.getDataRange().getValues();
                                      if (rows.length <= 1) return [];
                                      var logs = [];
                                      for (var i = 1; i < rows.length; i++) {
                                        var row = rows[i];
                                        logs.push({
                                          targetEnglish: String(row[0] || ""),
                                          userTypedSentence: String(row[1] || ""),
                                          createdAt: Number(row[2] || Date.now())
                                        });
                                      }
                                      return logs;
                                    }
                                    function appendWordToSheet(sheet, word) {
                                      sheet.appendRow([word.itemType, word.targetEnglish, word.targetMeaning, word.contextKr, word.targetHint, word.nativeExample, word.nativeExampleKr, word.status, word.createdAt || Date.now()]);
                                    }
                                    function appendLogToSheet(sheet, log) {
                                      sheet.appendRow([log.targetEnglish, log.userTypedSentence, log.createdAt || Date.now()]);
                                    }
                                    function updateWordStatusInSheet(sheet, rowNum, status) {
                                      sheet.getRange(rowNum, 8).setValue(status);
                                    }
                                """.trimIndent()

                                val clip = android.content.ClipData.newPlainText("AppsScript Code", scriptCode)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, "📋 스크립트 코드가 클립보드에 복사되었습니다! Apps Script 에디터에 붙여넣어 주세요.", Toast.LENGTH_LONG).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📋 앱스 스크립트 코드 복사")
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "4. 우측 상단 [배포(Deploy)] -> [새 배포(New Deployment)]를 클릭합니다.\n5. 유형에서 [웹앱(Web app)]을 선택하고, 액세스 권한 있는 주체를 [모든 사용자(Anyone)]로 설정한 후 배포합니다.\n6. 발급된 '웹앱 URL'을 위의 입력란에 붙여넣고 [주소 저장] 및 [지금 동기화]를 진행해 주세요!",
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveGoogleSheetsUrl(inputUrl)
                        viewModel.syncWithGoogleSheets()
                    },
                    enabled = !viewModel.isSyncing,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (viewModel.isSyncing) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("동기화 중...")
                    } else {
                        Text("지금 즉시 동기화")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyncDialog = false }) {
                    Text("닫기")
                }
            }
        )
    }

    if (showSettingsDialog) {
        var keyInput by remember { mutableStateOf(viewModel.geminiApiKey) }
        var newProfileNameInput by remember { mutableStateOf("") }
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "환경설정",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("설정 및 프로필 관리")
                }
            },
            shape = RoundedCornerShape(26.dp),
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // API Key Settings
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "🔑 Gemini API Key 설정",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "개인 Gemini API Key를 입력하여 독립 환경에서도 튜터가 원활히 작동하게 합니다.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = keyInput,
                                    onValueChange = { keyInput = it },
                                    label = { Text("Gemini API Key") },
                                    placeholder = { Text("AIzaSy...") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.saveGeminiApiKey(keyInput)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Key 저장", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Profile switching and list
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "👤 프로필 변경 및 추가",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "프로필을 추가/전환하여 각기 다른 학습 목록을 분리 보관하세요.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Text(
                                    "현재 활성 프로필: ${when(currentProfile) {
                                        "SHARED" -> "공동 (SHARED)"
                                        "ME" -> "본인 (ME)"
                                        "GIRLFRIEND" -> "여자친구 (GIRLFRIEND)"
                                        else -> currentProfile
                                    }}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Profile Chips
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    viewModel.profileList.forEach { profile ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (profile == currentProfile) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    else Color.Transparent
                                                )
                                                .clickable {
                                                    viewModel.switchProfile(profile)
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (profile == currentProfile) Icons.Filled.Check else Icons.Filled.Person,
                                                    contentDescription = null,
                                                    tint = if (profile == currentProfile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = when(profile) {
                                                        "SHARED" -> "공동 (SHARED)"
                                                        "ME" -> "본인 (ME)"
                                                        "GIRLFRIEND" -> "여자친구 (GIRLFRIEND)"
                                                        else -> profile
                                                    },
                                                    fontSize = 13.sp,
                                                    color = if (profile == currentProfile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            
                                            if (profile != "SHARED" && profile != "ME" && profile != "GIRLFRIEND") {
                                                IconButton(
                                                    onClick = {
                                                        viewModel.deleteProfile(profile)
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "프로필 삭제",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = newProfileNameInput,
                                    onValueChange = { newProfileNameInput = it },
                                    label = { Text("새 프로필 이름") },
                                    placeholder = { Text("예: 철수") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        if (newProfileNameInput.isNotBlank()) {
                                            viewModel.addProfile(newProfileNameInput)
                                            newProfileNameInput = ""
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("프로필 추가", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSettingsDialog = false },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("닫기")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "BYOD 영어 회화 튜터",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "구글 시트 기반 클라우드 자율 동기화",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Beautiful Row with Google Sheets Sync Trigger and Settings buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalIconButton(
                                onClick = { showSyncDialog = true },
                                modifier = Modifier.testTag("open_sync_dialog_button"),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (viewModel.googleSheetsUrl.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (viewModel.googleSheetsUrl.isNotEmpty()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                if (viewModel.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = "구글 시트 동기화 설정"
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = { showSettingsDialog = true },
                                modifier = Modifier.testTag("open_settings_dialog_button"),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (viewModel.geminiApiKey.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (viewModel.geminiApiKey.isNotEmpty()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "환경설정 및 프로필 관리"
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(if (selectedTab == 0) Icons.Filled.Star else Icons.Outlined.Star, contentDescription = "말하기 퀴즈") },
                    label = { Text("오늘의 퀴즈", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(if (selectedTab == 1) Icons.Filled.Refresh else Icons.Outlined.Refresh, contentDescription = "AI 추출") },
                    label = { Text("AI 자료 정제", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(if (selectedTab == 2) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle, contentDescription = "리포트") },
                    label = { Text("마감 리포트", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(if (selectedTab == 3) Icons.Filled.List else Icons.Outlined.List, contentDescription = "나의 단어장") },
                    label = { Text("학습 단어장", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "TabContent"
            ) { targetTab ->
                when (targetTab) {
                    0 -> QuizTabScreen(viewModel)
                    1 -> AIExtractorTabScreen(viewModel)
                    2 -> DailyReportTabScreen(viewModel)
                    3 -> WordbookTabScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun ProfileButton(
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) activeColor else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ======================== TAB 0: SPEAKING QUIZ SCREEN ========================

@Composable
fun QuizTabScreen(viewModel: BYODViewModel) {
    val activeProfile by viewModel.currentProfile.collectAsStateWithLifecycle()
    val words = viewModel.activeQuizWords
    val index = viewModel.currentWordIndex
    val step = viewModel.quizStep
    var promptAlertVisible by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        item {
            // Study Status Card & Progress Overview
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "💡 1일 10개 실전 회화 챌린지",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "프로필: ${when(activeProfile) {
                                "SHARED" -> "공동 (SHARED)"
                                "ME" -> "본인"
                                "GIRLFRIEND" -> "여자친구"
                                else -> activeProfile
                            }}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("active_profile_indicator")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "로컬 SQLite에서 PASSIVE(학습 전) 상태인 동사 및 청크 10개를 순차적으로 퀴즈로 뱉어냅니다.\n" +
                                "지연시간 0ms의 즉각 리프레시로 빠른 말하기 입출력을 연마해 보세요.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            val categories by viewModel.distinctCategories.collectAsStateWithLifecycle()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = "📚 단어 기반 체계적 학습 코스 선택",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        val isSelected = viewModel.selectedCategoryForQuiz == category
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.selectedCategoryForQuiz = category
                                    viewModel.resetQuiz()
                                },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = category,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        if (words.isEmpty()) {
            // Empty / Start State
            item {
                Spacer(modifier = Modifier.height(48.dp))
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "오늘의 영어 챌린지 시작",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "로컬 DB의 학습전 단어(PASSIVE)를 통해\n10개의 회화 퀴즈를 연속 진행합니다.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.startDailyQuiz() },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(50.dp)
                        .testTag("start_quiz_button"),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("챌린지 퀴즈 개시 (10개)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        } else if (step == 4) {
            // Done state
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(96.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "오늘의 퀴즈 완성!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "로그가 DB에 안전하게 기록되었습니다 (0ms 지연).\n" +
                            "이제 '마감 리포트' 탭에서 AI 상세 보고서와\n" +
                            "Gemini Live 전용 구동 프롬프트를 확인하세요!",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.resetQuiz() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("다시 진행하기")
                    }
                }
            }
        } else {
            // Active quiz run
            val currentWord = words[index]
            item {
                // Progress Bar
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "문제 ${index + 1} / ${words.size}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "코스: ${currentWord.category} | 유형: ${currentWord.itemType}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    // Gradient Progress Indicator (Vibrant Palette Indigo and Violet Gradient)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(((index + 1).toFloat() / words.size).coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Phase 1 Card: Cue & Hint (Extra Rounded Card representing rounded-[40px])
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // High Contrast Rounded Chip (Matching bg-indigo-100 style)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = currentWord.itemType.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.2.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Italic Elegant Cue Context (text-xl font-medium text-slate-500 italic)
                        Text(
                            text = "\"${currentWord.contextKr}\"",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 28.sp,
                            textAlign = TextAlign.Center,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Custom delicate divider compatible with design borders
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "🧩 첫 글자 및 글자수 힌트",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // Huge Bold Custom Mono styled text (r _ _  o u t  o f)
                        Text(
                            text = currentWord.targetHint,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Animated Answer Phase Card (Shows when step >= 2)
                AnimatedVisibility(
                    visible = step >= 2,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "✅ 정답 및 대표 예문",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentWord.targetEnglish,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = ": ${currentWord.targetMeaning}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "🗣️ 원어민 추천 낭독 예문",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = currentWord.nativeExample,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "뜻: ${currentWord.nativeExampleKr}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // User Writing/Typing Input Area (Visible when step >= 3)
                AnimatedVisibility(
                    visible = step >= 3,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "✍️ 나만의 실전 영작문 도전",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = viewModel.userSentenceInput,
                            onValueChange = { viewModel.userSentenceInput = it },
                            placeholder = { Text("위 표현을 활용해서 오늘 날것의 영작문을 작성하세요...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(115.dp)
                                .testTag("quiz_input_field"),
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Control Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (step < 3) {
                        Button(
                            onClick = { viewModel.nextQuizStep() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text(
                                text = if (step == 1) "정답 & 예문 보기" else "영작 도전하기",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                        }
                    } else {
                        // Submit button
                        Button(
                            onClick = { viewModel.submitQuizSentence() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("quiz_submit_button"),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("작문 제출 및 다음 퀴즈 (0ms SQLite)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Force skip option
                TextButton(
                    onClick = { viewModel.forceFinishQuiz() }
                ) {
                    Text("오늘 공부 그만하고 리포트 생성하러 가기 ➜", color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}


// ======================== TAB 1: AI LEARNING MATERIAL SELECTION ========================

@Composable
fun AIExtractorTabScreen(viewModel: BYODViewModel) {
    val isExtracting = viewModel.isExtracting
    val isResearching = viewModel.isResearching
    val extractedList = viewModel.extractedWordsList
    var selectedItemIndices = remember { mutableStateListOf<Int>() }
    var aiMode by remember { mutableStateOf(1) } // Default to 1 (Topic Research) as requested by user!

    // Synchronize selection state when list changes
    LaunchedEffect(extractedList) {
        selectedItemIndices.clear()
        for (i in extractedList.indices) {
            selectedItemIndices.add(i) // Select all by default
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        item {
            // Mode Toggle Card (Vibrant High-Contrast Segmented design)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (aiMode == 1) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { 
                                aiMode = 1 
                                viewModel.researchTopicInput = ""
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "AI 주제별 리서치 (코스)",
                            color = if (aiMode == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (aiMode == 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { 
                                aiMode = 0 
                                viewModel.extractionInputText = ""
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "파일 & 원문 텍스트 추출",
                            color = if (aiMode == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (aiMode == 1) {
            // MODE 1: Topic Research Panel
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "🔍 AI 맞춤형 상황 분석 및 교과 코스 생성",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "공항 수하물 분실 대처, 바이어 응대 식사, 스타벅스 복잡한 시럽 추가 주문 등 원하는 상황 키워드를 검색해 보세요.\n" +
                            "Gemini AI가 가장 빈번한 실용 영어 회화 데이터셋 5개를 입체적으로 가공해 즉석 코스로 제공합니다.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = viewModel.researchTopicInput,
                    onValueChange = { viewModel.researchTopicInput = it },
                    label = { Text("학습하고 싶은 입체적인 상황/주제 키워드를 입력하세요") },
                    placeholder = { Text("예: 스타벅스 주문하기, 공항 입국심사, 전화 호텔 예약") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )

                Button(
                    onClick = { viewModel.researchTopicExpressions() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("research_ai_button"),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    enabled = !isResearching
                ) {
                    if (isResearching) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("주제 연구 및 회화 연계 설계 중...")
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("체계적인 AI 맞춤 코스 만들기", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        } else {
            // MODE 0: Raw Text Extraction Panel
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "🎬 외부 텍스트에서 AI 구동사/청크 자동 추출",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "유튜브 자막, 트위터/X 피드, 드라마 대사, 좋아하는 뉴스 아티클 원문을 아래 붙여넣어 보세요.\n" +
                            "Gemini AI가 가장 빈번하고 실용적인 핵심 표현 5개를 엄선해 퀴즈 카드로 가공해 줍니다.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = viewModel.extractionInputText,
                    onValueChange = { viewModel.extractionInputText = it },
                    label = { Text("여기에 소스 영어 원문 텍스트를 붙여넣으세요") },
                    placeholder = { Text("예: Oh, we are running out of time! I can't come up with a good idea. Oh, by the way...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 6
                )
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { viewModel.extractWordsFromInput() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("extract_ai_button"),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ),
                    enabled = !isExtracting
                ) {
                    if (isExtracting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Gemini가 유용한 생활 회화 선별 중...")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI 영어 표현 5개 추출하기", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        if (extractedList.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📋 추출/리서치 완료된 맞춤 표현 후보",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    TextButton(
                        onClick = {
                            if (selectedItemIndices.size == extractedList.size) {
                                selectedItemIndices.clear()
                            } else {
                                selectedItemIndices.clear()
                                extractedList.indices.forEach { selectedItemIndices.add(it) }
                            }
                        }
                    ) {
                        Text(if (selectedItemIndices.size == extractedList.size) "전체 취소" else "전체 선택")
                    }
                }
            }

            itemsIndexed(extractedList) { idx, item ->
                val isSelected = selectedItemIndices.contains(idx)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable {
                            if (isSelected) selectedItemIndices.remove(idx)
                            else selectedItemIndices.add(idx)
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                if (checked == true) selectedItemIndices.add(idx)
                                else selectedItemIndices.remove(idx)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1.0f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(item.itemType) },
                                    modifier = Modifier.height(28.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.targetEnglish,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "뜻: ${item.targetMeaning}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "상황 힌트: ${item.contextKr}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "예문: ${item.nativeExample}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val categoryName = if (aiMode == 1) {
                            viewModel.researchTopicInput.trim().ifEmpty { "추출 회화" }
                        } else {
                            "추출 회화"
                        }
                        viewModel.saveExtractedWords(selectedItemIndices.toList(), categoryName)
                    },
                    enabled = selectedItemIndices.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("save_extracted_button"),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Done, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "선택한 ${selectedItemIndices.size}개 표현을 DB에 저장하기 (학습 복제)",
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}


// ======================== TAB 2: DAILY PROGRESS & INTEGRATED FEEDBACK REPORT ========================

@Composable
fun DailyReportTabScreen(viewModel: BYODViewModel) {
    val context = LocalContext.current
    val currentProfile by viewModel.currentProfile.collectAsStateWithLifecycle()
    val todayList = viewModel.todayCompletedLessons
    val generatedReport = viewModel.generatedReportForToday
    val isGenerating = viewModel.isGeneratingReport

    // Check on startup if today's report already exists
    LaunchedEffect(key1 = currentProfile, key2 = todayList.size) {
        viewModel.refreshTodayProgress()
        viewModel.checkAndFetchTodayReport()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "🏆 오늘의 10개 문장 마감 리포트 생성기",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "오늘 영작한 날것의 학습 자료 로그를 수집하여 Gemini AI가 뉘앙스를 정밀 분석합니다.\n" +
                        "동시에 구글 공식 앱에 붙여넣어 인출 훈련(Gemini Live 보이스 회화)을 바로 시작하는 전용 스마트 프롬프트를 일괄 수령하여 복사할 수 있습니다.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            // Profile & Progress Count indicator
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "오늘의 영작 도전 제출수",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${todayList.size} / 10 문장 완료",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Button(
                        onClick = { viewModel.generateDailyReport() },
                        enabled = !isGenerating && todayList.isNotEmpty(),
                        modifier = Modifier.testTag("generate_report_button"),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("생성 중...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("마감 리포트 산출")
                        }
                    }
                }
            }
        }
 
        if (todayList.isEmpty()) {
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "로그 기록이 비어 있습니다.",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "오늘 제출한 영작문이 아직 존재하지 않습니다.\n'오늘의 퀴즈' 탭에서 학습을 개시해 주십시오.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            if (generatedReport == null) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "일일 묶음 리포트가 아직 준비되지 않았습니다.",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "우측 상단의 '마감 리포트 산출' 버튼을 누르시면\n오늘 작성한 ${todayList.size} 문장을 바탕으로 피드백을 가공합니다.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            } else {
                // Generated integrated model is ready
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "피드백 & 음성회화 룰 보고서",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                SuggestionChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            if (generatedReport.isCopied == 1) "복사 완료 ✓" else "미복사",
                                            color = if (generatedReport.isCopied == 1) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
 
                            // Beautiful UI Markdown feedback text box with high rounded corners
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Column {
                                    MarkdownTextRenderer(reportText = generatedReport.markdownContent)
                                }
                            }
 
                            Spacer(modifier = Modifier.height(16.dp))
 
                            // Action Button: copy and redirect to Gemini Live App
                            Button(
                                onClick = {
                                    viewModel.copyReportAndLaunchGemini(
                                        context,
                                        generatedReport.markdownContent,
                                        generatedReport.id
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("copy_report_button"),
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "일일 보고서 복사 + Gemini Live 실행",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Historical raw entries listing
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "📝 오늘 누적된 영어 영작 로그 (${todayList.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(todayList) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🔑 ${item.word.targetEnglish}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = item.word.targetMeaning,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "💡 예문: ${item.word.nativeExample}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // Render all synced/local chat logging for this word
                        if (item.logs.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                item.logs.forEachIndexed { index, log ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (index % 2 == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                                else MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
                                            )
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "✍️ 영작 로그 #${index + 1}: \"${log.userTypedSentence}\"",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "👤 작성된 영작 로그가 없습니다.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Beautiful helper to render basic markdown elements statically in Material 3
 */
@Composable
fun MarkdownTextRenderer(reportText: String) {
    val lines = reportText.split("\n")
    Column(modifier = Modifier.fillMaxWidth()) {
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("###") -> {
                    Text(
                        text = trimmed.removePrefix("###").trim(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                trimmed.startsWith("##") -> {
                    Text(
                        text = trimmed.removePrefix("##").trim(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                trimmed.startsWith("#") -> {
                    Text(
                        text = trimmed.removePrefix("#").trim(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
                trimmed.startsWith("-") || trimmed.startsWith("*") -> {
                    Row(modifier = Modifier.padding(start = 8.dp).padding(vertical = 2.dp)) {
                        Text("• ", fontSize = 13.sp, fontWeight = FontWeight.Black)
                        Text(
                            text = trimmed.substring(1).trim(),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                trimmed.startsWith("---") -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                trimmed.contains("|") -> {
                    // Simple table line helper
                    Text(
                        text = trimmed,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(6.dp))
                }
                else -> {
                    Text(
                        text = trimmed,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
            }
        }
    }
}


// ======================== TAB 3: LOCAL SQLite WORDBOOK (나의 단어장) ========================

@Composable
fun WordbookTabScreen(viewModel: BYODViewModel) {
    val currentProfile by viewModel.currentProfile.collectAsStateWithLifecycle()
    val wordsList by viewModel.allWords.collectAsStateWithLifecycle()
    val categoriesList by viewModel.distinctCategories.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedFilterCategory by remember { mutableStateOf("전체 코스") }

    // Dialog form states
    var newEng by remember { mutableStateOf("") }
    var newMean by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("나만의 회화") }
    var newContext by remember { mutableStateOf("") }
    var newHint by remember { mutableStateOf("") }
    var newExample by remember { mutableStateOf("") }
    var newExampleKr by remember { mutableStateOf("") }
    var itemTypeChoice by remember { mutableStateOf("VERB") }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("동사/청크 수동 추가") },
            shape = RoundedCornerShape(26.dp),
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Text("회화 핵심 요소 구분", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = itemTypeChoice == "VERB",
                                onClick = { itemTypeChoice = "VERB" }
                            )
                            Text("동사 (VERB)")
                            Spacer(modifier = Modifier.width(16.dp))
                            RadioButton(
                                selected = itemTypeChoice == "CHUNK",
                                onClick = { itemTypeChoice = "CHUNK" }
                            )
                            Text("기초 청크 (CHUNK)")
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = newEng,
                            onValueChange = { newEng = it },
                            label = { Text("영어 표현 (예: pull off)") },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = newMean,
                            onValueChange = { newMean = it },
                            label = { Text("한국어 뜻 (예: 해내다, 성공시키다)") },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = newCategory,
                            onValueChange = { newCategory = it },
                            label = { Text("코스/주제 이름 (예: 일반 회화, 비즈니스, 스타벅스)") },
                            placeholder = { Text("예: 나만의 회화, 유니크 패턴 등") },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = newContext,
                            onValueChange = { newContext = it },
                            label = { Text("인출 자극용 한국어 문장") },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = newHint,
                            onValueChange = { newHint = it },
                            label = { Text("첫글자 힌트 포맷 (예: p___ ___ )") },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = newExample,
                            onValueChange = { newExample = it },
                            label = { Text("원어민 영어 표준 예문") },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = newExampleKr,
                            onValueChange = { newExampleKr = it },
                            label = { Text("예문 한국어 번역") },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newEng.trim().isNotEmpty() && newMean.trim().isNotEmpty()) {
                            val newWord = Word(
                                itemType = itemTypeChoice,
                                targetEnglish = newEng.trim(),
                                targetMeaning = newMean.trim(),
                                contextKr = newContext.trim().ifEmpty { "직접 추가한 맞춤형 영어 회화 훈련 상황" },
                                targetHint = newHint.trim().ifEmpty {
                                    if (newEng.trim().isEmpty()) "" else {
                                        val firstChar = newEng.trim().firstOrNull()?.toString() ?: ""
                                        val rest = newEng.trim().drop(1).map { if (it == ' ') ' ' else '_' }.joinToString("")
                                        firstChar + rest
                                    }
                                },
                                nativeExample = newExample.trim().ifEmpty { "Let's practice this useful pattern together." },
                                nativeExampleKr = newExampleKr.trim().ifEmpty { "이 유용한 패턴을 우리 함께 연습해 봅시다." },
                                status = "PASSIVE",
                                profile = currentProfile,
                                category = newCategory.trim().ifEmpty { "나만의 회화" }
                            )
                            viewModel.addManualWord(newWord)
                            
                            // reset and dismiss
                            newEng = ""
                            newMean = ""
                            newCategory = "나만의 회화"
                            newContext = ""
                            newHint = ""
                            newExample = ""
                            newExampleKr = ""
                            itemTypeChoice = "VERB"
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("추가하기")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "🗃️ 로컬 SQLite 단어장 관리자",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "현재 프로필 (${when(currentProfile) {
                            "SHARED" -> "공동 (SHARED)"
                            "ME" -> "본인"
                            "GIRLFRIEND" -> "여자친구"
                            else -> currentProfile
                        }})에 수록된 회화 단어들을 한눈에 보며 관리합니다. 학습 상태도 수동 조율 및 불필요한 단어 삭제가 0ms로 가능합니다.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "총 표현: ${wordsList.size}개 (미학습: ${wordsList.count { it.status == "PASSIVE" }} / 완료: ${wordsList.count { it.status == "ACTIVE" }})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp).testTag("manual_add_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("수동 추가", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 4.dp)
            ) {
                Text(
                    text = "📂 코스(주제)별 분류 보기",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categoriesList) { category ->
                        val isSelected = selectedFilterCategory == category
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedFilterCategory = category },
                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = category,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        val filteredWords = if (selectedFilterCategory == "전체 코스") {
            wordsList
        } else {
            wordsList.filter { it.category == selectedFilterCategory }
        }

        if (filteredWords.isEmpty()) {
            item {
                Spacer(modifier = Modifier.height(48.dp))
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "이 코스에 등록된 표현이 없습니다.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "신규 표현을 단어장에 추가하시거나\n[AI 맞춤 코스] 탭에서 리서치를 통해 학습 코스를 자동 증강해 보세요.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            items(filteredWords) { word ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1.0f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(word.itemType, fontSize = 10.sp) },
                                    modifier = Modifier.height(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(word.category, fontSize = 10.sp) },
                                    modifier = Modifier.height(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = word.targetEnglish,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (word.status == "ACTIVE") MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = word.status,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (word.status == "ACTIVE") MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "뜻: ${word.targetMeaning}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "맥락문맥: ${word.contextKr}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { viewModel.removeExpression(word.id) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "삭제",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}
