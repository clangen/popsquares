package org.clangen.gfx.popsquares;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;

public class PopSquares {
    private static final String TAG = "PopSquares";

    public final static String ACTION_ORIENTATION_CHANGED = "org.clangen.gfx.popsquares.EXTRA_ORIENTATION_CHANGED";

    private static final int PALLETTE_SIZE = 32;
    private static final int SIN_TABLE_SIZE = 64;
    private static final int SIN_TABLE_MAX_INDEX = SIN_TABLE_SIZE - 1;
    private static final int MOVE_FRAMES_PER_SEC = 30;
    private static final int MILLIS_PER_FRAME_MOVE = 1000 / MOVE_FRAMES_PER_SEC;

    public static final int MAX_FPS = 22;
    public static final int MIN_FPS = 5;
    public static final int MAX_DISTANCE = 20;

    private static final int MESSAGE_BASE = 0xdeadbeef;
    private static final int MESSAGE_DRAW_TYPE_STROBE = MESSAGE_BASE + 1;
    private static final int MESSAGE_DRAW_TYPE_MOVE = MESSAGE_BASE + 2;
    private static final int MESSAGE_QUIT = MESSAGE_BASE + 3;

    private Context mContext;
    private DrawQueue mDrawQueue;
    private SurfaceHolder mSurfaceHolder;
    private Effect mEffect;
    private long mLastStrobeTime = 0;
    private Paint mPallette[];
    private int mSin[];
    private Rect mLastBounds = new Rect();

    private ArrayList<Column> mColumns;
    private int mScreenCount = 3;
    private int mStrobeFps = 10;
    private int mVisibleColumns;
    private int mColumnWidth;
    private int mScreenWidth;
    private int mVirtualWidth = 0;
    private volatile int mCurrentOffset = 0;

    private static class Cell {
        public Rect mRect = new Rect();
        public int mOffset;
    }

    private static class Column {
        Column(int count) {
            mRows = new Cell[count];
        }

        Cell[] mRows;
    }

    private int getStrobeMillisPerFrame() {
        return 1000 / mStrobeFps;
    }

    public PopSquares(Context context) {
        mContext = context.getApplicationContext();
        mEffect = Effect.getInstance(mContext);
    }

    public synchronized void setScreenCount(int count) {
        if (count == mScreenCount) {
            return;
        }

        SurfaceHolder holder = mSurfaceHolder;

        stop();

        mScreenCount = count;

        if (holder != null) {
            start(holder);
        }
    }

    public synchronized void moveTo(float newOffset) {
        if (mDrawQueue != null) {
            int pixelOffset = (int) (newOffset * (float) (mVirtualWidth - mScreenWidth));
            //Log.i(TAG, String.valueOf(newOffset) + " " + String.valueOf(pixelOffset));
            mDrawQueue.sendDrawMove(pixelOffset);
            mDrawQueue.sendDrawStrobe(100);
        }
    }

    public synchronized void onEffectChanged() {
        if (mDrawQueue != null) {
            restartIfRunning();
        }
    }

    public synchronized boolean start(SurfaceHolder surfaceHolder) {
        stop();

        if (surfaceHolder != null) {
            mSurfaceHolder = surfaceHolder;
            init();
        }

        return (mDrawQueue != null);
    }

    public synchronized void stop() {
        if (mDrawQueue != null) {
            mDrawQueue.cancel();
            mDrawQueue = null;
        }

        mSurfaceHolder = null;
    }

    private void restartIfRunning() {
        if (mSurfaceHolder != null) {
            start(mSurfaceHolder);
        }
    }

    private void init() {
        Canvas canvas = mSurfaceHolder.lockCanvas();

        if (canvas != null) {
            try {
                initSinTable();
                initPallette();
                initSquares(canvas);

                mStrobeFps = clampInt(mEffect.getStrobeFps(), MIN_FPS, MAX_FPS);

                mDrawQueue = new DrawQueue();
                mDrawQueue.start();
                mDrawQueue.waitForStart();
            }
            finally {
                mSurfaceHolder.unlockCanvasAndPost(canvas);
            }
        }
    }

    private void initSquares(Canvas canvas) {
        final Rect bounds = canvas.getClipBounds();
        final int rowCount = mEffect.getRowCount();
        int colCount = mEffect.getColumnCount();

        final int rectWidth = (int) Math.ceil((double)bounds.width() / (double)colCount);
        final int rectHeight = (int) Math.ceil((double)bounds.height() / (double)rowCount);

        int colPixelOffset = 0;
        int rowPixelOffset = 0;

        int scrollDistance = clampInt(mEffect.getScrollDistance(), 0, MAX_DISTANCE);
        float screenColumnDelta = (float) scrollDistance / 2;

        colCount = colCount + (int) Math.ceil((float)(mScreenCount - 1) * screenColumnDelta);

        mColumns = new ArrayList<Column>();
        mColumnWidth = rectWidth;
        mVirtualWidth = colCount * mColumnWidth;
        mVisibleColumns = mEffect.getColumnCount();
        mScreenWidth = bounds.width();

        for (int x = 0; x < colCount; x++) {
            Column column = new Column(rowCount);

            rowPixelOffset = 0;
            for (int y = 0; y < rowCount; y++) {
                Cell cell = new Cell();
                cell.mRect.left = colPixelOffset;
                cell.mRect.right = colPixelOffset + rectWidth;
                cell.mRect.top = rowPixelOffset;
                cell.mRect.bottom = rowPixelOffset + rectHeight;
                cell.mOffset = (int)(Math.random() * Integer.MAX_VALUE) % SIN_TABLE_MAX_INDEX;

                column.mRows[y] = cell;

                rowPixelOffset += rectHeight;
            }

            colPixelOffset += rectWidth;

            mColumns.add(column);
        }

        mLastBounds = bounds;
    }

    private void initSinTable() {
        int count = SIN_TABLE_SIZE;
        int base = PALLETTE_SIZE / 2;
        mSin = new int[count];
        float mult = (float) (base - 1);
        double radPerStep = ((360.0 / (float)count) * (3.1415 / 180.0));
        for (int i = 0; i < count; i++) {
            mSin[i] = base + (int)(mult * Math.sin((double)i * radPerStep));
        }
    }

    private void initPallette() {
        mPallette = new Paint[PALLETTE_SIZE];

        float offset = 0.0f;
        float range = (float) mEffect.getContrast();
        float rangeStep = range / (float) PALLETTE_SIZE;

        final int red = mEffect.getRedAmount();
        final int green = mEffect.getGreenAmount();
        final int blue = mEffect.getBlueAmount();

        for (int i = 0; i < PALLETTE_SIZE; i++) {
            final int tmp = (int) offset;

            int color = Color.rgb(
                clampByte(red - tmp),
                clampByte(green - tmp),
                clampByte(blue - tmp));

            Paint paint = new Paint();
            paint.setColor(color);
            paint.setStrokeWidth(1);

            mPallette[i] = paint;

            offset += rangeStep;
        }
    }

    private static int clampInt(int input, int min, int max) {
        return Math.min(max, Math.max(min, input));
    }

    private static int clampByte(int input) {
        return Math.min(255, Math.max(0, input));
    }

    private void drawWithOffset(Canvas canvas, int pixelOffset, boolean strobe) {
        int firstCol = (int) pixelOffset / mColumnWidth;
        int lastCol = firstCol + mVisibleColumns;

        if (pixelOffset % mColumnWidth != 0) {
            lastCol++;
        }

        firstCol = Math.max(0, firstCol);
        lastCol = Math.min(mColumns.size(), lastCol);

        Column column;
        Cell[] rows;
        Cell cell;
        Rect rect;

        for (int x = firstCol; x < lastCol; x++) {
            column = mColumns.get(x);
            rows = column.mRows;

            for (int y = 0; y < column.mRows.length; y++) {
                cell = rows[y];

                rect = new Rect(cell.mRect);
                rect.left -= pixelOffset;
                rect.right -= pixelOffset;

                canvas.drawRect(rect, mPallette[mSin[cell.mOffset]]);

                if (strobe) {
                    cell.mOffset++;
                    cell.mOffset &= SIN_TABLE_MAX_INDEX;
                }
            }
        }
    }

    public long drawStrobeFrame(Canvas canvas) {
        long start = System.currentTimeMillis();

        drawWithOffset(canvas, mCurrentOffset, true);

        mLastStrobeTime = System.currentTimeMillis();
        long elapsed = mLastStrobeTime - start;
        return getStrobeMillisPerFrame() - elapsed;
    }

    public long drawMoveFrame(Canvas canvas, int globalOffset) {
        long start = System.currentTimeMillis();

        long elapsedSinceLastStrobe = (start - mLastStrobeTime);
        boolean strobe = (elapsedSinceLastStrobe >= getStrobeMillisPerFrame());

        drawWithOffset(canvas, globalOffset, strobe);

        if (strobe) {
            mLastStrobeTime = System.currentTimeMillis();
        }

        mCurrentOffset = globalOffset;

        long elapsed = System.currentTimeMillis() - start;
        return MILLIS_PER_FRAME_MOVE - elapsed;
    }

    private class DrawQueue extends Thread {
        private Looper mLooper;
        private Handler mHandler;
        private CountDownLatch mStopLatch, mStartLatch;

        public DrawQueue() {
            mStopLatch = new CountDownLatch(1);
            mStartLatch = new CountDownLatch(1);
            setName("PopSquares.DrawQueue");
        }

        public void cancel() {
            if (mLooper != null) {
                mHandler.removeCallbacksAndMessages(null);
                mHandler.sendMessageAtFrontOfQueue(newQuitMessage());

                try {
                    mStopLatch.await();
                    mLooper = null;
                }
                catch (InterruptedException ex) {
                    Log.i(TAG, "DrawQueue.cancel latch await interrupted??");
                }
            }
        }

        public void waitForStart() {
            try {
                mStartLatch.await();
            }
            catch (InterruptedException ex) {
                Log.i(TAG, "DrawQueue.waitForStart latch await interrupted??");
            }
        }

        public void sendDrawStrobe(long delay) {
            if (delay > 0) {
                mHandler.sendMessageDelayed(newStrobeMessage(), delay);
            }
            else {
                mHandler.sendMessage(newStrobeMessage());
            }
        }

        public void clearStrobes() {
            mHandler.removeCallbacksAndMessages(null);
        }

        public void sendDrawMove(int offset) {
            clearStrobes();
            mHandler.sendMessage(newMoveMessage(offset));
        }

        public void run() {
            Looper.prepare();
            mLooper = Looper.myLooper();

            mHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    if (msg.what == MESSAGE_QUIT) {
                        mLooper.quit();
                    }
                    else {
                        SurfaceHolder holder = mSurfaceHolder;
                        if (holder != null) {
                            Canvas canvas = holder.lockCanvas();
                            final Rect bounds = canvas.getClipBounds();

                            if ( ! mLastBounds.equals(bounds)) {
                                holder.unlockCanvasAndPost(canvas);
                                mContext.sendBroadcast(new Intent(ACTION_ORIENTATION_CHANGED));
                            }
                            else if (canvas != null) {
                                if (msg.what == MESSAGE_DRAW_TYPE_MOVE) {
                                    drawMoveFrame(canvas, msg.arg1);
                                }
                                else if (msg.what == MESSAGE_DRAW_TYPE_STROBE) {
                                    long delay = drawStrobeFrame(canvas);

                                    if (delay != Long.MAX_VALUE) {
                                        sendDrawStrobe(delay);
                                    }
                                }

                                holder.unlockCanvasAndPost(canvas);
                            }
                        }
                    }
                }
            };

            sendDrawStrobe(0);

            mStartLatch.countDown();
            Looper.loop();
            mStopLatch.countDown();
        }

        private Message newStrobeMessage() {
            Message message = mHandler.obtainMessage();
            message.what = MESSAGE_DRAW_TYPE_STROBE;
            return message;
        }

        private Message newQuitMessage() {
            Message message = mHandler.obtainMessage();
            message.what = MESSAGE_QUIT;
            return message;
        }

        private Message newMoveMessage(int offset) {
            Message message = mHandler.obtainMessage();
            message.what = MESSAGE_DRAW_TYPE_MOVE;
            message.arg1 = offset;
            return message;
        }
    }
}

