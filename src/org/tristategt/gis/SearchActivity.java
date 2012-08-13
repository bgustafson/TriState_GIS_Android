package org.tristategt.gis;

import java.util.Arrays;
import java.util.HashSet;

import com.esri.core.map.FeatureSet;
import com.esri.core.map.Graphic;
import com.esri.core.tasks.ags.query.Query;
import com.esri.core.tasks.ags.query.QueryTask;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;


public class SearchActivity extends Activity implements OnClickListener {
	
	Context context;
	Boolean isChecked;
	String[] outFields; 
	MySearchTask mSearchTask;
	ListView listView;
	Graphic selectedLocation;
	RadioButton rButtonStations;
	RadioButton rButtonLines;
	ImageButton iButton;
	TextView tv, tvSelected;
	String selectedFromList;
	
	private static final String webUri = "http://geowebd.tristategt.org/ArcGIS/rest/services/Android/MapServer";
	int layerID;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.search);
		listView = (ListView) findViewById(R.id.listview);
		rButtonStations = (RadioButton) findViewById(R.id.radioStations);
		rButtonStations.setOnClickListener(this);
		rButtonLines = (RadioButton) findViewById(R.id.radioLines);
		rButtonLines.setOnClickListener(this);
		iButton = (ImageButton) findViewById(R.id.findInMap);
		iButton.setOnClickListener(this);
		iButton.setClickable(false);
		iButton.setAlpha(50);
		tv = (TextView)findViewById(R.id.textView1);
		tvSelected = (TextView)findViewById(R.id.selectedfeature_tv);
		tvSelected.setText("");
		context = this;
		
		mSearchTask = new MySearchTask();
		mSearchTask.execute("");
		
		//on selection change in the list run a query for the selected
		//feature and then send the result back to the map activity
		listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				//Need to pass the selected name as a string
				iButton.setClickable(false);
				iButton.setAlpha(50);
				tvSelected.setText("");
				myFindFeatureTask mTask = new myFindFeatureTask();
				selectedFromList = (String)(listView.getItemAtPosition(arg2));
				mTask.execute(selectedFromList);	
			}
		});
			
	}
	
	@Override 
	protected void onDestroy() { 
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.searchmenu, menu);
		return true;		
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		switch(item.getItemId())
		{
			case R.id.itemPrefs:
				startActivity(new Intent(this, PrefsActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
				break;
		}
		return true;
	}
	
	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		return true;
	}
	
	private class MySearchTask extends AsyncTask<String, Void, FeatureSet>{

		Query query;
		QueryTask queryTask;
		
		@Override
		protected void onPreExecute() {
			tv.setText("Populating List");
			RadioButton mRadioButton = (RadioButton) findViewById(R.id.radioStations);
			isChecked = mRadioButton.isChecked();
			outFields = isChecked ? new String[] {"LOCATION"} : new String[] {"LINEDESCRIPTION"};
			layerID = isChecked ? 3 : 5;

			query = new Query();
			query.setReturnGeometry(false);
			query.setOutFields(outFields);
			query.setWhere("1=1");
			queryTask = new QueryTask(webUri + "/" + layerID);
		}
		
		@Override
		protected FeatureSet doInBackground(String... params) {
			try {
				return queryTask.execute(query);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		
		@Override
		protected void onPostExecute(FeatureSet results) {
						
			if(results != null && results.getGraphics() != null){
				Graphic[] graphics = results.getGraphics();
				
				HashSet<String> names = new HashSet<String>();
					
				
				for(Graphic g : graphics){
					names.add(g.getAttributeValue(outFields[0]).toString());
				}
				
				Object[] myArray = names.toArray();
				String[] stringArray = new String[myArray.length];
				int i = 0;
				for(Object o : myArray){
					stringArray[i] = o.toString();
					i++;
				}
				Arrays.sort(stringArray);
				
				ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, stringArray);
				listView.setAdapter(adapter);
			}else{
				Toast.makeText(context, "No results, check that you are connected to the TSGT network.", Toast.LENGTH_LONG).show();
			}
			
			LinearLayout layout = (LinearLayout) findViewById(R.id.processingProgress);
			layout.setVisibility(View.INVISIBLE);
		}
	}

	private class myFindFeatureTask extends AsyncTask<String, Void, FeatureSet>{
		Query query;
		QueryTask queryTask;
		
		@Override
		protected void onPreExecute() {
			tv.setText("Searching For Selected Feature");
			LinearLayout layout = (LinearLayout) findViewById(R.id.processingProgress);
			layout.setVisibility(View.VISIBLE);
			RadioButton mRadioButton = (RadioButton) findViewById(R.id.radioStations);
			isChecked = mRadioButton.isChecked();
			outFields = isChecked ? new String[] {"LOCATION"} : new String[] {"LINEDESCRIPTION"};
			layerID = isChecked ? 3 : 5;

			query = new Query();
			query.setReturnGeometry(true);
			query.setOutFields(outFields);
			queryTask = new QueryTask(webUri + "/" + layerID);
		}
		
		@Override
		protected FeatureSet doInBackground(String... params) {
			try {
				query.setWhere(outFields[0] + " = '" + params[0] + "'");
				return queryTask.execute(query);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		
		@Override
		protected void onPostExecute(FeatureSet results) {
						
			if(results != null){
				iButton.setClickable(true);
				iButton.setAlpha(1000);
				Graphic[] graphics = results.getGraphics();
									
				for(Graphic g : graphics){
					selectedLocation = g;
				}
				
				tvSelected.setText("Click globe to zoom too: " + selectedFromList);
			}else{
				Toast.makeText(context, "No Results", Toast.LENGTH_LONG).show();
			}
			
			LinearLayout layout = (LinearLayout) findViewById(R.id.processingProgress);
			layout.setVisibility(View.INVISIBLE);
		}
	}
	
	@Override
	public void onClick(View v) {
		int buttonId = v.getId();
		LinearLayout layout = (LinearLayout) findViewById(R.id.processingProgress);
		mSearchTask = new MySearchTask();
		
		iButton.setClickable(false);
		iButton.setAlpha(50);
		tvSelected.setText("");
		
		switch(buttonId){
			case R.id.radioStations:
				layout.setVisibility(View.VISIBLE);
				rButtonStations.setChecked(true);
				rButtonLines.setChecked(false);
				mSearchTask.execute("");
				break;
			case R.id.radioLines:
				layout.setVisibility(View.VISIBLE);
				rButtonLines.setChecked(true);
				rButtonStations.setChecked(false);
				mSearchTask.execute("");
				break;
			case R.id.findInMap:
				//send the selected graphic back to the map
				Intent intent = new Intent(this, MapActivity.class);
				intent.putExtra("from", "Search");
				intent.putExtra("selectedLocation", selectedLocation);
				setResult(Activity.RESULT_OK, intent);
		        finish();

				break;
		}
	}
	
}
