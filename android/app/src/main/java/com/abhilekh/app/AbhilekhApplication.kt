package com.abhilekh.app

import android.app.Application
import androidx.room.Room
import com.abhilekh.app.core.pdf.PdfBoxEngine
import com.abhilekh.app.data.db.AppDatabase

class AbhilekhApplication : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Apache PDFBox Android resource loader
        PdfBoxEngine.init(this)

        // Initialize Room Database
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "abhilekh_database"
        ).fallbackToDestructiveMigration().build()
    }
}
