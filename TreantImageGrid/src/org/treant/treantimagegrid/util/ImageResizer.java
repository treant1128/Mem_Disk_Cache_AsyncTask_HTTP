package org.treant.treantimagegrid.util;

import java.io.FileDescriptor;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;

/**
 * A simple subclass of ImageWorker that resizes images from resources given a 
 * target width and height. Useful for when input images might be too large to
 * simply load directly into memory.
 * @author Administrator
 *
 */
public class ImageResizer extends ImageWorker  {

	private static final String TAG="ImageResizer";
	private static final int ASPECT_RATIO_LIMIT=2;
	protected int mImageWidth;
	protected int mImageHeight;
	
	/**
	 * Initialize providing a single target image size (used for both width and height)
	 * @param context
	 * @param imageSize
	 */
	protected ImageResizer(Context context, int imageSize) {
		super(context);
		// TODO Auto-generated constructor stub
		setImageSize(imageSize);
	}
	
	protected ImageResizer(Context context, int width, int height){
		super(context);
		setImageSize(width, height);
	}
	
	/**
	 * Set the target image size with the same width and height
	 * @param size
	 */
	public void setImageSize(int size){
		setImageSize(size, size);
	}
	
	/**
	 * Set the target image width and height
	 * @param width
	 * @param height
	 */
	public void setImageSize(int width, int height){
		mImageWidth=width;
		mImageHeight=height;
	}
	
	/**
	 * Decode and sample down a bitmap from a file input stream to the requested dimensions
	 * @param fileDescriptor The file descriptor to read from
	 * @param reqWidth The requested width of the resulting bitmap
	 * @param reqHeight The requested height of the resulting bitmap
	 * @return A bitmap sampled down from the original with the same aspect ratio and dimensions
	 * 		  that are equal to or larger than the requested width and height.
	 */
	protected Bitmap decodeSampledBitmapFromDescriptor(FileDescriptor fileDescriptor, int reqWidth, int reqHeight){
		// First decode with inJustDecodeBounds=true to check dimensions
		final BitmapFactory.Options options=new BitmapFactory.Options();
		options.inJustDecodeBounds=true;
		BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);
		// Calculate inSampleSize
		options.inSampleSize=calculateInSampleSize(options, reqWidth, reqHeight);
		// Now decode bitmap with inSampleSize set works
		options.inJustDecodeBounds=false;
		return BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);
	}
	
	/**
	 * Calculate an inSampleSize for use in a BitmapFactory.Options object when decoding bitmaps using
	 * the decode*** methods from BitmapFactory. This implementation calculate the closest inSampleSize
	 * that will result in the final decoded bitmap having a width and height equal to or larger than the 
	 * requested width and height. 
	 * This implementation does not ensure a power of 2 is returned for inSampleSize which can be faster
	 * when decoding but results in a larger bitmap which isn't useful for caching purposes.
	 * 
	 * @param options An BitmapFactory.Options object with out** parameter already populated (run through a
	 * decode ** method with inJustDecodeBounds=true)
	 * @param reqWidth The requested width of the requesting bitmap
	 * @param reqHeight The requested height of the requesting bitmap
	 * @return The suitable value to be used for inSampleSize
	 */
	private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight){
		// Raw dimension of image
		final int width=options.outWidth;
		final int height=options.outHeight;
		int inSampleSize=1;
		
		if(width>reqWidth||height>reqHeight){
			// Calculate ratios of width and height to requested width and height
			final int widthRatio=Math.round((float)width/(float)reqWidth);
			final int heightRatio=Math.round((float)height/(float)reqHeight);
			// Choose the smaller ratio as inSampleSize value, this will guarantee a final image
			// with both dimensions larger than or equal to the requested dimensions.
			inSampleSize=widthRatio<heightRatio?widthRatio:heightRatio;
		}
		// while widthRatio/heightRatio or heightRatio/widthRatio larger than 2, then amplify inSampleSize.
		// This offers some additional logic in case the image has a strange aspect ratio.
		// For example, a panorama or a scroll has a much larger width than height. In this case the first calculated
		// inSampleSize is relatively smaller than practical needed and the total pixels might still end up being too
		// larger to fit comfortably in memory, so we should be more aggressive with sample down the image (=larger inSampleSize)
		final float totalPixels=width*height;
		final float totalReqPixels=reqWidth*reqHeight*ASPECT_RATIO_LIMIT;
		while(totalPixels/(inSampleSize*inSampleSize)>totalReqPixels){
			inSampleSize++;
		}
		return inSampleSize;
	}
	
	/**
	 * The following implementations is not currently used but may be also useful soon or later.
	 */
	@Override
	protected Bitmap processBitmap(Object data) {
		// TODO Auto-generated method stub
		return processBitmap(Integer.parseInt(String.valueOf(data)));
	}
	
	private Bitmap processBitmap(int resId){
		
		return decodeSampleBitmapFromResource(mResources, resId, mImageWidth, mImageHeight);
	}
	
	/**
	 * Decode and sample down a bitmap from resources to the requested dimensions
	 * @param res
	 * @param resId
	 * @param reqWidth
	 * @param reqHeight
	 * @return
	 */
	protected Bitmap decodeSampleBitmapFromResource(Resources res, int resId, int reqWidth, int reqHeight){
		final BitmapFactory.Options options=new BitmapFactory.Options();
		// First decode with inJustDecodeBounds=true to check dimensions
		options.inJustDecodeBounds=true;
		BitmapFactory.decodeResource(res, resId, options);
		
		
		options.inSampleSize=calculateInSampleSize(options, reqWidth, reqHeight); // calculate inSampleSize
		
		
		options.inJustDecodeBounds=false;
		return BitmapFactory.decodeResource(res, resId, options);
	}
	/**
	 * Decode and sample down a bitmap from file to the requested dimensions
	 * @param fileName
	 * @param reqWidth
	 * @param reqHeight
	 * @return
	 */
	protected Bitmap decodeSampleBitmapFromFile(String filePath, int reqWidth, int reqHeight){
		
		
		final BitmapFactory.Options options=new BitmapFactory.Options();
		options.inJustDecodeBounds=true;
		BitmapFactory.decodeFile(filePath, options);
		
		
		options.inSampleSize=calculateInSampleSize(options, reqWidth, reqHeight);
		
		
		options.inJustDecodeBounds=false;
		return BitmapFactory.decodeFile(filePath, options);
	}
}
