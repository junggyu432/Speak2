package com.example.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "words",
    indices = [Index(value = ["target_english", "profile"], unique = true)]
)
data class Word(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "item_type") val itemType: String = "VERB", // 'VERB' or 'CHUNK'
    @ColumnInfo(name = "target_english") val targetEnglish: String,
    @ColumnInfo(name = "target_meaning") val targetMeaning: String,
    @ColumnInfo(name = "context_kr") val contextKr: String,
    @ColumnInfo(name = "target_hint") val targetHint: String,
    @ColumnInfo(name = "native_example") val nativeExample: String,
    @ColumnInfo(name = "native_example_kr") val nativeExampleKr: String,
    val status: String = "PASSIVE", // 'PASSIVE' or 'ACTIVE'
    val profile: String = "ME", // "ME" or "GIRLFRIEND"
    val category: String = "기초 필수",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "daily_chat_logs",
    foreignKeys = [
        ForeignKey(
            entity = Word::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["word_id"])]
)
data class ChatLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "word_id") val wordId: Long,
    @ColumnInfo(name = "user_typed_sentence") val userTypedSentence: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "daily_reports",
    indices = [Index(value = ["report_date", "profile"], unique = true)]
)
data class DailyReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "report_date") val reportDate: String, // format: YYYY-MM-DD
    @ColumnInfo(name = "markdown_content") val markdownContent: String,
    @ColumnInfo(name = "is_copied") val isCopied: Int = 0,
    val profile: String = "ME", // "ME" or "GIRLFRIEND"
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
