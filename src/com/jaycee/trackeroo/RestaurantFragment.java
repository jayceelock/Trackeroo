package com.jaycee.trackeroo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

public class RestaurantFragment extends Fragment 
{
	private ArrayAdapter<String> mRestaurantAdapter;
	private final String LOG_TAG = RestaurantFragment.class.getSimpleName();
	
	LocationManager locationManager;
	String restuarantLongitude, restaurantLatitude;
	
	public RestaurantFragment() 
	{
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		//setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) 
	{
		mRestaurantAdapter =
                new ArrayAdapter<String>(
                    getActivity(), // The current context (this activity)
                    R.layout.list_item_restaurant, // The name of the layout ID.
                    R.id.list_item_restaurant_textview, // The ID of the textview to populate.
                    new ArrayList<String>());

        View rootView = inflater.inflate(R.layout.fragment_restaurant_detail, container, false);

        // Get a reference to the ListView, and attach this adapter to it.
        ListView listView = (ListView) rootView.findViewById(R.id.listview_restaurants);
        listView.setAdapter(mRestaurantAdapter);
        
        locationManager = (LocationManager)getActivity().getSystemService(Context.LOCATION_SERVICE);
		
		Button mButton = (Button)rootView.findViewById(R.id.detail_open_map);
	    mButton.setOnClickListener(new OnClickListener() 
	    {
	        @Override
	        public void onClick(View view) 
	        {
	        	//Toast.makeText(getActivity(), "awe", Toast.LENGTH_SHORT).show();
	        	Location loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
	        	//loc.getAccuracy();

	        	String uri = "http://maps.google.com/maps?daddr=" + String.valueOf(restaurantLatitude) + "," + String.valueOf(restuarantLongitude) + "&saddr=" + String.valueOf(loc.getLatitude()) + "," + String.valueOf(loc.getLongitude());
	        	Log.v(LOG_TAG, "URI:" + uri);
	        	Intent intent = new Intent(Intent.ACTION_VIEW);
	        	intent.setData(Uri.parse(uri));

	        	startActivity(intent);
	        }
	    });
		
		return rootView;
	}
	
	private void updateRestaurants()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		String location = prefs.getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default));
		
		Intent intent = getActivity().getIntent();
		String restaurant = intent.getStringExtra("EXTRA_RESTAURANT");
		
		FetchRestaurantsTask fetchRestaurant = new FetchRestaurantsTask();
		
		fetchRestaurant.execute(restaurant, location);
	}
	
	@Override
	public void onStart()
	{
		super.onStart();
		updateRestaurants();
	}
	
	public class FetchRestaurantsTask extends AsyncTask<String, Void, String> 
	{
		private final String LOG_TAG = FetchRestaurantsTask.class.getSimpleName();

		@Override
		protected String doInBackground(String... params) 
		{
			// If there's no zip code, there's nothing to look up. Verify size of params.
			if (params.length == 0) 
			{
				return null;
			}

			// These two need to be declared outside the try/catch
			// so that they can be closed in the finally block.
			HttpURLConnection urlConnection = null;
			BufferedReader reader = null;

			// Will contain the raw JSON response as a string.
			String specialJsonStr = null;

			try 
			{
				//Construct the query

				final String SPECIAL_BASE_URL = "http://trackeroo-specials.appspot.com/restaurantpage";
				final String RESTAURANT_PARAM = "rest";
				final String LOCATION_PARAM = "loc";

				Uri builtUri = Uri.parse(SPECIAL_BASE_URL).buildUpon()
						.appendQueryParameter(RESTAURANT_PARAM, params[0].toLowerCase().replaceAll(" ", ""))
						.appendQueryParameter(LOCATION_PARAM, params[1].toLowerCase().replaceAll(" ", ""))
						.build();

				URL url = new URL(builtUri.toString());
				
				// Create the request to OpenWeatherMap, and open the connection
				urlConnection = (HttpURLConnection) url.openConnection();
				urlConnection.setRequestMethod("GET");
				urlConnection.connect();				
				
				// Read the input stream into a String
				Log.v(LOG_TAG, "website: " + builtUri.toString());
				if(urlConnection.getResponseCode() >= 400)
				{
					String badPage = "Oops, could not find this restaurant on the server. The server is most likely down.";
					return badPage;
				}
				
				InputStream inputStream = urlConnection.getInputStream();
				StringBuffer buffer = new StringBuffer();
				
				if (inputStream == null) 
				{
					// Nothing to do.
					return null;
				}

				reader = new BufferedReader(new InputStreamReader(inputStream));

				String line;

				while ((line = reader.readLine()) != null) 
				{
					// Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
					// But it does make debugging a *lot* easier if you print out the completed
					// buffer for debugging.
					buffer.append(line + "\n");
				}

				if (buffer.length() == 0) 
				{
					// Stream was empty. No point in parsing.
					return null;
				}

				specialJsonStr = buffer.toString();
				Log.v(LOG_TAG, "Forecast string: " + specialJsonStr);
				
				
			} 

			catch (IOException e) 
			{
				Log.e(LOG_TAG, "Error ", e);

				// If the code didn't successfully get the weather data, there's no point in attemping
				// to parse it.
				return null;
			} 

			finally 
			{
				if (urlConnection != null) 
				{
					urlConnection.disconnect();
				}

				if (reader != null) 
				{
					try 
					{
						reader.close();
					} 

					catch (final IOException e) 
					{
						Log.e(LOG_TAG, "Error closing stream", e);
					}
				}
			}

			try 
			{
				return getRestaurantsFromJson(specialJsonStr);
			}

			catch (JSONException e) 
			{
				Log.e(LOG_TAG, e.getMessage(), e);
				e.printStackTrace();
			}

			// This will only happen if there was an error getting or parsing the forecast.
			return null;
		}

		@Override
		protected void onPostExecute(String result) 
		{
			Log.v(LOG_TAG, "Entering onPostExecute method");
			//Toast.makeText(getActivity(), "Hello", Toast.LENGTH_LONG).show();
			if (result != null) 
			{
				TextView restaurantDetailsText = (TextView)getActivity().findViewById(R.id.textview_restaurant_detail);
				
				restaurantDetailsText.setText(result);
				//int i = 0;
				//Log.v(LOG_TAG, result[0].getClass().getName());
				//mRestaurantAdapter.clear();
				//Log.v(LOG_TAG, result[0]);
				//Log.v(LOG_TAG, result[1]);
				
				//Log.v(LOG_TAG, String.valueOf(i++) + restaurantStr);
				//mRestaurantAdapter.add(result);
				
				// New data is back from the server. Hooray!
			}
		}
		
		private String getRestaurantsFromJson(String jsonString) throws JSONException
		{
			JSONObject restaurantObject = new JSONObject(jsonString);
			JSONObject extrasObject = restaurantObject.getJSONObject("extras");
			
			String latitude = restaurantObject.getString("lat");
			String longitude = restaurantObject.getString("long");
			String description = restaurantObject.getString("description");
			String address = restaurantObject.getString("address");
			String telnumber = restaurantObject.getString("telnumber");
			String email = restaurantObject.getString("email");
			String website = restaurantObject.getString("website");
			String operatingHours = restaurantObject.getString("operating_hours");
			String wifi = extrasObject.getString("wifi");
			
			Log.v(LOG_TAG, latitude);
			
			/*
			String[] returnResult = new String[restaurantObject.length()];
			
			returnResult[0] = latitude;
			returnResult[1] = longitude;
			returnResult[2] = description;
			returnResult[3] = address;
			returnResult[4] = telnumber;
			returnResult[5] = email;
			returnResult[6] = website;
			returnResult[7] = operatingHours;
			returnResult[8] = wifi;
			*/
			restaurantLatitude = latitude;
			restuarantLongitude = longitude;
			
			String returnResult = "Description:    " + description + 
								  "\nAddress:		   " + address		+
								  "\nTelephone nr:   " + telnumber + 
								  "\nEmail:		   " + email + 
								  "\nWebsite:		   " + website + 
								  "\nOperating hours:" + operatingHours + 
								  "\nWiFi			   " + wifi;
			
			Log.v(LOG_TAG, "Lat and long:  " + restaurantLatitude + " " + longitude);
			
			return returnResult;
		}
	}
}