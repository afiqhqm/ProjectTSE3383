

package de.Finesse.pedometer.util;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Device;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import de.j4velin.pedometer.BuildConfig;
import de.j4velin.pedometer.Database;

/**
 * Class to manage the Google Fit sync
 */
public abstract class GoogleFit {

    public static class Sync extends AsyncTask<Void, Void, Void> {

        private final GoogleApiClient mClient;
        private final Context context;

        public Sync(final GoogleApiClient client, final Context c) {
            mClient = client;
            context = c;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            long syncedUntil = context.getSharedPreferences("GoogleFit", Context.MODE_PRIVATE)
                    .getLong("syncedUntil", 0);

            // Create a data source
            DataSource dataSource =
                    new DataSource.Builder().setAppPackageName(context.getPackageName())
                            .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                            .setName("Pedometer - step count").setType(DataSource.TYPE_RAW)
                            .setDevice(Device.getLocalDevice(context)).build();

            Database db = Database.getInstance(context);

            Cursor c =
                    db.query(new String[]{"date, steps"}, "date > " + syncedUntil, null, null, null,
                            "date ASC", null);
            c.moveToFirst();
            Calendar endOfDay = Calendar.getInstance();
            while (!c.isAfterLast()) {
                if (c.getInt(1) > 0) {
                    // Create a data set
                    DataSet dataSet = DataSet.create(dataSource);
                    // For each data point, specify a start time, end time, and the data value -- in this case,
                    // the number of new steps.
                    endOfDay.setTimeInMillis(c.getLong(0));
                    endOfDay.add(Calendar.DAY_OF_MONTH, 1);
                    endOfDay.add(Calendar.SECOND, -1);
                    dataSet.add(dataSet.createDataPoint()
                            .setTimeInterval(c.getLong(0), endOfDay.getTimeInMillis(),
                                    TimeUnit.MILLISECONDS).setIntValues(c.getInt(1)));
                    com.google.android.gms.common.api.Status insertStatus =
                            Fitness.HistoryApi.insertData(mClient, dataSet)
                                    .await(1, TimeUnit.MINUTES);
                    if (BuildConfig.DEBUG) Logger.log(
                            "sync with Fit: " + c.getLong(0) + " - " + endOfDay.getTimeInMillis() +
                                    ": " + c.getInt(1) + " steps. Status: " +
                                    insertStatus.getStatusMessage());
                }
                syncedUntil = c.getLong(0);
                c.moveToNext();
            }
            c.close();
            db.close();

            context.getSharedPreferences("GoogleFit", Context.MODE_PRIVATE).edit().
                    putLong("syncedUntil", syncedUntil).apply();

            return null;
        }
    }

}
