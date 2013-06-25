package org.tristategt.gis;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tristategt.common.GenericMapTouchListener;
import org.tristategt.common.Dialogs.DrawDialog;
import org.tristategt.common.Dialogs.MeasureDialog;
import org.tristategt.common.Draw.DrawTouchListener;
import org.tristategt.common.Identify.IdentifyListener;
import org.tristategt.common.Legend.LegendActivity;
import org.tristategt.common.Measure.MeasureCalcConverter;
import org.tristategt.common.Measure.MeasureTouchListener;
import org.tristategt.gis.portal.AGSPortalGroupsActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.android.map.GraphicsLayer;
import com.esri.android.map.Layer;
import com.esri.android.map.LocationService;
import com.esri.android.map.MapOnTouchListener;
import com.esri.android.map.MapView;
import com.esri.android.map.ags.ArcGISDynamicMapServiceLayer;
import com.esri.android.map.ags.ArcGISLayerInfo;
import com.esri.android.map.ags.ArcGISLocalTiledLayer;
import com.esri.android.map.ags.ArcGISTiledMapServiceLayer;
import com.esri.core.geometry.Envelope;
import com.esri.core.geometry.Geometry;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.Polyline;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.map.Graphic;
import com.esri.core.map.Legend;


public class MapActivity extends Activity implements OnSharedPreferenceChangeListener {
	
	private File extSDCard;
	private File fileBase;
	private static final String webUri = "http://geowebp.tristategt.org/ArcGIS/rest/services/Android/MapServer";
	private String localUri;
	private CharSequence[] units;
	private SharedPreferences prefs;
	private Boolean	mode;
	private Menu myMenu;
	private MapView mMapView;
	private Layer TSGTLayer;
	private ArcGISTiledMapServiceLayer bgLayerStreet, bgLayerAerial;
	private GraphicsLayer graphicsLayer, measureGraphicsLayer;
	private DrawTouchListener drawTouchListener;
	private MeasureTouchListener measureTouchListener;
	private IdentifyListener identifyListener;
	private MeasureCalcConverter measureCalculator;
	private Drawable idOn, idOff;

		
	LocationService ls;
	PowerManager pm;
    PowerManager.WakeLock wl;
	final static double SEARCH_RADIUS = 5;
	

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mMapView = (MapView) findViewById(R.id.map);
                
        idOn = getResources().getDrawable(R.drawable.id_on);
		idOff = getResources().getDrawable(R.drawable.id_off);
		createRequiredDirs();
		localUri = "file://" + fileBase + "/East";
	     
        LinearLayout layout = (LinearLayout) findViewById(R.id.measurelayout);
        final TextView mTextView = (TextView) findViewById(R.id.measuretextView);
        mTextView.setOnClickListener( new OnClickListener() {
			@Override
			public void onClick(View v) {
				String type = measureTouchListener.getType();
				
				if(type.equalsIgnoreCase("Area")){
					units =  new CharSequence[] {"SQ Miles", "SQ Feet", "SQ Kilometers", "SQ Meters"};
				}else{
					units = new CharSequence[] {"Miles", "Feet", "Kilometers", "Meters"};
				}
												
				// convert the text in the text view to the selected value
				AlertDialog.Builder builder = new AlertDialog.Builder(mMapView.getContext());
				builder.setTitle("Pick a Unit");
				builder.setItems(units, new DialogInterface.OnClickListener() {
				    public void onClick(DialogInterface dialog, int item) {
				    	//call the conversion function and set the text				        
				        String selectedUnit = units[item].toString();
				        
				        //area and length are always returned in sq miles or miles
				        if(selectedUnit.equalsIgnoreCase("SQ Miles")){
				        	mTextView.setText("Sq Miles: " + measureCalculator.calcSqMiles((measureTouchListener.getArea())));
				        }else if(selectedUnit.equalsIgnoreCase("SQ Feet")){
				        	mTextView.setText("Sq Feet: " + measureCalculator.calcSqFeet(measureTouchListener.getArea()));
				        }else if(selectedUnit.equalsIgnoreCase("SQ Kilometers")){
				        	mTextView.setText("Sq KM: " + measureCalculator.calcSqKiloMeters(measureTouchListener.getArea()));
				        }else if(selectedUnit.equalsIgnoreCase("SQ Meters")){
				        	mTextView.setText("Sq Meters: " + measureCalculator.calcSqMeters(measureTouchListener.getArea()));
				        }else if(selectedUnit.equalsIgnoreCase("Miles")){
				        	mTextView.setText("Miles: " + measureCalculator.calcMiles(measureTouchListener.getLength()));
				        }else if(selectedUnit.equalsIgnoreCase("Feet")){
				        	mTextView.setText("Feet: " + measureCalculator.calcFeet(measureTouchListener.getLength()));
				        }else if(selectedUnit.equalsIgnoreCase("Kilometers")){
				        	mTextView.setText("KM: " + measureCalculator.calcKiloMeters(measureTouchListener.getLength()));
				        }else if(selectedUnit.equalsIgnoreCase("Meters")){
				        	mTextView.setText("Meters: " + measureCalculator.calcMeters(measureTouchListener.getLength()));
				        }
				    }
				});
				builder.create().show();
			}
		});
                
        //figure out the mode to start in
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        
        //figure out where to look for cache
        fileBase = getCacheLocation(prefs.getBoolean("StorageLocation", true));
        if(!prefs.contains("cachelocation")){
        	Editor editor = prefs.edit();
        	if(prefs.getBoolean("StorageLocation", true))
        		editor.putString("cachelocation", "SD Card\\GIS_Data");
        	else
        		editor.putString("cachelocation", "Internal Storage\\GIS_Data");
        	editor.commit();
        }
                
        String s = prefs.getString("cache_location", "East");
        localUri = "file://" + fileBase + "/" + s;
        mode = prefs.getBoolean("mode", true);
        String txt = mode ? "Web Data Mode" : "Local Data Mode";
        Toast.makeText(this, txt, Toast.LENGTH_SHORT).show();
						
		// Add dynamic layer to MapView
		bgLayerStreet = new ArcGISTiledMapServiceLayer("http://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer");//"http://services.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer");
		bgLayerAerial = new ArcGISTiledMapServiceLayer("http://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer");
		graphicsLayer = new GraphicsLayer();
		measureGraphicsLayer = new GraphicsLayer();
		bgLayerAerial.setVisible(false);
		mMapView.addLayer(bgLayerStreet);
		mMapView.addLayer(bgLayerAerial);
		mMapView.addLayer(graphicsLayer);
		mMapView.addLayer(measureGraphicsLayer);

		//if mode is true then load in dynamic layer if mode is false load in local layer
		if(mode == true){
			TSGTLayer = new ArcGISDynamicMapServiceLayer(webUri);
			mMapView.addLayer(TSGTLayer, 2);	
        }else{
        	try{
        		TSGTLayer = new ArcGISLocalTiledLayer(localUri);
        		mMapView.addLayer(TSGTLayer, 2);
        	}catch(Exception e){
        		Editor editor = prefs.edit();
        		editor.putBoolean("mode", true);
        		editor.commit();
				Toast.makeText(this, "Failed to open local layer\nContact GIS to get cache.", Toast.LENGTH_SHORT).show();
			}
        }
		
		identifyListener = new IdentifyListener(MapActivity.this, mMapView, TSGTLayer, getApplication());
		drawTouchListener = new DrawTouchListener(MapActivity.this, mMapView, graphicsLayer);		
		measureTouchListener = new MeasureTouchListener(MapActivity.this, mMapView, mTextView, layout, measureGraphicsLayer);
		measureCalculator = new MeasureCalcConverter();
		
		//wake lock for GPS
		pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "TSGT GIS Android");
    }
        
	@Override 
	protected void onDestroy() { 
		super.onDestroy();
	}
	
	@Override
	protected void onPause() {
		super.onPause();		
		mMapView.pause();
	}
	
	@Override 	
	protected void onResume() {
		super.onResume(); 
		mMapView.unpause();
						
		if(getIntent().getStringExtra("from") !=null)
        {
			Graphic g = (Graphic) getIntent().getSerializableExtra("selectedLocation");

			if(g.getGeometry().getType() == Geometry.Type.POINT){
				Point pt = (Point)g.getGeometry();
	            mMapView.zoomToResolution(pt, 3);
			}else{
				Polyline pl = (Polyline)g.getGeometry();
				mMapView.setExtent(pl);
			}
        }
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.mapmenu, menu);
		myMenu = menu;
		
		if(mode == false){
			myMenu.getItem(2).setEnabled(false);
    		myMenu.getItem(2).setVisible(false);
    		myMenu.getItem(3).setEnabled(false);
    		myMenu.getItem(3).setVisible(false);
		}
		return true;		
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		switch(item.getItemId())
		{
			case R.id.itemSearch:
				startActivityForResult(new Intent(this, SearchActivity.class), 1);
				break;
			case R.id.itemPrefs:
				startActivity(new Intent(this, PrefsActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
				break;
			case R.id.itemSatellite:
				bgLayerStreet.setVisible(false);
				bgLayerAerial.setVisible(true);
				Toast.makeText(this, "Setting Aerial Background", Toast.LENGTH_SHORT).show();
				break;
			case R.id.itemIdentify:
				Drawable d = item.getIcon();
				boolean b = d.equals(idOn);
				
				Drawable _icon = b ? idOff: idOn;
				item.setIcon(_icon);
								
				if(b)
					mMapView.setOnTouchListener(new GenericMapTouchListener(mMapView.getContext(), mMapView));
				else
					mMapView.setOnTouchListener(identifyListener);
				
				break;
			case R.id.itemStreets:	
				bgLayerAerial.setVisible(false);
				bgLayerStreet.setVisible(true);
				Toast.makeText(this, "Setting Streets Background", Toast.LENGTH_SHORT).show();
				break;
			case R.id.itemMyLocation:
				getMyLocation();
				break;
			case R.id.itemDraw:
				createDrawDialog();
				break;
			case R.id.itemFullExtent:
				mMapView.setExtent(new Envelope(-1.274213768636875E7, 3499505.2532140743, 
						-1.0998589775505995E7, 5664273.193113774));
				Toast.makeText(this, "Zoomed To Initial Extent", Toast.LENGTH_SHORT).show();
				break;
			case R.id.itemMeasure:
				createMeasureDialog();
				break;
			case R.id.itemCompass:
				Toast.makeText(this, "Starting Compass", Toast.LENGTH_SHORT).show();
				startActivity(new Intent(this, CompassActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
				break;
			case R.id.itemCamera:
				Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);				
				intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);			
				startActivity(intent);
				break;
			case R.id.itemGoogleMaps:
				Toast.makeText(this, "Double Tap Your Destination On The Map.", Toast.LENGTH_SHORT).show();
				
				mMapView.setOnTouchListener(new MapOnTouchListener(null, mMapView) {
					
					@Override
					public boolean onDoubleTap(MotionEvent point) {
						
						Point pnt = (Point) GeometryEngine.project(mMapView.toMapPoint(point.getX(), point.getY()),mMapView.getSpatialReference(),SpatialReference.create(4326));			
						
						Location currLocation = getMyCurrentLocation();
						ls.stop();
						double lattitude = currLocation.getLatitude();
						double longitude = currLocation.getLongitude();
						Intent intentGMaps = new Intent(android.content.Intent.ACTION_VIEW, Uri.parse("http://maps.google.com/maps?saddr="  + lattitude + "," + longitude +"&daddr=" + pnt.getY() + "," + pnt.getX()));
						startActivity(intentGMaps);
						
						mMapView.setOnTouchListener(new GenericMapTouchListener(mMapView.getContext(), mMapView));
						return true;
					}
				});
				break;
			case R.id.itemLegend:	
				//call http://geowebd/arcgis/rest/services/Android/MapServer/legend?f=pJson
				//build legend from json
				
				//Begin remove once bug fixed
				new GetLegendsTaskToRemove().execute();            
				//stop remove and uncomment below	
				//new GetLegendsTask().execute(((ArcGISDynamicMapServiceLayer) TSGTLayer));
				break;
			/*case R.id.itemAGSOnline:
				String username = prefs.getString("username", "null");
				String password = prefs.getString("password", "null");
				
				if(username == "null" || password == "null"){
					Toast.makeText(getApplicationContext(), "You need to enter a username and password into your preferences.", Toast.LENGTH_LONG).show();
				}else{
					startActivity(new Intent(this, AGSPortalGroupsActivity.class));
				}
				break;*/
		}
		return true;
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		if(data == null){ return; };
		
		//do something with activity result here not in on resume
		if(data.getStringExtra("from") !=null)
        {
			Graphic g = (Graphic) data.getSerializableExtra("selectedLocation");

			if(g.getGeometry().getType() == Geometry.Type.POINT){
				Point pt = (Point)g.getGeometry();
	            mMapView.zoomToResolution(pt, 3);
			}else{
				Polyline pl = (Polyline)g.getGeometry();
				mMapView.setExtent(pl);
			}
        }
	}

	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		return true;
	}	
	
	private void createDrawDialog(){
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		DrawDialog myDrawDialog = DrawDialog.newInstance("Message");
		myDrawDialog.setMyListener(drawTouchListener);
		myDrawDialog.setMapView(mMapView);
		myDrawDialog.show(ft, "");
	}
	
	private void createMeasureDialog(){
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		MeasureDialog myMeasureDialog = MeasureDialog.newInstance("Message");
		myMeasureDialog.setMyListener(measureTouchListener);
		myMeasureDialog.setMapView(mMapView);
		myMeasureDialog.show(ft, "");
	}
		
	//GPS Service
	public Location getMyCurrentLocation(){
		ls = mMapView.getLocationService();		
		ls.setAutoPan(false);
		ls.start();
		return ls.getLocation();
	}
	
	public void getMyLocation(){
		ls = mMapView.getLocationService();
				
		if(!ls.isStarted()){
			ls.setAutoPan(true);
			ls.start();
			wl.acquire();
			Toast.makeText(this, "Adding Your Location To The Map", Toast.LENGTH_SHORT).show();
			return;
		}
		
		ls.stop();
		wl.release();
		Toast.makeText(this, "Removing Your Location From The Map", Toast.LENGTH_SHORT).show();
		return;
					
	}
	
	@Override
 	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {	
		if(key.equalsIgnoreCase("password") || key.equalsIgnoreCase("username")){
        	return;
        }
		
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		fileBase = getCacheLocation(prefs.getBoolean("StorageLocation", true));
        mode = prefs.getBoolean("mode", true);
        String s = prefs.getString("cache_location", "East");
        localUri = "file://" + fileBase + "/" + s;
		
		//change out map layers as needed
		if(mode == true){
			
			mMapView.removeLayer(2);
			TSGTLayer = new ArcGISDynamicMapServiceLayer(webUri);
			mMapView.addLayer(TSGTLayer, 2);	
			identifyListener = new IdentifyListener(MapActivity.this, mMapView, TSGTLayer, getApplication());
			mMapView.setOnTouchListener(identifyListener);
			
			myMenu.getItem(2).setEnabled(true);
			myMenu.getItem(2).setVisible(true);
			myMenu.getItem(3).setEnabled(true);
			myMenu.getItem(3).setVisible(true);
			myMenu.getItem(11).setEnabled(true);
			myMenu.getItem(11).setVisible(true);
						
			Toast.makeText(this, "Web Data Mode", Toast.LENGTH_SHORT).show();
			
		}else{		
			try{mMapView.removeLayer(2);
				TSGTLayer = new ArcGISLocalTiledLayer(localUri);
				mMapView.addLayer(TSGTLayer, 2);
				identifyListener = new IdentifyListener(MapActivity.this, mMapView, null, getApplication());
        		mMapView.setOnTouchListener(identifyListener);
        	
        		myMenu.getItem(2).setEnabled(false);
        		myMenu.getItem(2).setVisible(false);
        		myMenu.getItem(3).setEnabled(false);
        		myMenu.getItem(3).setVisible(false);
        		myMenu.getItem(11).setEnabled(false);
        		myMenu.getItem(11).setVisible(false);
        	
        		//zoom to layer        	
        		mMapView.zoomToResolution(TSGTLayer.getFullExtent().getCenter(), TSGTLayer.getResolution());
        	
        		Toast.makeText(this, "Local Data Mode", Toast.LENGTH_SHORT).show();
			}catch(Exception e){
				Editor editor = prefs.edit();
        		editor.putBoolean("mode", true);
        		editor.commit();
				Toast.makeText(this, "Failed to open local layer\nContact GIS to get cache.", Toast.LENGTH_SHORT).show();
			}
		}
	}

	private File getCacheLocation(boolean sdcard){
		
		if(sdcard){
			
			File storageDir = new File("/Removable/");//check Asus locations
			if(storageDir.isDirectory()){
			    String[] dirList = storageDir.list();
			    for(String s : dirList){		    	
			    	if(s.contains("MicroSD")){		
			    		extSDCard = new File(storageDir + "/" + s);
			    		break;
			    	}
			    }
			}
			
			storageDir = new File("/mnt/");//check mnt locations
			if(storageDir.isDirectory()){
			    String[] dirList = storageDir.list();
			    
			    for(String dir : dirList){		    	
			    	if(dir.contains("external")){	  		
			    		extSDCard = new File(storageDir + "/" + dir);
			    		break;
			    	}
			    	else if(dir.contains("extSdCard")){
			    		extSDCard = new File(storageDir + "/" + dir);
			    		break;
			    	}
			    	else if(dir.contains("sdcard")){	
		    			File f = new File("/mnt/sdcard");
		    			String[] dirList2 = f.list();
			    		for(String dir2 : dirList2){
			    			if(dir2.contains("extStorages")){	  		
					    		extSDCard = new File("/mnt/sdcard" + dir2 + "/SdCard");
					    		break;
					    	}
			    		}
			    	}
			    }
			}
			
			if (extSDCard != null) {
				return new File(extSDCard.toString() + "/GIS_Data");
			}else{
				Editor editor = prefs.edit();
        		editor.putBoolean("StorageLocation", false);
        		editor.commit();
				return new File(Environment.getExternalStorageDirectory() + "/GIS_Data");
			}
		}else{
			return new File(Environment.getExternalStorageDirectory() + "/GIS_Data");
		}
	}
	
	public void createRequiredDirs()
	{
		//test for external SD card
		File storageDir = new File("/Removable/");//check Asus locations
		if(storageDir.isDirectory()){
		    String[] dirList = storageDir.list();
		    for(String s : dirList){		    	
		    	if(s.contains("MicroSD")){		
		    		extSDCard = new File(storageDir + "/" + s);
		    		break;
		    	}
		    }
		}
		
		storageDir = new File("/mnt/");//check mnt locations
		if(storageDir.isDirectory()){
		    String[] dirList = storageDir.list();
		    
		    for(String dir : dirList){		    	
		    	if(dir.contains("external")){	  		
		    		extSDCard = new File(storageDir + "/" + dir);
		    		break;
		    	}
		    	else if(dir.contains("extSdCard")){
		    		extSDCard = new File(storageDir + "/" + dir);
		    		break;
		    	}
		    	else if(dir.contains("sdcard")){	
	    			File f = new File("/mnt/sdcard");
	    			String[] dirList2 = f.list();
		    		for(String dir2 : dirList2){
		    			if(dir2.contains("extStorages")){	  		
				    		extSDCard = new File("/mnt/sdcard" + dir2 + "/SdCard");
				    		break;
				    	}
		    		}
		    	}
		    }
		}
		
		if (extSDCard != null) {
			fileBase = new File(extSDCard.toString() + "/GIS_Data");
			if(!fileBase.exists()){
				fileBase.mkdirs();
				
				File fileEast = new File(fileBase + "/East");
				fileEast.mkdirs();
				File fileWest = new File(fileBase + "/West");
				fileWest.mkdirs();
				File fileSouth = new File(fileBase + "/South");
				fileSouth.mkdirs();
				File fileProject = new File(fileBase + "/Project");
				fileProject.mkdirs();
				
				Editor editor = prefs.edit();
				editor.putString("cachelocation", "SD Card\\GIS_Data");
        		editor.commit();
				Toast.makeText(getApplicationContext(), "Cache Directory @ SD Card\\GIS_Data", Toast.LENGTH_LONG).show();
			}
		}else{	
			fileBase = new File(Environment.getExternalStorageDirectory() + "/GIS_Data");
			if(!fileBase.exists()) {
				fileBase.mkdirs();
			
				File fileEast = new File(fileBase + "/East");
				fileEast.mkdirs();
				File fileWest = new File(fileBase + "/West");
				fileWest.mkdirs();
				File fileSouth = new File(fileBase + "/South");
				fileSouth.mkdirs();
				File fileProject = new File(fileBase + "/Project");
				fileProject.mkdirs();
				
				Editor editor = prefs.edit();
				editor.putString("cachelocation", "Internal Storage\\GIS_Data");
        		editor.commit();
				Toast.makeText(getApplicationContext(), "Cache Directory @ Internal Storage\\GIS_Data", Toast.LENGTH_LONG).show();
			}
		}      
	}
	
	private void startLegendActivity(HashMap<String, HashMap<String, String>> legendToBuild){
		Intent i = new Intent(this, LegendActivity.class);
		i.putExtra("legends", legendToBuild);
		startActivity(i);
	}
	
	public class GetLegendsTask extends AsyncTask<ArcGISDynamicMapServiceLayer, Integer, HashMap<String, Bitmap>> {
		
		@Override
		protected HashMap<String, Bitmap> doInBackground(ArcGISDynamicMapServiceLayer... layer) {
			 
			Boolean b = layer[0].retrieveLegendInfo();  
			ArcGISLayerInfo[] layerInfos = layer[0].getAllLayers();
	    	HashMap<String, Bitmap> legendToBuild = new HashMap<String, Bitmap>();
	    	
	    	for(ArcGISLayerInfo layerInfo : layerInfos){
				List<Legend> legends = layerInfo.getLegend();
				
				for(Legend legend : legends){
					String label = legend.getLabel();
					Bitmap bitmap = legend.getImage();
					legendToBuild.put(label, bitmap);
				}
	    	}	
	    	
			return legendToBuild;
	    }

		@Override
	    protected void onPostExecute(HashMap<String, Bitmap> legendToBuild) {    	
	    	//startLegendActivity(legendToBuild);
	    }
	}
	
	public class GetLegendsTaskToRemove extends AsyncTask<Void, Void, HashMap<String, HashMap<String, String>>> {
				
		@Override
		protected HashMap<String, HashMap<String, String>> doInBackground(Void... voids) {			
			
			HashMap<String, HashMap<String, String>> legendToBuild = new HashMap<String, HashMap<String, String>>();
			HttpClient client = new DefaultHttpClient();
			HttpPost httpPost = new HttpPost("http://geowebp.tristategt.org/arcgis/rest/services/Android/MapServer/legend");
            httpPost.addHeader("Content-type", "application/x-www-form-urlencoded");
            httpPost.addHeader("Accept", "text/plain");

            List<NameValuePair> list = new ArrayList<NameValuePair>(1);
            list.add(new BasicNameValuePair("f","json"));

            try {
                httpPost.setEntity(new UrlEncodedFormEntity(list));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            
            			
            HttpResponse response;
            try {
            	response = client.execute(httpPost);
            	String responseBody = EntityUtils.toString(response.getEntity());
                JSONObject jsonObject = new JSONObject(responseBody);

                JSONArray layers = jsonObject.getJSONArray("layers");
                
                for (int i = 0; i < layers.length(); i++) {
                	JSONObject layer = layers.getJSONObject(i);
                	String name = layer.getString("layerName");
                	int id = layer.getInt("layerId");
                	JSONArray legends = layer.getJSONArray("legend");
                	
                	HashMap<String, String> legendInfo = new HashMap<String, String>();
                	for(int ii = 0; ii < legends.length(); ii++){
                		JSONObject legend = legends.getJSONObject(ii);
                    	String label = legend.getString("label");
                    	String imageUrl = id + "/images/" + legend.getString("url");
						legendInfo.put(label, imageUrl);
                	}
                	
                	legendToBuild.put(name, legendInfo);
                }
                
                
            } catch (ClientProtocolException e) {
            	e.printStackTrace();
            } catch (IOException e) {
            	e.printStackTrace();
            } catch (JSONException e) {
				e.printStackTrace();
			}
	    	
			return legendToBuild;
	    }

		@Override
	    protected void onPostExecute(HashMap<String, HashMap<String, String>> legendToBuild) {    	
	    	startLegendActivity(legendToBuild);
	    }
	}
}

