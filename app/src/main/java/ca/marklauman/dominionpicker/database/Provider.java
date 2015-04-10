/* Copyright (c) 2014 Mark Christopher Lauman
 * 
 * Licensed under the The MIT License (MIT)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.                                                                  */
package ca.marklauman.dominionpicker.database;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import ca.marklauman.dominionpicker.R;

/** This content provider is where all database queries in this app end up.
 *  @author Mark Lauman */
@SuppressWarnings("WeakerAccess")
public class Provider extends ContentProvider {

    /** The authority this provider operates over */
    public static final String AUTHORITY = "ca.marklauman.dominionpicker";

    /** MIME type for cards. */
    public static final String MIME_CARD = "ca.marklauman.dominionpicker.card";
    /** Mime type for supplies. */
    public static final String MIME_SHUFFLE = "ca.marklauman.dominionpicker.supply";

    /** Internal id for unrecognized URIs */
    private static final int ID_WTF = 0;
    /** Internal id for the card table's URI. */
    private static final int ID_CARD = 1;
    /** Internal id for the history table's URI. */
    private static final int ID_HIST = 2;
	
	/** URI to access the card table. */
	public static final Uri URI_CARDS = Uri.parse("content://ca.marklauman.dominionpicker/cards");
    /** URI to access the history table */
    public static final Uri URI_HIST = Uri.parse("content://ca.marklauman.dominionpicker/history");

    /** Card type used for Event cards */
    public static String TYPE_EVENT;

    /** Used to match URIs to tables. */
    UriMatcher matcher;
    /** Handle to the card database. */
	private CardDb card_db;
    /** Handle to the data database */
    private DataDb data_db;


	@Override
	public boolean onCreate() {
        // setup the uri matcher
        matcher = new UriMatcher(ID_WTF);
        matcher.addURI(AUTHORITY, "cards", ID_CARD);
        matcher.addURI(AUTHORITY, "history", ID_HIST);

        Context c = getContext();
        TYPE_EVENT = c.getString(R.string.card_type_event);
        // remove the generic db used in db versions < 9
        c.deleteDatabase("cards.db");
        // get the database files
		card_db = new CardDb(c);
        data_db = new DataDb(c);
		return true;
	}


	@Override
	public String getType(Uri uri) {
        switch (matcher.match(uri)) {
            case ID_CARD: return MIME_CARD;
            case ID_HIST: return MIME_SHUFFLE;
            default:      return null;
        }
	}
	
	@Override
	public Cursor query(Uri uri, String[] projection,
				String selection, String[] selectionArgs,
				String sortOrder) {
        SQLiteDatabase db;
        switch(matcher.match(uri)) {
            case ID_CARD:
                return card_db.query(projection, selection, selectionArgs, sortOrder);
            case ID_HIST:
                db = data_db.getReadableDatabase();
                return db.query(DataDb.TABLE_HISTORY, projection,
                                selection, selectionArgs,
                                null, null, sortOrder);
            default: return null;
        }
	}


    @Override
    public void onConfigurationChanged (Configuration newConfig) {
        String newLanguage = newConfig.locale.getLanguage();
        if(! newLanguage.equals(card_db.language)) {
            TYPE_EVENT = getContext().getString(R.string.card_type_event);
            // if the language has changed, open that language's database
            card_db.close();
            card_db = new CardDb(getContext());
            // Notify any listeners that their cursors are invalid
            notifyChange(URI_CARDS);
        }
    }

	
	@Override
	public Uri insert(Uri uri, ContentValues values) {
        // insertions only allowed on history table
        int id = matcher.match(uri);
        if(id != ID_HIST) return null;

        // perform insert
        long row = data_db.getReadableDatabase()
                          .insert(DataDb.TABLE_HISTORY, null, values);
        if(row == -1L) return null;
        notifyChange(URI_HIST);
        return Uri.withAppendedPath(URI_HIST, "" + row);
	}
	
	@Override
	public int update(Uri uri, ContentValues values,
                      String selection, String[] selectionArgs) {
        switch(matcher.match(uri)) {
            case ID_HIST:
                int change = data_db.getReadableDatabase()
                                    .update(DataDb.TABLE_HISTORY, values,
                                            selection, selectionArgs);
                if(change != 0) notifyChange(URI_HIST);
                return change;
            default: return 0;
        }
	}
	
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
        switch (matcher.match(uri)) {
            case ID_HIST:
                int change = data_db.getReadableDatabase()
                                    .delete(DataDb.TABLE_HISTORY,
                                            selection, selectionArgs);
                if(change != 0) notifyChange(URI_HIST);
                return change;
            default: return 0;
        }
    }

    /** Notify all listening processes that the data at the uri has changed */
    private void notifyChange(Uri uri) {
        getContext().getContentResolver()
                    .notifyChange(uri, null);
    }
}