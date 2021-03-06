package org.ipv8.android;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDelegate;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.cantrowitz.rxbroadcast.RxBroadcast;
import com.facebook.stetho.Stetho;

import org.ipv8.android.restapi.EventStream;
import org.ipv8.android.service.IPV8Service;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import butterknife.BindView;
import rx.Observable;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MainActivity extends BaseActivity implements Handler.Callback {

    public static final int ADD_ACCOUNT_ACTIVITY_REQUEST_CODE = 103;
    public static final int INPUT_REQUIRED_ACTIVITY_REQUEST_CODE = 105;

    public static final int WRITE_STORAGE_PERMISSION_REQUEST_CODE = 110;

    static {
        // Backwards compatibility for vector graphics
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    @BindView(R.id.drawer_layout)
    DrawerLayout drawer;

    @BindView(R.id.nav_view)
    NavigationView navigationView;

    @BindView(R.id.main_progress)
    View progressView;

    @BindView(R.id.main_progress_status)
    TextView statusBar;

    private ActionBarDrawerToggle _navToggle;
    private ConnectivityManager _connectivityManager;
    private Handler _eventHandler;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // clear all pending inputs
        SharedPreferences sharedPref = getSharedPreferences("preferences", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putStringSet("pendingInputs", new HashSet<String>());
        editor.apply();

        // Hamburger icon
        _navToggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(_navToggle);
        _navToggle.setDrawerIndicatorEnabled(false);
        _navToggle.syncState();

        // getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Stetho.initializeWithDefaults(getApplicationContext()); //DEBUG

        initConnectivityManager();

        // Start listening to events on the main thread so the gui can be updated
        _eventHandler = new Handler(Looper.getMainLooper(), this);
        EventStream.addHandler(_eventHandler);

        if (!EventStream.isReady()) {
            showLoading(R.string.status_opening_eventstream);
            EventStream.openEventStream();
        }

        // Write permissions on sdcard?
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_STORAGE_PERMISSION_REQUEST_CODE);
        } else {
            startService();
        }

        // Create API client
        // TODO
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDestroy() {
        drawer.removeDrawerListener(_navToggle);
        EventStream.removeHandler(_eventHandler);
        super.onDestroy();
        _navToggle = null;
        _connectivityManager = null;
        _eventHandler = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean handleMessage(Message message) {
        // TODO

        return true;
    }

    public void enableNavigationMenu() {
        _navToggle.setDrawerIndicatorEnabled(true);
        _navToggle.syncState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void handleIntent(Intent intent) {
        String action = intent.getAction();
        if (TextUtils.isEmpty(action)) {
            return;
        }
        switch (action) {

            case Intent.ACTION_MAIN:
                // Handle intent only once
                intent.setAction(null);

                drawer.openDrawer(GravityCompat.START);
                return;

            case ConnectivityManager.CONNECTIVITY_ACTION:
            case WifiManager.NETWORK_STATE_CHANGED_ACTION:
            case WifiManager.WIFI_STATE_CHANGED_ACTION:
            case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION:
            case WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION:
            case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:
            case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
            case WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION:

                // Warn user if connection is lost
                if (!MyUtils.isNetworkConnected(_connectivityManager)) {
                    Toast.makeText(MainActivity.this, R.string.warning_lost_connection, Toast.LENGTH_SHORT).show();
                }
                return;

            case NfcAdapter.ACTION_NDEF_DISCOVERED:
                Log.v("ACTION_NDEF_DISCOVERED", String.format("%b", intent.hasExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)));

                Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
                if (rawMsgs != null && rawMsgs.length > 0) {
                    for (Parcelable rawMsg : rawMsgs) {
                        // Decode message
                        NdefRecord[] records = ((NdefMessage) rawMsg).getRecords();

                        // TODO we can parse payments here
                    }
                }
                return;

            case Intent.ACTION_SHUTDOWN:
                // Handle intent only once
                intent.setAction(null);

                shutdown();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {

            case ADD_ACCOUNT_ACTIVITY_REQUEST_CODE:
                // Update view
                Fragment fragment = getCurrentFragment();
                if (fragment instanceof ListFragment) {
                    ((ListFragment) fragment).reload();
                }
                return;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {

            case WRITE_STORAGE_PERMISSION_REQUEST_CODE:
                // If request is cancelled, the result arrays are empty
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startService();
                } else {
                    finish();
                }
                return;
        }
        // Propagate results
        Fragment fragment = getCurrentFragment();
        if (fragment != null) {
            fragment.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    protected void showLoading(@Nullable CharSequence text) {
        if (text == null) {
            progressView.setVisibility(View.GONE);
        } else {
            statusBar.setText(text);
            progressView.setVisibility(View.VISIBLE);
        }
    }

    protected void showLoading(boolean show) {
        showLoading(show ? "" : null);
    }

    protected void showLoading(@StringRes int resId) {
        showLoading(getText(resId));
    }

    private void initConnectivityManager() {
        _connectivityManager =
                (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        Observer observer = new Observer<Intent>() {

            public void onNext(Intent intent) {
                handleIntent(intent);
            }

            public void onCompleted() {
            }

            public void onError(Throwable e) {
                Log.v("connectivityMgr", e.getMessage(), e);
            }
        };

        // Listen for connectivity changes
        rxSubs.add(RxBroadcast.fromBroadcast(this, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
                .subscribe(observer));

        // Listen for network state changes
        rxSubs.add(RxBroadcast.fromBroadcast(this, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION))
                .subscribe(observer));

        // Listen for Wi-Fi state changes
        rxSubs.add(RxBroadcast.fromBroadcast(this, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))
                .subscribe(observer));

        // Listen for Wi-Fi direct state changes
        rxSubs.add(RxBroadcast.fromBroadcast(this, new IntentFilter(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION))
                .subscribe(observer));

        // Listen for Wi-Fi direct discovery changes
        rxSubs.add(RxBroadcast.fromBroadcast(this, new IntentFilter(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION))
                .subscribe(observer));

        // Listen for Wi-Fi direct peer changes
        rxSubs.add(RxBroadcast.fromBroadcast(this, new IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION))
                .subscribe(observer));

        // Listen for Wi-Fi direct connection changes
        rxSubs.add(RxBroadcast.fromBroadcast(this, new IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION))
                .subscribe(observer));

        // Listen for Wi-Fi direct device changes
        rxSubs.add(RxBroadcast.fromBroadcast(this, new IntentFilter(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION))
                .subscribe(observer));
    }

    @Nullable
    public Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.fragment_main);
    }

    /**
     * @param newFragmentClass The desired fragment class
     * @return True if fragment is switched, false otherwise
     */
    public boolean switchFragment(Class newFragmentClass) {
        // Check if current fragment is desired fragment
        if (!newFragmentClass.isInstance(getCurrentFragment())) {
            FragmentManager fragmentManager = getSupportFragmentManager();

            // Check if desired fragment is already instantiated
            String className = newFragmentClass.getName();
            Fragment fragment = fragmentManager.findFragmentByTag(className);
            if (fragment == null) {
                try {
                    fragment = (Fragment) newFragmentClass.newInstance();
                    fragment.setRetainInstance(true);
                } catch (InstantiationException ex) {
                    Log.e("switchFragment", className, ex);
                } catch (IllegalAccessException ex) {
                    Log.e("switchFragment", className, ex);
                }
            }
            fragmentManager
                    .beginTransaction()
                    .replace(R.id.fragment_main, fragment, className)
                    .commit();
            return true;
        }
        return false;
    }

    /**
     * @return Fragment that was removed, if any
     */
    @Nullable
    private Fragment removeFragment() {
        Fragment fragment = getCurrentFragment();
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .remove(fragment)
                    .commit();
        }
        return fragment;
    }

    public void navShutdownClicked(MenuItem item) {
        drawer.closeDrawer(GravityCompat.START);
        Intent shutdown = new Intent(Intent.ACTION_SHUTDOWN);
        // Ask user to confirm shutdown
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_shutdown);
        builder.setPositiveButton(R.string.action_shutdown_short, (dialog, which) -> {
            onNewIntent(shutdown);
        });
        builder.setNegativeButton(R.string.action_cancel, (dialog, which) -> {
            // Do nothing
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Create API client
        // TODO

        Context context = this;
    }

    private void shutdown() {
        // Clear view
        removeFragment();

        showLoading(R.string.status_shutting_down);

        EventStream.closeEventStream();

        // TODO
    }

    protected void startService() {
        IPV8Service.start(this); // Run normally
    }

    protected void killService() {
        IPV8Service.stop(this);
    }

}