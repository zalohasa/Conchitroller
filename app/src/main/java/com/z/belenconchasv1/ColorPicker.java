package com.z.belenconchasv1;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_color_picker);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Intent intent = getIntent();

        Conchitroller appState = (Conchitroller)getApplicationContext();
        final LedContainer selectedLed = appState.getSelectedLed();

        LightnessSlider lightnessSlider = (LightnessSlider) findViewById(R.id.v_lightness_slider);

        final ColorPickerView colorPicker = (ColorPickerView) findViewById(R.id.color_picker_view);
        colorPicker.setInitialColor(selectedLed.getColor(), false);
        colorPicker.setLightnessSlider(lightnessSlider);
        lightnessSlider.setColor(selectedLed.getColor());

        lightnessSlider.setOnValueChangedListener(new OnValueChangedListener() {
            @Override
            public void onValueChanged(float v) {
                setNewLedColor(colorPicker.getSelectedColor(), selectedLed);
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
                setNewLedColor(selectedColor, selectedLed);
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




//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//            }
//        });
    }

    public void fireModeClicked(View view)
    {
        Conchitroller appState = (Conchitroller)getApplicationContext();
        final LedContainer selectedLed = appState.getSelectedLed();
        CheckBox check = (CheckBox)view;
        if (check.isChecked())
        {
            Log.d("FIREMODE","Fire mode on");
            setFireMode(true, selectedLed);
        }else{
            Log.d("FIREMODE","Fire mode off");
            setFireMode(false, selectedLed);
        }
    }

    private void setNewLedColor(int color, LedContainer led)
    {
        Conchitroller appState = (Conchitroller)getApplicationContext();
        led.setColor(color);
        Log.d("COMMAND","Sending: " + "s" + led.toColorCommandString());
        appState.bt_.write("s" + led.toColorCommandString(), true);
        //ledListAdapter.notifyDataSetChanged();
    }

    private void setFireMode(boolean enable, LedContainer led)
    {
        Conchitroller appState = (Conchitroller)getApplicationContext();
        led.setFlick(enable);
        Log.d("COMMAND","Sending: " + "f" + led.toFlickCommandString());
        appState.bt_.write("f" + led.toFlickCommandString(), true);
        //ledListAdapter.notifyDataSetChanged();
    }

}
