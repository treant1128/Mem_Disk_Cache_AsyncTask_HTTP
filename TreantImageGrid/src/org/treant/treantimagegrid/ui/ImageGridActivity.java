package org.treant.treantimagegrid.ui;

import org.treant.treantimagegrid.BuildConfig;
import org.treant.treantimagegrid.util.Utils;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;

public class ImageGridActivity extends FragmentActivity {

	private static final String TAG="ImageGridActivity";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		if(BuildConfig.DEBUG){
			Utils.enableStrictMode();
		}
		super.onCreate(savedInstanceState);
		// getSupportFragmentManager() --> Return the FragmentManager for interacting with fragments associated with this activity

		// findFragmentByTag(String tag)--> Finds a Fragment that was identified by the given tag either when inflated from XML
		// or as supplied when added in a transaction. This first searches through fragments that are currently added to the
		// manager'activity, if no such fragment is found, then all fragments currently on the back stack are searched.
		
		// android.R.id.content---------->ºÜÖØÒª
		if(getSupportFragmentManager().findFragmentByTag(TAG)==null){
			final FragmentTransaction fragmentTransaction=getSupportFragmentManager().beginTransaction();
			fragmentTransaction.add(android.R.id.content, new ImageGridFragment(), TAG);
			fragmentTransaction.commit();
		}
	}
}
