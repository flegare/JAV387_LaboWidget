package com.mobidroid.widgetfact.model;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.provider.BaseColumns;
import android.util.Log;

import com.mobidroid.widgetfact.R;

public class FactDatabase {

	private static final String TAG = FactDatabase.class.getName();

	private static final int DATABASE_VERSION = 1; // Utile pour les upgrades
	private static final String DATABASE_NAME = "fact_database";

	public static final String TABLE_FACT = "fact";
	public static final String TABLE_FACT_COL_CONTENT = "content";

	// CREATE TABLE fact ( content TEXT )
	private static final String FACT_TABLE_CREATE = "CREATE TABLE "
			+ TABLE_FACT + " (" + TABLE_FACT_COL_CONTENT + " TEXT);";

	private static final HashMap<String, String> mColumnMap = buildColumnMap();

	/**
	 * On construit une map qui contient tout les colonnes de cette base de
	 * données. Ceci permet au "consommateur" de faire abstraction du vrai nom
	 * des colonnes
	 */
	private static HashMap<String, String> buildColumnMap() {
		HashMap<String, String> map = new HashMap<String, String>();
		map.put(TABLE_FACT_COL_CONTENT, TABLE_FACT_COL_CONTENT);
		map.put(BaseColumns._ID, "rowid AS " + BaseColumns._ID); // Id standard
																 // sous
																 // Android
		return map;
	}

	private FactDatabaseOpenHelper mDatabaseOpenHelper;

	public FactDatabase(Context ctx) {
		mDatabaseOpenHelper = new FactDatabaseOpenHelper(ctx);
	}

	/**
	 * 
	 * @return
	 */
	public String getRandomFact() {

		String[] selectionArgs = new String[] {};
		String query = "SELECT " + TABLE_FACT_COL_CONTENT + " FROM "
				+ TABLE_FACT + " ORDER BY RANDOM () LIMIT 1;";

		SQLiteDatabase db = mDatabaseOpenHelper.getReadableDatabase();
		String fact = DatabaseUtils.stringForQuery(db, query, selectionArgs);
		db.close();

		return fact;

	}

	/**
	 * Retourne un curseur à la position demandée (rowId)
	 * 
	 * @param rowId
	 *            id of word to retrieve
	 * @param columns
	 *            The columns to include, if null then all are included
	 * @return Cursor positioned to matching word, or null if not found.
	 */
	public Cursor getFact(String rowId, String[] columns) {

		String selection = "rowid = ?";
		String[] selectionArgs = new String[] { rowId };

		return query(selection, selectionArgs, columns);

		/*
		 * Voici la syntaxe que ceci retourne: SELECT <columns> FROM <table>
		 * WHERE rowid = <rowId>
		 */
	}

	/**
	 * Execute une requete sur la bd et retourne un curseur avec les donnees
	 * 
	 * @param selection
	 *            la claus SELECT
	 * @param selectionArgs
	 *            les parametres d'entree (remplace le ? par la valeur)
	 * @param columns
	 *            Les colonnes à retourner
	 * @return Un curseur avec ce qu'on voulait
	 */
	private Cursor query(String selection, String[] selectionArgs,
			String[] columns) {
		/*
		 * Le SQL Builder est une classe pratique qui contient le mappage creer
		 * plus ci-dessu, encore une fois c'est pour faire abstraction des noms
		 * reelle des colonnes.
		 */
		SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
		builder.setTables(TABLE_FACT);
		builder.setProjectionMap(mColumnMap);

		Cursor cursor = builder.query(
				mDatabaseOpenHelper.getReadableDatabase(), columns, selection,
				selectionArgs, null, null, null);

		if (cursor == null) {
			return null;
		} else if (!cursor.moveToFirst()) {
			cursor.close();
			return null;
		}
		return cursor;
	}

	/**
	 * SQLiteHelper nous permet de creer ou mettre a jour facilemet la bd
	 */
	public class FactDatabaseOpenHelper extends SQLiteOpenHelper {

		private SQLiteDatabase mDatabase;
		private Context ctx;

		public FactDatabaseOpenHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
			ctx = context;
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			mDatabase = db;
			db.execSQL(FACT_TABLE_CREATE);

			// Lance un thread d'insertion
			new Thread(new Runnable() {
				public void run() {
					try {
						insertFacts();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			}).start();
		}

		/*
		 * Lancer lors d'une mise à jour de la base de donne via un changement
		 * de DATABASE_VERSION
		 */
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
					+ newVersion + ", which will destroy all old data");
			db.execSQL("DROP IF EXIST " + TABLE_FACT);
			onCreate(db);
		}

		/**
		 * Charge la liste de fait initialement fournie
		 * 
		 * @throws IOException
		 */
		private void insertFacts() throws IOException {
			Log.d(TAG, "Loading facts...");
			final Resources resources = ctx.getResources();
			InputStream inputStream = resources
					.openRawResource(R.raw.facts_init);
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					inputStream));

			try {
				String line;
				while ((line = reader.readLine()) != null) {
					long id = addFact(line);
					if (id < 0) {
						Log.e(TAG, "unable to add fact: " + line.trim());
					}
				}
			} finally {
				reader.close();
			}
			Log.d(TAG, "DONE loading facts.");
		}

		/**
		 * Ajoute un fait à la liste
		 * 
		 * @return rowId or -1 if failed
		 */
		public long addFact(String fact) {
			ContentValues initialValues = new ContentValues();
			initialValues.put(TABLE_FACT_COL_CONTENT, fact);
			return mDatabase.insert(TABLE_FACT, null, initialValues);
		}

	}

	/**
	 * Update facts from the file
	 * 
	 * @param f
	 * @throws IOException
	 */
	public void updateFactFromFile(final Context ctx, final File f)
			throws IOException {
		
		//TODO: Exécuter dans un thread
		Log.d(TAG, "Inserting fact right now...");
		SQLiteDatabase db = mDatabaseOpenHelper.getWritableDatabase();

		//Petit menage
		Log.d(TAG, "Deleting existing facts");
		db.delete(TABLE_FACT, null, null);

		BufferedReader reader = new BufferedReader(new FileReader(f));
		try {			
			String line;				
			while ((line = reader.readLine()) != null) {
				ContentValues cv = new ContentValues();
				cv.put(TABLE_FACT_COL_CONTENT, line);
				long id = db.insert(TABLE_FACT, null, cv);		
				if (id < 0) {
					Log.e(TAG, "unable to add fact: " + line.trim());
				}
			}			
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			Log.d(TAG, "closing database");
			reader.close();
			db.close();
		}
	}
}
