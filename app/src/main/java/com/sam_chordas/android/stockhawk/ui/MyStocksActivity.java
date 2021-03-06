package com.sam_chordas.android.stockhawk.ui;

import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.ActionBar;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.afollestad.materialdialogs.MaterialDialog;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.QuoteCursorAdapter;
import com.sam_chordas.android.stockhawk.rest.RecyclerViewItemClickListener;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.sam_chordas.android.stockhawk.service.StockIntentService;
import com.sam_chordas.android.stockhawk.service.StockTaskService;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.Task;
import com.melnykov.fab.FloatingActionButton;
import com.sam_chordas.android.stockhawk.touch_helper.SimpleItemTouchHelperCallback;

import java.io.IOException;

public class MyStocksActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor>{

  /**
   * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
   */

  /**
   * Used to store the last screen title. For use in {@link #restoreActionBar()}.
   */
  private CharSequence mTitle;
  private Intent mServiceIntent;
  private ItemTouchHelper mItemTouchHelper;
  private static final int CURSOR_LOADER_ID = 0;
  private QuoteCursorAdapter mCursorAdapter;
  private Context mContext;
  private Cursor mCursor;
    TextView emptyStock;
    public Snackbar snackbar;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mContext = this;

    if(!checkConnection()){
        networkToast();
    }
    ConnectivityManager cm =
        (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

    setContentView(R.layout.activity_my_stocks);
    // The intent service is for executing immediate pulls from the Yahoo API
    // GCMTaskService can only schedule tasks, they cannot execute immediately
    mServiceIntent = new Intent(this, StockIntentService.class);
    if (savedInstanceState == null){
      // Run the initialize task service so that some stocks appear upon an empty database
      mServiceIntent.putExtra("tag", "init");
      if (checkConnection()){
        startService(mServiceIntent);
      } else{
        networkToast();
      }
    }
      RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
   recyclerView.setLayoutManager(new LinearLayoutManager(this));
    RecyclerView.ItemDecoration itemDecoration = new
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST);
    recyclerView.addItemDecoration(itemDecoration);

    getLoaderManager().initLoader(CURSOR_LOADER_ID, null, this);

    mCursorAdapter = new QuoteCursorAdapter(this, null);
    recyclerView.addOnItemTouchListener(new RecyclerViewItemClickListener(this,
            new RecyclerViewItemClickListener.OnItemClickListener() {
              @Override public void onItemClick(View v, int position) {
                //TODO:
                // do something on item click
                  Intent intent=new Intent(mContext,LineChartActivity.class);
                  mCursor.moveToPosition(position);
                  intent.putExtra("symbol",mCursor.getString(mCursor.getColumnIndex("symbol")));
                  startActivity(intent);

              }
            }));
    recyclerView.setAdapter(mCursorAdapter);


    FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
    fab.attachToRecyclerView(recyclerView);
    fab.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        if (checkConnection()){
          new MaterialDialog.Builder(mContext).title(R.string.symbol_search)
              .content(R.string.content_test)
              .inputType(InputType.TYPE_CLASS_TEXT)
              .input(R.string.input_hint, R.string.input_prefill, new MaterialDialog.InputCallback() {
                @Override public void onInput(MaterialDialog dialog, CharSequence input) {
                  // On FAB click, receive user input. Make sure the stock doesn't already exist
                  // in the DB and proceed accordingly
                  Cursor c = getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                      new String[] { QuoteColumns.SYMBOL }, QuoteColumns.SYMBOL + "= ?",
                      new String[] { input.toString().toUpperCase() }, null);
                  if (c.getCount() != 0) {
                    Toast toast =
                        Toast.makeText(MyStocksActivity.this, getString(R.string.stock_present),
                            Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, Gravity.CENTER, 0);
                    toast.show();
                    return;
                  } else {
                    // Add the stock to DB
                    mServiceIntent.putExtra("tag", "add");
                    mServiceIntent.putExtra("symbol", input.toString().toUpperCase());
                    startService(mServiceIntent);
                  }
                }
              })
              .show();
            snackbarDismiss();
        } else {
          networkToast();
        }

      }
    });

    emptyStock=(TextView) findViewById(R.id.noStock);


    ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(mCursorAdapter);
    mItemTouchHelper = new ItemTouchHelper(callback);
    mItemTouchHelper.attachToRecyclerView(recyclerView);
    mTitle = getTitle();
    if (checkConnection()){
     long period = 3600L;
      long flex = 10L;

      String periodicTag = "periodic";

      // create a periodic task to pull stocks once every hour after the app has been opened. This
      // is so Widget data stays up to date.
      PeriodicTask periodicTask = new PeriodicTask.Builder()
          .setService(StockTaskService.class)
          .setPeriod(period)
          .setFlex(flex)
          .setTag(periodicTag)
          .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
          .setRequiresCharging(false)
          .build();
      // Schedule task with tag "periodic." This ensure that only the stocks present in the DB
      // are updated.
      GcmNetworkManager.getInstance(this).schedule(periodicTask);
    }
  }

  public void onRefresh(){
    mServiceIntent.putExtra("tag", "periodic");
    if (checkConnection()){
      startService(mServiceIntent);
    } else{
      networkToast();
    }
  }

  public boolean checkConnection(){
    boolean isConnected;
    ConnectivityManager cm =
            (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
    isConnected = activeNetwork != null &&
            activeNetwork.isConnectedOrConnecting();
    return isConnected;
  }
  @Override
  public void onResume() {
    super.onResume();
    getLoaderManager().restartLoader(CURSOR_LOADER_ID, null, this);
  }

  public void networkToast(){
       snackbar = Snackbar
              .make(findViewById(android.R.id.content), getString(R.string.network_toast), Snackbar.LENGTH_INDEFINITE)
              .setAction(getString(R.string.retry), new View.OnClickListener() {
                  @Override
                  public void onClick(View view) {
                      try{
                          if(!checkConnection()){
                              networkToast();
                          }

                  }catch(Exception e){

                  }}
              });
      snackbar.show();
  }

  public void restoreActionBar() {
    ActionBar actionBar = getSupportActionBar();
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
    actionBar.setDisplayShowTitleEnabled(true);
    actionBar.setTitle(mTitle);
  }
    private void snackbarDismiss(){
        try {
            View snackbarView = snackbar.getView();
            Button snackbarActionButton = (Button) snackbarView.findViewById(android.support.design.R.id.snackbar_action);
            snackbarActionButton.performClick();
        }catch (Exception e){}
    }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
      getMenuInflater().inflate(R.menu.my_stocks, menu);
      restoreActionBar();
      return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == R.id.action_refresh) {
        if(!checkConnection()){
            networkToast();
        }
        else{
            onRefresh();
            snackbarDismiss();
        }

      return true;
    }

    if (id == R.id.action_change_units){
        Utils.showPercent = !Utils.showPercent;
      this.getContentResolver().notifyChange(QuoteProvider.Quotes.CONTENT_URI, null);
    }

    return super.onOptionsItemSelected(item);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args){
    // This narrows the return to only the stocks that are most current.
    return new CursorLoader(this, QuoteProvider.Quotes.CONTENT_URI,
        new String[]{ QuoteColumns._ID, QuoteColumns.SYMBOL, QuoteColumns.NAME, QuoteColumns.BIDPRICE,
            QuoteColumns.PERCENT_CHANGE, QuoteColumns.CHANGE, QuoteColumns.ISUP},
        QuoteColumns.ISCURRENT + " = ?",
        new String[]{"1"},
        null);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor data){
    mCursorAdapter.swapCursor(data);
    mCursor = data;
    if(data.getCount()<=0){
      emptyStock.setVisibility(View.VISIBLE);
    }
    else{
      emptyStock.setVisibility(View.GONE);
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader){
    mCursorAdapter.swapCursor(null);
  }
}
