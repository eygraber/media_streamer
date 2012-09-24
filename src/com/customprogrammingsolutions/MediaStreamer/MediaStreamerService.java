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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

public class MediaStreamerService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener{	
	private final static String TAG = "MediaStreamer";
	

	public static final int AUDIO_FOCUS_DENIED_ERROR = 0;
	public static final int MEDIA_PLAYER_ERROR = 1;
	
	public static final int NOTIFICATION_ID = 0;

	
	private MediaPlayer mMediaPlayer;
	private static boolean isPlaying = false;
	private static boolean isStreamError = false;
	
	private static String urlToStream = "";
	
	private static boolean isRunning = false;
	
	private BroadcastReceiver audioTooNoisyReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			stop();
        }
    };
	
	@Override
    public void onCreate() {
        Log.i(TAG, "MediaStreamerService.onCreate()");
        
        isPlaying = false;
        isRunning = true;
        
        IntentFilter inf = new IntentFilter();
        inf.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        inf.addAction(MainActivity.PLAYBACK_TIMEOUT_INTENT);
        
        registerReceiver(audioTooNoisyReceiver, inf);        
    }
	
	@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // No intent, tell the system not to restart us.
        if (intent == null) {
            stopSelf();
            return START_STICKY;
        }

        if(intent.getAction() == MainActivity.PLAY_INTENT){
        	Log.i(TAG, "MediaStreamerService.onStartCommand() - Received play intent");
        	
        	stop();
        	
        	urlToStream = intent.getStringExtra(MainActivity.URL_EXTRA);
        	if(urlToStream == null)
        		urlToStream = "";
        	
        	play();
        }
        else if(intent.getAction() == MainActivity.STOP_INTENT){
        	Log.i(TAG, "MediaStreamerService.onStartCommand() - Received stop intent");
        	
        	stop();
        }
        else if(intent.getAction() == MainActivity.KILL_SERVICE_INTENT){
        	Log.i(TAG, "MediaStreamerService.onStartCommand() - Received kill intent");
        	
        	stopSelf();
        }

        return START_STICKY;
    }
	
	private void play(){
		Log.i(TAG, "MediaStreamerService.play()");
		
		if(!requestAudioFocus()){
			Log.i(TAG, "MediaStreamerService.play() - AudioFocus request denied");
			
			notifyStreamError(AUDIO_FOCUS_DENIED_ERROR);
		}
		
		mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnErrorListener(this);
        mMediaPlayer.setOnPreparedListener(this);
        
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);  
        mMediaPlayer.setVolume(1.0f, 1.0f);
    	try {
        	mMediaPlayer.setDataSource(this, Uri.parse(urlToStream));
		} catch (Exception e){
    		Log.e(TAG, "MediaStreamerService.play() - Error setting data source for the media player", e);
    		stop();
    		notifyStreamError(MEDIA_PLAYER_ERROR);
    		return;
    	}
    	try{
            mMediaPlayer.prepareAsync(); // prepare async to not block main thread
		} catch (Exception e){
    		Log.e(TAG, "MediaStreamerService.play() - Error preparing the media player", e);
    		stop();
    		notifyStreamError(MEDIA_PLAYER_ERROR);
    		return;
		}
        
	}
	
	private boolean requestAudioFocus(){
		AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

		if(result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) 
			return false;
		else
			return true;
	}
	
	@Override
	public void onPrepared(MediaPlayer mp) {
		try{
        	mMediaPlayer.start();
		} catch (Exception e){
    		Log.e(TAG, "MediaStreamerService.play() - onPreparedProxy - Error starting the media player", e);
    		stop();
    		notifyStreamError(MEDIA_PLAYER_ERROR);
    		return;
    	}
		
        isPlaying = true;
		notifyClearStreamError();
		
		startNotification();
		
		addToRecents();
		
		sendBroadcast(new Intent(MainActivity.STARTED_PLAYBACK_INTENT));
	}
	
	private void startNotification(){
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
		
		RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.custom_notification_layout);
		contentView.setImageViewResource(R.id.notification_icon, R.drawable.notification_icon);
		contentView.setTextViewText(R.id.notification_title, getString(R.string.notification_title));
		contentView.setTextViewText(R.id.notification_text, urlToStream);
		if(isPlaying){
			contentView.setImageViewResource(R.id.media_state_indicator_icon, R.drawable.stop_button);
			PendingIntent stopIntentPending = PendingIntent.getService(this, 0, new Intent(MainActivity.STOP_INTENT), PendingIntent.FLAG_CANCEL_CURRENT);
			contentView.setOnClickPendingIntent(R.id.media_state_indicator_icon, stopIntentPending);
		}
		else{
			contentView.setImageViewResource(R.id.media_state_indicator_icon, R.drawable.play_button);
			Intent playIntent = new Intent(MainActivity.PLAY_INTENT);
			playIntent.putExtra(MainActivity.URL_EXTRA, urlToStream);
			
			PendingIntent playIntentPending = PendingIntent.getService(this, 0, playIntent, PendingIntent.FLAG_CANCEL_CURRENT);
			contentView.setOnClickPendingIntent(R.id.media_state_indicator_icon, playIntentPending);
		}
		
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
		contentView.setOnClickPendingIntent(R.id.notification_layout, contentIntent);
		
		PendingIntent killIntentPending = PendingIntent.getService(this, 0, new Intent(MainActivity.KILL_SERVICE_INTENT), 0);
		contentView.setOnClickPendingIntent(R.id.close_notification_icon, killIntentPending);
		
		builder.setContent(contentView);
		
		builder.setSmallIcon(R.drawable.notification_icon);

		builder.setAutoCancel(false);
		
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		nm.notify(NOTIFICATION_ID, builder.build());
	}
	
	private void addToRecents(){
		RecentsDBHelper rdb = new RecentsDBHelper(this).open();
		rdb.insertRecent(urlToStream);
		rdb.close();
	}
	
	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		Log.e(TAG, "Error occurred while playing audio. What = " + what + " - Extra = " + extra);
		stop();
		notifyStreamError(MEDIA_PLAYER_ERROR);
		return false;
    }
	
	@Override
	public void onCompletion(MediaPlayer mp) {
		stop();
	}
	
	/*package*/ void stop(){
		Log.i(TAG, "MediaStreamerService.stop() - Just dropping by");
		if(isPlaying)
    		isPlaying = false;

    	if (mMediaPlayer != null) {
            try{
            	if(mMediaPlayer.isPlaying()){
            		Log.i(TAG, "MediaStreamerService.stop() - Stopping media player");
            		mMediaPlayer.stop();
            		sendBroadcast(new Intent(MainActivity.STOPPED_PLAYBACK_INTENT));
            		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            		boolean persistentNotification = prefs.getBoolean(getString(R.string.pref_notification_key), Boolean.parseBoolean(getString(R.string.pref_notification_default))); 
            		if(persistentNotification)
            			startNotification();
            		else{
            			NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            			nm.cancel(NOTIFICATION_ID);
            		}
            	}
            }
            catch(Exception e){
            	Log.e(TAG, "MediaStreamerService.stop() - Error while attempting to stop media player - ", e);
            }
            try{
            	Log.i(TAG, "MediaStreamerService.stop() - Releasing media player");
            	mMediaPlayer.release();
            }
            catch(Exception e){
            	Log.e(TAG, "MediaStreamerService.stop() - Error while attempting to release media player - ", e);
            }
            Log.i(TAG, "MediaStreamerService.stop() - mMediaPlayer = null");
            mMediaPlayer = null;
    	}
	}
	
	private void notifyStreamError(int error){
		isStreamError = true;
		Intent i = new Intent(MainActivity.ERROR_INTENT);
		i.putExtra(MainActivity.ERROR_EXTRA, error);
		sendBroadcast(i);
	}
	
	private void notifyClearStreamError(){
		if(isStreamError){
			isStreamError = false;
			sendBroadcast(new Intent(MainActivity.CLEAR_ERROR_INTENT));
		}
	}
	
	public static boolean isRunning(){
		return isRunning;
	}
	
	public static boolean isPlaying(){
		return isPlaying;
	}
	
	public static boolean isStreamError(){
		return isStreamError;
	}
	
	public static String getUrlToStream(){
		return urlToStream;
	}
	
	@Override
    public void onDestroy() {
		Log.i("UrlMediaStreamer", "MediaStreamerService.onDestroy()");
		stop();
		isRunning = false;
		if(mMediaPlayer != null)
			mMediaPlayer.release();
		
		unregisterReceiver(audioTooNoisyReceiver);
		
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel(NOTIFICATION_ID);
    }

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onAudioFocusChange(int focusChange) {
		//very inefficient...try optimizing when you have time  
		switch (focusChange) {
        case AudioManager.AUDIOFOCUS_GAIN:
            stop();
            play();
            break;

        case AudioManager.AUDIOFOCUS_LOSS:
            // Lost focus for an unbounded amount of time: stop playback and release media player
            stop();
            break;

        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            // Lost focus for a short time, but we have to stop
            stop();
            break;

        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
            // Lost focus for a short time, but it's ok to keep playing
            // at an attenuated level
            stop();
            break;
    }
		
	}

}
