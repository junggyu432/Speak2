package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    // Words queries
    @Query("SELECT * FROM words WHERE profile = :profile ORDER BY id ASC")
    fun getAllWordsFlow(profile: String): Flow<List<Word>>

    @Query("SELECT * FROM words WHERE profile = :profile AND status = 'PASSIVE' ORDER BY id ASC LIMIT :limit")
    suspend fun getPassiveWords(profile: String, limit: Int): List<Word>

    @Query("SELECT * FROM words WHERE profile = :profile AND status = 'PASSIVE' AND category = :category ORDER BY id ASC LIMIT :limit")
    suspend fun getPassiveWordsByCategory(profile: String, category: String, limit: Int): List<Word>

    @Query("SELECT DISTINCT category FROM words WHERE profile = :profile ORDER BY category ASC")
    fun getDistinctCategoriesFlow(profile: String): Flow<List<String>>

    @Query("SELECT * FROM words WHERE profile = :profile AND status = 'ACTIVE' ORDER BY id DESC")
    fun getActiveWordsFlow(profile: String): Flow<List<Word>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWord(word: Word): Long

    @Query("SELECT * FROM words WHERE target_english = :targetEnglish AND profile = :profile LIMIT 1")
    suspend fun getWordByEnglish(targetEnglish: String, profile: String): Word?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWords(words: List<Word>)

    @Update
    suspend fun updateWord(word: Word)

    @Query("UPDATE words SET status = :status WHERE id = :wordId")
    suspend fun updateWordStatus(wordId: Long, status: String)

    @Query("DELETE FROM words WHERE id = :wordId")
    suspend fun deleteWord(wordId: Long)

    @Query("DELETE FROM words WHERE profile = :profile")
    suspend fun clearAllWords(profile: String)

    // Chat Logs queries
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatLog(log: ChatLog): Long

    @Query("""
        SELECT * FROM words 
        WHERE profile = :profile 
          AND id IN (SELECT DISTINCT word_id FROM daily_chat_logs WHERE created_at >= :since)
    """)
    suspend fun getWordsWithTodayLogs(profile: String, since: Long): List<Word>

    @Transaction
    @Query("""
        SELECT * FROM words 
        WHERE profile = :profile 
          AND id IN (SELECT DISTINCT word_id FROM daily_chat_logs WHERE created_at >= :since)
        ORDER BY created_at DESC
    """)
    suspend fun getWordsWithLogsForToday(profile: String, since: Long): List<WordWithLogs>

    @Query("SELECT * FROM daily_chat_logs WHERE word_id = :wordId ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestLogByWord(wordId: Long): ChatLog?

    @Query("SELECT * FROM daily_chat_logs WHERE word_id = :wordId AND user_typed_sentence = :userTypedSentence LIMIT 1")
    suspend fun getChatLogBySentence(wordId: Long, userTypedSentence: String): ChatLog?

    @Query("""
        SELECT w.target_english as targetEnglish, c.user_typed_sentence as userTypedSentence, c.created_at as createdAt
        FROM daily_chat_logs c
        INNER JOIN words w ON c.word_id = w.id
        WHERE w.profile = :profile
    """)
    suspend fun getAllChatLogsWithEnglish(profile: String): List<ChatLogWithEnglish>

    @Query("SELECT COUNT(*) FROM daily_chat_logs WHERE created_at >= :since")
    fun getTodayLogCountFlow(since: Long): Flow<Int>

    // Daily Reports queries
    @Query("SELECT * FROM daily_reports WHERE profile = :profile ORDER BY report_date DESC")
    fun getReportsFlow(profile: String): Flow<List<DailyReport>>

    @Query("SELECT * FROM daily_reports WHERE profile = :profile AND report_date = :reportDate LIMIT 1")
    suspend fun getReportForDate(profile: String, reportDate: String): DailyReport?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: DailyReport)

    @Query("UPDATE daily_reports SET is_copied = :isCopied WHERE id = :reportId")
    suspend fun updateReportCopiedStatus(reportId: Long, isCopied: Int)
}

data class WordWithLogs(
    @Embedded val word: Word,
    @Relation(
        parentColumn = "id",
        entityColumn = "word_id"
    )
    val logs: List<ChatLog>
)

data class ChatLogWithEnglish(
    val targetEnglish: String,
    val userTypedSentence: String,
    val createdAt: Long
)
