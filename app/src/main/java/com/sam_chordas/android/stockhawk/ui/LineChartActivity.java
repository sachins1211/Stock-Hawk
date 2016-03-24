package com.sam_chordas.android.stockhawk.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.realm.implementation.RealmLineData;
import com.github.mikephil.charting.data.realm.implementation.RealmLineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.Volley_Networking.AppController;
import com.sam_chordas.android.stockhawk.rest.HistoricalData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmConfiguration;
import io.realm.RealmResults;

public class LineChartActivity extends Activity {

    Context mContext;
    String currentDate;
    String pastDate;
    LineChart chart;
    int lastId;
    RealmLineData realmLineData;
    Realm realm;
    String symbol;
    RealmChangeListener realmChangeListener;
    RealmResults<HistoricalData> results;
    RealmLineDataSet<HistoricalData> historicalDataRealmLineDataSet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_line_graph);
        chart = (LineChart) findViewById(R.id.chart);
        mContext=this;
        RealmConfiguration realmConfig = new RealmConfiguration.Builder(mContext).build();
        realm = Realm.getInstance(realmConfig);
        realmChangeListener=new RealmChangeListener() {
            @Override
            public void onChange() {
                Log.d("TEST", "onChange()");
                setData();
            }
        };

        symbol=getIntent().getStringExtra("Symbol");
        try {
            fetchData(urlBuild(symbol));
        } catch (IOException e) {
            e.printStackTrace();
        }
        setData();
    }

    String urlBuild(String stockInput){
        getDates();
        StringBuilder urlStringBuilder = new StringBuilder();
        try {
            urlStringBuilder.append("https://query.yahooapis.com/v1/public/yql?q=");
            urlStringBuilder.append(URLEncoder.encode("select * from yahoo.finance.historicaldata where symbol =  "
                    , "UTF-8"));
            urlStringBuilder.append(URLEncoder.encode("\""+stockInput+"\" and startDate = \""+pastDate+"\" and endDate = \""+currentDate+"\"", "UTF-8"));
            urlStringBuilder.append("&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables."
                    + "org%2Falltableswithkeys&callback=");

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return urlStringBuilder.toString();
    }
    void fetchData(String url) throws IOException {
        Log.d("TEST",url);
        JsonObjectRequest jsonObjectRequest=new JsonObjectRequest(url,
                new com.android.volley.Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d("TEST",response.toString());
                        try {
                            JSONObject jsonObject = response.getJSONObject("query");
                            if(jsonObject.getInt("count")>1){
                                jsonObject=jsonObject.getJSONObject("results");
                                if(jsonObject!=null) {
                                    JSONArray resultsArray = jsonObject.getJSONArray("quote");
                                    saveData(resultsArray);
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new com.android.volley.Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d("TEST",error.toString());
                    }
                });
        AppController.getInstance().addToRequestQueue(jsonObjectRequest);
    }

    private void saveData(final JSONArray jsonArray){

        realm.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                for (int i = jsonArray.length() - 1; i >= 0; i--) {
                    JSONObject object = null;
                    try {
                        object = jsonArray.getJSONObject(i);
                        realm.copyToRealm(new HistoricalData(++lastId, symbol, object.getString("Date"), Float.parseFloat(object.getString("High"))));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                // begin & end transcation calls are done for you

            }
        }/*, new Realm.Transaction.Callback() {
            @Override
            public void onSuccess() {
                // Original Queries and Realm objects are automatically updated.
                //puppies.size(); // => 0 because there are no more puppies (less than 2 years old)
                dog.getAge();   // => 3 the dogs age is updated
            }
        }*/);
    }
    private void setData() {
        //realm.beginTransaction();
        results = realm.where(HistoricalData.class).equalTo("stock",symbol).findAll();
        results.addChangeListener(realmChangeListener);
        //Toast.makeText(mContext, results.size() + "", Toast.LENGTH_SHORT).show();
        historicalDataRealmLineDataSet=new RealmLineDataSet<HistoricalData>(results,"value","id");
        // create a dataset and give it a type
        //LineDataSet set1 = new LineDataSet(yVals, "DataSet 1");
        historicalDataRealmLineDataSet.setFillAlpha(110);
        historicalDataRealmLineDataSet.setFillColor(Color.RED);

        // set the line to be drawn like this "- - - - - -"
        historicalDataRealmLineDataSet.enableDashedLine(10f, 5f, 0f);
        historicalDataRealmLineDataSet.enableDashedHighlightLine(10f, 5f, 0f);
        historicalDataRealmLineDataSet.setColor(Color.BLACK);
        historicalDataRealmLineDataSet.setCircleColor(Color.BLACK);
        historicalDataRealmLineDataSet.setLineWidth(1f);
        //historicalDataRealmLineDataSet.setCircleRadius(3f);
        historicalDataRealmLineDataSet.setDrawCircleHole(false);
        historicalDataRealmLineDataSet.setValueTextSize(9f);
        //Drawable drawable = ContextCompat.getDrawable(this, R.drawable.fade_red);
        //set1.setFillDrawable(drawable);
        historicalDataRealmLineDataSet.setDrawFilled(true);

        ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
        dataSets.add(historicalDataRealmLineDataSet); // add the datasets
        // create a data object with the datasets
        //LineData data = new LineData(xVals, dataSets);
        realmLineData=new RealmLineData(results,"date",dataSets);
        // set data
        chart.setAutoScaleMinMaxEnabled(true);
        chart.getLegend().setEnabled(false);
        chart.setData(realmLineData);
        chart.animateXY(3000, 1000, Easing.EasingOption.Linear, Easing.EasingOption.Linear);
    }

    public void getDates() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance();
        currentDate=dateFormat.format(cal.getTime());
        RealmResults<HistoricalData> historicalData = realm.where(HistoricalData.class).equalTo("stock",symbol).findAll();
        if(historicalData.size()>0) {
            //Toast.makeText(mContext,historicalData.size(),Toast.LENGTH_SHORT).show();
            pastDate = historicalData.last().getDate();
            lastId=historicalData.last().getId();
            Log.d("TEST",lastId+"");
        }
        else{
            lastId=-1;
            cal.add(Calendar.YEAR,-1);
            pastDate=dateFormat.format(cal.getTime());}
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        realm.removeAllChangeListeners();
        realm.close();
    }
}
