package com.mobidroid.widgetfact.service;

import java.io.File;
import java.io.IOException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;

import com.mobidroid.widgetfact.R;
import com.mobidroid.widgetfact.model.FactDatabase;
import com.mobidroid.widgetfact.widget.FactWidgetProvider;

public class FactService extends Service {

	private static final String TAG = FactService.class.getName();

	private static final String REMOTE_FACT_LIST_VERSION_URL = "http://www.mobidroid.com/cours/JAV387/version";
	private static final String REMOTE_FACT_LIST_URL = "http://www.mobidroid.com/cours/JAV387/facts_full";

	
	private static final File LOCAL_FACT_LIST_FILE = new File(Environment.getExternalStorageDirectory() +
			  									      File.separator + Environment.DIRECTORY_DOWNLOADS +
			  									      File.separator +
			  										  "factList");

	
	private static final String KEY_LOCAL_FACT_VERSION = "lastUpdateFactListVersion";
	private static final String KEY_UPDATE_TIMESTAMP = "lastUpdate";
	
	
	private FactDatabase fdb;

	private BroadcastReceiver onDownloadFinishReceiver;
	private BroadcastReceiver onNotificationClickReceiver;

	private DownloadManager dMgr;

	private boolean isUpdating;

	private int[] widgetIds;

	private boolean broadcastReceiverRegistered;
		
	public void onCreate() {
		super.onCreate();
		Log.d(TAG, "Test service created"); // initialiser les trucs ici
		fdb = new FactDatabase(this);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		Log.d(TAG, "Executing logic now... IU: " + isUpdating); // Execution de la logique ici
		// Est-ce qu'on doit mettre à jour?
		if (!isUpdating && isFactListReadyForUpdate()) {		
			widgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
			//download une nouvelle liste de faits			
			launchDownloadFactList();
		}else{
			// met a jour les widgets
			updateAllWidgets(this,intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS));
		}
						
		// Si on est en mode update, on ne ferme pas le service d'ici
		if (!isUpdating) {			
			stopSelf();
		}

		return super.onStartCommand(intent, flags, startId);
	}

	/**
	 * Lance le téléchargement d'une liste de faits.
	 */
	private void launchDownloadFactList() {

		// On indique à notre service que nous sommes en train de télécharger,
		// on ne doit pas terminer le service tant que le téléchargement n'est
		// pas terminé.
		isUpdating = true;
		
		// On enregistre des broadcasts receivers
		registerBroadcastReceivers();

		// On cree une requete de telechargement pour le DM
		Request dr = createDonwloadRequest();
		
		if(LOCAL_FACT_LIST_FILE.exists()){
			Log.d(TAG, "deleted old list file");
			LOCAL_FACT_LIST_FILE.delete();			
		}
		
		// On obtient un download manager
		dMgr = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
		
		// On ajoute la requête dans la file, on obtient une reference
		long downloadId = dMgr.enqueue(dr);
		
		Log.d(TAG, "Requested download of fact list, id is :" + downloadId);
		
	}	

	/**
	 * Cree une requete de téléchargement pour le download manager
	 * @return
	 */
	private Request createDonwloadRequest() {
		
		Uri uri = Uri.parse(REMOTE_FACT_LIST_URL);

		// Obtient le path du repertoire de telechargement android par defaut
		// (et le cree si il n'existe pas)
		Environment.getExternalStoragePublicDirectory(
				Environment.DIRECTORY_DOWNLOADS).mkdirs();

		// On cree une requete de telechargement
		Request dr = new DownloadManager.Request(uri);
		dr.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI
				| DownloadManager.Request.NETWORK_MOBILE);
		dr.setTitle("Update facts");
		dr.setDescription("A updated version of the fact list for the Fact widget.");
		dr.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
				"factList");			
		return dr;
	}

	/**
	 * Enregistre deux broadcast receiver pour le download completion
	 */
	private void registerBroadcastReceivers() {
		
		broadcastReceiverRegistered = true;

		// Souscrit deux broadcast receiver, un pour la fin du téléchargement
		registerReceiver(onDownloadFinishReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent i) {
				Log.d(TAG, "Download finished intent received");
				updateFactFromDownloadedList(i.getExtras().getLong(DownloadManager.EXTRA_DOWNLOAD_ID));				
			}
		}, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

		// L'autre pour quand l'usager click sur la notification de
		// téléchargement (pas trop utile dans notre cas)
		registerReceiver(onNotificationClickReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				// TODO nothing for now...
			}
		}, new IntentFilter(DownloadManager.ACTION_NOTIFICATION_CLICKED));

	}
	
	/**
	 * On vérifie l'etat du download car notre ami le 
	 * gestionnaire de download est semi efficace
	 * et envois quelques event invalide... Voir:
	 * http://code.google.com/p/android/issues/detail?id=18462
	 * @param long1
	 * @return
	 */
	private boolean validDownload(long downloadId) {

		Log.d(TAG,"Checking download status for id: " + downloadId);
		//Verify if download is a success
		Cursor c= dMgr.query(new DownloadManager.Query().setFilterById(downloadId));
		
		if(c.moveToFirst()){			
			int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
			
			if(status == DownloadManager.STATUS_SUCCESSFUL){
				Log.d(TAG, "File was downloading properly.");
				return true;
			}else{
				int reason = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_REASON));
				Log.d(TAG, "Download not correct, status [" + status + "] reason [" + reason + "]");			
				return false;
			}	
		}				
		return false;				  					
	}

	/**
	 * On met à jour la base de donnée via le fichier télécharger
	 */
	protected void updateFactFromDownloadedList(long downloadId) {
							
		//On s'assure que c'est un bon download (fix pour l'issue 18462)
		if(validDownload(downloadId)){
			
			Log.d(TAG, "Fact file successfully downloaded, we will now insert new facts...");
			// /mnt/sdcard/Download/factList

			
			Log.d(TAG, "File to insert: " + LOCAL_FACT_LIST_FILE.getAbsolutePath());
			Log.d(TAG, "File exist: " + LOCAL_FACT_LIST_FILE.exists());

			try {
				
				fdb.updateFactFromFile(this, LOCAL_FACT_LIST_FILE);				
				Log.d(TAG, "Deleting the fact file");				
				//On efface le fichier
				LOCAL_FACT_LIST_FILE.delete();				
				Log.d(TAG, "Deletion was a success :  " + !LOCAL_FACT_LIST_FILE.exists());
												
			} catch (IOException e) {
				e.printStackTrace();
			}					
		}
					
		// On aura plus besoin des receivers.
		if(broadcastReceiverRegistered){
			deregisterBroadcastReceivers();
		}
			
		//On refresh la vue
		updateAllWidgets(this, widgetIds);
	
		// On termine le service... job well done!
		Log.d(TAG, "Terminating service");
		stopSelf();
	}
	

	/**
	 * Clean les broadcast receivers
	 */
	private void deregisterBroadcastReceivers() {
		
		try {
			// On clean les broadcast receiver
			Log.d(TAG, "Cleaning receivers");		
			unregisterReceiver(onDownloadFinishReceiver);
			unregisterReceiver(onNotificationClickReceiver);
			
		} catch (IllegalArgumentException e) {
			//Patch for bug: http://code.google.com/p/android/issues/detail?id=6191
			Log.w(TAG, "unable to de-register broadcast receiver...");
		}
		
	}

	/**
	 * On vérifie si la bd remote est une version differente de notre bd
	 * 
	 * @return
	 * @throws Exception 
	 */
	private boolean isRemoteDatabaseChanged() throws Exception {

		// Restore preferences
		SharedPreferences settings = getSharedPreferences(TAG, 0);
		int localFactListVersion = settings.getInt(KEY_LOCAL_FACT_VERSION,0);
				
		if(isPhoneAbleToDownload()){

			int remoteFactListVersion = getRemoteDatabaseVersion();

			if (remoteFactListVersion != -1 && 
					remoteFactListVersion != localFactListVersion) {
				Log.d(TAG, "Fact list changed, we should update");
				return true;
			} else if (remoteFactListVersion == -1) {
				Log.w(TAG, "Unable to get remote fact list version!");
				throw new Exception("Unable to fetch version");				
			} else {
				Log.d(TAG, "Database not changed, we are in fact up to date...");
				return false;
			}			
		}else{
			throw new Exception("Unable to fetch version");
		}

	}

	/**
	 * Retourne la version de la BD remote
	 * 
	 * @return le no. de version ou -1 si erreur
	 */
	public int getRemoteDatabaseVersion() {

		// TODO: Il faut que ce soit exécuté en thread!
		HttpClient httpclient = new DefaultHttpClient();
		HttpGet request = new HttpGet(REMOTE_FACT_LIST_VERSION_URL);
		ResponseHandler<String> handler = new BasicResponseHandler();

		int version = -1;

		try {
			String content = httpclient.execute(request, handler).trim();
			Log.d(TAG, "Received database version [" + content + "]");
			version = Integer.parseInt(content);
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NumberFormatException e) {
			e.printStackTrace();
			version = -1;
		}

		httpclient.getConnectionManager().shutdown();

		return version;
	}

	/**
	 * On met a jour tout les widgets
	 * 
	 * @param widgetIds
	 */
	private void updateAllWidgets(Context ctx, int[] widgetIds) {

		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this
				.getApplicationContext());

		for (int widgetId : widgetIds) {
			appWidgetManager.updateAppWidget(widgetId,generateWidgetView(ctx, widgetIds));
		}

	}

	/**
	 * On s'assure que: 1- nous avons un connectivité 2- nous sommes sous wifi
	 * ou 3G
	 * 
	 * @return
	 */
	private boolean isPhoneAbleToDownload() {

		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

		// Si nous ne somme pas connecter ceci retourne null
		NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();

		if (activeNetworkInfo != null) {

			// Est-ce que c'est du WIFI?

			boolean isRoaming = activeNetworkInfo.isRoaming();
			boolean isWifi = activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI;
			boolean isMobile = activeNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE; // type
																								// generique
			boolean isConnected = activeNetworkInfo.isConnected();
			boolean isAvailable = activeNetworkInfo.isAvailable();

			Log.d(TAG, "mobile: " + isMobile + " WIFI: " + isWifi
					+ " isConnected: " + isConnected + " isAvailable:"
					+ isAvailable + " isRoaming " + isRoaming);

			return ((isMobile || isWifi) && isConnected && isAvailable && !isRoaming);

		}

		Log.d(TAG, "No connectivity available!");

		return false;
	}

	public void onDestroy() { // RIP
		Log.d(TAG, "Test service stopped"); // nettoyage ici
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	/**
	 * Cette méthode s'occupe de construire une "RemoteView", une vue qui peut
	 * s'afficher dans un autre process que notre application. Dans ce cas on
	 * parle du home screen android
	 * 
	 * @param ctx
	 * @param appWidgetIds
	 * @return
	 */
	private RemoteViews generateWidgetView(Context ctx, int[] appWidgetIds) {

		// Obtient un nouveau fait
		String newFact = getNewFact(ctx);

		// On va cherche le layout de notre vue et encapsule dans une remote
		// view
		// le package indique ou allez cherche le fichier xml de notre vue
		RemoteViews remoteWidgetView = new RemoteViews(ctx.getPackageName(),
				R.layout.main);

		// Met a jour le "fait" dans le text view
		remoteWidgetView.setTextViewText(R.id.txtKnowledge, newFact);
		// Nous allons creer un Intent qui demandera la mise a jour de tout les
		// widgets
		Intent i = new Intent(ctx.getApplicationContext(),
				FactWidgetProvider.class);
		i.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
		i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);

		/*
		 * Utilison un pending intent pour "lancer au click"
		 * 
		 * Parameters context The Context in which this PendingIntent should
		 * perform the broadcast. requestCode Private request code for the
		 * sender (currently not used). intent The Intent to be broadcast. flags
		 * May be FLAG_ONE_SHOT, FLAG_NO_CREATE, FLAG_CANCEL_CURRENT,
		 * FLAG_UPDATE_CURRENT [...]
		 */
		PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i,
				PendingIntent.FLAG_UPDATE_CURRENT);

		// On applique ce pending intent au bouton
		remoteWidgetView.setOnClickPendingIntent(R.id.btNext, pi);

		// Voila notre vue est "construite" on la retourne
		return remoteWidgetView;

	}

	/**
	 * Est-ce qu'on doit updater ou pas?
	 * 
	 * @return
	 */
	private boolean isFactListReadyForUpdate() {
			
		final long WAIT_TIME_FOR_NEXT_CHECK = 1000 * 60;// * 60 * 24; // 24H
	
		//L'objet sharedPreferences permet de charger des données sauver sur disque
		SharedPreferences sharedPrefs = getSharedPreferences(TAG, Context.MODE_PRIVATE);
		long lastUpdateTimeStamp = sharedPrefs.getLong(KEY_UPDATE_TIMESTAMP, 0);
							
		//Est-ce que l'on doit verifier sur le serveur si la bd a changer?
		if(System.currentTimeMillis() > lastUpdateTimeStamp + WAIT_TIME_FOR_NEXT_CHECK){
			
			//Est-ce que la BD remote est plus récente?
			try {
				boolean shouldUpdate = isRemoteDatabaseChanged();				
				//On re-verifira dans 24h
			    SharedPreferences.Editor editor = sharedPrefs.edit();
			    editor.putLong(KEY_UPDATE_TIMESTAMP, System.currentTimeMillis());
			    editor.commit();			    
				return shouldUpdate;

			} catch (Exception e) {
				//On a pas été en mesure de connaitre la version remote, 
				//le prochain appel nous allons refaire une verification
				Log.w(TAG, "Unable to check remote fact list, will retry later");
				return false;
			}							
		}			
		return false;
	}

	private String getNewFact(Context ctx) {
		// Initialise the database et retourne un fait au hasard...
		Log.d(TAG, "Requesting a new random fact");
		return fdb.getRandomFact();
	}

}
