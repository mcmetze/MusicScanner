package com.metze.scanner;

import java.io.File;
import java.io.FileOutputStream;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.Utils;
import java.lang.Math;


public class ProcessTask extends AsyncTask<Double, Integer, Bitmap>
{
	protected static final String TAG = "ProcessTask";
    
	private Mat mMatToProcess;
	private byte mPixels[];
	
    private int mImageWidth;
    private int mImageHeight;
    
    private MusicStaffInfo mStaffInfo;
    
    private Bitmap mReturnBmp;	//for display/debugging
    
    public ProcessTask()
    {
    	mMatToProcess = null;
    	mStaffInfo = null;
    	mReturnBmp = null;
    	mPixels = null;
    	mImageWidth = 0;
    	mImageHeight = 0;
    }

    @Override
    protected Bitmap doInBackground(Double... doubles) 
    {
    	Log.i(TAG, "doInBackground");
        final double thetaRads = doubles[0].doubleValue();
     
        preProcessMat();
        rotateImage(thetaRads);
    	mMatToProcess.get(0, 0, mPixels);
    	
        mStaffInfo = new MusicStaffInfo(mPixels, mImageWidth, mImageHeight);
        //mStaffInfo.processImage();
        //removeStaffLines();
        
        //Utils.matToBitmap(mMatToProcess, mReturnBmp, true);
        mReturnBmp = mStaffInfo.getStablePathsAsBmp();
        return mReturnBmp; 
    }

    @Override
	protected void onProgressUpdate(Integer... values) {
		super.onProgressUpdate(values);
	}

	@Override
	protected void onPostExecute(Bitmap result) {
		super.onPostExecute(result);
		
		Log.i(TAG, "onPostExecute");
		FileOutputStream out = null;
		String path = Environment.getExternalStorageDirectory().toString();
		try 
		{
			File file = new File(path + "/out.PNG");
			out = new FileOutputStream(file);
			Log.i(TAG, "Saving image to "+path);
			result.compress(Bitmap.CompressFormat.PNG, 90, out);
		} 
		catch (Exception e) 
		{
		    e.printStackTrace();
		} 
		finally 
		{
			try
			{
				out.close();
		    } 
			catch(Throwable ignore) {}
		}
	}

	public void setImageToProcess(Bitmap bmpToProcess)
    {
    	mReturnBmp = bmpToProcess;
    	mImageWidth = bmpToProcess.getWidth();
    	mImageHeight = bmpToProcess.getHeight();
    	mMatToProcess = new Mat(mImageHeight, mImageWidth, CvType.CV_8UC4);
    	mPixels = new byte[(int) (mMatToProcess.total() * mMatToProcess.channels())];
    }
    
    private void rotateImage(double angleRads)
    {
    	Log.i(TAG, "RotateImage");
        Point center = new Point(mImageWidth/2, mImageHeight/2);
        Mat rotationMat = Imgproc.getRotationMatrix2D(center, -1*Math.toDegrees(angleRads), 1);
        Imgproc.warpAffine(mMatToProcess, mMatToProcess, rotationMat, mMatToProcess.size());
    }
    
    private void preProcessMat()
    {
    	Log.i(TAG, "preProcess");
        Utils.bitmapToMat(mReturnBmp, mMatToProcess, true);	//is created as 'CV_8UC4' type, it keeps the image in RGBA format.
    	Imgproc.cvtColor(mMatToProcess, mMatToProcess, Imgproc.COLOR_RGBA2GRAY);
    	Imgproc.threshold(mMatToProcess, mMatToProcess, 100, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C | Imgproc.THRESH_OTSU | Imgproc.THRESH_BINARY_INV );
    }
    
    private void removeStaffLines()
    {
    	Log.i(TAG, "removeStaffLines");

    	for(int row=1; row<mImageHeight-1; ++row)
    	{
            //if the current y has a staff line see if we need to remove it
    		if(mStaffInfo.isStaffLineAt(row))
            {
                int top = row-1;
                int bottom = row+1;
                //find the top and bottom y value of the current line
                while(mStaffInfo.isStaffLineAt(top))
                {
                    --top;
                }
                while(mStaffInfo.isStaffLineAt(bottom))
                {
                    ++bottom;
                }
                //bounds checks
                top = Math.max(top, 0);
                bottom = Math.min(bottom, mImageHeight-1);

                for(int col=0; col<mImageWidth; ++col)
                {
                    //if no activity on either side of the current line, remove the line
                    if(mPixels[top*mImageWidth+col] ==0 && mPixels[bottom*mImageWidth+col] ==0)
                    {
                        int tmpTop = top;
                        while(tmpTop <= bottom)
                        {
                        	mPixels[tmpTop*mImageWidth+col] = 0;
                            ++tmpTop;
                        }
                    }
                }
                row = bottom;
            }
    	}
    	
    	mMatToProcess.put(0, 0, mPixels);
    }
    
}