package com.example.data.database

import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class StudyRepository(private val wordDao: WordDao) {

    fun getAllWordsFlow(profile: String): Flow<List<Word>> = wordDao.getAllWordsFlow(profile)

    fun getActiveWordsFlow(profile: String): Flow<List<Word>> = wordDao.getActiveWordsFlow(profile)

    suspend fun getPassiveWords(profile: String, limit: Int = 10): List<Word> {
        return wordDao.getPassiveWords(profile, limit)
    }

    suspend fun getPassiveWordsByCategory(profile: String, category: String, limit: Int = 10): List<Word> {
        return wordDao.getPassiveWordsByCategory(profile, category, limit)
    }

    fun getDistinctCategoriesFlow(profile: String): Flow<List<String>> = wordDao.getDistinctCategoriesFlow(profile)

    suspend fun insertWord(word: Word): Long {
        return wordDao.insertWord(word)
    }

    suspend fun insertWords(words: List<Word>) {
        wordDao.insertWords(words)
    }

    suspend fun updateWord(word: Word) {
        wordDao.updateWord(word)
    }

    suspend fun updateWordStatus(wordId: Long, status: String) {
        wordDao.updateWordStatus(wordId, status)
    }

    suspend fun deleteWord(wordId: Long) {
        wordDao.deleteWord(wordId)
    }

    suspend fun clearAllWords(profile: String) {
        wordDao.clearAllWords(profile)
    }

    suspend fun addChatLogAndCompleteWord(wordId: Long, sentence: String) {
        val log = ChatLog(wordId = wordId, userTypedSentence = sentence)
        wordDao.insertChatLog(log)
        wordDao.updateWordStatus(wordId, "ACTIVE")
    }

    fun getTodayLogCountFlow(): Flow<Int> {
        return wordDao.getTodayLogCountFlow(getStartOfTodayMillis())
    }

    suspend fun getTodayCompletedLessons(profile: String): List<WordWithLogs> {
        return wordDao.getWordsWithLogsForToday(profile, getStartOfTodayMillis())
    }

    fun getReportsFlow(profile: String): Flow<List<DailyReport>> {
        return wordDao.getReportsFlow(profile)
    }

    suspend fun getReportForDate(profile: String, reportDate: String): DailyReport? {
        return wordDao.getReportForDate(profile, reportDate)
    }

    suspend fun insertReport(report: DailyReport) {
        wordDao.insertReport(report)
    }

    suspend fun updateReportCopied(reportId: Long, isCopied: Boolean) {
        wordDao.updateReportCopiedStatus(reportId, if (isCopied) 1 else 0)
    }

    suspend fun getAllChatLogsWithEnglish(profile: String): List<ChatLogWithEnglish> {
        return wordDao.getAllChatLogsWithEnglish(profile)
    }

    suspend fun syncWordsAndLogs(
        profile: String,
        cloudWords: List<Word>,
        cloudLogs: List<com.example.data.api.SheetChatLogItem>
    ) {
        // 1. Sync words list
        cloudWords.forEach { word ->
            val existing = wordDao.getWordByEnglish(word.targetEnglish, word.profile)
            if (existing == null) {
                wordDao.insertWord(word)
            } else {
                // If cloud has status ACTIVE but local has PASSIVE, update it
                if (existing.status == "PASSIVE" && word.status == "ACTIVE") {
                    wordDao.updateWordStatus(existing.id, "ACTIVE")
                }
            }
        }

        // 2. Sync chat logs
        cloudLogs.forEach { logItem ->
            val word = wordDao.getWordByEnglish(logItem.targetEnglish, profile)
            if (word != null) {
                val existingLog = wordDao.getChatLogBySentence(word.id, logItem.userTypedSentence)
                if (existingLog == null) {
                    val newLog = ChatLog(
                        wordId = word.id,
                        userTypedSentence = logItem.userTypedSentence,
                        createdAt = logItem.createdAt
                    )
                    wordDao.insertChatLog(newLog)
                }
            }
        }
    }

    companion object {
        fun getStartOfTodayMillis(): Long {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.timeInMillis
        }
    }
}
