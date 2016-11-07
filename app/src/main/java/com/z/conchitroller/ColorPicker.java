package com.z.conchitroller;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.OnColorSelectedListener;
import com.flask.colorpicker.slider.LightnessSlider;
import com.flask.colorpicker.slider.OnValueChangedListener;

public class ColorPicker extends AppCompatActivity {

    BelenService belen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_color_picker);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Intent intent = getIntent();

        Conchitroller appState = (Conchitroller)getApplicationContext();
        final LedContainer selectedLed = appState.getSelectedLed();
        setTitle(selectedLed.getNumber() + " - " + selectedLed.getName());

        LightnessSlider lightnessSlider = (LightnessSlider) findViewById(R.id.v_lightness_slider);

        final ColorPickerView colorPicker = (ColorPickerView) findViewById(R.id.color_picker_view);
        colorPicker.setInitialColor(selectedLed.getColor(), false);
        colorPicker.setLightnessSlider(lightnessSlider);
        lightnessSlider.setColor(selectedLed.getColor());

        lightnessSlider.setOnValueChangedListener(new OnValueChangedListener() {
            @Override
            public void onValueChanged(float v) {
                belen.setNewLedColor(colorPicker.getSelectedColor(), selectedLed);
                Log.d("SLIDER", "ValueChanged: " + v);
            }
        });

        CheckBox check = (CheckBox) findViewById(R.id.fire_mode_check);
        if (selectedLed.getFlick())
        {
            check.setChecked(true);
        }
        else
        {
            check.setChecked(false);
        }

        colorPicker.addOnColorSelectedListener(new OnColorSelectedListener() {
            @Override
            public void onColorSelected(int selectedColor) {
                belen.setNewLedColor(selectedColor, selectedLed);
            }
        });

        EditText name = (EditText)findViewById(R.id.led_name_edit);
        name.setText(selectedLed.getName());
        name.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                selectedLed.setName(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        setResult(Activity.RESULT_OK);

    }

    @Override
    protected void onStart()
    {
        super.onStart();
        Intent intent = new Intent(this, BelenService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        unbindService(mConnection);
    }

    public void fireModeClicked(View view)
    {
        Conchitroller appState = (Conchitroller)getApplicationContext();
        final LedContainer selectedLed = appState.getSelectedLed();
        CheckBox check = (CheckBox)view;
        if (check.isChecked())
        {
            Log.d("FIREMODE","Fire mode on");
            belen.setFireMode(true, selectedLed);
        }else{
            Log.d("FIREMODE","Fire mode off");
            belen.setFireMode(false, selectedLed);
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
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {

        }
    };

}
