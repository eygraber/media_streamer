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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends FragmentActivity implements OnClickListener, LoaderManager.LoaderCallbacks<Cursor>{
	/*package*/ final static String PLAY_INTENT = "com.customprogrammingsolutions.MediaStreamer.PLAY";
	/*package*/ final static String STOP_INTENT = "com.customprogrammingsolutions.MediaStreamer.STOP";
	/*package*/ final static String CANCEL_PLAYBACK_INTENT = "com.customprogrammingsolutions.MediaStreamer.CANCEL_PLAYBACK";
	
	/*package*/ final static String KILL_SERVICE_INTENT = "com.customprogrammingsolutions.MediaStreamer.KILL_SERVICE";
	
	/*package*/ final static String URL_EXTRA = "com.customprogrammingsolutions.MediaStreamer.URL_EXTRA";
	/*package*/ final static String ERROR_EXTRA = "com.customprogrammingsolutions.MediaStreamer.ERROR_EXTRA";

	/*package*/ final static String STARTED_PLAYBACK_INTENT = "com.customprogrammingsolutions.MediaStreamer.STARTED_PLAYBACK";
	/*package*/ final static String PLAYBACK_TIMEOUT_INTENT = "com.customprogrammingsolutions.MediaStreamer.PLAYBACK_TIMEOUT";
	/*package*/ final static String STOPPED_PLAYBACK_INTENT = "com.customprogrammingsolutions.MediaStreamer.STOPPED_PLAYBACK";
	
	/*package*/ final static String ERROR_INTENT = "com.customprogrammingsolutions.MediaStreamer.ERROR";
	/*package*/ final static String CLEAR_ERROR_INTENT = "com.customprogrammingsolutions.MediaStreamer.CLEAR_ERROR";
	
	private final static String TAG = "MediaStreamer";
	
	private EditText urlBar;
	private TextView errorText;
	private ImageButton mediaStateButton;
	
	private boolean isPlayDrawable;
	private boolean receivedError = false;
	
	private ProgressDialog pd;
	
	private TabHost tabHost;
	private ListView recents, favorites;
	private SimpleCursorAdapter recentsAdapter, favoritesAdapter;

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if(intent.getAction() == ERROR_INTENT){
				Log.i(TAG, "MainActivity.mReceiver received an error!");
				int error = intent.getIntExtra(ERROR_EXTRA, MediaStreamerService.MEDIA_PLAYER_ERROR);
				switch(error){
					case MediaStreamerService.MEDIA_PLAYER_ERROR:
						errorText.setText(R.string.media_player_error);
						break;
					case MediaStreamerService.AUDIO_FOCUS_DENIED_ERROR:
						errorText.setText(R.string.audio_focus_denied_error);
						break;
					default:
						errorText.setText(R.string.media_player_error);
						break;
				}
				errorText.setVisibility(View.VISIBLE);
				if(pd.isShowing())
					pd.dismiss();
				receivedError = true;
			}
			else if(intent.getAction() == CLEAR_ERROR_INTENT){
				Log.i(TAG, "MainActivity.mReceiver received a clear error!");
				errorText.setVisibility(View.INVISIBLE);
			}
			else if(intent.getAction() == STOPPED_PLAYBACK_INTENT){
				Log.i(TAG, "MainActivity.mReceiver received a stopped playback!");
			}
			else if(intent.getAction() == STARTED_PLAYBACK_INTENT){
				Log.i(TAG, "MainActivity.mReceiver received a started playback!");
				getSupportLoaderManager().restartLoader(0, null, MainActivity.this);
		        getSupportLoaderManager().restartLoader(1, null, MainActivity.this);
			}
			setMediaStateRepresentation();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        urlBar = (EditText) findViewById(R.id.url_bar);
        errorText = (TextView) findViewById(R.id.stream_error_text);
        mediaStateButton = (ImageButton) findViewById(R.id.media_state_indicator_button);
        
        mediaStateButton.setOnClickListener(this);
        
        pd = new ProgressDialog(this);
        pd.setTitle("Connecting");
        pd.setMessage("Connecting to stream...");
        pd.setIndeterminate(true);
        pd.setCancelable(true);
        pd.setOnCancelListener(new OnCancelListener(){

			@Override
			public void onCancel(DialogInterface dialog) {
				startService(new Intent(CANCEL_PLAYBACK_INTENT));
			}
        	
        });
        
        IntentFilter myActions = new IntentFilter();
        myActions.addAction(ERROR_INTENT);
        myActions.addAction(CLEAR_ERROR_INTENT);
        myActions.addAction(STOPPED_PLAYBACK_INTENT);
        myActions.addAction(STARTED_PLAYBACK_INTENT);
        registerReceiver(mReceiver, myActions);
        
        setUpTabs();  
        
        registerForContextMenu(recents);
        registerForContextMenu(favorites);
    }
    
    @Override
	public void onResume(){
		super.onResume();
		
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
		
		setMediaStateRepresentation();
		
		if(MediaStreamerService.isStreamError()){
			errorText.setVisibility(View.VISIBLE);
		}
		else{
			errorText.setVisibility(View.INVISIBLE);
		}
    }
    
    @Override
	public void onClick(View v) {
		if(v == mediaStateButton){
			if(isPlayDrawable){
				String urlToStream = urlBar.getText().toString().trim();
				if(urlToStream.compareTo("") == 0){
					Toast.makeText(MainActivity.this, getString(R.string.error_empty_url), Toast.LENGTH_SHORT).show();
					return;
				}
				else if(!urlToStream.startsWith("http://") && !urlToStream.startsWith("rtsp://")){
					Toast.makeText(MainActivity.this, getString(R.string.error_invalid_url), Toast.LENGTH_SHORT).show();
					return;
				}
				
				//reset this here for the loop in WaitForStreamConnectionTask
				receivedError = false;
				
				Activity act = MainActivity.this;
				WaitForStreamConnectionTask t = new WaitForStreamConnectionTask(act, urlToStream);
				t.start();
			}
			else{
				isPlayDrawable = true;
				mediaStateButton.setImageResource(R.drawable.play_button);
				startService(new Intent(STOP_INTENT));
			}
		}
	}
    
    private void setMediaStateRepresentation(){
    	if(MediaStreamerService.isPlaying()){
			mediaStateButton.setImageResource(R.drawable.stop_button);
			isPlayDrawable = false;
			String urlToStream = MediaStreamerService.getUrlToStream();
			if(urlToStream == null)
				urlToStream = "";
			urlBar.setText(urlToStream);
		}
		else{
			mediaStateButton.setImageResource(R.drawable.play_button);
			isPlayDrawable = true;
		}
    }
    
    @Override
    public void onPause(){
    	super.onPause();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if(pd != null) {
        	if(pd.isShowing()) {
        		pd.dismiss();
        	}
        }
        unregisterReceiver(mReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        	case R.id.menu_settings:
        		startActivity(new Intent(MainActivity.this, Preferences.class));
        		break;
        	case R.id.menu_add_favorite:
        		showFavoritesDialog(false, true, false, "", "", -1);
        		break;
        }
        return true;
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
      super.onCreateContextMenu(menu, v, menuInfo);
      MenuInflater inflater = getMenuInflater();
      if(v == recents){
    	  inflater.inflate(R.menu.recents_context_menu, menu);
      }
      else if(v == favorites){
    	  inflater.inflate(R.menu.favorites_context_menu, menu);
      }
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
      AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
      String url;
      String name;
      long rowId;
      switch (item.getItemId()) {
      	case R.id.delete_recent:
      		url = ((TextView)recents.getChildAt(info.position).findViewById(R.id.recent_url)).getText().toString();
      		RecentsDBHelper rdb = new RecentsDBHelper(MainActivity.this).open();
      		rdb.deleteRecent(url);
      		rdb.close();
      		getSupportLoaderManager().restartLoader(0, null, MainActivity.this);
	        getSupportLoaderManager().restartLoader(1, null, MainActivity.this);
    	  return true;
      	case R.id.delete_favorite:
      		url = ((TextView)favorites.getChildAt(info.position).findViewById(R.id.favorite_url)).getText().toString();
      		FavoritesDBHelper fdb = new FavoritesDBHelper(MainActivity.this).open();
      		fdb.deleteFavorite(url);
      		fdb.close();
      		getSupportLoaderManager().restartLoader(0, null, MainActivity.this);
	        getSupportLoaderManager().restartLoader(1, null, MainActivity.this);
    	  return true;
      	case R.id.add_recent_to_favorites:
      		url = ((TextView)recents.getChildAt(info.position).findViewById(R.id.recent_url)).getText().toString();
      		showFavoritesDialog(true, false, false, "", url, -1);
      		return true;
      	case R.id.edit_favorite:
      		url = ((TextView)favorites.getChildAt(info.position).findViewById(R.id.favorite_url)).getText().toString();
      		name = ((TextView)favorites.getChildAt(info.position).findViewById(R.id.favorite_name)).getText().toString();
      		rowId = Long.parseLong(((TextView)favorites.getChildAt(info.position).findViewById(R.id.favorite_row_id)).getText().toString());
      		showFavoritesDialog(false, false, true, name, url, rowId);
      		return true;
      	default:
      		return super.onContextItemSelected(item);
      }
    }
    
    private void showFavoritesDialog(boolean addFromRecents, boolean addFromUser, boolean edit, String name, String url, long rowId){
    	FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        FavoritesDialog tPrev =  (FavoritesDialog) fragmentManager.findFragmentByTag("favorite_dialog");
        if(tPrev!=null)
            fragmentTransaction.remove(tPrev);
        
        FavoritesDialog favoritesDialog = FavoritesDialog.newInstance(addFromRecents, addFromUser, edit, name, url, rowId, getSupportLoaderManager(), this);
        favoritesDialog.show(fragmentTransaction, "favorite_dialog");
    }
    
    private void setUpTabs(){
        Resources res = getResources();
        tabHost = (TabHost) findViewById(R.id.tabhost);
        tabHost.setup();
        
        recentsAdapter = new SimpleCursorAdapter(this, R.layout.recent_item_layout, null, new String[] {RecentsDBHelper.KEY_URL}, new int[] {R.id.recent_url}, 0);
        favoritesAdapter = new SimpleCursorAdapter(this, R.layout.favorite_item_layout, null, new String[] {FavoritesDBHelper.KEY_URL, FavoritesDBHelper.KEY_NAME, FavoritesDBHelper.KEY_ROWID}, new int[] {R.id.favorite_url, R.id.favorite_name, R.id.favorite_row_id}, 0);
        recents = (ListView) findViewById(R.id.recents_tab);
        recents.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        favorites = (ListView) findViewById(R.id.favorites_tab);
        favorites.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        recents.setAdapter(recentsAdapter);
        favorites.setAdapter(favoritesAdapter);
        
        recents.setOnItemClickListener(new OnItemClickListener(){

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				TextView tv = (TextView) view.findViewById(R.id.recent_url);
				String url = tv.getText().toString();
				urlBar.setText(url);
				
				if(isPlayDrawable){
					mediaStateButton.performClick();
				}
				else{
					mediaStateButton.performClick();
					mediaStateButton.performClick();
				}
			}
        	
        });
        
        favorites.setOnItemClickListener(new OnItemClickListener(){

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				TextView tv = (TextView) view.findViewById(R.id.favorite_url);
				String url = tv.getText().toString();
				urlBar.setText(url);
				
				if(isPlayDrawable){
					mediaStateButton.performClick();
				}
				else{
					mediaStateButton.performClick();
					mediaStateButton.performClick();
				}
			}
        	
        });
        
        getSupportLoaderManager().initLoader(0, null, this);
        getSupportLoaderManager().initLoader(1, null, this);
        
        TabHost.TabSpec spec;  // Resusable TabSpec for each tab
        spec = tabHost.newTabSpec("recents").setIndicator("Recents", res.getDrawable(R.drawable.ic_action_search)).setContent(R.id.recents_tab);
        tabHost.addTab(spec);
        spec = tabHost.newTabSpec("favorites").setIndicator("Favorites", res.getDrawable(R.drawable.ic_action_search)).setContent(R.id.favorites_tab);
        tabHost.addTab(spec);
    }
    
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle arg1) {
		CursorLoader loader = null;;
		if(id == 0){
			loader = new CursorLoader(MainActivity.this) {
				final RecentsDBHelper rdb = new RecentsDBHelper(MainActivity.this).open();      
	        	@Override
	        	public Cursor loadInBackground() {
	        		Cursor c = null;
	        		c = rdb.getAllRecents();
	        		return c;
	        	}

	     	};
		}
		else if(id == 1){
			loader = new CursorLoader(MainActivity.this) {
				final FavoritesDBHelper fdb = new FavoritesDBHelper(MainActivity.this).open();      
	        	@Override
	        	public Cursor loadInBackground() {
	        		Cursor c = null;
	        		c = fdb.getAllFavorites();
	        		return c;
	        	}

	     	};
		}
		
		return loader;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
		if(loader.getId() == 0){
			recentsAdapter.swapCursor(c);
		}
		else if(loader.getId() == 1){
			favoritesAdapter.swapCursor(c);
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		if(loader.getId() == 0){
			recentsAdapter.swapCursor(null);
		}
		else if(loader.getId() == 1){
			favoritesAdapter.swapCursor(null);
		}
	};
    
    private class WaitForStreamConnectionTask extends Thread{
    	private Activity act;
    	private String urlToStream;
    	private int timer = 0;
    	
    	public WaitForStreamConnectionTask(Activity act, String urlToStream){
    		this.act = act;
    		this.urlToStream = urlToStream;
    	}
    	
		@Override
		public void run(){
			act.runOnUiThread(new Runnable(){
				@Override
				public void run(){
					pd.show();
					Log.i(TAG, "WaitForStreamConnectionTask.run() - Sending play intent with " + urlToStream + " url");
		        	Intent playIntent = new Intent(PLAY_INTENT);
					playIntent.putExtra(URL_EXTRA, urlToStream);
					startService(playIntent);
				}
			});
			
			while(!MediaStreamerService.isPlaying()){
				/*if(MediaStreamerService.isStreamError()){
					act.runOnUiThread(new Runnable(){
						@Override
						public void run(){
							if(pd.isShowing())
								pd.dismiss();
						}
					});
					return;
				}*/
				if(receivedError)
					return;
				
				try {Thread.sleep(500);}catch(InterruptedException e){}
				timer++;
				
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
				int timeout = Integer.parseInt(prefs.getString(getString(R.string.pref_timeout_key), getString(R.string.pref_timeout_default)));
				if(timer >= timeout){
					act.runOnUiThread(new Runnable(){
						@Override
						public void run(){
							if(pd.isShowing())
								pd.dismiss();
							Toast.makeText(act, getString(R.string.connection_timeout_msg), Toast.LENGTH_LONG).show();
							act.sendBroadcast(new Intent(MainActivity.PLAYBACK_TIMEOUT_INTENT));
						}
					});
					return;
				}
			}
			
			act.runOnUiThread(new Runnable(){
				@Override
				public void run(){
					if(pd.isShowing())
						pd.dismiss();
					
		        	isPlayDrawable = false;
					mediaStateButton.setImageResource(R.drawable.stop_button);
				}
			});
		}
	}

}
