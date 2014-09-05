package com.jaycee.trackeroo;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Fragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class SpecialsFragment extends Fragment
{
	private ArrayAdapter<String> mSpecialsAdapter;
	private final String LOG_TAG = FetchSpecialsTask.class.getSimpleName();
	
	private double[] mapLong;
	private double[] mapLat;

	public SpecialsFragment()
	{
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		setHasOptionsMenu(true);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		inflater.inflate(R.menu.specialsfragment, menu);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) 
	{
		List<String> weekForecast = new ArrayList<String>();

		mSpecialsAdapter =	new ArrayAdapter<String>(
				getActivity(), // The current context (this activity)
				R.layout.list_item_special, // The name of the layout ID.
				R.id.list_item_specials_textview, // The ID of the textview to populate.
				weekForecast);

		View rootView = inflater.inflate(R.layout.fragment_main, container, false);

		ListView listView = (ListView)rootView.findViewById(R.id.listview_specials);
		listView.setAdapter(mSpecialsAdapter);
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() 
		{
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) 
			{
				String specials = mSpecialsAdapter.getItem(position); 
				
				Bundle extras = new Bundle();
				
				extras.putString("EXTRA_SPECIALS", specials);
				extras.putDouble("EXTRA_LAT", mapLat[position]);
				extras.putDouble("EXTRA_LONG", mapLong[position]);
				
				Intent intent = new Intent(getActivity(), DetailActivity.class).putExtras(extras);
				startActivity(intent);
			}
		});

		return rootView;
	}

	public void updateSpecials()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		String location = prefs.getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default));
		String day = prefs.getString(getString(R.string.pref_day_key), getString(R.string.pref_day_default));
		
		FetchSpecialsTask specialsTask = new FetchSpecialsTask();
		
		//Log.v(LOG_TAG, "Location: " + location);
		specialsTask.execute(location, day);
	}

	@Override
	public void onStart()
	{
		super.onStart();
		updateSpecials();
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		updateSpecials();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		int id = item.getItemId();

		if(id == R.id.action_refresh)
		{
			updateSpecials();

			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	public class FetchSpecialsTask extends AsyncTask<String, Void, String[]> 
	{
		private final String LOG_TAG = FetchSpecialsTask.class.getSimpleName();

		/* The date/time conversion code is going to be moved outside the asynctask later,
		 * so for convenience we're breaking it out into its own method now.
		 */
		private String getReadableDateString(long time)
		{
			// Because the API returns a unix timestamp (measured in seconds),
			// it must be converted to milliseconds in order to be converted to valid date.
			Date date = new Date(time * 1000);
			SimpleDateFormat format = new SimpleDateFormat("E, MMM d");
			return format.format(date).toString();
		}

		/**
		 * Prepare the weather high/lows for presentation.
		 */
		private String formatHighLows(double high, double low) 
		{
			// For presentation, assume the user doesn't care about tenths of a degree.
			long roundedHigh = Math.round(high);
			long roundedLow = Math.round(low);
			String highLowStr = roundedHigh + "/" + roundedLow;
			return highLowStr;
		}

		private String[] getSpecialsFromJson(String jsonString) throws JSONException
		{
			final String OWM_RESTAURANT_NAME = "restaurant_name";

			JSONArray specialsJson = new JSONArray(jsonString);

			String[] returnResult = new String[specialsJson.length()];
			
			mapLong = new double[specialsJson.length()];
			mapLat = new double[specialsJson.length()];

			for(int i = 0; i < specialsJson.length(); i ++)
			{
				JSONObject restaurantObject = specialsJson.getJSONObject(i);
				JSONArray restaurantDetailsArray = restaurantObject.getJSONArray("details");

				String restaurantName = restaurantObject.getString("restaurant");
				restaurantName = Character.toUpperCase(restaurantName.charAt(0)) + restaurantName.substring(1);
				
				mapLat[i] = restaurantObject.getDouble("lat");
				mapLong[i] = restaurantObject.getDouble("long");

				String price = restaurantDetailsArray.getString(0);
				String descrip = restaurantDetailsArray.getString(1);

				returnResult[i] = "Restaurant: " + restaurantName + "\nPrice: R" + price + "\n" + descrip;

				//Log.v(LOG_TAG, "Forecast entry: " + returnResult[i]);
			}

			return returnResult;
		}

		@Override
		protected String[] doInBackground(String... params) 
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
			//String day = "monday";

			try 
			{
				//Construct the query

				final String SPECIAL_BASE_URL = "http://trackeroo-specials.appspot.com/";
				final String DAY_PARAM = "day";
				final String LOCATION_PARAM = "loc";

				Uri builtUri = Uri.parse(SPECIAL_BASE_URL).buildUpon()
						.appendQueryParameter(DAY_PARAM, params[1].toLowerCase())
						.appendQueryParameter(LOCATION_PARAM, params[0].toLowerCase().replaceAll(" ", ""))
						.build();

				URL url = new URL(builtUri.toString());
				
				// Create the request to OpenWeatherMap, and open the connection
				urlConnection = (HttpURLConnection) url.openConnection();
				urlConnection.setRequestMethod("GET");
				urlConnection.connect();				
				
				// Read the input stream into a String
				
				if(urlConnection.getResponseCode() >= 400)
				{
					String[] badPage = {"There are no specials in " + params[0] + " on a " + params[1]};
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
				return getSpecialsFromJson(specialJsonStr);
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
		protected void onPostExecute(String[] result) 
		{
			if (result != null) 
			{
				mSpecialsAdapter.clear();

				for(String specialStr : result) 
				{
					mSpecialsAdapter.add(specialStr);
				}
				// New data is back from the server. Hooray!
			}
		}
	}
}
