package com.jaycee.trackeroo;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class DetailActivity extends Activity 
{
	private final String LOG_TAG = DetailActivity.class.getSimpleName();
	
	private double mapLong, mapLat;
	
	LocationManager locationManager;
	
	public void openMap(View view)
	{			
		Location loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		//loc.getAccuracy();
		
		String uri = "http://maps.google.com/maps?daddr=" + String.valueOf(mapLat) + "," + String.valueOf(mapLong) + "&saddr=" + String.valueOf(loc.getLatitude()) + "," + String.valueOf(loc.getLongitude());

		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setData(Uri.parse(uri));
		
		startActivity(intent);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_detail);
		
		Intent intent = this.getIntent();
        Bundle extras = intent.getExtras();
        
        mapLong = extras.getDouble("EXTRA_LONG");
        mapLat = extras.getDouble("EXTRA_LAT");
		
		if (savedInstanceState == null) 
		{
			getFragmentManager().beginTransaction().add(R.id.container, new DetailFragment()).commit();
		}
		
		locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		//getMenuInflater().inflate(R.menu.detail, menu);
		
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		
		if (id == R.id.action_settings) 
		{
			startActivity(new Intent(this, SettingsActivity.class));
			
			return true;
		}
		
		return super.onOptionsItemSelected(item);
	}

	public static class DetailFragment extends Fragment 
	{
		private static final String LOG_TAG = DetailFragment.class.getSimpleName();
		
		private String mSpecialsStr;
		//private double mapLong, mapLat;
		
		public DetailFragment() 
		{
			setHasOptionsMenu(true);
		}
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) 
		{
			View rootView = inflater.inflate(R.layout.fragment_detail, container, false);
			
			 // The detail Activity called via intent.  Inspect the intent for forecast data.
            Intent intent = getActivity().getIntent();
            Bundle extras = intent.getExtras();
            
            mSpecialsStr = extras.getString("EXTRA_SPECIALS");
            
            ((TextView) rootView.findViewById(R.id.detail_text)).setText(mSpecialsStr);
            
			return rootView;
		}
	}
}
