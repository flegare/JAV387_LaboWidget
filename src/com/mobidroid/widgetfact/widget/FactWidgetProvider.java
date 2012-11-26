package com.mobidroid.widgetfact.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.mobidroid.widgetfact.service.FactService;

public class FactWidgetProvider extends AppWidgetProvider {

	private static final String TAG = FactWidgetProvider.class.getName();
	
	@Override
	public void onEnabled(Context context) {
		super.onEnabled(context);
		Log.d(TAG, "Initial widget setup");	
	}

	@Override
	public void onUpdate(Context ctx, AppWidgetManager wMgr, int[] appWidgetIds) {
							
		Log.d(TAG, "Updating widget, total count: " + appWidgetIds.length);		

		//Nous devons identifier notre widget car il existe plusieurs widget dans
		//le home screen
		ComponentName me = new ComponentName(ctx, FactWidgetProvider.class);
		int[] ids = wMgr.getAppWidgetIds(me);
		
		Intent i = new Intent(ctx.getApplicationContext(),FactService.class);
		i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
		ctx.startService(i);
						
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {	
		super.onReceive(context, intent);		
		Log.d(TAG, "Intent received");				
	}

	
	@Override
	public void onDeleted(Context context, int[] appWidgetIds) {
		super.onDeleted(context, appWidgetIds);
		Log.d(TAG, "All widget instances deleted, total count: "
				+ appWidgetIds.length);
	}

	@Override
	public void onDisabled(Context context) {
		super.onDisabled(context);
		Log.d(TAG, "Widget instance deleted");
	}

	public class FactUpdateRequestReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "New fact received");
		}

	}

}
