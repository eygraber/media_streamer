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

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class FavoritesDialog extends DialogFragment {
	
	private EditText nameBox;
	private EditText urlBox;
	
	public LoaderManager loader;
	public LoaderManager.LoaderCallbacks<Cursor> callingActivity;
	
	public static FavoritesDialog newInstance(boolean addFromRecent, boolean addFromUser, boolean edit, String name, String url, long rowId, LoaderManager loader, LoaderManager.LoaderCallbacks<Cursor> callingActivity) {
		FavoritesDialog frag = new FavoritesDialog();
        Bundle args = new Bundle();
        args.putBoolean("addFromRecent", addFromRecent);
        args.putBoolean("addFromUser", addFromUser);
        args.putBoolean("edit", edit);
        args.putString("name", name);
        args.putString("url", url);
        args.putLong("rowId", rowId);
        frag.setArguments(args);
        frag.loader = loader;
        frag.callingActivity = callingActivity;
        return frag;
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
		View layout = inflater.inflate(R.layout.favorite_dialog, null);
		final boolean addFromRecent = getArguments().getBoolean("addFromRecent");
		final boolean addFromUser = getArguments().getBoolean("addFromUser");
		boolean edit = getArguments().getBoolean("edit");
		String name = getArguments().getString("name"), url = getArguments().getString("url");
		
		nameBox = (EditText)layout.findViewById(R.id.favorite_dialog_name);
		urlBox = (EditText)layout.findViewById(R.id.favorite_dialog_url);
		
		if(addFromRecent){
			urlBox.setText(url);
		}
		else if(edit){
			urlBox.setText(url);
			nameBox.setText(name);
		}
		
		String positiveButtonLabel = addFromRecent || addFromUser ? getString(R.string.favorite_dialog_positive_label_add) : getString(R.string.favorite_dialog_positive_label_edit);
		Button positiveButton = (Button)layout.findViewById(R.id.favorite_dialog_positive_button);
		positiveButton.setText(positiveButtonLabel);
		positiveButton.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				FavoritesDBHelper fdb = new FavoritesDBHelper(getActivity()).open();
        		long result;
        		String url = urlBox.getText().toString().trim();
        		String name = nameBox.getText().toString().trim();
        		if(addFromRecent || addFromUser){
        			result = fdb.insertFavorite(url, name);
        		}
        		else{
        			result = fdb.updateFavorite(getArguments().getLong("rowId"), url, name);
        		}
        		if(result == -1){
        			Toast.makeText(getActivity(), getString(R.string.favorite_dialog_name_error), Toast.LENGTH_LONG).show();
        			return;
        		}
        		if(result == -2){
        			Toast.makeText(getActivity(), getString(R.string.favorite_dialog_url_error), Toast.LENGTH_LONG).show();
        			return;
        		}
        		fdb.close();
        		loader.restartLoader(0, null, callingActivity);
                loader.restartLoader(1, null, callingActivity);
        		getDialog().dismiss();
			}
		});
		
		Button negativeButton = (Button)layout.findViewById(R.id.favorite_dialog_negative_button);
		negativeButton.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				getDialog().dismiss();
			}
			
		});
		
		getDialog().setTitle(R.string.favorite_dialog_title);

        return layout;
    }

}

