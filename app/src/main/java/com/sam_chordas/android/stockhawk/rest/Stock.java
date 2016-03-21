package com.sam_chordas.android.stockhawk.rest;

import java.io.Serializable;

/**
 * Created by ajitesh on 8/3/16.
 */
public class Stock implements Serializable{
    private String name;
    private String change;
    private int isUp;

    public Stock(String name, String change, int isUp){
        this.name=name;
        this.change=change;
        this.isUp=isUp;
    }

    public int getIsUp() {
        return isUp;
    }

    public String getChange() {
        return change;
    }

    public String getName() {
        return name;
    }
}
