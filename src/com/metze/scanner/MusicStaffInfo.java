package com.metze.scanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.util.SparseIntArray;

public class MusicStaffInfo {
	protected static final String TAG = "MusicStaffInfo";
	
    private SparseIntArray mStaffLineMap;
    private byte[] mPixelBuffer;
    private int mWidth;
    private int mHeight;
    private int[] mHorizontalProjHist;
    
    private List<int[]> mStablePaths;
    int[] mBestTmpPath;
    float mTmpShortestCost;
    int[] mTmpPath;

    private int mStaffLineThickness;
    private int mStaffLineSpacing;
    private int mTotalStaffLines;
    private int mNumStaffs;
    
    public MusicStaffInfo(byte[] pixels, int w, int h)
    {
    	mPixelBuffer = pixels;
    	mHeight = h;
    	mWidth = w;
    	
    	mStaffLineThickness = 0;
        mStaffLineSpacing = 0;
        mTotalStaffLines = 0;
        mNumStaffs = 0;
        
        mHorizontalProjHist = new int[mHeight];
        mStaffLineMap = new SparseIntArray();
    	mStablePaths = new ArrayList<int[]>();

    	mBestTmpPath = new int[w];
    	mTmpPath = new int[w];
    }
	
    public int getStaffLineSize()
    {
    	return mStaffLineThickness;
    }
    
    public int getStaffSpaceSize()
    {
    	return mStaffLineSpacing;
    }
    
    public int getTotalStaffLines()
    {
    	return mTotalStaffLines;
    }
    
    public int getNumStaffs()
    {
    	return mNumStaffs;
    }
    
    public void processImage()
    {
    	calcHorizontalProjHist();
    	findStaffLinePositions();
    	calcStaffSpacingAndThickness();
    }
    
    public boolean isStaffLineAt(int y)
    {
    	return (mStaffLineMap.indexOfKey(y) >= 0);
    }
    
    public Bitmap getProjectionHistAsBmp()
    {
    	Log.i(TAG, "getProjectionHistAsBmp");
    	Bitmap bmpHist = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
    	
    	int row, col;
    	for(row=0; row< mHeight; row++)
      	{
      		for(col=0; col< mWidth; col++)
      		{
      				if(mHorizontalProjHist[row] >= col)
      					bmpHist.setPixel(col, row, Color.BLACK);

      				else
      					bmpHist.setPixel(col, row, Color.WHITE);
      		}
      	}
       	
    	return bmpHist;
    }
    
    public Bitmap getStablePathsAsBmp()
    {
    	findStablePaths();
    	
    	Bitmap bmp = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
    	bmp.eraseColor(Color.WHITE);
    	for(int[] p : mStablePaths)
    	{
    		int x = 0;
    		for(int y : p)
    		{
    			bmp.setPixel(x, y, Color.BLACK);
    			++x;
    		}
    	}
    	
    	return bmp;
    }
    
    //creates a histogram of the # of white pixels in each row of the image
    private void calcHorizontalProjHist()
    {
    	Log.i(TAG, "calcHorizontalProjHist");

      	int row, col, rowSum, pixelColor;
      	for(row=0; row<mHeight; ++row)
      	{
      		rowSum = 0;
      		for(col=0; col< mWidth; ++col)
      		{
      			pixelColor = (int)mPixelBuffer[row*mWidth+col];
      			if(pixelColor != 0)
      				rowSum++;
      		}
      		mHorizontalProjHist[row] = rowSum;
      	}

    }
    
    //finds the y position of the staff lines and places them into mStaffLineMap
    private void findStaffLinePositions()
    {
        Log.i(TAG, "findStaffLinePositions");
        final int sliceStart = (int)(0.45*mWidth);
        final int sliceEnd = (int)(0.65*mWidth);
        
        boolean connected;
        int lineNum;
        for(int x = sliceStart; x<sliceEnd; ++x)  //only consider a slice of x values
        {
            connected=false;
            lineNum=0;
            for(int y=0; y<mHeight; ++y)
            {
                if(mHorizontalProjHist[y] >= x)  //the histogram for this y value is within the slice we're looking at
                {
                    if(mStaffLineMap.indexOfKey(y) < 0)    //we haven't seen this y value, so add it
                    {
                    	mStaffLineMap.put(y, lineNum);
                    }
                    connected = true;
                }
                else if(connected)  //encountered whitespace after a line
                {
                    lineNum++;
                    connected = false;
                }
            }
        }

    }
    
    //this method assumes white is foreground/staff lines with an int val of -1 and black an int val of 0
    private void shortestPath(float cost, int x, int y, int x_end)
    {
    	float right = 1.0f + (int)mPixelBuffer[y*mWidth+x] + cost;
		float up = 1.414f + (int)mPixelBuffer[(y-1)*mWidth+x] + cost;
		float down = 1.414f + (int)mPixelBuffer[(y+1)*mWidth+x] + cost;

		if(x+1 < x_end)
		{
			if(right < mTmpShortestCost)
			{
				mTmpPath[x+1] = y;
				shortestPath(right, x+1, y, x_end);
			}
			if(y>1 && up < mTmpShortestCost)
			{
				mTmpPath[x+1] = y-1;
				shortestPath(up, x+1, y-1, x_end);
			}
			if(y<mHeight-1 && down < mTmpShortestCost)
			{
				mTmpPath[x+1] = y+1;
				shortestPath(down, x+1, y+1, x_end);
			}
		}
		else
		{
			if(cost < mTmpShortestCost)
			{
				mTmpShortestCost = cost;
				mBestTmpPath = Arrays.copyOf(mTmpPath, mWidth);
			}
		}

    }
    
    private void findStablePaths()
    { 
    	float wCrop = 0.4f*mWidth;
    	float hCrop = 0.3f*mHeight;
    	int xStart = (int)wCrop;
    	int xEnd = (int)(mWidth - wCrop);
    	int yStart = (int)hCrop;
    	int yEnd = (int)(mHeight-hCrop);

    	for(int row=yStart; row<yEnd; ++row)
    	{
    		Arrays.fill(mBestTmpPath, 0);
    		Arrays.fill(mTmpPath, 0);
    		mTmpShortestCost = Float.MAX_VALUE;
    		
    		shortestPath(0.f, xStart, row, xEnd);
    		mStablePaths.add(mBestTmpPath);
    		
    		System.out.println("row= "+row+ " shortest path = "+ mTmpShortestCost);
    	}
    }
    
    private void calcStaffSpacingAndThickness()
    {
    	Log.i(TAG, "calcStaffSpacingAndThickness");

        int lastLineNum = -1;
        int lastLineY = -1;
        int totalLineThickness = 0;
        int totalStaffSpacing = 0;
        
        for(int i=0; i<mHeight; ++i)
        {
            if(mStaffLineMap.indexOfKey(i) >= 0) //this y value was mapped to a staff line
            {
                totalLineThickness++;
                int lineNumber = mStaffLineMap.get(i);
                if(lineNumber != lastLineNum)   //if it's not part of the same line as last iteration
                {                               //increment the line count and check the spacing
                	mTotalStaffLines++;
                    if(lineNumber%5 != 0)   //not the first line of a staff
                    {
                        totalStaffSpacing += (i-lastLineY);
                    }
                    lastLineY = i;
                    lastLineNum = lineNumber;
                }
            }
        }

        if(mTotalStaffLines > 1)
        {
        	mStaffLineThickness = totalLineThickness/mTotalStaffLines;
            mStaffLineSpacing = totalStaffSpacing/(mTotalStaffLines-1);
        }
        
        mNumStaffs = (int) (mTotalStaffLines/5.0);
    }
    
}
