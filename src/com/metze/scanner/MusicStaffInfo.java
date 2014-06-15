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
    private float[] mCostList;
    private int pathLeftBound;
    private int pathRightBound;
    
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
    	pathLeftBound = w/8;
    	pathRightBound = w - pathLeftBound;
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

    	findStablePaths(pathLeftBound, pathRightBound);
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
    
    public Bitmap overlayStaffLines(Bitmap bmp)
    {    	
    	for(int[] p : mStablePaths)
    	{
    		int r = (int) (256*Math.random());
    		int g = (int) (256*Math.random());
    		int b = (int) (256*Math.random());
    		int color = Color.rgb(r, g, b);
    		for(int x = pathLeftBound; x<=pathRightBound; ++x)
    		{
    			bmp.setPixel(x, p[x], color);
    		}
    	}
    	
    	return bmp;
    }
    
    public Bitmap getProcessedBmp()
    {
    	Bitmap bmp = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
    	for(int y=0; y<mHeight; ++y)
    	{
    		for(int x=0; x<mWidth; ++x)
    		{
    			int c = Color.BLACK;
    			if(mPixelBuffer[y*mWidth+x] == -1)
    				c = Color.WHITE;
    			bmp.setPixel(x, y, c);
    		}
    	}
    	
    	return overlayStaffLines(bmp);
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
    	int xStart = x;
    	int yStart = y;
    	final float k = 5.f;
    	
    	int dir = 1;
    	if(!leftToRight)
    		dir = -1;
    	
    	mCostList[y*mWidth+x] = 0.0f;
    	
		while(x != xEnd)
		{
			x+=dir;

			//only search update/search y positions as far as we've moved x
			// this is because each move in x, we only allow the staff line to move up or down a maximum of 1 pixel
			int xDiff = Math.abs(x - xStart);
			int yIndexMin = Math.max(0, yStart-xDiff);
			int yIndexMax = Math.min(mHeight-1, yStart+xDiff);

			while(yIndexMin <= yIndexMax)
			{
				int curBufferIndex = yIndexMin*mWidth+x;
				float pixelWeight = k*(mPixelBuffer[curBufferIndex] + 1.f);	//bigger k value = less likely to choose background pixel
				
				float costFromPrev = 1.0f + mCostList[yIndexMin*mWidth+(x-dir)] + pixelWeight;
				if(costFromPrev < mCostList[curBufferIndex])
					mCostList[curBufferIndex] = costFromPrev;

				if(yIndexMin > 0)
				{
					float costFromPrevUp = 1.414f + mCostList[(yIndexMin-1)*mWidth+(x-dir)] + pixelWeight;
					if(costFromPrevUp < mCostList[curBufferIndex])
						 mCostList[curBufferIndex] = costFromPrevUp;
				}
				if(yIndexMin < mHeight-1)
				{
					float costFromPrevDown = 1.414f + mCostList[(yIndexMin+1)*mWidth+(x-dir)] + pixelWeight;
					if(costFromPrevDown < mCostList[curBufferIndex])
						 mCostList[curBufferIndex] = costFromPrevDown;
				}

				++yIndexMin;
			}
		}
    	
		//fill out the actual path based on lowest cost at each x
		int path[] = new int[mWidth];
		float bestCost = Float.MAX_VALUE;
		int bestY = y;
		
		x = xStart;
		y = yStart;
		while(x != xEnd)
		{
			path[x] = y;
			x+=dir;
			
			bestCost = mCostList[y*mWidth+x];
			bestY = y;
			 
			if(y > 0)
			{
				if(mCostList[(y-1)*mWidth+x] < bestCost)
				{
					bestCost = mCostList[(y-1)*mWidth+x];
					bestY = y-1;
				}
			}
			if(y < mHeight-1)
			{
				if(mCostList[(y+1)*mWidth+x] < bestCost)
				{
					bestCost = mCostList[(y+1)*mWidth+x];
					bestY = y+1;
				}
			}
			 
			y = bestY;
		}

		return path;
    }
    
    private void findStablePaths(int xStart, int xEnd)
    { 
    	int[] leftToRightPath = new int[mWidth];
    	Arrays.fill(leftToRightPath, 0);
    	int[] rightToLeftPath = new int[mWidth];
    	Arrays.fill(rightToLeftPath, 0);

    	int rightEndY = 0;
    	for(int row=5; row<mHeight-5; ++row)		//find the shortest path for each row, starting on the left side
    	{
    		Arrays.fill(mCostList, Float.MAX_VALUE-5.f);
    		leftToRightPath = shortestPath(xStart, xEnd, row, true);
    		if(leftToRightPath[xEnd-1] != rightEndY)	//only search backwards if the endpoint is at a different y value than before
    		{
    			rightEndY = leftToRightPath[xEnd-1];
    			Arrays.fill(mCostList, Float.MAX_VALUE-5.f);
    			rightToLeftPath = shortestPath(xEnd, xStart, rightEndY, false);
    		}

    		//if the endpoints are the same from each search, the path is stable
			if(rightToLeftPath[xStart+1] == leftToRightPath[xStart+1] && rightToLeftPath[xEnd-1] == leftToRightPath[xEnd-1])	
			{
				mStablePaths.add(leftToRightPath);
				System.out.println("adding stable path");
			}
    	}//end for row
    }
    
    public void removeStaffLines()
    {
    	if(mStablePaths.size() < 5)
    	{
    		System.out.println("Error: less than 5 staff lines found");
    		return;
    	}
    	
    	for(int[] path : mStablePaths)
    	{
    		for(int x=pathLeftBound; x<pathRightBound; ++x)
    		{
    			int y = path[x];
    			int yTop = y;
    			int yBot = y;
    			while(yTop >=0 && mPixelBuffer[yTop*mWidth+x] == -1)
    			{
    				yTop--;
    			}
    			while(yBot < mHeight && mPixelBuffer[yBot*mWidth+x] == -1)
    			{
    				yBot++;
    			}
    			
    			int lineWidth = yBot-yTop;
    			if(lineWidth <= mStaffLineThickness+0.1*mStaffLineThickness)
    			{
    				while(yBot >= yTop)
    				{
    					mPixelBuffer[yTop*mWidth+x] = 0;
    					yTop++;
    				}
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
