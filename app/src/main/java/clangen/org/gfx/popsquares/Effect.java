package org.clangen.gfx.popsquares;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Effect {
    public static final String ACTION_EFFECT_CHANGED = "org.clangen.gfx.popsquares.ACTION_SETTINGS_CHANGED";

    private final static int R_AMOUNT = 41;
    private final static int G_AMOUNT = 109;
    private final static int B_AMOUNT = 183;
    private final static int ROW_COUNT = 5;
    private final static int COLUMN_COUNT = 5;
    private final static int STROBE_FPS = 9;
    private final static int CONTRAST = 36;
    private final static int DISTANCE = 3; // == columns per scroll * 2

    private static Effect sInstance;

    private SharedPreferences mPrefs;
    private Application mContext;
    private boolean mTransaction;

    private Effect(Context context) {
        mContext = (Application) context.getApplicationContext();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public static Effect getInstance(Context context) {
        synchronized (Effect.class) {
            if (sInstance == null) {
                sInstance = new Effect(context.getApplicationContext());
            }

            return sInstance;
        }
    }

    public int getRowCount() {
        return Math.max(1, getInteger(R.string.pref_row_count, ROW_COUNT));
    }

    public int getColumnCount() {
        return Math.max(1, getInteger(R.string.pref_column_count, COLUMN_COUNT));
    }
    
    public void setRowCount(int count) {
        setInteger(R.string.pref_row_count, count);
    }
    
    public void setColumnCount(int count) {
        setInteger(R.string.pref_column_count, count);
    }

    public int getContrast() {
        return Math.max(1, getInteger(R.string.pref_contrast, CONTRAST));
    }

    public void setContrast(int range) {
        setInteger(R.string.pref_contrast, range);
    }

    public int getStrobeFps() {
        return Math.max(1, getInteger(R.string.pref_strobe_fps, STROBE_FPS));
    }

    public void setStrobeFps(int strobeFps) {
        setInteger(R.string.pref_strobe_fps, strobeFps);
    }

    public void setScrollDistance(int distance) {
        setInteger(R.string.pref_distance_per_scroll, distance);
    }

    public int getScrollDistance() {
        return getInteger(R.string.pref_distance_per_scroll, DISTANCE);
    }

    public int getRedAmount() {
        return getInteger(R.string.pref_red_amount, R_AMOUNT);
    }

    public void setRedAmount(int value) {
        setInteger(R.string.pref_red_amount, value);
    }

    public int getGreenAmount() {
        return getInteger(R.string.pref_green_amount, G_AMOUNT);
    }

    public void setGreenAmount(int value) {
        setInteger(R.string.pref_green_amount, value);
    }

    public int getBlueAmount() {
        return getInteger(R.string.pref_blue_amount, B_AMOUNT);
    }

    public void setBlueAmount(int value) {
        setInteger(R.string.pref_blue_amount, value);
    }

    public void resetEffectToDefault() {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.remove(getKey(R.string.pref_size));
        editor.remove(getKey(R.string.pref_row_count));
        editor.remove(getKey(R.string.pref_column_count));
        editor.remove(getKey(R.string.pref_red_amount));
        editor.remove(getKey(R.string.pref_green_amount));
        editor.remove(getKey(R.string.pref_blue_amount));
        editor.remove(getKey(R.string.pref_strobe_fps));
        editor.remove(getKey(R.string.pref_contrast));
        editor.remove(getKey(R.string.pref_distance_per_scroll));
        editor.commit();

        broadcastChanged();
    }

    private String getKey(int key) {
        return mContext.getString(key);
    }

    private int getInteger(int key, int defValue) {
        return mPrefs.getInt(getKey(key), defValue);
    }

    private void setInteger(int key, int value) {
        if (setIntegerNoBroadcast(key, value)) {
            if ( ! mTransaction) {
                broadcastChanged();
            }
        }
    }

    private boolean setIntegerNoBroadcast(int key, int value) {
        int current = getInteger(key, -1);
        if (current != value) {
            SharedPreferences.Editor editor = mPrefs.edit();
            editor.putInt(getKey(key), value);
            editor.commit();
            return true;
        }

        return false;
    }

    private synchronized void beginTransaction() {
        endTransaction();
        mTransaction = true;
    }

    private synchronized void endTransaction() {
        if (mTransaction) {
            mTransaction = false;
            broadcastChanged();
        }
    }

    public String toString() {
        JSONObject json = new JSONObject();

        try {
            json.put(getKey(R.string.pref_row_count), getRowCount());
            json.put(getKey(R.string.pref_column_count), getColumnCount());
            json.put(getKey(R.string.pref_contrast), getContrast());
            json.put(getKey(R.string.pref_red_amount), getRedAmount());
            json.put(getKey(R.string.pref_green_amount), getGreenAmount());
            json.put(getKey(R.string.pref_blue_amount), getBlueAmount());
            json.put(getKey(R.string.pref_strobe_fps), getStrobeFps());
            json.put(getKey(R.string.pref_distance_per_scroll), getScrollDistance());
        }
        catch (JSONException ex) {
            throw new RuntimeException("org.clangen.gfx.plasma.Settings.toString() failed");
        }

        return json.toString();
    }

    public boolean fromString(String string) {
        try {
            // reading an invalid key will throw an exception
            JSONObject json = new JSONObject(string);
            int rowCount = json.getInt(getKey(R.string.pref_row_count));
            int columnCount = json.getInt(getKey(R.string.pref_column_count));
            int range = json.getInt(getKey(R.string.pref_contrast));
            int redAmount = json.getInt(getKey(R.string.pref_red_amount));
            int greenAmount = json.getInt(getKey(R.string.pref_green_amount));
            int blueAmount = json.getInt(getKey(R.string.pref_blue_amount));
            int strobeFps = json.getInt(getKey(R.string.pref_strobe_fps));
            int scrollDistance = getJsonInt(json, R.string.pref_distance_per_scroll, DISTANCE);

            // only set once we're sure we have the full set of values!
            try {
                beginTransaction();

                setRowCount(rowCount);
                setColumnCount(columnCount);
                setContrast(range);
                setRedAmount(redAmount);
                setGreenAmount(greenAmount);
                setBlueAmount(blueAmount);
                setStrobeFps(strobeFps);
                setScrollDistance(scrollDistance);
            }
            finally {
                endTransaction();
            }
        }
        catch (JSONException ex) {
        }

        return false;
    }

    private int getJsonInt(JSONObject json, int key, int def) {
        try {
            return json.getInt(getKey(key));
        }
        catch (JSONException ex) {
            return def;
        }
    }

    private void broadcastChanged() {
        mContext.sendBroadcast(new Intent(ACTION_EFFECT_CHANGED));
    }
}
