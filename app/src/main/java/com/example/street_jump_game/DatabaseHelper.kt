package com.example.street_jump_game

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class WorkoutSession(
    val id: Long = 0,
    val dateString: String,
    val routineName: String,
    val jumpCount: Int,
    val durationSeconds: Int
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "StreetJumpDB.db"

        const val TABLE_WORKOUTS = "workouts"
        const val COLUMN_ID = "_id"
        const val COLUMN_DATE = "dateString"
        const val COLUMN_ROUTINE_NAME = "routineName"
        const val COLUMN_JUMP_COUNT = "jumpCount"
        const val COLUMN_DURATION = "durationSeconds"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE $TABLE_WORKOUTS ("
                + "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "$COLUMN_DATE TEXT,"
                + "$COLUMN_ROUTINE_NAME TEXT,"
                + "$COLUMN_JUMP_COUNT INTEGER,"
                + "$COLUMN_DURATION INTEGER)")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WORKOUTS")
        onCreate(db)
    }

    fun insertWorkout(session: WorkoutSession): Long {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_DATE, session.dateString)
        values.put(COLUMN_ROUTINE_NAME, session.routineName)
        values.put(COLUMN_JUMP_COUNT, session.jumpCount)
        values.put(COLUMN_DURATION, session.durationSeconds)

        val id = db.insert(TABLE_WORKOUTS, null, values)
        db.close()
        return id
    }

    fun getAllWorkouts(): List<WorkoutSession> {
        val workoutList = mutableListOf<WorkoutSession>()
        val selectQuery = "SELECT * FROM $TABLE_WORKOUTS ORDER BY $COLUMN_ID DESC"
        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val dateStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE))
                val routineName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ROUTINE_NAME))
                val jumpCount = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_JUMP_COUNT))
                val duration = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DURATION))

                workoutList.add(WorkoutSession(id, dateStr, routineName, jumpCount, duration))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return workoutList
    }
}
