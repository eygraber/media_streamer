/*
 * Copyright 2012 Eliezer Graber (Custom Programming Solutions)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.customprogrammingsolutions.MediaStreamer;

import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class RecentsDBHelper {
    public static final String KEY_ROWID = "_id";
    public static final String KEY_URL = "url";
    public static final String KEY_COUNT = "count";
    public static final String KEY_CREATED = "date";
    
    public static final int COLUMN_ROWID = 0;
    public static final int COLUMN_URL = 1;
    public static final int COLUMN_COUNT = 2;
    public static final int COLUMN_CREATED = 3;
 
    private static final String DATABASE_TABLE = "recents";
    private static final int DATABASE_VERSION = 1;
 
    private static final String DATABASE_CREATE =
        "CREATE TABLE " + DATABASE_TABLE + " (" + KEY_ROWID + " integer primary key autoincrement, " + KEY_URL + " text not null, " + KEY_COUNT + " integer not null, " + KEY_CREATED + " date);";
 
    private final Context context; 
    
    private final static String TAG = "MediaStreamer";
 
    private DatabaseHelper DBHelper;
    private SQLiteDatabase db;
 
    public RecentsDBHelper(Context ctx){
        this.context = ctx;
        DBHelper = new DatabaseHelper(context);
    }
 
    private static class DatabaseHelper extends SQLiteOpenHelper{
        DatabaseHelper(Context context){
            super(context, DATABASE_TABLE, null, DATABASE_VERSION);
        }
 
        @Override
        public void onCreate(SQLiteDatabase db){
            db.execSQL(DATABASE_CREATE);
        }
 
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion){
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + DATABASE_TABLE);
            onCreate(db);
        }
    }    
 
    //---opens the database---
    public RecentsDBHelper open() throws SQLException{
        db = DBHelper.getWritableDatabase();
        return this;
    }
 
    //---closes the database---    
    public void close(){
        DBHelper.close();
    }
 
    //---insert a recent into the database---
    public long insertRecent(String url){
    	Cursor c = getRecent(url);
    	if(c.getCount() > 0){
    		updateRecent(url, c.getInt(COLUMN_COUNT) + 1);
    		c.close();
    		return -1;
    	}
    	c.close();
    	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); 
    	Date date = new Date();
    	String q = "INSERT INTO " + DATABASE_TABLE + " VALUES (NULL, '" + url + "', 1, '" + dateFormat.format(date) + "')";
    	db.execSQL(q);
    	return 0;
    }
 
    //---deletes a particular recent by id---
    public boolean deleteRecent(long rowId){
    	String q = "DELETE FROM " + DATABASE_TABLE + " WHERE " + KEY_ROWID + " = " + Long.toString(rowId);
    	db.execSQL(q);
    	return true;
    }
    
    //---deletes a particular recent by url---
    public boolean deleteRecent(String url){
    	String q = "DELETE FROM " + DATABASE_TABLE + " WHERE " + KEY_URL + " = '" + url + "'";
    	db.execSQL(q);
    	return true;
    }
 
    //---retrieves all the recents---
    public Cursor getAllRecents(){
    	String q = "SELECT * FROM " + DATABASE_TABLE + " ORDER BY " + KEY_CREATED + " DESC";
    	return db.rawQuery(q, null);
        /*return db.query(DATABASE_TABLE, new String[] {
        		KEY_ROWID, 
        		KEY_URL,
        		KEY_COUNT,
        		KEY_CREATED}, 
                null, 
                null, 
                null, 
                null, 
                KEY_CREATED + " DESC");*/
    }
 
    //---retrieves a particular recent by id---
    public Cursor getRecent(long rowId) throws SQLException{
    	String q = "SELECT * FROM " + DATABASE_TABLE + " WHERE " + KEY_ROWID + " = ? LIMIT 1";
    	Cursor mCursor = db.rawQuery(q, new String[] {Long.toString(rowId)});
        /*Cursor mCursor =
                db.query(true, DATABASE_TABLE, new String[] {
                		KEY_ROWID,
                		KEY_URL, 
                		KEY_COUNT,
                		KEY_CREATED}, 
                		KEY_ROWID + "=" + rowId, 
                		null,
                		null, 
                		null, 
                		KEY_CREATED + " DESC", 
                		"1");*/
        if (mCursor != null) {
            mCursor.moveToFirst();
        }
        return mCursor;
    }
    
    //---retrieves a particular recent by url---
    public Cursor getRecent(String url) throws SQLException{
    	String q = "SELECT * FROM " + DATABASE_TABLE + " WHERE " + KEY_URL + " = ? LIMIT 1";
    	Cursor mCursor = db.rawQuery(q, new String[] {url});
        /*Cursor mCursor =
                db.query(true, DATABASE_TABLE, new String[] {
                		KEY_ROWID,
                		KEY_URL, 
                		KEY_COUNT,
                		KEY_CREATED}, 
                		KEY_URL + "=" + url, 
                		null,
                		null, 
                		null, 
                		KEY_CREATED + " DESC", 
                		"1");*/
        if (mCursor != null){
            mCursor.moveToFirst();
        }
        return mCursor;
    }
 
    //---updates a recent by id---
    public boolean updateRecent(long rowId, String url, int count){
    	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); 
    	Date date = new Date();
        /*ContentValues args = new ContentValues();
        args.put(KEY_URL, url);
        args.put(KEY_COUNT, count);
        args.put(KEY_CREATED, dateFormat.format(date));
        return db.update(DATABASE_TABLE, args, KEY_ROWID + "=" + rowId, null) > 0;*/
        String q = "UPDATE " + DATABASE_TABLE + " SET " + KEY_URL + " = '" + url + "', " + KEY_COUNT + " = " + Integer.toString(count) + ", " + KEY_CREATED + " = '" + dateFormat.format(date) + "' WHERE " + KEY_ROWID + " = " + Long.toString(rowId);
        db.execSQL(q);
        return true;
    }
    
  //---updates a recent by url---
    public boolean updateRecent(String url, int count){
    	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); 
    	Date date = new Date();
        /*ContentValues args = new ContentValues();
        args.put(KEY_URL, url);
        args.put(KEY_COUNT, count);
        args.put(KEY_CREATED, dateFormat.format(date));
        return db.update(DATABASE_TABLE, args, KEY_URL + "=" + url, null) > 0;*/
    	String q = "UPDATE " + DATABASE_TABLE + " SET " + KEY_URL + " = '" + url + "', " + KEY_COUNT + " = " + Integer.toString(count) + ", " + KEY_CREATED + " = '" + dateFormat.format(date) + "' WHERE " + KEY_URL + " = '" + url + "'";
        db.execSQL(q);
        return true;
    }
}