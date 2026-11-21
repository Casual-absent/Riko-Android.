package com.casualabsent.riko;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Private on-device chat history. API credentials are not stored in this database. */
public final class ChatDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "riko_chat.db";
    private static final int DATABASE_VERSION = 1;
    public static final int MAX_CONTEXT_MESSAGES = 24;

    private static final String TABLE_MESSAGES = "messages";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_ROLE = "role";
    private static final String COLUMN_CONTENT = "content";
    private static final String COLUMN_CREATED_AT = "created_at";

    public ChatDatabase(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_MESSAGES + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_ROLE + " TEXT NOT NULL CHECK (" + COLUMN_ROLE
                + " IN ('user', 'assistant')), "
                + COLUMN_CONTENT + " TEXT NOT NULL, "
                + COLUMN_CREATED_AT + " INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_messages_created ON "
                + TABLE_MESSAGES + "(" + COLUMN_ID + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Schema version 1 is the first Android release with persistent chat history.
        // Future schema changes should be added here as explicit migrations.
    }

    /** Returns the latest bounded context in chronological order. */
    public synchronized List<ChatMessage> loadRecentMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.query(
                TABLE_MESSAGES,
                new String[]{COLUMN_ROLE, COLUMN_CONTENT},
                null,
                null,
                null,
                null,
                COLUMN_ID + " DESC",
                String.valueOf(MAX_CONTEXT_MESSAGES))) {
            while (cursor.moveToNext()) {
                messages.add(new ChatMessage(
                        cursor.getString(0),
                        cursor.getString(1)));
            }
        }

        Collections.reverse(messages);
        return messages;
    }

    /** Saves a complete user/assistant turn atomically, then trims old context. */
    public synchronized void saveTurn(String userText, String assistantText) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            insertMessage(db, "user", userText);
            insertMessage(db, "assistant", assistantText);
            db.execSQL("DELETE FROM " + TABLE_MESSAGES + " WHERE " + COLUMN_ID
                    + " NOT IN (SELECT " + COLUMN_ID + " FROM " + TABLE_MESSAGES
                    + " ORDER BY " + COLUMN_ID + " DESC LIMIT "
                    + MAX_CONTEXT_MESSAGES + ")");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void clearHistory() {
        getWritableDatabase().delete(TABLE_MESSAGES, null, null);
    }

    private void insertMessage(SQLiteDatabase db, String role, String content) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_ROLE, role);
        values.put(COLUMN_CONTENT, content == null ? "" : content);
        values.put(COLUMN_CREATED_AT, System.currentTimeMillis());
        db.insertOrThrow(TABLE_MESSAGES, null, values);
    }
}
