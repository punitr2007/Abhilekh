package com.abhilekh.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "folder_id") val folderId: String? = null,
    @ColumnInfo(name = "page_count") val pageCount: Int = 1,
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long = 0,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String,
    @ColumnInfo(name = "pdf_path") val pdfPath: String,
    @ColumnInfo(name = "export_type") val exportType: String = "standard",
    @ColumnInfo(name = "has_masked_aadhaar") val hasMaskedAadhaar: Boolean = false,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("document_id")]
)
data class PageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "page_number") val pageNumber: Int,
    @ColumnInfo(name = "image_path") val imagePath: String,
    @ColumnInfo(name = "thumb_path") val thumbPath: String,
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "filter_type") val filterType: String = "illumination_division",
    @ColumnInfo(name = "ocr_text") val ocrText: String? = null,
    @ColumnInfo(name = "is_masked") val isMasked: Boolean = false
)
