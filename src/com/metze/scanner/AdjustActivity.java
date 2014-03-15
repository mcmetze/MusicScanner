package com.metze.scanner;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;

import com.metze.musicscanner.R;

public class AdjustActivity extends Activity
{
    private ImageView mImageView;
    private double mCurrentRotationAngle;
    private double mMouseDownAngle;
    private int mScreenWidth;
    private int mScreenHeight;
    private Bitmap mOriginalBmp;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null)
        {
            restoreDataFromBundle(savedInstanceState);
        }
        else
        {
            mCurrentRotationAngle = 0;
            mMouseDownAngle = 0;
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_adjust);

        mImageView = (ImageView)findViewById(R.id.imageView);

        Bundle extras = getIntent().getExtras();
        mOriginalBmp = (Bitmap)extras.get("picture");

        GuideOverlay overlay = new GuideOverlay(this);
        addContentView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        outState.putDouble("curRotationAngle", mCurrentRotationAngle);
        super.onSaveInstanceState(outState);
    }

    private void restoreDataFromBundle(Bundle savedInstanceState) {
        mCurrentRotationAngle = savedInstanceState.getDouble("curRotationAngle");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus){
        mScreenHeight = mImageView.getHeight();
        mScreenWidth = mImageView.getWidth();

        Bitmap scaledPic = Bitmap.createScaledBitmap(mOriginalBmp, mScreenWidth, mScreenHeight, true);
        mImageView.setImageBitmap(scaledPic);

        mImageView.setPivotX(mScreenWidth/2);
        mImageView.setPivotY(mScreenHeight/2);
    }

    public void cancel(View view)
    {
        finish();
    }

    public void acceptPic(View view)
    {
        ProcessTask task = new ProcessTask();
        task.setImageToProcess(mOriginalBmp);
        task.execute(mCurrentRotationAngle);
        finish();
    }

    public void resetChanges(View view)
    {
        mCurrentRotationAngle = 0.0;
        mImageView.setRotation(0);
        mImageView.invalidate();
    }

    protected double getRotationAngle(float x, float y)
    {
        double angle = 0;

        if(x != 0 && y != 0)
            angle = Math.PI + Math.atan2(x, y);

        return angle;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        float xFromCenter = event.getX()-mImageView.getPivotX();
        float yFromCenter = -1*(event.getY()-mImageView.getPivotY());

        switch(event.getAction())
        {
            case MotionEvent.ACTION_DOWN:
                mMouseDownAngle = getRotationAngle(xFromCenter, yFromCenter);
                break;

            case MotionEvent.ACTION_MOVE:
                double deltaAngle = getRotationAngle(xFromCenter, yFromCenter) - mMouseDownAngle;
                mCurrentRotationAngle += deltaAngle;
                mMouseDownAngle += deltaAngle;
                break;
        }

        mImageView.setRotation((float)Math.toDegrees(mCurrentRotationAngle));
        mImageView.invalidate();

        return true;
    }

    class GuideOverlay extends View
    {
        Paint mGridLinePaint;

        public GuideOverlay(Context context)
        {
            super(context);
            mGridLinePaint = new Paint();
            mGridLinePaint.setColor(Color.GREEN);
        }

        @Override
        protected void onDraw(Canvas canvas)
        {
            final int stride = 30;
            final int xOffset = mScreenWidth/10;
            final int yOffset = mScreenHeight/5;
            for(int y=yOffset; y < mScreenHeight-yOffset; y+=stride)
            {
                canvas.drawLine(xOffset, y, xOffset+mScreenWidth, y, mGridLinePaint);
                canvas.drawLine(xOffset, y+1, xOffset+mScreenWidth, y+1, mGridLinePaint);
            }
            super.onDraw(canvas);
        }
    }
}