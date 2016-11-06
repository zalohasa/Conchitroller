package com.z.conchitroller;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v4.app.NotificationCompat;
import android.widget.ArrayAdapter;
import android.util.Log;
import android.widget.Toast;

import com.macroyau.blue2serial.BluetoothSerial;
import com.macroyau.blue2serial.BluetoothSerialRawListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class BelenService extends Service implements BluetoothSerialRawListener{

    private static final int RETRIES_MAX_COUNT = 8;
    private static final int NOTIFICATION_ID = 1;


    //Preferences name
    static final private String SHARED_PREFERENCES_NAME = "com.z.conchitroller.BelenService.PREFERENCES";
    //Valid actions
    static final public String ACTION_BLUETHOOT_NOT_SUPPORTED = "com.z.conchitroller.BelenService.BLUETHOOT_NOT_SUPPORTED";
    static final public String ACTION_ENABLE_BLUETHOOT = "com.z.conchitroller.BelenService.ENABLE_BLUETHOOT";
    static final public String ACTION_BLUETHOOT_DEVICE_DISCONECTED = "com.z.conchitroller.BelenService.BLUETHOOT_DEVICE_DISCONECTED";
    static final public String ACTION_BLUETHOOT_DEVICE_CONNECTING = "com.z.conchitroller.BelenService.BLUETHOOT_DEVICE_CONNECTING";
    static final public String ACTION_BLUETHOOT_DEVICE_CONNECTED = "com.z.conchitroller.BelenService.BLUETHOOT_DEVICE_CONNECTED";
    static final public String ACTION_LED_LIST_CHANGED = "com.z.conchitroller.BelenService.LED_LIST_CHANGED";
    static final public String ACTION_SHOW_TOAST = "com.z.conchitroller.BelenService.SHOW_TOAST";

    //Extra ID's
    static final public String EXTRA_TOAST_STRING = "com.z.conchitroller.BelenService.TOAST_STRING";

    private LocalBroadcastManager broadcaster = LocalBroadcastManager.getInstance(this);
    private final IBinder binder = new LocalBinder();
    private BluetoothSerial bt;

    private int currentDelay;
    private int numberOfLeds;

    private enum CommandStatus {IDLE, WAITING_FOR_RESPONSE};
    private enum Command {IDLE, NUMBER_OF_LEDS, CURRENT_CONFIG, CURRENT_DELAY, OK_COMMAND};
    private enum Status {STARTED}
    private CommandStatus currentCommandStatus;
    private Command currentCommand;
    private QueuedCommand outgoingCommand;
    private Queue<QueuedCommand> pendingCommands = new ArrayDeque<QueuedCommand>();
    private StringBuilder partialResponse = new StringBuilder();
    private List<LedContainer> leds = new ArrayList<LedContainer>();
    private BluetoothDevice lastDevice = null;

    public BelenService() {
    }

    @Override
    public void onCreate()
    {
        currentCommandStatus = CommandStatus.IDLE;
        currentCommand = Command.IDLE;
        outgoingCommand = null;
        pendingCommands.clear();
        partialResponse.setLength(0);
        leds.clear();

        bt = new BluetoothSerial(this, this);
        if (bt.isBluetoothEnabled())
            bt.setup();

        Log.d("SERVICE", "Belen service created");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        //Notification notification = new Notification(R.drawable.ic_fire, "TEST", System.currentTimeMillis());
        //startForeground(33, notification);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this).setSmallIcon(R.drawable.ic_shell)
                .setContentTitle("Conchitroller")
                .setContentText("Conchitroller funcionando");
        Intent resultIntent = new Intent(this, MainActivity.class);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);
        startForeground(NOTIFICATION_ID, mBuilder.build());

        return START_STICKY;
    }

    @Override
    public void onDestroy()
    {
        bt.stop();
    }

    //------------------------
    //--- Public interface ---
    //------------------------

    public int getBluethootState()
    {
        return bt.getState();
    }

    public void stopBluethooth()
    {
        bt.stop();
    }

    public void setupBluetooth()
    {
        bt.setup();
    }

    public boolean isBluetoothEnabled()
    {
        return bt.isBluetoothEnabled();
    }

    public String getDeviceName()
    {
        return bt.getConnectedDeviceName();
    }

    public void connectToDevice(BluetoothDevice device)
    {
        bt.connect(device);
        lastDevice = device;
    }

    public boolean isConnected()
    {
        if (bt.isBluetoothEnabled())
            return bt.isConnected();
        else
            return false;
    }

    public Set<BluetoothDevice> getPairedDevices()
    {
        return bt.getPairedDevices();
    }

    public int getCurrentDelay()
    {
        return currentDelay;
    }

    public int getNumberOfLeds()
    {
        return numberOfLeds;
    }

    public List<LedContainer> getLedList()
    {
        return leds;
    }

    public void sendFpsCommand(int value)
    {
        currentDelay = value;
        Log.d("COMMAND", "Sending delay command: " + value);
        sendOkTypeCommand(String.format("d%03d", value));
    }

    public void sendPersistCommand()
    {
        Log.d("PersistCommand", "Sending persist command");
        sendOkTypeCommand("p");
    }

    public void sendResetCommand()
    {
        Log.d("ResetCommand", "Sending reset command");
        sendOkTypeCommand("r");
    }

    public void setFireMode(boolean enable, LedContainer led)
    {
        led.setFlick(enable);
        Log.d("COMMAND","Sending: " + "f" + led.toFlickCommandString());
        sendOkTypeCommand("f" + led.toFlickCommandString());
    }

    public void setNewLedColor(int color, LedContainer led)
    {
        led.setColor(color);
        Log.d("COMMAND","Sending: " + "s" + led.toColorCommandString());
        sendOkTypeCommand("s" + led.toColorCommandString());
    }

    public void checkCurrentConfig()
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

    public void checkCurrentDelay()
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


    public void loadLedsFromFile(String filename)
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
                SharedPreferences prefs = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
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
                Intent intent = new Intent(ACTION_LED_LIST_CHANGED);
                broadcaster.sendBroadcast(intent);
            }
        }catch (Exception e)
        {
            Log.e("LOAD", "Error reading file");
            sendToast("Error leyendo el fichero");
        }
    }

    public void saveLedsToFile(String filename)
    {
        File filesDir = getFilesDir();
        try{
            File out = new File(filesDir, filename + ".led");
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
            sendToast("Error guardando el fichero");
        }
    }

    public void resetLedNames()
    {
        SharedPreferences prefs = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        edit.clear();
        edit.commit();

        //Reload led colors from hardware.
        checkCurrentConfig();
    }

    public void saveLedsNames()
    {
        SharedPreferences prefs = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (int i = 0; i < leds.size(); ++i)
        {
            LedContainer current = leds.get(i);
            editor.putString(generateLedKey(current.getNumber()), current.getName());
        }
        editor.commit();
    }

    //--------------------
    //--- Commands API ---
    //--------------------

    private void sendToast(String str)
    {
        Intent intent = new Intent(ACTION_SHOW_TOAST);
        intent.putExtra(EXTRA_TOAST_STRING, str);
        broadcaster.sendBroadcast(intent);
    }

    private void createReconnectToast()
    {
        sendToast("Ha habido un error durante la lectura de datos. Desconecta y conecta de nuevo.");
    }



    private String generateLedKey(int ledNumber)
    {
        return String.format("LED_NAME%03d", ledNumber);
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
            SharedPreferences prefs = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
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
            Intent intent = new Intent(ACTION_LED_LIST_CHANGED);
            broadcaster.sendBroadcast(intent);
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
        } else {
            sendToast("Todos los comandos enviados");
        }
    }

    //-------------------------------------
    //--- Bluethoot serial interface-------
    //-------------------------------------
    @Override
    public void onBluetoothNotSupported() {
        Intent intent = new Intent(ACTION_BLUETHOOT_NOT_SUPPORTED);
        broadcaster.sendBroadcast(intent);
    }

    @Override
    public void onBluetoothDisabled() {
        Intent intent = new Intent(ACTION_ENABLE_BLUETHOOT);
        broadcaster.sendBroadcast(intent);
    }

    @Override
    public void onBluetoothDeviceDisconnected()
    {
        Intent intent = new Intent(ACTION_BLUETHOOT_DEVICE_DISCONECTED);
        broadcaster.sendBroadcast(intent);
        currentCommandStatus = CommandStatus.IDLE;
        currentCommand = Command.IDLE;
        partialResponse.setLength(0);
        leds.clear();
    }

    @Override
    public void onConnectingBluetoothDevice()
    {
        Intent intent = new Intent(ACTION_BLUETHOOT_DEVICE_CONNECTING);
        broadcaster.sendBroadcast(intent);
    }

    @Override
    public void onBluetoothDeviceConnected(String name, String address) {
        Intent intent = new Intent(ACTION_BLUETHOOT_DEVICE_CONNECTED);
        broadcaster.sendBroadcast(intent);

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

    public class LocalBinder extends Binder
    {
        BelenService getService() {
            return BelenService.this;
        }
    }

    private class QueuedCommand
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
