package com.example.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiService
import com.example.data.api.GeneratedWordItem
import com.example.data.database.DailyReport
import com.example.data.database.StudyRepository
import com.example.data.database.Word
import com.example.data.database.WordWithLogs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BYODViewModel(
    private val repository: StudyRepository,
    private val geminiService: GeminiService,
    private val context: Context
) : ViewModel() {

    private val _currentProfile = MutableStateFlow("SHARED")
    val currentProfile: StateFlow<String> = _currentProfile.asStateFlow()

    // Course Category selection for Study / Quiz / Wordbook
    var selectedCategoryForQuiz by mutableStateOf("전체 코스")

    // Reactive categories list matching profile database content
    val distinctCategories: StateFlow<List<String>> = _currentProfile
        .flatMapLatest { profile -> repository.getDistinctCategoriesFlow(profile) }
        .map { list -> listOf("전체 코스") + list.filter { it.isNotEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("전체 코스"))

    // AI Research States
    var researchTopicInput by mutableStateOf("")
    var isResearching by mutableStateOf(false)

    // Dynamic Gemini API Key & custom dynamic profiles lists
    var geminiApiKey by mutableStateOf("")
        private set

    var profileList by mutableStateOf<List<String>>(emptyList())
        private set

    // Google Sheets Sync Settings & States
    var googleSheetsUrl by mutableStateOf("")
        private set
    var isSyncing by mutableStateOf(false)
        private set
    var lastSyncTime by mutableStateOf("동기화 이력 없음")
        private set

    // Reactive words list matching profile
    val allWords: StateFlow<List<Word>> = _currentProfile
        .flatMapLatest { profile -> repository.getAllWordsFlow(profile) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeWords: StateFlow<List<Word>> = _currentProfile
        .flatMapLatest { profile -> repository.getActiveWordsFlow(profile) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allReports: StateFlow<List<DailyReport>> = _currentProfile
        .flatMapLatest { profile -> repository.getReportsFlow(profile) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Quiz flow states
    var activeQuizWords by mutableStateOf<List<Word>>(emptyList())
        private set

    var currentWordIndex by mutableStateOf(0)
        private set

    var quizStep by mutableStateOf(1) // 1: Recall cue, 2: Detail confirmation, 3: User Typing state, 4: Done/Transition
        private set

    var userSentenceInput by mutableStateOf("")

    // Today progress & logs matching active profile
    var todayCompletedLessons by mutableStateOf<List<WordWithLogs>>(emptyList())
        private set

    // Extraction Screen States
    var extractionInputText by mutableStateOf("")
    var isExtracting by mutableStateOf(false)
    var extractedWordsList by mutableStateOf<List<GeneratedWordItem>>(emptyList())
        private set

    // Report Generation States
    var isGeneratingReport by mutableStateOf(false)
    var generatedReportForToday by mutableStateOf<DailyReport?>(null)

    // Customizable Prompt States
    var customExtractionPrompt by mutableStateOf("")
    var customResearchPrompt by mutableStateOf("")
    var customReportPrompt by mutableStateOf("")

    // General Info and Messages
    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents: SharedFlow<String> = _uiEvents.asSharedFlow()

    init {
        loadGoogleSheetsSyncSettings()
        loadProfileSettings()
        // Automatically fetch and seed if empty, and load today's compiled data
        viewModelScope.launch {
            _currentProfile.collect { profile ->
                seedDefaultWordsIfEmpty(profile)
                refreshTodayProgress()
            }
        }
    }

    private fun loadGoogleSheetsSyncSettings() {
        val prefs = context.getSharedPreferences("byod_prefs", Context.MODE_PRIVATE)
        googleSheetsUrl = prefs.getString("google_sheets_url", "") ?: ""
        lastSyncTime = prefs.getString("last_sheets_sync_time", "동기화 이력 없음") ?: "동기화 이력 없음"
        geminiApiKey = prefs.getString("gemini_api_key", "") ?: ""
        geminiService.customApiKey = geminiApiKey.ifBlank { null }

        customExtractionPrompt = prefs.getString("custom_extraction_prompt", "") ?: ""
        geminiService.customExtractionPrompt = if (customExtractionPrompt.isBlank()) null else customExtractionPrompt

        customResearchPrompt = prefs.getString("custom_research_prompt", "") ?: ""
        geminiService.customResearchPrompt = if (customResearchPrompt.isBlank()) null else customResearchPrompt

        customReportPrompt = prefs.getString("custom_report_prompt", "") ?: ""
        geminiService.customReportPrompt = if (customReportPrompt.isBlank()) null else customReportPrompt
    }

    fun saveGoogleSheetsUrl(url: String) {
        googleSheetsUrl = url.trim()
        val prefs = context.getSharedPreferences("byod_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("google_sheets_url", googleSheetsUrl).apply()
    }

    fun saveGeminiApiKey(key: String) {
        val trimmed = key.trim()
        geminiApiKey = trimmed
        val prefs = context.getSharedPreferences("byod_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("gemini_api_key", trimmed).apply()
        geminiService.customApiKey = trimmed.ifBlank { null }
        
        viewModelScope.launch {
            if (trimmed.isNotEmpty()) {
                _uiEvents.emit("🔑 Gemini API Key가 성공적으로 업데이트되었습니다.")
            } else {
                _uiEvents.emit("🔑 Gemini API Key가 초기화되었습니다 (기본 비밀값 사용).")
            }
        }
    }

    fun saveCustomExtractionPrompt(prompt: String) {
        val trimmed = prompt.trim()
        customExtractionPrompt = trimmed
        val prefs = context.getSharedPreferences("byod_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("custom_extraction_prompt", trimmed).apply()
        geminiService.customExtractionPrompt = if (trimmed.isBlank()) null else trimmed
        viewModelScope.launch {
            _uiEvents.emit("📝 AI 표현 추출 프롬프트가 업데이트되었습니다.")
        }
    }

    fun saveCustomResearchPrompt(prompt: String) {
        val trimmed = prompt.trim()
        customResearchPrompt = trimmed
        val prefs = context.getSharedPreferences("byod_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("custom_research_prompt", trimmed).apply()
        geminiService.customResearchPrompt = if (trimmed.isBlank()) null else trimmed
        viewModelScope.launch {
            _uiEvents.emit("📝 AI 주제별 리서치 프롬프트가 업데이트되었습니다.")
        }
    }

    fun saveCustomReportPrompt(prompt: String) {
        val trimmed = prompt.trim()
        customReportPrompt = trimmed
        val prefs = context.getSharedPreferences("byod_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("custom_report_prompt", trimmed).apply()
        geminiService.customReportPrompt = if (trimmed.isBlank()) null else trimmed
        viewModelScope.launch {
            _uiEvents.emit("📝 일일 보고서 및 보이스 프롬프트가 업데이트되었습니다.")
        }
    }

    private fun loadProfileSettings() {
        val prefs = context.getSharedPreferences("byod_prefs", Context.MODE_PRIVATE)
        val storedProfiles = prefs.getString("byod_profiles", "SHARED,ME,GIRLFRIEND") ?: "SHARED,ME,GIRLFRIEND"
        profileList = storedProfiles.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        val activeProj = prefs.getString("byod_active_profile", "SHARED") ?: "SHARED"
        if (activeProj in profileList) {
            _currentProfile.value = activeProj
        } else if (profileList.isNotEmpty()) {
            _currentProfile.value = profileList.first()
        } else {
            _currentProfile.value = "SHARED"
            profileList = listOf("SHARED")
        }
    }

    fun addProfile(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (profileList.contains(trimmed)) {
            viewModelScope.launch { _uiEvents.emit("⚠️ 이미 존재하는 프로필 이름입니다.") }
            return
        }
        val newList = profileList + trimmed
        saveProfileList(newList)
        switchProfile(trimmed)
        viewModelScope.launch { _uiEvents.emit("✨ 새 프로필 '${trimmed}'이(가) 추가 및 활성화되었습니다.") }
    }

    fun deleteProfile(name: String) {
        if (name == "SHARED") {
            viewModelScope.launch { _uiEvents.emit("⚠️ 기본 공동 프로필('SHARED')은 삭제할 수 없습니다.") }
            return
        }
        val newList = profileList.filter { it != name }
        saveProfileList(newList)
        if (_currentProfile.value == name) {
            switchProfile("SHARED")
        }
        viewModelScope.launch { _uiEvents.emit("🗑️ 프로필 '${name}'이(가) 삭제되었습니다.") }
    }

    private fun saveProfileList(list: List<String>) {
        profileList = list
        val prefs = context.getSharedPreferences("byod_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("byod_profiles", list.joinToString(",")).apply()
    }

    fun syncWithGoogleSheets() {
        val url = googleSheetsUrl.trim()
        if (url.isEmpty()) {
            viewModelScope.launch {
                _uiEvents.emit("동기화 설정 필요: 구글 앱스 스크립트 웹앱 주소(URL)를 상단 동기화 아이콘에서 먼저 입력해 주세요!")
            }
            return
        }

        viewModelScope.launch {
            isSyncing = true
            try {
                val activeProfileStr = _currentProfile.value
                // 1. Gather all words and chat logs from local Room DB
                // Since passive and active might be fetched differently, let's load all words directly 
                val currentLocalList = repository.getAllWordsFlow(activeProfileStr).stateIn(viewModelScope).value
                
                // Map words to transfer objects
                val itemsToSync = currentLocalList.map {
                    com.example.data.api.SheetWordItem(
                        itemType = it.itemType,
                        targetEnglish = it.targetEnglish,
                        targetMeaning = it.targetMeaning,
                        contextKr = it.contextKr,
                        targetHint = it.targetHint,
                        nativeExample = it.nativeExample,
                        nativeExampleKr = it.nativeExampleKr,
                        status = it.status,
                        createdAt = it.createdAt
                    )
                }

                // Gather and map all local chat logs matching this profile
                val localLogsList = repository.getAllChatLogsWithEnglish(activeProfileStr)
                val logsToSync = localLogsList.map {
                    com.example.data.api.SheetChatLogItem(
                        targetEnglish = it.targetEnglish,
                        userTypedSentence = it.userTypedSentence,
                        createdAt = it.createdAt
                    )
                }

                // 2. Perform background web call (preserving POST body on redirect!)
                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.data.api.GoogleSheetsSyncService.performSync(url, itemsToSync, logsToSync)
                }

                if (response.status == "success" && response.words != null) {
                    val cloudWords = response.words.map {
                        Word(
                            itemType = it.itemType,
                            targetEnglish = it.targetEnglish,
                            targetMeaning = it.targetMeaning,
                            contextKr = it.contextKr,
                            targetHint = it.targetHint,
                            nativeExample = it.nativeExample,
                            nativeExampleKr = it.nativeExampleKr,
                            status = it.status,
                            createdAt = it.createdAt,
                            profile = activeProfileStr
                        )
                    }

                    // 3. Sync and merge both words and chat logs into local database
                    repository.syncWordsAndLogs(activeProfileStr, cloudWords, response.chatLogs ?: emptyList())
                    
                    // Update sync metadata
                    val currentFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
                    lastSyncTime = "최근 동기화: $currentFormat"
                    val prefs = context.getSharedPreferences("byod_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("last_sheets_sync_time", lastSyncTime).apply()

                    val wordCount = cloudWords.size
                    val logCount = response.chatLogs?.size ?: 0
                    _uiEvents.emit("☁️ 양방향 동기화 완벽 갱신! (단어 ${wordCount}개, 영작문 ${logCount}개 동기화 완료)")
                } else {
                    _uiEvents.emit("동기화 실패: ${response.message ?: "스크립트 오류"}")
                }
            } catch (e: Exception) {
                Log.e("BYODViewModel", "Sync network error", e)
                _uiEvents.emit("☁️ 동기화 오류: 웹앱 URL 또는 네트워크 상태를 점검해 보세요.")
            } finally {
                isSyncing = false
            }
        }
    }

    fun switchProfile(profile: String) {
        if (profileList.contains(profile)) {
            _currentProfile.value = profile
            selectedCategoryForQuiz = "전체 코스"
            val prefs = context.getSharedPreferences("byod_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("byod_active_profile", profile).apply()
            viewModelScope.launch {
                seedDefaultWordsIfEmpty(profile)
                _uiEvents.emit("👤 프로필이 '${profile}'(으)로 변경되었습니다.")
                refreshTodayProgress()
            }
        }
    }

    /**
     * Seeds the local pre-populated 10 words if the profile's vocabulary database is dry.
     */
    private suspend fun seedDefaultWordsIfEmpty(profile: String) {
        viewModelScope.launch {
            val wordsList = repository.getPassiveWords(profile, 5)
            if (wordsList.isEmpty()) {
                Log.d("BYODViewModel", "Database empty for profile $profile, seeding default items...")
                val defaultSeeds = listOf(
                    Word(
                        itemType = "VERB",
                        targetEnglish = "run out of",
                        targetMeaning = "~이 다 떨어지다",
                        contextKr = "차 기름이 다 떨어져 가고 있어. 우리 주유소 찾아야 해.",
                        targetHint = "r__ ___ __",
                        nativeExample = "We are running out of gas. We need to find a gas station.",
                        nativeExampleKr = "우리는 기름이 다 떨어져 가고 있어. 주유소를 찾아야 해.",
                        profile = profile,
                        category = "원어민 필수 구동사"
                    ),
                    Word(
                        itemType = "VERB",
                        targetEnglish = "come up with",
                        targetMeaning = "~을 생각해내다",
                        contextKr = "우리 마케팅 회의 때 좋은 아이디어가 아직 안 떠올랐어.",
                        targetHint = "c___ __ ____",
                        nativeExample = "I can't come up with a good idea for the meeting yet.",
                        nativeExampleKr = "나는 아직 회의를 위한 좋은 아이디어를 생각해낼 수 없어.",
                        profile = profile,
                        category = "원어민 필수 구동사"
                    ),
                    Word(
                        itemType = "VERB",
                        targetEnglish = "get along with",
                        targetMeaning = "~와 잘 지내다",
                        contextKr = "새로 이사 온 룸메이트랑 별일 없이 잘 지내고 있어?",
                        targetHint = "g__ a____ ____",
                        nativeExample = "Are you getting along with your new roommate?",
                        nativeExampleKr = "새로운 룸메이트랑 잘 지내고 있니?",
                        profile = profile,
                        category = "원어민 필수 구동사"
                    ),
                    Word(
                        itemType = "VERB",
                        targetEnglish = "look forward to",
                        targetMeaning = "~을 기대하다 (고대하다)",
                        contextKr = "이번 주말에 드디어 너 만나는 거 정말 기대하고 있어.",
                        targetHint = "l___ _______ __",
                        nativeExample = "I am looking forward to meeting you this weekend.",
                        nativeExampleKr = "나는 이번 주말에 너를 만나는 것을 고대하고 있어.",
                        profile = profile,
                        category = "원어민 필수 구동사"
                    ),
                    Word(
                        itemType = "VERB",
                        targetEnglish = "put off",
                        targetMeaning = "미루다 (연기하다)",
                        contextKr = "더 이상 무서워서 치과 예약 미루면 안 돼.",
                        targetHint = "p__ ___",
                        nativeExample = "You shouldn't put off your dental appointment any longer.",
                        nativeExampleKr = "더 이상 치과 예약을 미루면 안 돼.",
                        profile = profile,
                        category = "원어민 필수 구동사"
                    ),
                    Word(
                        itemType = "CHUNK",
                        targetEnglish = "take care of",
                        targetMeaning = "~을 돌보다 (처리하다)",
                        contextKr = "오늘 밤 우리 강아지 점심 좀 챙겨주고 돌봐줄 수 있니?",
                        targetHint = "t___ ____ __",
                        nativeExample = "Can you take care of our dog for a bit tonight?",
                        nativeExampleKr = "오늘 밤 잠시 우리 강아지 좀 돌봐줄 수 있어?",
                        profile = profile,
                        category = "기초 회화 청크"
                    ),
                    Word(
                        itemType = "VERB",
                        targetEnglish = "bring up",
                        targetMeaning = "(이야기/안건) 이야기를 꺼내다",
                        contextKr = "오늘 저녁 먹을 때 그 우울한 화제는 다시 꺼내지 마.",
                        targetHint = "b____ __",
                        nativeExample = "Don't bring up that sad topic during dinner tonight.",
                        nativeExampleKr = "오늘 저녁 식사 시간에 그 슬픈 이야기를 꺼내지 마라.",
                        profile = profile,
                        category = "기초 회화 청크"
                    ),
                    Word(
                        itemType = "VERB",
                        targetEnglish = "turn out",
                        targetMeaning = "알고 보니 ~인 것으로 드러나다",
                        contextKr = "결국 그 무서운 소문은 오해이자 루머인 것으로 드러났어.",
                        targetHint = "t___ ___",
                        nativeExample = "It turned out that the scary rumor was a total misunderstanding.",
                        nativeExampleKr = "그 무서운 소문은 결국 완전한 오해인 것으로 드러났어.",
                        profile = profile,
                        category = "기초 회화 청크"
                    ),
                    Word(
                        itemType = "VERB",
                        targetEnglish = "break down",
                        targetMeaning = "고장 나다 (차나 기계)",
                        contextKr = "퇴근길 한복판에서 노트북이랑 컴퓨터가 고장 났어.",
                        targetHint = "b____ ____",
                        nativeExample = "My laptop suddenly broke down on my way home.",
                        nativeExampleKr = "집으로 돌아오는 길에 내 노트북이 갑자기 고장 났어.",
                        profile = profile,
                        category = "기초 회화 청크"
                    ),
                    Word(
                        itemType = "CHUNK",
                        targetEnglish = "make sure",
                        targetMeaning = "반드시 ~하다 (확인하다)",
                        contextKr = "집을 외출해서 나서기 전에 불이 다 꺼졌는지 꼭 확인해.",
                        targetHint = "m___ ____",
                        nativeExample = "Make sure you turn off all the lights before leaving the house.",
                        nativeExampleKr = "집을 나서기 전에 반드시 전등을 모두 켰는지 확인해라.",
                        profile = profile,
                        category = "기초 회화 청크"
                    )
                )
                repository.insertWords(defaultSeeds)
            }
        }
    }

    /**
     * Resets the active quiz states
     */
    fun resetQuiz() {
        activeQuizWords = emptyList()
        currentWordIndex = 0
        quizStep = 1
        userSentenceInput = ""
    }

    /**
     * Loads 10 PASSIVE words from Room to trigger the daily speaking challenge
     */
    fun startDailyQuiz() {
        viewModelScope.launch {
            val passives = if (selectedCategoryForQuiz == "전체 코스") {
                repository.getPassiveWords(_currentProfile.value, 10)
            } else {
                repository.getPassiveWordsByCategory(_currentProfile.value, selectedCategoryForQuiz, 10)
            }
            if (passives.isEmpty()) {
                if (selectedCategoryForQuiz == "전체 코스") {
                    _uiEvents.emit("학습할 단어가 없습니다. [AI 자료 추천] 탭에서 원문 텍스트나 리서치 기능을 통해 새로운 표현을 생성해 주세요!")
                } else {
                    _uiEvents.emit("[$selectedCategoryForQuiz] 코스에 아직 학습 대기 중인 표현이 없습니다. [AI 자료 추천] 탭에서 해당 키워드로 리서치해 표현을 생성해 보세요!")
                }
            } else {
                activeQuizWords = passives
                currentWordIndex = 0
                quizStep = 1
                userSentenceInput = ""
            }
        }
    }

    fun nextQuizStep() {
        if (quizStep < 3) {
            quizStep++
        }
    }

    /**
     * Submits user typed sentence. Saves to Logs, puts status as ACTIVE immediately.
     */
    fun submitQuizSentence() {
        val currentWord = activeQuizWords.getOrNull(currentWordIndex) ?: return
        if (userSentenceInput.trim().isEmpty()) {
            viewModelScope.launch {
                _uiEvents.emit("영작 문장을 입력해 주세요!")
            }
            return
        }

        viewModelScope.launch {
            repository.addChatLogAndCompleteWord(currentWord.id, userSentenceInput.trim())
            refreshTodayProgress()
            
            // Move to next word or complete
            if (currentWordIndex < activeQuizWords.size - 1) {
                currentWordIndex++
                quizStep = 1
                userSentenceInput = ""
            } else {
                quizStep = 4 // Completed all
                _uiEvents.emit("축하합니다! 오늘의 10개 회화 퀴즈를 모두 완료했습니다. 마감 리포트를 보고 피드백을 확인해 보세요!")
                checkAndFetchTodayReport()
            }
        }
    }

    fun forceFinishQuiz() {
        quizStep = 4
    }

    suspend fun refreshTodayProgress() {
        val list = repository.getTodayCompletedLessons(_currentProfile.value)
        todayCompletedLessons = list
    }

    /**
     * Calls Gemini to extract up to 5 English chunks from copied TV transcripts or texts.
     */
    fun extractWordsFromInput() {
        if (extractionInputText.trim().isEmpty()) {
            viewModelScope.launch {
                _uiEvents.emit("영어 대사, 유튜브 자막 등 텍스트를 입력해 주세요!")
            }
            return
        }

        viewModelScope.launch {
            isExtracting = true
            try {
                if (!geminiService.hasValidApiKey()) {
                    _uiEvents.emit("API_KEY_REQUIRED")
                    isExtracting = false
                    return@launch
                }
                val result = geminiService.extractLearningMaterials(extractionInputText.trim())
                if (result.isEmpty()) {
                    _uiEvents.emit("중요한 영어 표현 선별에 실패했습니다. 다른 텍스트를 입력해 보세요.")
                } else {
                    extractedWordsList = result
                    _uiEvents.emit("AI 분석 완료! ${result.size}개의 유용한 회화 표현을 선별했습니다.")
                }
            } catch (e: Exception) {
                Log.e("BYODViewModel", "Extraction error", e)
                val isApiKeyError = e is retrofit2.HttpException && (e.code() == 400 || e.code() == 403)
                if (e.message == "API_KEY_MISSING" || isApiKeyError) {
                    _uiEvents.emit("API_KEY_REQUIRED")
                } else {
                    _uiEvents.emit("AI 분석 중 네트워크 오류가 발생했습니다: ${e.message}")
                }
            } finally {
                isExtracting = false
            }
        }
    }

    /**
     * Saves selected extracted words into the database.
     */
    fun saveExtractedWords(selectedIndices: List<Int>, customCategory: String? = null) {
        if (extractedWordsList.isEmpty()) return
        val finalCategory = customCategory?.trim()?.ifBlank { null } ?: "일용용 추출"
        viewModelScope.launch {
            val wordsToSave = selectedIndices.map { index ->
                val item = extractedWordsList[index]
                Word(
                    itemType = item.itemType,
                    targetEnglish = item.targetEnglish,
                    targetMeaning = item.targetMeaning,
                    contextKr = item.contextKr,
                    targetHint = item.targetHint,
                    nativeExample = item.nativeExample,
                    nativeExampleKr = item.nativeExampleKr,
                    status = "PASSIVE",
                    profile = _currentProfile.value,
                    category = finalCategory
                )
            }
            repository.insertWords(wordsToSave)
            _uiEvents.emit("✨ [${finalCategory}] 코스에 ${wordsToSave.size}개의 핵심 표현이 등록되었습니다!")
            extractedWordsList = emptyList()
            extractionInputText = ""
            researchTopicInput = ""
        }
    }

    /**
     * Research a conversation topic or situational keyword using Gemini AI
     */
    fun researchTopicExpressions() {
        val topic = researchTopicInput.trim()
        if (topic.isEmpty()) {
            viewModelScope.launch {
                _uiEvents.emit("학습하고 싶은 주제나 상황 키워드를 입력해 주세요!")
            }
            return
        }

        viewModelScope.launch {
            isResearching = true
            try {
                if (!geminiService.hasValidApiKey()) {
                    _uiEvents.emit("API_KEY_REQUIRED")
                    isResearching = false
                    return@launch
                }
                
                val result = geminiService.researchTopicExpressions(topic)
                if (result.isEmpty()) {
                    _uiEvents.emit("해당 주제에 적합한 영어 회화 표현 추출에 실패했습니다. 다른 키워드로 검색해 보세요.")
                } else {
                    extractedWordsList = result
                    _uiEvents.emit("🔍 '$topic' 주제 완벽 분석! 실용 표현 ${result.size}개를 엄선해 연계 스키마를 구성했습니다.")
                }
            } catch (e: Exception) {
                Log.e("BYODViewModel", "Research topic error", e)
                val isApiKeyError = e is retrofit2.HttpException && (e.code() == 400 || e.code() == 403)
                if (e.message == "API_KEY_MISSING" || isApiKeyError) {
                    _uiEvents.emit("API_KEY_REQUIRED")
                } else {
                    _uiEvents.emit("AI 리서치 중 오류가 발생했습니다: ${e.message}")
                }
            } finally {
                isResearching = false
            }
        }
    }

    /**
     * Checks if today's report is already generated.
     */
    fun checkAndFetchTodayReport() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        viewModelScope.launch {
            val existing = repository.getReportForDate(_currentProfile.value, todayStr)
            generatedReportForToday = existing
        }
    }

    /**
     * Generates detailed feedback and Gemini Live voice chat prompt for today's logs.
     */
    fun generateDailyReport() {
        viewModelScope.launch {
            isGeneratingReport = true
            try {
                if (!geminiService.hasValidApiKey()) {
                    _uiEvents.emit("API_KEY_REQUIRED")
                    isGeneratingReport = false
                    return@launch
                }

                refreshTodayProgress()
                if (todayCompletedLessons.isEmpty()) {
                    _uiEvents.emit("오늘 진행한 영작 챌린지 로그가 최소 1개 이상 필요합니다.")
                    isGeneratingReport = false
                    return@launch
                }

                val reportMarkdown = geminiService.generateDailyReport(todayCompletedLessons)
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val newReport = DailyReport(
                    reportDate = todayStr,
                    markdownContent = reportMarkdown,
                    profile = _currentProfile.value
                )
                repository.insertReport(newReport)
                generatedReportForToday = newReport
                _uiEvents.emit("AI 종합 일일 보고서가 생성되었습니다!")
            } catch (e: Exception) {
                Log.e("BYODViewModel", "Report Generation error", e)
                val isApiKeyError = e is retrofit2.HttpException && (e.code() == 400 || e.code() == 403)
                if (e.message == "API_KEY_MISSING" || isApiKeyError) {
                    _uiEvents.emit("API_KEY_REQUIRED")
                } else {
                    _uiEvents.emit("보고서 생성 실패: ${e.message}")
                }
            } finally {
                isGeneratingReport = false
            }
        }
    }

    /**
     * Handles copying the report/prompt to clipboard and launching Gemini App.
     */
    fun copyReportAndLaunchGemini(context: Context, content: String, reportId: Long) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Gemini Live Voice Prompt", content)
        clipboard.setPrimaryClip(clip)

        // Mark report as copied in Database
        viewModelScope.launch {
            repository.updateReportCopied(reportId, true)
            checkAndFetchTodayReport()
        }

        Toast.makeText(context, "프롬프트가 클립보드에 복사되었습니다! 이동할 수 있게 보이스 가이드를 로드합니다.", Toast.LENGTH_LONG).show()

        // Try opening the official Gemini App or falls back to Web
        val geminiWebUrl = "https://gemini.google.com/"
        val geminiAppIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(geminiWebUrl)
            // Look for Google App package to handle voice/Live if installed
            setPackage("com.google.android.apps.bard")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(geminiAppIntent)
        } catch (e: Exception) {
            // Fallback: If App is not installed, open directly in mobile browser
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(geminiWebUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    fun addManualWord(word: Word) {
        viewModelScope.launch {
            repository.insertWord(word)
            _uiEvents.emit("새로운 생활 회화 표현이 로컬 단어장에 등록되었습니다! (0ms)")
        }
    }

    fun removeExpression(wordId: Long) {
        viewModelScope.launch {
            repository.deleteWord(wordId)
            _uiEvents.emit("표현이 삭제되었습니다.")
        }
    }
}

class BYODViewModelFactory(
    private val repository: StudyRepository,
    private val geminiService: GeminiService,
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BYODViewModel::class.java)) {
            return BYODViewModel(repository, geminiService, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
