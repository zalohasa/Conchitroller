package com.z.conchitroller;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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
import com.macroyau.blue2serial.BluetoothSerialRawListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class MainActivity extends AppCompatActivity
            implements BluetoothSerialRawListener, BluetoothDeviceListDialog.OnDeviceSelectedListener
{
    public final static String LED_DATA = "com.z.belenconchasv1.LED_DATA";
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private static final int REQUEST_MODIFY_LED = 2;
    private static final int RETRIES_MAX_COUNT = 8;

    private BluetoothSerial bt;
    private int numberOfLeds;
    private int currentDelay;
    private MenuItem submenuBelen;
    private MenuItem submenuGuardados;



    private enum CommandStatus {IDLE, WAITING_FOR_RESPONSE};
    private enum Command {IDLE, NUMBER_OF_LEDS, CURRENT_CONFIG, CURRENT_DELAY, OK_COMMAND};
    private enum Status {STARTED}
    private CommandStatus currentCommandStatus;
    private Command currentCommand;
    private QueuedCommand outgoingCommand;
    private Queue<QueuedCommand> pendingCommands = new ArrayDeque<QueuedCommand>();
    private StringBuilder partialResponse = new StringBuilder();
    private List<LedContainer> leds = new ArrayList<LedContainer>();
    private ArrayAdapter<LedContainer> ledListAdapter;
    private BluetoothDevice lastDevice = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        currentCommandStatus = CommandStatus.IDLE;
        currentCommand = Command.IDLE;
        pendingCommands.clear();
        numberOfLeds = 0;
        leds.clear();
        final Activity mainActivity = this;



        ListView ledList = (ListView) findViewById(R.id.listView);
        ledListAdapter = new LedsAdapter(this, R.layout.row, R.id.led_name);
        ledList.setAdapter(ledListAdapter);
        ledList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
           @Override
           public void onItemClick(AdapterView<?> adapterView, View view, int i, long l)
           {
               Conchitroller appState = (Conchitroller)getApplicationContext();
               appState.setSelectedLed(ledListAdapter.getItem(i));

               Intent intent = new Intent(mainActivity, ColorPicker.class);
                startActivityForResult(intent, REQUEST_MODIFY_LED);
           }
        });

        Conchitroller appState = (Conchitroller)getApplicationContext();
        appState.bt_ = new BluetoothSerial(this, this);
        bt = appState.bt_;
        if (bt.isBluetoothEnabled())
            bt.setup();
    }

    @Override
    protected void onResume()
    {
        Log.d("ACTIVITY","On resume");
        super.onResume();

        if (bt.checkBluetooth() && bt.isBluetoothEnabled())
        {
            if (!bt.isConnected())
            {
                bt.start();
                if (lastDevice != null)
                    bt.connect(lastDevice);
            }
        }
        else
        {
            Log.d("BLUETH", "bt checks failed");
        }
        ledListAdapter.notifyDataSetChanged();

    }

    @Override
    protected void onStart()
    {
        super.onStart();

    }

    @Override
    protected void onStop()
    {
        super.onStop();

        //bt.stop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        submenuBelen = menu.findItem(R.id.submenu_belen);
        submenuGuardados = menu.findItem(R.id.submenu_guardados);
        if (bt.isConnected())
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
                    bt.setup();
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
            bt.stop();
            ledListAdapter.clear();
            ledListAdapter.notifyDataSetChanged();
            return true;
        }
        else if (id == R.id.action_belen_save)
        {
            sendPersistCommand();
        }
        else if (id == R.id.action_belen_reset)
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Estas seguro que quieres resetear todos los colores guardados en el belén? La próxima vez que apages y enciendas el belén, se generarán colores aleatorios!!");
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    sendResetCommand();
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
            val.setText("Velocidad: " + currentDelay);
            bar.setProgress(currentDelay);
            builder.setView(view);
            builder.setTitle("Cambiar refresco");
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    int value = bar.getProgress();
                    sendFpsCommand(value);
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
            checkCurrentConfig();
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateBluetoothState()
    {
        final int state;
        if (bt != null)
        {
            state = bt.getState();
        }
        else
        {
            state = BluetoothSerial.STATE_DISCONNECTED;
        }

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
                txt.setText("Conectado a: " + bt.getConnectedDeviceName());
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
                        loadLeds(currentNameToLoad);
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

    private void loadLeds(String filename)
    {
        File filesDir = getFilesDir();
        File data = new File(filesDir, filename + ".led");
        if (!data.exists())
        {
            Log.e("LOAD", "File not found");
            return;
        }
        try {
            //Load de led data from the file in a temporal LedContainer list.
            BufferedReader br = new BufferedReader(new FileReader(data));
            String line;
            ArrayList<LedContainer> ledList = new ArrayList<LedContainer>();
            while ((line = br.readLine()) != null)
            {
                SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
                String number = line.substring(0, 3);
                String r = line.substring(3,6);
                String g = line.substring(6,9);
                String b = line.substring(9,12);
                String f = line.substring(12);
                try{
                    int numberi = Integer.parseInt(number);
                    int ri = Integer.parseInt(r);
                    int gi = Integer.parseInt(g);
                    int bi = Integer.parseInt(b);
                    int fi = Integer.parseInt(f);
                    boolean flick = false;
                    if (fi == 1)
                    {
                        flick = true;
                    }
                    String savedLedName = prefs.getString(generateLedKey(numberi), "DESCONOCIDO");
                    ledList.add(new LedContainer(numberi, savedLedName, ri, gi, bi, flick));
                }catch (NumberFormatException e)
                {
                    Log.e("CONFIG","Error parsing string: " + e.getMessage());
                }
            }
            if (ledList.size() > 0)
            {
                //Lets set the new led colors to the hardware leds
                for (LedContainer l : ledList)
                {
                    setFireMode(l.getFlick(), l);
                    setNewLedColor(l.getColor(),l);
                }

                //Save new leds into the table.
                //TODO check if saved number of leds matches with actual number of leds
                leds.clear();
                leds.addAll(ledList);
                setNewLedList();
            }
        }catch (Exception e)
        {
            Log.e("LOAD", "Error reading file");
            Toast.makeText(this, "Error leyendo el fichero", Toast.LENGTH_LONG);
        }
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
                    try{
                        File out = new File(filesDir, name + ".led");
                        Log.d("SAVEFILE","Saving file: " + out.getAbsolutePath() + out.getName());
                        PrintStream ps = new PrintStream(new FileOutputStream(out));
                        for (int i = 0; i < leds.size(); ++i)
                        {
                            ps.println(leds.get(i).toSerializeCommandString());
                        }
                        ps.close();
                    }catch (Exception e)
                    {
                        Log.e("SAVE FILE", "Error saving file");
                        Toast.makeText(MainActivity.this, "Error guardando el fichero", Toast.LENGTH_LONG);
                    }
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

    private void createReconnectToast()
    {
        Toast.makeText(this, "Ha habido un error durante la lectura de datos. Desconecta y conecta de nuevo.", Toast.LENGTH_LONG).show();
    }

    private void sendFpsCommand(int value)
    {
        currentDelay = value;
        Log.d("COMMAND", "Sending delay command: " + value);
        sendOkTypeCommand(String.format("d%03d", value));
    }

    private void sendPersistCommand()
    {
        Log.d("PersistCommand", "Sending persist command");
        sendOkTypeCommand("p");
    }

    private void sendResetCommand()
    {
        Log.d("ResetCommand", "Sending reset command");
        sendOkTypeCommand("r");
    }

    private void setFireMode(boolean enable, LedContainer led)
    {
        led.setFlick(enable);
        Log.d("COMMAND","Sending: " + "f" + led.toFlickCommandString());
        sendOkTypeCommand("f" + led.toFlickCommandString());
    }

    private void setNewLedColor(int color, LedContainer led)
    {
        led.setColor(color);
        Log.d("COMMAND","Sending: " + "s" + led.toColorCommandString());
        sendOkTypeCommand("s" + led.toColorCommandString());
        ledListAdapter.notifyDataSetChanged();
    }

    private String generateLedKey(int ledNumber)
    {
        return String.format("LED_NAME%03d", ledNumber);
    }

    private void resetNames()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage("Estas seguro que quieres borrar todos los nombres de los LEDS? Esta acción no se puede deshacer!!");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor edit = prefs.edit();
                edit.clear();
                edit.commit();

                //Reload led colors from hardware.
                checkCurrentConfig();
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
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (int i = 0; i < leds.size(); ++i)
        {
            LedContainer current = leds.get(i);
            editor.putString(generateLedKey(current.getNumber()), current.getName());
        }
        editor.commit();
    }

    private void setNewLedList()
    {
        ledListAdapter.clear();
        ledListAdapter.addAll(leds);
    }

    private void showDeviceListDialog()
    {
        BluetoothDeviceListDialog dialog = new BluetoothDeviceListDialog(this);
        dialog.setOnDeviceSelectedListener(this);
        dialog.setTitle("Selecciona dispositivo");
        dialog.setDevices(bt.getPairedDevices());
        dialog.showAddress(true);
        dialog.show();
    }

    private void checkNumberOfLeds()
    {
        if (currentCommandStatus == CommandStatus.IDLE)
        {
            currentCommandStatus = CommandStatus.WAITING_FOR_RESPONSE;
            currentCommand = Command.NUMBER_OF_LEDS;
            Log.d("COMMAND","Sent command number of leds");
            bt.write("n", true);
        }
    }

    private void checkCurrentConfig()
    {
        if (currentCommandStatus == CommandStatus.IDLE)
        {
            currentCommandStatus = CommandStatus.WAITING_FOR_RESPONSE;
            currentCommand = Command.CURRENT_CONFIG;
            Log.d("COMMAND","Sent command get current config");
            bt.write("q",true);
        }
        else
        {
            pendingCommands.add(new QueuedCommand(Command.CURRENT_CONFIG));
        }
    }

    private void checkCurrentDelay()
    {
        if (currentCommandStatus == CommandStatus.IDLE)
        {
            currentCommandStatus = CommandStatus.WAITING_FOR_RESPONSE;
            currentCommand = Command.CURRENT_DELAY;
            Log.d("COMMAND", "Sent command get current delay");
            bt.write("t", true);
        }
        else
        {
            pendingCommands.add(new QueuedCommand(Command.CURRENT_DELAY));
        }
    }

    /**
     * Special version of the sendOkTypeCommand used to retry an already launched command.
     * @param command
     */
    private void sendOkTypeCommand(QueuedCommand command)
    {
        currentCommandStatus = CommandStatus.WAITING_FOR_RESPONSE;
        currentCommand = Command.OK_COMMAND;
        Log.d("COMMAND","Sent retryed command: " + command.textCommand);
        bt.write(command.textCommand, true);
    }

    private void sendOkTypeCommand(String command)
    {
        if (currentCommandStatus == CommandStatus.IDLE)
        {
            currentCommandStatus = CommandStatus.WAITING_FOR_RESPONSE;
            currentCommand = Command.OK_COMMAND;
            Log.d("COMMAND","Sent command: " + command);
            bt.write(command, true);
            outgoingCommand = new QueuedCommand(Command.OK_COMMAND, command);
        }
        else
        {
            pendingCommands.add(new QueuedCommand(Command.OK_COMMAND, command));
        }
    }

    private void parseCurrentDelay(String response)
    {
        Log.d("COMMANDS", "Current delay response: " + String.format("%040x", new BigInteger(1, response.getBytes())));
        try
        {
            currentDelay = Integer.parseInt(response);
            Log.i("COMMAND", "Current delay: " + currentDelay);
        } catch (NumberFormatException e)
        {
            Log.e("COMMAND", e.getMessage());
            Log.e("COMMAND", "Wrong number for current delay: " + response);
            createReconnectToast();
        }
    }

    private void parseNumberOfLedsResponse(String response)
    {
        try
        {
            numberOfLeds = Integer.parseInt(response);
            Log.d("CONFIG", "NumberOfLeds:" + numberOfLeds);
        } catch (NumberFormatException e)
        {
            numberOfLeds = -1;
            Log.e("COMMANDS", e.getMessage());
            Log.e("COMMANDS", "Wrong number of leds format: " + response);
        }

    }

    private void parseCurrentConfigResponse(String response)
    {
        Log.d("COMMANDS", "Current config response: " + response);
        leds.clear();
        try {
            SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
            String[] items = response.split(":");
            for (int i = 0; i < items.length; ++i) {
                String current = items[i];
                String number = current.substring(0, 3);
                String r = current.substring(3, 6);
                String g = current.substring(6, 9);
                String b = current.substring(9, 12);
                String f = current.substring(12);

                int numberi = Integer.parseInt(number);
                int ri = Integer.parseInt(r);
                int gi = Integer.parseInt(g);
                int bi = Integer.parseInt(b);
                int fi = Integer.parseInt(f);
                boolean flick = false;
                if (fi == 1) {
                    flick = true;
                }

                String savedLedName = prefs.getString(generateLedKey(numberi), "DESCONOCIDO");
                leds.add(new LedContainer(numberi, savedLedName, ri, gi, bi, flick));
            }
            setNewLedList();
        } catch (StringIndexOutOfBoundsException | NumberFormatException e)
        {
            Log.e("COMMAND","Error parsing current config response");
            Log.e("COMMAND", e.getMessage());
            createReconnectToast();
        }
    }

    private void commandFinished(boolean withError)
    {
        QueuedCommand c;
        currentCommand = Command.IDLE;
        currentCommandStatus = CommandStatus.IDLE;
        partialResponse.setLength(0);
        if (withError) {
            c = outgoingCommand;
            if (c != null) {
                c.retries++;
                Log.w("COMMAND","Retrying command " + c.textCommand);
                Log.w("COMMAND", "Retry number: " + c.retries);
                if (c.retries == RETRIES_MAX_COUNT)
                {
                    Log.e("COMMAND","Maximum number of retries reached for: " + c.textCommand);
                    Log.e("COMMAND", "Skipping command");
                    c = pendingCommands.poll();
                    outgoingCommand = null;
                }
            }
        }
        else
        {
            outgoingCommand = null;
            c = pendingCommands.poll();
        }
        if (c != null)
        {
            Log.d("COMMAND", "Pending command found, processing...");
            switch (c.command)
            {
                case CURRENT_CONFIG:
                    checkCurrentConfig();
                    break;
                case CURRENT_DELAY:
                    checkCurrentDelay();
                    break;
                case OK_COMMAND:
                    if (outgoingCommand != null)
                    {
                        sendOkTypeCommand(outgoingCommand);
                    } else
                    {
                        sendOkTypeCommand(c.textCommand);
                    }
                    break;
            }
        }
    }

    @Override
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

    @Override
    public void onBluetoothDisabled() {
        Log.d("ZALO", "BLUETU DISABLED");
        Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBluetooth, REQUEST_ENABLE_BLUETOOTH);
    }

    @Override
    public void onBluetoothDeviceDisconnected()
    {
        Log.d("BLUETH","Device disconnected");
        updateBluetoothState();
        currentCommandStatus = CommandStatus.IDLE;
        currentCommand = Command.IDLE;
        partialResponse.setLength(0);
        ledListAdapter.clear();
        ledListAdapter.notifyDataSetChanged();
    }

    @Override
    public void onConnectingBluetoothDevice() {
        updateBluetoothState();
    }

    @Override
    public void onBluetoothDeviceConnected(String name, String address) {
        updateBluetoothState();
        //checkNumberOfLeds();
        checkCurrentConfig();
        checkCurrentDelay();
    }

    @Override
    public void onBluetoothSerialRead(String message) {

    }

    @Override
    public void onBluetoothSerialReadRaw(byte[] bytes) {
        if (currentCommandStatus == CommandStatus.WAITING_FOR_RESPONSE)
        {
            //We are currently waiting for a command response.
            boolean full = false;
            for (int i = 0; i < bytes.length; ++i)
            {
                if (bytes[i] == 0x0a)
                {
                    //Sometimes there are 0x0a line feeds in the respose. I'm trying to discover why.
                    Log.w("COMMAND","Line feed character eliminated.");
                    continue;
                }
                if (bytes[i] == 0x0D)
                {
                    //End of line char, end of command.
                    full = true;
                    break;
                }
                partialResponse.append((char)bytes[i]);
            }
            if (full){
                boolean withError = false;
                String fullResponse = partialResponse.toString();
                if (currentCommand == Command.NUMBER_OF_LEDS) {
                    parseNumberOfLedsResponse(fullResponse);
                } else if (currentCommand == Command.CURRENT_CONFIG)
                {
                    parseCurrentConfigResponse(fullResponse);
                }
                else if (currentCommand == Command.CURRENT_DELAY)
                {
                    parseCurrentDelay(fullResponse);
                }
                else if (currentCommand == Command.OK_COMMAND)
                {
                    if (fullResponse.equalsIgnoreCase("OK"))
                    {
                        withError = false;
                        Log.d("COMMAND", "Command OK");
                    }
                    else
                    {
                        withError = true;
                        Log.d("COMMAND", "Command error: " + fullResponse);
                    }
                }
                commandFinished(withError);
            }
        }
        else
        {
            //We are not waiting for a command response, so treat the message as a general log message.
            StringBuilder sb = new StringBuilder(bytes.length);
            for (int i = 0; i < bytes.length; ++i)
            {
                if (bytes[i] >= 0x20 && bytes[i] <= 0x7E)
                    sb.append((char)bytes[i]);
                else if (bytes[i] == 0x0d)
                {
                    Log.d("READRAW",sb.toString());
                    sb.setLength(0);
                }
            }
            if (sb.length() > 0)
                Log.d("READRAW", sb.toString());
        }
    }

    @Override
    public void onBluetoothSerialWriteRaw(byte[] bytes) {

    }

    @Override
    public void onBluetoothSerialWrite(String message) {

    }

    @Override
    public void onBluetoothDeviceSelected(BluetoothDevice device) {
        bt.connect(device);
        lastDevice = device;
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

    class QueuedCommand
    {
        public Command command;
        public String textCommand;
        public int retries;

        public QueuedCommand(Command c)
        {
            command = c;
            retries = 0;
        }

        public QueuedCommand(Command c, String txt)
        {
            command = c;
            textCommand = txt;
            retries = 0;
        }
    }
}
