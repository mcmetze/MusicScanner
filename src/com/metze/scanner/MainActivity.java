package com.metze.scanner;

import java.io.FileNotFoundException;
import java.io.InputStream;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;

import com.metze.musicscanner.R;

public class MainActivity extends Activity
{
    private static final int TAKE_PIC_CODE = 1888;
    private static final int ADJUST_PIC_CODE = 1999;
    private static final int CHOOSE_PIC_CODE = 1777;
	protected static final String TAG = "MainActivity";

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_6, this, mLoaderCallback);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
    
    public void choosePicture(View view)
    {
        //read from gallery or SD card
    	Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
    	photoPickerIntent.setType("image/*");
    	startActivityForResult(photoPickerIntent, CHOOSE_PIC_CODE); 
    }

    public void takePicture(View view)
    {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(takePictureIntent, TAKE_PIC_CODE);
    }

    private void adjustPicture(Bitmap bmp)
    {
    	Intent adjustImageIntent = new Intent(this, AdjustActivity.class);
    	adjustImageIntent.putExtra("picture", bmp);
        startActivityForResult(adjustImageIntent, ADJUST_PIC_CODE);
    }
    
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode)
        {
            case TAKE_PIC_CODE:
                if (resultCode == RESULT_OK)
                {
                    Bitmap picBmp = (Bitmap)(data.getExtras().get("data"));
                    adjustPicture(picBmp);
                }
                break;
              
            case ADJUST_PIC_CODE:
            		if(resultCode == RESULT_OK)
            		{
	            		//new activity for displaying and playing the music.. 
	            		//	pass an instance of the object containing all the data?
	            		//	return lots of data in the intent? what data?
            		}
            		break;
            
            case CHOOSE_PIC_CODE:
            	if(resultCode == RESULT_OK)
            	{  
                    Uri selectedImage = data.getData();
					try 
					{
						InputStream imageStream = getContentResolver().openInputStream(selectedImage);
						Bitmap selectedBmp = BitmapFactory.decodeStream(imageStream);

						if(selectedBmp.getAllocationByteCount() > 900000)
						{
							final float densityMultiplier = this.getResources().getDisplayMetrics().density;        
	
							int h= (int) (0.25*selectedBmp.getHeight()*densityMultiplier);
							int w= (int) (h * selectedBmp.getWidth()/((double) selectedBmp.getHeight()));
	
							selectedBmp = Bitmap.createScaledBitmap(selectedBmp, w, h, true);
						}	
						
						adjustPicture(selectedBmp);
					} 
					catch (FileNotFoundException e) 
					{
						e.printStackTrace();
					}
                    
                }
            	break;
        }
    }
}
