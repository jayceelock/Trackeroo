package com.jaycee.trackeroo;

import java.io.BufferedReader;
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
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class SpecialsFragment extends Fragment
{
	private ArrayAdapter<String> mForecastAdapter;

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
		
		mForecastAdapter =	new ArrayAdapter<String>(
				getActivity(), // The current context (this activity)
				R.layout.list_item_special, // The name of the layout ID.
				R.id.list_item_specials_textview, // The ID of the textview to populate.
				weekForecast);
		
		FetchSpecialsTask specialsTask = new FetchSpecialsTask();
		specialsTask.execute("Stellenbosch");
		
		View rootView = inflater.inflate(R.layout.fragment_main, container, false);

		ListView listView = (ListView)rootView.findViewById(R.id.listview_specials);
		listView.setAdapter(mForecastAdapter);

		return rootView;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		int id = item.getItemId();

		if(id == R.id.action_refresh)
		{
			FetchSpecialsTask specialsTask = new FetchSpecialsTask();
			
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String location = prefs.getString(getString(R.string.pref_location_key),
                    getString(R.string.pref_location_default));
            
			specialsTask.execute("stellenbosch");

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
			
			for(int i = 0; i < specialsJson.length(); i ++)
			{
				JSONObject restaurantObject = specialsJson.getJSONObject(i);
				JSONArray restaurantDetailsArray = restaurantObject.getJSONArray("details");
				
				String restaurantName = restaurantObject.getString("restaurant");
				restaurantName = Character.toUpperCase(restaurantName.charAt(0)) + restaurantName.substring(1);
				
				String price = restaurantDetailsArray.getString(0);
				String descrip = restaurantDetailsArray.getString(1);
				
				returnResult[i] = "Restaurant: " + restaurantName + "\nPrice: R" + price + "\n" + descrip;
				
				//Log.v(LOG_TAG, "Forecast entry: " + returnResult[i]);
			}
			
			//Log.v(LOG_TAG, "Forecast entry: " + specialsJson.length());
			//String[] awe = {"hello", "daar"};
			
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
			String day = "monday";
			String location = "stellenbosch";
			
			//int numDays = 7;
			
			try 
			{
				// Construct the URL for the OpenWeatherMap query
				// Possible parameters are avaiable at OWM's forecast API page, at
				// http://openweathermap.org/API#forecast
				//final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
				//final String QUERY_PARAM = "q";
				//final String FORMAT_PARAM = "mode";
				//final String UNITS_PARAM = "units";
				//final String DAYS_PARAM = "cnt";
				
				final String SPECIAL_BASE_URL = "http://trackeroo-specials.appspot.com/";
				final String DAY_PARAM = "day";
				final String LOCATION_PARAM = "loc";
				
				Uri builtUri = Uri.parse(SPECIAL_BASE_URL).buildUpon()
						.appendQueryParameter(DAY_PARAM, day)
						.appendQueryParameter(LOCATION_PARAM, location)
						.build();
				
				URL url = new URL(builtUri.toString());
				Log.v(LOG_TAG, "Built URI " + builtUri.toString());
				//Toast.makeText(getActivity(), "BLAH", Toast.LENGTH_LONG).show();
				// Create the request to OpenWeatherMap, and open the connection
				urlConnection = (HttpURLConnection) url.openConnection();
				urlConnection.setRequestMethod("GET");
				urlConnection.connect();
				
				// Read the input stream into a String
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
				//return getWeatherDataFromJson(forecastJsonStr, numDays);
				
				return getSpecialsFromJson(specialJsonStr);
				
				//String[] meh = {"blah", "blah"};
				//return meh;
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
				mForecastAdapter.clear();
				
				for(String specialStr : result) 
				{
					mForecastAdapter.add(specialStr);
				}
				// New data is back from the server. Hooray!
			}
		}
	}
}
