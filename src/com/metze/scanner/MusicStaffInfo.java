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
    float[] mCostList;

    
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

    	mCostList = new float[w*h];
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
    	bmp.eraseColor(Color.LTGRAY);

    	for(int[] p : mStablePaths)
    	{
    		int x = 0;
    		int r = (int) (256*Math.random());
    		int g = (int) (256*Math.random());
    		int b = (int) (256*Math.random());
    		int color = Color.rgb(r, g, b);
    		for(int y : p)
    		{
    			bmp.setPixel(x, y, color);
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
    private int[] shortestPath(int x, int xEnd, int y, boolean leftToRight)
    {
    	int path[] = new int[mWidth];
    	int xStart = x;
    	int yStart = y;
    	int dir = 1;
    	if(!leftToRight)
    		dir = -1;
    	
    	mCostList[y*mWidth+x] = 0.0f;
    	
		while(x+dir != xEnd)
		{
			path[x] = y;
			x+=dir;

			int xDiff = Math.abs(x - xStart);
			int yIndexMin = Math.max(0, yStart-xDiff);
			int yIndexMax = Math.min(mHeight-1, yStart+xDiff);
			int minCostY = 0;
			int leftUpDownMin = 0;
			float columnMinCost = Float.MAX_VALUE;
			while(yIndexMin <= yIndexMax)
			{
				int curBufferIndex = yIndexMin*mWidth+x;
				float pixelWeight =  mPixelBuffer[curBufferIndex];
				
				float costFromPrev = 1.0f + mCostList[yIndexMin*mWidth+(x-dir)] + pixelWeight;
				if(costFromPrev < mCostList[curBufferIndex])
				{
					mCostList[curBufferIndex] = costFromPrev;
					leftUpDownMin = yIndexMin;
				}
				if(yIndexMin > 0)
				{
					float costFromPrevUp = 1.414f + mCostList[(yIndexMin-1)*mWidth+(x-dir)] + pixelWeight;
					if(costFromPrevUp < mCostList[curBufferIndex])
					{
						 mCostList[curBufferIndex] = costFromPrevUp;
						 leftUpDownMin = (yIndexMin-1);
					}
				}
				if(yIndexMin < mHeight-1)
				{
					float costFromPrevDown = 1.414f + mCostList[(yIndexMin+1)*mWidth+(x-dir)] + pixelWeight;
					if(costFromPrevDown < mCostList[curBufferIndex])
					{
						 mCostList[curBufferIndex] = costFromPrevDown;
						 leftUpDownMin = (yIndexMin+1);
					}
				}
				
				if( mCostList[curBufferIndex] < columnMinCost)
				{
					columnMinCost = mCostList[curBufferIndex];
					minCostY = leftUpDownMin;
				}
				++yIndexMin;
			}
			
			y = minCostY;
			
		}

		return path;
    }
    
    private void findStablePaths()
    { 
    	float wCrop = 0.25f*mWidth;
    	int xStart = (int)wCrop;
    	int xEnd = (int)(mWidth - wCrop);
    	int[] tmpPath;
    	List<int[]> potentialPaths = new ArrayList<int[]>();
    	
    	for(int row=10; row<mHeight-10; ++row)
    	{
    		Arrays.fill(mCostList, Float.MAX_VALUE);
    		tmpPath = shortestPath(xStart, xEnd, row, true);
    		potentialPaths.add(tmpPath);
    	}
    	System.out.println("done searching left to right");
    	
    	for(int row=10; row<mHeight-10; ++row)
    	{
    		Arrays.fill(mCostList, Float.MAX_VALUE);
    		tmpPath = shortestPath(xEnd, xStart, row, false);
    		for(int[] p : potentialPaths)
    		{
    			if(p[xStart] == tmpPath[xStart+2] && p[xEnd-2] == tmpPath[xEnd] )
    			{
    				mStablePaths.add(p);
    				System.out.println("adding stable path");
    			}
    		}
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
