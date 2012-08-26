package org.clangen.gfx.popsquares;

import java.util.HashMap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

public class PopSquaresService extends WallpaperService {
    private final static HashMap<String, Integer> MODEL_TO_SCREEN_COUNT;

    static {
        MODEL_TO_SCREEN_COUNT = new HashMap<String, Integer>();
        MODEL_TO_SCREEN_COUNT.put("ADR6300", 7); // incredible
        MODEL_TO_SCREEN_COUNT.put("PC36100", 7); // evo
        MODEL_TO_SCREEN_COUNT.put("HTC Liberty", 7); // aria
    }

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    private class Engine extends WallpaperService.Engine {
        private PopSquares mPopSquares;
        private boolean mVisible;
        private Integer mGuessedScreenCount;
        private float mLastOffset = 0;

        public Engine() {
            mVisible = false;
            mPopSquares = new PopSquares(PopSquaresService.this);
            setTouchEventsEnabled(true);
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            registerReceivers();
        }

        @Override
        public void onDestroy() {
            unregisterReceivers();
            super.onDestroy();
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset,
                float xOffsetStep, float yOffsetStep, int xPixelOffset,
                int yPixelOffset)
        {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep,
                    xPixelOffset, yPixelOffset);

            /*
             * Hack around HTC Sense UI launcher screen calculation
             */
            if (xOffsetStep == -1.0) {
                mPopSquares.setScreenCount(guessScreenCount(7));
            }
            else {
                int screenCount = (int) (1.0f / xOffsetStep) + 1;

                /*
                 * Hack for other launchers that may be sending bogus
                 * screen offset steps. bleh.
                 */
                if (screenCount <= 0) {
                    mPopSquares.setScreenCount(guessScreenCount(5));
                }
                else {
                    mPopSquares.setScreenCount(screenCount);
                }
            }

            mLastOffset = xOffset;
            mPopSquares.moveTo(xOffset);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            if (mVisible && ( ! SettingsActivity.isActive())) {
                start();
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            mVisible = visible;

            if (visible) {
                if ( ! SettingsActivity.isActive()) {
                    start();
                }
            }
            else {
                stop();
            }
        }

        private synchronized void start() {
            mPopSquares.start(getSurfaceHolder());
            mPopSquares.moveTo(mLastOffset);
        }

        private synchronized void stop() {
            mPopSquares.stop();
        }

        private int guessScreenCount(int def) {
            if (mGuessedScreenCount != null) {
                return mGuessedScreenCount;
            }

            mGuessedScreenCount = MODEL_TO_SCREEN_COUNT.get(Build.MODEL);

            if (mGuessedScreenCount == null) {
                mGuessedScreenCount = def;
            }

            return mGuessedScreenCount;
        }

        private void registerReceivers() {
            registerReceiver(
                mOnSettingsFinishedReceiver,
                new IntentFilter(SettingsActivity.ACTION_SETTINGS_FINISHED));

            registerReceiver(
                mOnEffectChangedReceiver,
                new IntentFilter(Effect.ACTION_EFFECT_CHANGED));

            registerReceiver(
                mOnOrientationChangedReceiver,
                new IntentFilter(PopSquares.ACTION_ORIENTATION_CHANGED));
        }

        private void unregisterReceivers() {
            unregisterReceiver(mOnSettingsFinishedReceiver);
            unregisterReceiver(mOnEffectChangedReceiver);
            unregisterReceiver(mOnOrientationChangedReceiver);
        }

        private BroadcastReceiver mOnEffectChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (mPopSquares != null) {
                    mPopSquares.onEffectChanged();
                }
            }
        };

        private BroadcastReceiver mOnSettingsFinishedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (mVisible) {
                    start();
                }
            }
        };

        private BroadcastReceiver mOnOrientationChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (mPopSquares != null) {
                    mPopSquares.onEffectChanged();
                }
            }
        };
    }
}
