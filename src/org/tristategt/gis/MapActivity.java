package org.tristategt.gis;

import org.tristategt.common.GenericMapTouchListener;
import org.tristategt.common.Dialogs.DrawDialog;
import org.tristategt.common.Dialogs.MeasureDialog;
import org.tristategt.common.Draw.DrawTouchListener;
import org.tristategt.common.Identify.IdentifyListener;
import org.tristategt.common.Measure.MeasureCalcConverter;
import org.tristategt.common.Measure.MeasureTouchListener;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.android.map.GraphicsLayer;
import com.esri.android.map.Layer;
import com.esri.android.map.LocationService;
import com.esri.android.map.MapView;
import com.esri.android.map.ags.ArcGISDynamicMapServiceLayer;
import com.esri.android.map.ags.ArcGISLocalTiledLayer;
import com.esri.android.map.ags.ArcGISTiledMapServiceLayer;
import com.esri.core.geometry.Envelope;
import com.esri.core.geometry.Geometry;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.Polyline;
import com.esri.core.map.Graphic;


public class MapActivity extends Activity implements OnSharedPreferenceChangeListener {
	
	private static final String webUri = "http://geowebp.tristategt.org/ArcGIS/rest/services/Android/MapServer";
	private String localUri = "file:///mnt/sdcard/GIS_Data/East";
	Object init;
	CharSequence[] units;
	
	SharedPreferences prefs;
	boolean	mode;
	Menu myMenu;
	MapView mMapView;
	Layer TSGTLayer;
	ArcGISTiledMapServiceLayer bgLayerStreet, bgLayerAerial;
	GraphicsLayer graphicsLayer, measureGraphicsLayer;
	DrawTouchListener drawTouchListener;
	MeasureTouchListener measureTouchListener;
	IdentifyListener identifyListener;
	MeasureCalcConverter measureCalculator;
	Drawable idOn, idOff;

		
	LocationService ls;
	final static double SEARCH_RADIUS = 5;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mMapView = (MapView) findViewById(R.id.map);
        
        idOn = getResources().getDrawable(R.drawable.id_on);
		idOff = getResources().getDrawable(R.drawable.id_off);
                                 
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
        localUri = prefs.getString("cache_location", "file:///mnt/sdcard/GIS_Data/East");
        mode = prefs.getBoolean("mode", true);
        String txt = mode ? "Web Data Mode" : "Local Data Mode";
        Toast.makeText(this, txt, Toast.LENGTH_SHORT).show();
						
		// Add dynamic layer to MapView
		bgLayerStreet = new ArcGISTiledMapServiceLayer("http://services.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer");
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
        	myMenu.getItem(4).setEnabled(false);
        	myMenu.getItem(4).setVisible(false);
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
				break;
			case R.id.itemMeasure:
				createMeasureDialog();
				break;
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
	public void getMyLocation(){
		ls = mMapView.getLocationService();
		
		if(ls.isStarted() == true){
			ls.stop();
			return;
		}
					
		ls.setAutoPan(true);
		ls.start();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mode = prefs.getBoolean("mode", true);
        localUri = prefs.getString("cache_location", "file:///mnt/sdcard/GIS_Data/East");
		
		//change out map layers as needed
		if(mode == true){
			
			mMapView.removeLayer(2);
			TSGTLayer = new ArcGISDynamicMapServiceLayer(webUri);
			mMapView.addLayer(TSGTLayer, 2);	
			identifyListener = new IdentifyListener(MapActivity.this, mMapView, TSGTLayer, getApplication());
			mMapView.setOnTouchListener(identifyListener);
			
			myMenu.getItem(2).setEnabled(true);
			myMenu.getItem(2).setVisible(true);
			myMenu.getItem(4).setEnabled(true);
			myMenu.getItem(4).setVisible(true);
						
			Toast.makeText(this, "Web Data Mode", Toast.LENGTH_SHORT).show();
			
		}else{
			
			try{mMapView.removeLayer(2);
				TSGTLayer = new ArcGISLocalTiledLayer(localUri);
				mMapView.addLayer(TSGTLayer, 2);
				identifyListener = new IdentifyListener(MapActivity.this, mMapView, null, getApplication());
        		mMapView.setOnTouchListener(identifyListener);
        	
        		myMenu.getItem(2).setEnabled(false);
        		myMenu.getItem(2).setVisible(false);
        		myMenu.getItem(4).setEnabled(false);
        		myMenu.getItem(4).setVisible(false);
        	
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
}