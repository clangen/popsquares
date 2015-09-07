package org.clangen.gfx.popsquares;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SimpleCursorAdapter;
import android.widget.ViewFlipper;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class SettingsActivity extends Activity {
    private static final String TAG = "SettingsActivity";

    public static final String ACTION_SETTINGS_STARTED = "org.clangen.gfx.popsquares.ACTION_SETTINGS_STARTED";
    public static final String ACTION_SETTINGS_FINISHED = "org.clangen.gfx.popsquares.ACTION_SETTINGS_FINISHED";

    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 16;
    private static final int SIZE_STEP_COUNT = MAX_SIZE - MIN_SIZE;
    private static final int MIN_CONTRAST = 0;
    private static final int MAX_CONTRAST = 255;
    private static final int CONTRAST_STEP_COUNT = MAX_CONTRAST - MIN_CONTRAST;
    private static final int MIN_SPEED = PopSquares.MIN_FPS;
    private static final int MAX_SPEED = PopSquares.MAX_FPS;
    private static final int TOTAL_SPEED_STEPS = (MAX_SPEED - MIN_SPEED);
    private static final int MAX_COLOR = 255;
    private static final int MAX_DISTANCE = PopSquares.MAX_DISTANCE;

    private static boolean sIsActive = false;

    private Views mViews = new Views();
    private Effect mEffect;
    private ProfileLibrary mProfileLibrary;
    private SimpleCursorAdapter mListAdapter;
    private SharedPreferences mPrefs;
    private boolean mPaused = true;
    private PopSquares mPopSquares;

    private static class Views {
        SurfaceView mSurfaceView;
        SeekBar mRedAmountSeekBar;
        SeekBar mGreenAmountSeekBar;
        SeekBar mBlueAmountSeekBar;
        SeekBar mContrastSeekBar;
        SeekBar mColumnCountSeekBar;
        SeekBar mRowCountSeekBar;
        SeekBar mStrobeFpsSeekBar;
        SeekBar mScrollDistanceSeekBar;
        ListView mProfilesListView;
        Button mBuilderButton;
        Button mLibraryButton;
        ViewFlipper mFlipper;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mEffect = Effect.getInstance(this);
        mProfileLibrary = ProfileLibrary.getInstance(this);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.settings);

        mPopSquares = new PopSquares(this);

        mViews.mSurfaceView = (SurfaceView) findViewById(R.id.SurfaceView);
        mViews.mSurfaceView.getHolder().addCallback(mSurfaceHolderCallback);

        mViews.mRedAmountSeekBar = configureSeekBar(R.id.RedAmountSeekBar, MAX_COLOR);
        mViews.mGreenAmountSeekBar = configureSeekBar(R.id.GreenAmountSeekBar, MAX_COLOR);
        mViews.mBlueAmountSeekBar = configureSeekBar(R.id.BlueAmountSeekBar, MAX_COLOR);
        mViews.mContrastSeekBar = configureSeekBar(R.id.ContrastSeekBar, CONTRAST_STEP_COUNT);
        mViews.mColumnCountSeekBar = configureSeekBar(R.id.ColumnCountSeekBar, SIZE_STEP_COUNT);
        mViews.mRowCountSeekBar = configureSeekBar(R.id.RowCountSeekBar, SIZE_STEP_COUNT);
        mViews.mStrobeFpsSeekBar = configureSeekBar(R.id.StrobeFpsSeekBar, TOTAL_SPEED_STEPS);
        mViews.mScrollDistanceSeekBar = configureSeekBar(R.id.DistanceSeekBar, MAX_DISTANCE);

        mViews.mFlipper = (ViewFlipper) findViewById(R.id.ViewFlipper);
        mViews.mFlipper.setInAnimation(AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left));
        mViews.mFlipper.setOutAnimation(AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right));
        mViews.mFlipper.setAnimateFirstView(true);

        mViews.mProfilesListView = (ListView) findViewById(R.id.ProfilesListView);
        mViews.mProfilesListView.setOnItemClickListener(mOnProfileRowClickListener);
        registerForContextMenu(mViews.mProfilesListView);

        findViewById(R.id.ResetButton).setOnClickListener(mResetClickListener);
        findViewById(R.id.OKButton).setOnClickListener(mDoneClickListener);
        findViewById(R.id.SaveButton).setOnClickListener(mSaveButtonClickListener);

        mViews.mLibraryButton = (Button) findViewById(R.id.LibraryButton);
        mViews.mLibraryButton.setOnClickListener(mLibraryClickListener);

        mViews.mBuilderButton = (Button) findViewById(R.id.BuilderButton);
        mViews.mBuilderButton.setOnClickListener(mBuilderClickListener);

        reloadSettings();
        reloadProfiles();

        Log.i("PopSquares", "Loaded effect: " + mEffect.toString());
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);

        if (view == mViews.mProfilesListView) {
            getMenuInflater().inflate(R.menu.profilescontextmenu, menu);
            menu.setHeaderTitle(R.string.context_menu_title);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getMenuInfo() == null) {
            return false;
        }

        AdapterContextMenuInfo itemInfo;
        itemInfo = (AdapterContextMenuInfo) item.getMenuInfo();

        switch (item.getItemId()) {
        case R.id.ProfileListMenuRename:
            showEditProfileNameDialog(itemInfo.id);

            return true;

        case R.id.ProfileListMenuReplace:
            showConfirmReplaceProfileDialog(itemInfo.id);
            return true;

        case R.id.ProfileListMenuDelete:
            showConfirmProfileDeleteDialog(itemInfo.id);
            return true;
        }
        return super.onContextItemSelected(item);
    }

    public static boolean isActive() {
        return sIsActive;
    }

    private SeekBar configureSeekBar(int id, int max) {
        SeekBar seekbar = (SeekBar) findViewById(id);
        seekbar.setMax(max);
        seekbar.setOnSeekBarChangeListener(mSizeChangeListener);
        return seekbar;
    }

    protected void onResume() {
        super.onResume();
        sIsActive = true;
        mPaused = false;
        sendBroadcast(new Intent(ACTION_SETTINGS_STARTED));
        addBuiltInProfiles();
    }

    protected void onPause() {
        super.onPause();
        sIsActive = false;
        mPaused = true;
        sendBroadcast(new Intent(ACTION_SETTINGS_FINISHED));
    }

    private boolean builtInProfilesAdded() {
        String key = getString(R.string.pref_built_in_profiles_added);
        return (mPrefs.getBoolean(key, false));
    }

    private void addBuiltInProfiles() {
        if ((!builtInProfilesAdded())) {
            new ProfileLoader().execute(new Void[] { });
        }
    }

    private void reloadSettings() {
        mViews.mRedAmountSeekBar.setProgress(mEffect.getRedAmount());
        mViews.mGreenAmountSeekBar.setProgress(mEffect.getGreenAmount());
        mViews.mBlueAmountSeekBar.setProgress(mEffect.getBlueAmount());
        mViews.mContrastSeekBar.setProgress(mEffect.getContrast() - MIN_CONTRAST);
        mViews.mColumnCountSeekBar.setProgress(mEffect.getColumnCount());
        mViews.mRowCountSeekBar.setProgress(mEffect.getRowCount());
        mViews.mStrobeFpsSeekBar.setProgress(mEffect.getStrobeFps() - MIN_SPEED);
        mViews.mScrollDistanceSeekBar.setProgress(mEffect.getScrollDistance());
    }

    private void reloadProfiles() {
        Cursor cursor = mProfileLibrary.getProfileNames();
        startManagingCursor(cursor);

        mListAdapter = new SimpleCursorAdapter(
            this,
            R.layout.profilerow,
            cursor,
            new String[] { ProfileLibrary.NAME_COLUMN },
            new int[] { R.id.ProfileRowName });

        mViews.mProfilesListView.setAdapter(mListAdapter);
    }

    private void showInvalidNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.invalid_name_title);
        builder.setMessage(R.string.invalid_name_message);
        builder.setPositiveButton(R.string.button_ok, null);
        builder.show();
    }

    private void showConfirmDialog(String title, String message, DialogInterface.OnClickListener onYes) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(R.string.button_yes, onYes);
        builder.setNegativeButton(R.string.button_no, null);
        builder.show();
    }

    private void showProfileNameEntryDialog(
        String text,
        DialogInterface.OnClickListener onSave,
        DialogInterface.OnClickListener onCancel,
        DialogInterface.OnCancelListener onDialogCancel)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.name_effect_title);
        builder.setView(View.inflate(this, R.layout.profilenamedialog, null));
        builder.setPositiveButton(R.string.button_save, onSave);
        builder.setNegativeButton(R.string.button_cancel, onCancel);
        builder.setOnCancelListener(onDialogCancel);

        Dialog dlg = builder.show();

        EditText editText = (EditText) dlg.findViewById(R.id.ProfileNameEditText);
        editText.setText(text == null ? "" : text);
    }

    private void showConfirmResetDialog() {
        showConfirmDialog(
            getString(R.string.confirm_reset_title),
            getString(R.string.confirm_reset_message),
            mConfirmResetDefaultsListener);
    }

    private void showConfirmProfileDeleteDialog(long id) {
        String message = getString(
            R.string.confirm_delete_message,
            mProfileLibrary.getNameById(id));

        showConfirmDialog(
            getString(R.string.confirm_delete_title),
            message,
            new ConfirmDeleteProfileListener(id));
    }

    private void showConfirmReplaceProfileDialog(long id) {
        String message = getString(
            R.string.confirm_replace_message,
            mProfileLibrary.getNameById(id));

        showConfirmDialog(
            getString(R.string.confirm_replace_title),
            message,
            new ConfirmReplaceProfileListener(id));
    }

    private void showNewProfileDialog() {
        showProfileNameEntryDialog(
            "",
            mSaveProfileDialogClickListener,
            mSaveProfileDialogCancelClickListener,
            mSaveProfileDialogCancelListener);
    }

    private void showEditProfileNameDialog(long id) {
        showProfileNameEntryDialog(
            mProfileLibrary.getNameById(id),
            new ConfirmRenameProfileListener(id),
            null,
            null);
    }

    private String getProfileNameFromDialog(DialogInterface dialogInterface) {
        Dialog dialog = (Dialog) dialogInterface;
        EditText edit = (EditText) dialog.findViewById(R.id.ProfileNameEditText);

        String name = edit.getText().toString().trim();
        if ((name.length() == 0)) {
            showInvalidNameDialog();
            return null;
        }

        return name;
    }

    private void flipToLibrary() {
        mViews.mLibraryButton.setVisibility(View.GONE);
        mViews.mBuilderButton.setVisibility(View.VISIBLE);
        mViews.mFlipper.showPrevious();
    }

    private void flipToBuilder() {
        mViews.mLibraryButton.setVisibility(View.VISIBLE);
        mViews.mBuilderButton.setVisibility(View.GONE);
        mViews.mFlipper.showNext();
    }

    private void onEffectChanged() {
        mPopSquares.onEffectChanged();
        reloadSettings();
    }

    private OnItemClickListener mOnProfileRowClickListener =
        new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int index, long id) {
                String settings = mProfileLibrary.getSettingsById(id);
                mEffect.fromString(settings);
                onEffectChanged();
            }
        };

    private DialogInterface.OnClickListener mConfirmResetDefaultsListener =
        new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                mEffect.resetEffectToDefault();
                onEffectChanged();
            }
        };

    private class ConfirmRenameProfileListener implements DialogInterface.OnClickListener {
        private long mId;

        public ConfirmRenameProfileListener(long id) {
            mId = id;
        }

        public void onClick(DialogInterface dialog, int which) {
            String name = getProfileNameFromDialog(dialog);
            if (name != null) {
                mProfileLibrary.renameProfile(mId, name);
                mListAdapter.getCursor().requery();
            }
        }
    }

    private class ConfirmDeleteProfileListener implements DialogInterface.OnClickListener {
        long mId;

        public ConfirmDeleteProfileListener(long id) {
            mId = id;
        }

        public void onClick(DialogInterface dialog, int which) {
            mProfileLibrary.deleteProfile(mId);
            mListAdapter.getCursor().requery();
        }
    }

    private class ConfirmReplaceProfileListener implements DialogInterface.OnClickListener {
        long mId;

        public ConfirmReplaceProfileListener(long id) {
            mId = id;
        }

        public void onClick(DialogInterface dialog, int which) {
            mProfileLibrary.updateProfile(mId, mEffect.toString());
            mListAdapter.getCursor().requery();
        }
    }

    private DialogInterface.OnClickListener mSaveProfileDialogClickListener =
        new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String name = getProfileNameFromDialog(dialog);
                if (name != null) {
                    mProfileLibrary.addProfile(name, mEffect.toString());
                    mListAdapter.getCursor().requery();
                }
            }
        };

    private DialogInterface.OnClickListener mSaveProfileDialogCancelClickListener =
        new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                flipToBuilder();
            }
        };

    private DialogInterface.OnCancelListener mSaveProfileDialogCancelListener =
        new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                flipToBuilder();
            }
        };

    private OnClickListener mSaveButtonClickListener = new OnClickListener() {
        public void onClick(View view) {
            flipToLibrary();
            showNewProfileDialog();
        }
    };

    private OnClickListener mResetClickListener = new OnClickListener() {
        public void onClick(View view) {
            showConfirmResetDialog();
        }
    };

    private OnClickListener mDoneClickListener = new OnClickListener() {
        public void onClick(View view) {
            finish();
        }
    };

    private OnClickListener mLibraryClickListener = new OnClickListener() {
        public void onClick(View view) {
            flipToLibrary();
        }
    };

    private OnClickListener mBuilderClickListener = new OnClickListener() {
        public void onClick(View view) {
            flipToBuilder();
        }
    };

    private SurfaceHolder.Callback mSurfaceHolderCallback = new SurfaceHolder.Callback() {
        public void surfaceDestroyed(SurfaceHolder holder) {
            mPopSquares.stop();
        }

        public void surfaceCreated(SurfaceHolder holder) {
            // surfaceChanged() always called at least once after created(), start it there.
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (holder.getSurface().isValid()) {
                mPopSquares.start(holder);
            }
        }
    };

    private OnSeekBarChangeListener mSizeChangeListener = new OnSeekBarChangeListener() {
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        }

        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        public void onStopTrackingTouch(SeekBar seekBar) {
            int progress = seekBar.getProgress();
            if (seekBar == mViews.mColumnCountSeekBar) {
                mEffect.setColumnCount(MIN_SIZE + progress);
            }
            else if (seekBar == mViews.mRowCountSeekBar) {
                mEffect.setRowCount(MIN_SIZE + progress);
            }
            else if (seekBar == mViews.mStrobeFpsSeekBar) {
                mEffect.setStrobeFps(MIN_SPEED + progress);
            }
            else if (seekBar == mViews.mRedAmountSeekBar) {
                mEffect.setRedAmount(progress);
            }
            else if (seekBar == mViews.mGreenAmountSeekBar) {
                mEffect.setGreenAmount(progress);
            }
            else if (seekBar == mViews.mBlueAmountSeekBar) {
                mEffect.setBlueAmount(progress);
            }
            else if (seekBar == mViews.mContrastSeekBar) {
                mEffect.setContrast(MIN_CONTRAST + progress);
            }
            else if (seekBar == mViews.mScrollDistanceSeekBar) {
                mEffect.setScrollDistance(progress);
            }

            onEffectChanged();
        }
    };

    private class ProfileLoader extends AsyncTask<Void, Void, Void> {
        private ProgressDialog mProgressDialog;

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);

            if (!mPaused && mProgressDialog != null) {
                mProgressDialog.dismiss();
            }

            reloadProfiles();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (!mPaused) {
                mProgressDialog =
                    ProgressDialog.show(
                        SettingsActivity.this,
                        getString(R.string.library_init_title),
                        getString(R.string.library_init_message));
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            synchronized (ProfileLoader.class) {
                if (!builtInProfilesAdded()) {
                    Log.i(TAG, "Profiles don't exist, creating...");

                    if (mProfileLibrary.addBuiltInProfiles()) {
                        setPreferenceBoolean(
                            R.string.pref_built_in_profiles_added);

                        Log.i(TAG, "Profiles don't exist, created!");
                    }
                }
            }

            return null;
        }

        protected void setPreferenceBoolean(int id) {
            SharedPreferences.Editor editor = mPrefs.edit();
            editor.putBoolean(getString(id), true);
            editor.apply();
        }
    }
}
