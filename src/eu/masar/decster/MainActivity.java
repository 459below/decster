package eu.masar.decster;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;
import eu.masar.decster.R;

import net.hockeyapp.android.CheckUpdateTask;

/**
 * This file is part of Decster.
 * Decster is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Decster is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Decster.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 * @author tmasar
 *
 */
public class MainActivity extends Activity {
	private CheckUpdateTask checkUpdateTask;
	public static class switchcmds {
		public static final String GET_SWITCH_LIST = "getswitchlist";
		public static final String SWITCH_ON = "setswitchon";
		public static final String SWITCH_OFF = "setswitchoff";
		public static final String SWITCH_GET = "getswitchstate";
		public static final String SWITCH_NAME = "getswitchname";
	}

	TextView tvStatus;
	Button buttonConnect;
	ToggleButton buttonToggleDevice;
	EditText editAddress, editPassword;
	Spinner spinnerAin;
	SharedPreferences preferences;
	SharedPreferences.Editor editor;
	String tag = this.getClass().getSimpleName();
	Context context;
	String sid = "", address = "", password = "";
	List<String> aktore;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		context = getApplicationContext();
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		tvStatus = (TextView) findViewById(R.id.text_status);
		buttonConnect = (Button) findViewById(R.id.button_connect);
		buttonToggleDevice = (ToggleButton) findViewById(R.id.switch_device);
		editAddress = (EditText) findViewById(R.id.edit_address);
		editPassword = (EditText) findViewById(R.id.edit_password);
		spinnerAin = (Spinner) findViewById(R.id.spinner_ain);

//		UpdateActivity.iconDrawableId = R.drawable.icon;
		checkForUpdates();
		
		buttonConnect.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				//If address did not change refresh instead of reconnect
				if(!address.equals(editAddress.getText().toString())){
					storeSettings();
					refreshStatus();
				} else {
					refreshStatus();
				}
			}
		});

		buttonToggleDevice.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				String switchcmd;
				String ain=spinnerAin.getSelectedItem().toString();
				if(ain!=null&&!ain.equals("")){
					if(buttonToggleDevice.isChecked()){
						switchcmd=switchcmds.SWITCH_ON;
					} else {
						switchcmd=switchcmds.SWITCH_OFF;
					}
					FritzConnector fritzConnector = new FritzConnector();
					fritzConnector.execute(switchcmd, ain);
				}
			}
		});
		
		spinnerAin.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//				parent.getItemAtPosition(position).toString();
				refreshStatus();
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				//Do nothing for now
			}
		});

		//loading password if it exists
		preferences = getPreferences(MODE_PRIVATE);
		address = preferences.getString("address", "fritz.box");
		password = preferences.getString("password", "");
		editAddress.setText(address);
		refreshStatus();
	}
	@Override
	protected void onRestart(){
		super.onResume();
		refreshStatus();
	}

	private void refreshStatus(){
		FritzConnector fritzConnector = new FritzConnector();
		fritzConnector.execute(switchcmds.GET_SWITCH_LIST);
		
		if(spinnerAin.getSelectedItem()!=null){
			String ain=spinnerAin.getSelectedItem().toString();
			fritzConnector = new FritzConnector();
			fritzConnector.execute(switchcmds.SWITCH_GET,ain);
			fritzConnector = new FritzConnector();
			fritzConnector.execute(switchcmds.SWITCH_NAME,ain);
		}
	}

	private class FritzConnector extends AsyncTask<String, Void, String> {
		String switchcmd, ain;
		
		@Override
		protected String doInBackground(String... params) {
			try {
				if(!password.equals("")&&(sid.equals("")||sid.equals("0000000000000000"))){
//					FritzAuthConnector fritzAuthConnector = new FritzAuthConnector();
//					fritzAuthConnector.execute();
					try {
						DefaultHttpClient androidHttpClient = new DefaultHttpClient();
						HttpGet request = new HttpGet ("http://"+address+"/login_sid.lua");
						HttpResponse response = androidHttpClient.execute(request);
						DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
						DocumentBuilder builder = factory.newDocumentBuilder();
						Document document = builder.parse(response.getEntity().getContent());
						//				response.getEntity().consumeContent();
						sid = document.getElementsByTagName("SID").item(0).getFirstChild().getNodeValue();
						if(sid.equals("")||sid.equals("0000000000000000")){
							androidHttpClient = new DefaultHttpClient();
							String challenge = document.getElementsByTagName("Challenge").item(0).getFirstChild().getNodeValue();
							MessageDigest md;
							md = MessageDigest.getInstance("MD5");
							byte[] byteArray;
							byteArray = (challenge + "-" + password).getBytes("utf-16le");
							String responseString = challenge + "-" + new BigInteger(1,md.digest(byteArray)).toString(16);
							request = new HttpGet ("http://"+address+"/login_sid.lua?response="+responseString);
							response = androidHttpClient.execute(request);
							document = builder.parse(response.getEntity().getContent());
							//					response.getEntity().consumeContent();
							sid = document.getElementsByTagName("SID").item(0).getFirstChild().getNodeValue();
						}
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								if(!sid.equals("")&&!sid.equals("0000000000000000")){
									tvStatus.setText(R.string.text_status_connected);
									buttonConnect.setText(R.string.button_refresh);
								} else {
									tvStatus.setText(R.string.text_status_not_connected);
									buttonConnect.setText(R.string.button_connect);
								}
							}
						});
					} catch (Exception e) {
						Log.e(tag, e.toString());
					}
				}

				DefaultHttpClient androidHttpClient = new DefaultHttpClient();
				HttpGet request=null;
				switchcmd = params[0];
				if(
						switchcmd.equals(switchcmds.SWITCH_ON)||
						switchcmd.equals(switchcmds.SWITCH_OFF)||
						switchcmd.equals(switchcmds.SWITCH_GET)||
						switchcmd.equals(switchcmds.SWITCH_NAME)){
					ain = params[1];
					request = new HttpGet ("http://"+address+"/webservices/homeautoswitch.lua?sid="+sid+"&switchcmd="+switchcmd+"&ain="+ain);
				} else if(switchcmd.equals(switchcmds.GET_SWITCH_LIST)){
					request = new HttpGet ("http://"+address+"/webservices/homeautoswitch.lua?sid="+sid+"&switchcmd="+switchcmd);
				}
				HttpResponse response = androidHttpClient.execute(request);
				String responseString = EntityUtils.toString(response.getEntity());
				//				response.getEntity().consumeContent();
				return responseString;
			} catch (Exception e) {
				Log.e(tag, e.toString());
			}
			return null;
		}

		protected void onPostExecute(final String result) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					//If request was unsuccessful we will be presented a NULL here
					if(result!=null){
						//getswitchlist, setswitchon, setswitchoff, getswitchstate, getswitchname
						if(switchcmd.equals(switchcmds.SWITCH_ON)||switchcmd.equals(switchcmds.SWITCH_OFF)||switchcmd.equals(switchcmds.SWITCH_GET)){
							buttonToggleDevice.setChecked(result.contains("1"));
						} else if(switchcmd.equals(switchcmds.SWITCH_NAME)) {
							if(result!=null&&!result.equals("")){
								tvStatus.setText(result.replaceAll("\\s", ""));
							}
						} else if(switchcmd.equals(switchcmds.GET_SWITCH_LIST)){
							List<String> spinnerArray =  new ArrayList<String>();
							for (String aktor : result.replaceAll("\\s", "").split(",")){//removing all whitespaces then splitting it at ','
								spinnerArray.add(aktor);
							}
							//Only rebuild spinner list has changed (unlikely)
							if(!spinnerArray.equals(aktore)){
								aktore=spinnerArray;
								ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, spinnerArray);
								adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
								Spinner sItems = (Spinner) findViewById(R.id.spinner_ain);
								sItems.setAdapter(adapter);
							}
						}
					}
				}
			});
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_options_menu, menu);
		return true;
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
		case R.id.menu_save:
			storeSettings();
			break;
		case R.id.menu_clear:
			clearSettings();
			break;

		default:
			break;
		}
		return true;
	}

	public void storeSettings(){
		address=editAddress.getText().toString();
		password=editPassword.getText().toString();
		editor = preferences.edit();
		editor.putString("address", address);
		editor.putString("password", password);
		editor.commit();
	}

	public void clearSettings(){
		editor = preferences.edit();
		editor.clear();
		editor.commit();
	}
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		checkUpdateTask.detach();
		return checkUpdateTask;
	}

	private void checkForUpdates() {
		checkUpdateTask = (CheckUpdateTask)getLastNonConfigurationInstance();
		if (checkUpdateTask != null) {
			checkUpdateTask.attach(this);
		}
		else {
			checkUpdateTask = new CheckUpdateTask(this, "https://www.459below.org/hockey/");
			checkUpdateTask.execute();
		}
	}
}