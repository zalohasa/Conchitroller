package com.z.belenconchasv1;

import android.graphics.Color;

/**
 * Created by zalo on 30/09/16.
 */
public class LedContainer {

    private int number_;
    private String name_;
    private int color_;
    private boolean flick_;

    public LedContainer(int number)
    {
        number_ = number;
        name_ = "";
        color_ = 0;
    }

    public LedContainer(int number, String name, int color, boolean flick)
    {
        this(number);
        name_ = name;
        color_ = color;
        flick_ = flick;
    }

    public LedContainer(int number, String name, int red, int green, int blue, boolean flick)
    {
        this(number, name, Color.rgb(red, green, blue), flick);
    }

    public void setName(String name)
    {
        name_ = name;
    }

    public void setColor(int color)
    {
        color_ = color;
    }

    public void setColor(int red, int green, int blue)
    {
        color_ = Color.rgb(red, green, blue);
    }

    public void setFlick(boolean flick)
    {
        flick_ = flick;
    }

    public int getNumber()
    {
        return number_;
    }

    public int getColor()
    {
        return color_;
    }

    public String getName()
    {
        return name_;
    }

    public boolean getFlick()
    {
        return flick_;
    }

    @Override
    public String toString()
    {
        //return String.format("%03d - %03d - %03d - %03d - %01d", number_, Color.red(color_), Color.green(color_), Color.blue(color_), flick_?1:0);
        return number_ + " - " + name_;
    }

    public String toColorCommandString()
    {
        return String.format("%03d%03d%03d%03d", number_, Color.red(color_), Color.green(color_), Color.blue(color_));
    }

    public String toFlickCommandString()
    {
        return String.format("%03d%01d", number_, flick_?1:0);
    }

    public String toSerializeCommandString()
    {
        return String.format("%03d%03d%03d%03d%01d", number_, Color.red(color_), Color.green(color_), Color.blue(color_), flick_?1:0);
    }


}
