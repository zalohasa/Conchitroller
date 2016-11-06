package com.z.conchitroller;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.macroyau.blue2serial.BluetoothDeviceListDialog;
import com.macroyau.blue2serial.BluetoothSerial;

import java.io.File;
import java.io.FilenameFilter;

public class MainActivity extends AppCompatActivity
            implements BluetoothDeviceListDialog.OnDeviceSelectedListener
{
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private static final int REQUEST_MODIFY_LED = 2;

    private MenuItem submenuBelen;
    private MenuItem submenuGuardados;
    private BelenService belen;
    private BroadcastReceiver broadcastReceiver;


    private ArrayAdapter<LedContainer> ledListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        belen = null;
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        TextView txt = (TextView) findViewById(R.id.textView);
        txt.setText("Conectate al belen usando el menú");

        ListView ledList = (ListView) findViewById(R.id.listView);
        ledListAdapter = new LedsAdapter(this, R.layout.row, R.id.led_name);
        ledList.setAdapter(ledListAdapter);
        ledList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
           @Override
           public void onItemClick(AdapterView<?> adapterView, View view, int i, long l)
           {
               Conchitroller appState = (Conchitroller)getApplicationContext();
               appState.setSelectedLed(ledListAdapter.getItem(i));

               Intent intent = new Intent(MainActivity.this, ColorPicker.class);
                startActivityForResult(intent, REQUEST_MODIFY_LED);
           }
        });

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                switch (action)
                {
                    case BelenService.ACTION_BLUETHOOT_NOT_SUPPORTED:
                        Log.d("MESSAGE", "Bluetooth not supperted");
                        onBluetoothNotSupported();
                        break;
                    case BelenService.ACTION_BLUETHOOT_DEVICE_CONNECTED:
                        Log.d("MESSAGE", "Bluetooth connected");
                        onBluetoothDeviceConnected();
                        break;
                    case BelenService.ACTION_BLUETHOOT_DEVICE_CONNECTING:
                        Log.d("MESSAGE", "Bluetooth connecting");
                        onConnectingBluetoothDevice();
                        break;
                    case BelenService.ACTION_BLUETHOOT_DEVICE_DISCONECTED:
                        Log.d("MESSAGE", "Bluetooth disconected");
                        onBluetoothDeviceDisconnected();
                        break;
                    case BelenService.ACTION_ENABLE_BLUETHOOT:
                        Log.d("MESSAGE", "Bluetooth not enabled");
                        onBluetoothDisabled();
                        break;
                    case BelenService.ACTION_LED_LIST_CHANGED:
                        Log.d("MESSAGE", "Led list changed.");
                        setNewLedList();
                        break;
                    case BelenService.ACTION_SHOW_TOAST:
                        String str = intent.getStringExtra(BelenService.EXTRA_TOAST_STRING);
                        Log.d("MESSAGE", "Show toast: " + str);
                        Toast.makeText(MainActivity.this, str, Toast.LENGTH_LONG).show();
                        break;
                }
            }
        };
    }

    @Override
    protected void onResume()
    {
        super.onResume();
    }

    @Override
    protected void onStart()
    {
        super.onStart();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BelenService.ACTION_BLUETHOOT_DEVICE_CONNECTED);
        filter.addAction(BelenService.ACTION_BLUETHOOT_DEVICE_CONNECTING);
        filter.addAction(BelenService.ACTION_BLUETHOOT_DEVICE_DISCONECTED);
        filter.addAction(BelenService.ACTION_BLUETHOOT_NOT_SUPPORTED);
        filter.addAction(BelenService.ACTION_ENABLE_BLUETHOOT);
        filter.addAction(BelenService.ACTION_LED_LIST_CHANGED);
        filter.addAction(BelenService.ACTION_SHOW_TOAST);

        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, filter);

        Intent intent = new Intent (this, BelenService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        Log.d("ACTIVITY","onStop()");
        unbindService(mConnection);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
    }

    @Override
    public void onBackPressed()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage("Estás seguro que quieres salir de conchitroller?");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent (MainActivity.this, BelenService.class);
                stopService(intent);
                finish();
            }
        })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
        builder.create().show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        submenuBelen = menu.findItem(R.id.submenu_belen);
        submenuGuardados = menu.findItem(R.id.submenu_guardados);
        if (belen != null && belen.isConnected())
        {
            submenuBelen.setEnabled(true);
            submenuGuardados.setEnabled(true);
        }
        else
        {
            submenuBelen.setEnabled(false);
            submenuGuardados.setEnabled(false);
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode)
        {
            case REQUEST_ENABLE_BLUETOOTH:
                if (resultCode == Activity.RESULT_OK)
                {
                    if (belen != null)
                        belen.setupBluetooth();
                }
                break;
            case REQUEST_MODIFY_LED:
                if (resultCode == Activity.RESULT_OK)
                {
                    saveLedNames();
                }
                else
                {
                    Log.d("ActivityResult", "Activity result: " + resultCode);
                }
                break;
        }
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_connect) {
            showDeviceListDialog();
            return true;
        }
        else if (id == R.id.action_disconnect)
        {
            belen.stopBluethooth();
            ledListAdapter.clear();
            ledListAdapter.notifyDataSetChanged();
            return true;
        }
        else if (id == R.id.action_belen_save)
        {
            belen.sendPersistCommand();
        }
        else if (id == R.id.action_belen_reset)
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Estas seguro que quieres resetear todos los colores guardados en el belén? La próxima vez que apages y enciendas el belén, se generarán colores aleatorios!!");
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    belen.sendResetCommand();
                }
            })
            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
            builder.create().show();

        }
        else if (id == R.id.action_set_fps)
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            LayoutInflater inflater = getLayoutInflater();
            View view = inflater.inflate(R.layout.fps_dialog, null);
            final SeekBar bar = (SeekBar) view.findViewById(R.id.seekBar);
            final TextView val = (TextView) view.findViewById(R.id.seek_delay_text);
            val.setText("Velocidad: " + belen.getCurrentDelay());
            bar.setProgress(belen.getCurrentDelay());
            builder.setView(view);
            builder.setTitle("Cambiar refresco");
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    int value = bar.getProgress();
                    belen.sendFpsCommand(value);
                }
            })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    });

            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    //Toast.makeText(getApplicationContext(), "Value: " + progress, Toast.LENGTH_SHORT).show();
                    val.setText("Velocidad: " + progress);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            builder.create().show();
        }
        else if (id == R.id.action_save)
        {
            saveLedsToFile();
        }
        else if (id == R.id.action_load)
        {
            loadLedsFromFile();

        }
        else if (id == R.id.action_delete)
        {
            deleteSavedFile();
        }
        else if (id == R.id.action_reset_names)
        {
            resetNames();
        }
        else if (id == R.id.action_refresh)
        {
            belen.checkCurrentConfig();
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateBluetoothState()
    {
        final int state;
        state = belen.getBluethootState();

        TextView txt = (TextView) findViewById(R.id.textView);

        ImageView icon = (ImageView) findViewById(R.id.toolbar_icon);
//        View submenu = findViewById(R.id.submenu_belen);
        //TODO update status string.
        switch (state) {
            case BluetoothSerial.STATE_CONNECTING:
                icon.setImageResource(android.R.drawable.presence_invisible);
//                submenu.setEnabled(false);
                if (submenuBelen != null)
                {
                    submenuBelen.setEnabled(false);
                }
                if (submenuGuardados != null)
                {
                    submenuGuardados.setEnabled(false);
                }
                txt.setText("Conectando...");
                break;
            case BluetoothSerial.STATE_CONNECTED:
                icon.setImageResource(android.R.drawable.presence_online);
//                submenu.setEnabled(true);
                if (submenuBelen != null)
                {
                    submenuBelen.setEnabled(true);
                }
                if (submenuGuardados != null)
                {
                    submenuGuardados.setEnabled(true);
                }
                txt.setText("Conectado a: " + belen.getDeviceName());
                break;
            default:
                icon.setImageResource(android.R.drawable.presence_offline);
//                submenu.setEnabled(false);
                if (submenuBelen != null)
                {
                    submenuBelen.setEnabled(false);
                }
                if (submenuGuardados != null)
                {
                    submenuGuardados.setEnabled(false);
                }
                txt.setText("Desconectado");
                break;
        }

    }

    private void deleteSavedFile()
    {
        final File filesDir = getFilesDir();
        FilenameFilter filter = new LedFileFilter();
        File[] files = filesDir.listFiles(filter);
        final CharSequence[] fileNames = new CharSequence[files.length];
        for (int i = 0; i < files.length; ++i)
        {
            String filename = files[i].getName();
            int last = filename.lastIndexOf('.');
            filename = filename.substring(0, last);
            fileNames[i] = filename;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Selecciona un fichero");
        builder.setItems(fileNames, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d("LOAD","File selected: " + fileNames[which]);
                final String currentNameToDelete = fileNames[which].toString() + ".led";
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setMessage("Estas seguro que quieres borrar el fichero con colores guardados? Esta acción no tiene vuelta atrás!!");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File toBeDeleted = new File(filesDir, currentNameToDelete);
                        if (toBeDeleted.exists())
                        {
                            toBeDeleted.delete();
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                builder.create().show();
            }
        });
        builder.create().show();
    }

    private void loadLedsFromFile()
    {
        final File filesDir = getFilesDir();
        FilenameFilter filter = new LedFileFilter();
        File[] files = filesDir.listFiles(filter);
        final CharSequence[] fileNames = new CharSequence[files.length];
        for (int i = 0; i < files.length; ++i)
        {
            String filename = files[i].getName();
            int last = filename.lastIndexOf('.');
            filename = filename.substring(0, last);
            fileNames[i] = filename;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Selecciona un fichero");
        builder.setItems(fileNames, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d("LOAD","File selected: " + fileNames[which]);
                final String currentNameToLoad = fileNames[which].toString();
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setMessage("Estas seguro que quieres cargar los colores de un fichero? Si no los has guardado en el belen o en un fichero, perderás los colores actuales!!");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        belen.loadLedsFromFile(currentNameToLoad);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                builder.create().show();
            }
        });
        builder.create().show();
    }



    private void saveLedsToFile()
    {
        final File filesDir = getFilesDir();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.input_text_dialog, null);
        final TextView txt = (TextView) view.findViewById(R.id.input_edit_text);
        builder.setView(view);
        builder.setTitle("Nombre");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = txt.getText().toString();
                if (!name.isEmpty())
                {
                    belen.saveLedsToFile(name);
                }
            }
        })
        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        builder.create().show();

    }

    private void resetNames()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage("Estas seguro que quieres borrar todos los nombres de los LEDS? Esta acción no se puede deshacer!!");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                belen.resetLedNames();
            }
        })
        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        builder.create().show();
    }

    private void saveLedNames()
    {
        if (belen != null)
            belen.saveLedsNames();
    }

    private void setNewLedList()
    {

        ledListAdapter.clear();
        ledListAdapter.addAll(belen.getLedList());
    }

    private void initializeFromBelen()
    {
        if (belen.isBluetoothEnabled()) {
            if (belen.isConnected()) {
                if (belen.getLedList().size() > 0)
                    setNewLedList();
                updateBluetoothState();
            } else {
                ledListAdapter.clear();
                ledListAdapter.notifyDataSetChanged();
            }
        } else {
            onBluetoothDisabled();
        }

    }

    private void showDeviceListDialog()
    {
        BluetoothDeviceListDialog dialog = new BluetoothDeviceListDialog(this);
        dialog.setOnDeviceSelectedListener(this);
        dialog.setTitle("Selecciona dispositivo");
        dialog.setDevices(belen.getPairedDevices());
        dialog.showAddress(true);
        dialog.show();
    }


    public void onBluetoothNotSupported() {
        new AlertDialog.Builder(this)
                .setMessage("No bluetooth")
                .setPositiveButton("Quit", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
    }

    public void onBluetoothDisabled() {
        Log.d("ZALO", "BLUETU DISABLED");
        Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBluetooth, REQUEST_ENABLE_BLUETOOTH);
    }

    public void onBluetoothDeviceDisconnected()
    {
        Log.d("BLUETH","Device disconnected");
        updateBluetoothState();
        ledListAdapter.clear();
        ledListAdapter.notifyDataSetChanged();
    }


    public void onConnectingBluetoothDevice() {
        updateBluetoothState();
    }

    public void onBluetoothDeviceConnected() {
        Log.d("MainActivity", "OnBluetoothDeviceConnected");
        updateBluetoothState();
    }

    @Override
    public void onBluetoothDeviceSelected(BluetoothDevice device) {
        belen.connectToDevice(device);
    }

    class LedsAdapter extends ArrayAdapter<LedContainer>
    {
        LedsAdapter(Context context, int resource, int textViewResource)
        {
            super(context, resource, textViewResource);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            View row = super.getView(position, convertView, parent);
            View space = (View) row.findViewById(R.id.led_color);
            ImageView fireImage = (ImageView) row.findViewById(R.id.fire_image);
            LedContainer led = getItem(position);
            space.setBackgroundColor(led.getColor());
            if (led.getFlick())
            {
                fireImage.setVisibility(View.VISIBLE);
            }
            else
            {
                fireImage.setVisibility(View.INVISIBLE);
            }

            return row;
        }
    }

    class LedFileFilter implements FilenameFilter
    {
        @Override
        public boolean accept(File dir, String name) {
            if (name.lastIndexOf('.') > 0)
            {
                int last = name.lastIndexOf('.');
                String str = name.substring(last);
                if (str.equals(".led"))
                {
                    return true;
                }
            }
            return false;
        }
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            Log.d("SERVICE","Service binded");
            BelenService.LocalBinder binder = (BelenService.LocalBinder) service;
            belen = binder.getService();
            initializeFromBelen();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            belen = null;
        }
    };

}
