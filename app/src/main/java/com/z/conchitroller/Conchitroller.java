package com.z.conchitroller;

import android.app.Application;

import com.macroyau.blue2serial.BluetoothSerial;

/**
 * Created by zalo on 11/10/16.
 */

public class Conchitroller extends Application {
    private LedContainer selectedLed_;

    public LedContainer getSelectedLed() {
        return selectedLed_;
    }

    public void setSelectedLed(LedContainer led)
    {
        selectedLed_ = led;
    }

}
