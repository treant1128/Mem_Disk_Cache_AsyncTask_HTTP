package org.treant.treantimagegrid.util;

import java.lang.ref.WeakReference;

import org.treant.treantimagegrid.BuildConfig;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.widget.ImageView;

/**
 * This class wraps up completing some arbitrary long running work when loading
 * a bitmap to an ImageView. It handles things like using a memory and disk cache.
 * running the work in a background thread and setting a placeholder image.
 * @author Administrator
 *
 */
public abstract class ImageWorker {

	private static final String TAG="ImageWorker";
	private static final int FADE_IN_TIME=200;
	
	private ImageCache mImageCache;
	private ImageCache.ImageCacheParams mImageCacheParams;
	private Bitmap mLoadingBitmap;
	private boolean mFadeInBitmap=true;
	private boolean mExitTasksEarly=false;
	private boolean mPauseWork=false;
	private final Object mPauseWorkLock=new Object();
	
	protected Resources mResources;
	
	private static final int MESSAGE_CLEAR=0;
	private static final int MESSAGE_INIT_DISK_CACHE=1;
	private static final int MESSAGE_FLUSH=2;
	private static final int MESSAGE_CLOSE=3;
	
	protected ImageWorker(Context context){
		mResources=context.getResources();//Returns a resources instance for the application's package
	}
	
	/**
	 * Load an image specified by the data parameter into an ImageView. (override method processBitmap(Object))
	 * in ImageWorker class to define the processing logic.  
	 * A memory and disk cache will be used if an ImageCache has been set using setImageCache(ImageCache).  
	 * If the image is found in the memory cache, it is set immediately, otherwise an AsyncTask will
	 * be created to asynchronously load the bitmap
	 * @param data The URL of the image to download.
	 * @param imageView  The ImageView to bind the downloaded image.
	 */
	public void loadImage(Object data, ImageView imageView){
		if(data==null){
			return;
		}
		
		Bitmap bitmap=null;
		if(mImageCache!=null){
			bitmap=mImageCache.getBitmapFromMemCache(String.valueOf(data));//load from memory
		}
		
		if(bitmap!=null){
			//Bitmap found in memory cache
			imageView.setImageBitmap(bitmap);
		}else if(cancelPotentialWork(data, imageView)){
			final BitmapWorkerTask task=new BitmapWorkerTask(imageView);
			final AsyncDrawable asyncDrawable=new AsyncDrawable(mResources, mLoadingBitmap, task);
			imageView.setImageDrawable(asyncDrawable);
			//                                 Note: 
			// This uses a custom version of AsyncTask that has been pulled from the framework and slightly
			// modified. Refer to the comments at the top of the class for more info on what was changed.
			task.executeOnExecutor(AsyncTask.DUAL_THREAD_EXECUTOR, data);
		}
		
	}
	
	/**
	 * Set placeholder bitmap that shows when the background thread is running
	 * @param resId
	 */
	public void setLoadingImage(int resId){
		this.mLoadingBitmap=BitmapFactory.decodeResource(mResources, resId);
	}
	
	/**
	 * Adds an ImageCache to this worker in the background (to present disk access on UI thread)
	 * @param fragmentManager
	 * @param cacheParams
	 */
	public void addImageCache(FragmentManager fragmentManager, ImageCache.ImageCacheParams cacheParams){
		this.mImageCacheParams=cacheParams;
		setImageCache(ImageCache.findOrCreateCache(fragmentManager, mImageCacheParams));
		new CacheAsyncTask().execute(MESSAGE_INIT_DISK_CACHE);
	}
	
	/**
	 * Set the ImageCache object to use with this ImageWorker. Usually you will not need to call this directly.
	 * Instead use addImageCache(FragmentManager, ImageCache.ImageCacheParams) method which will create and
	 * the ImageCache in a background thread (to ensure no disk access on main/UI thread)
	 * @param imageCache
	 */
	public void setImageCache(ImageCache imageCache){
		this.mImageCache=imageCache;
	}
	
	public void setImageFadeIn(boolean fadeIn){
		this.mFadeInBitmap=fadeIn;
	}
	
	public void setExitTasksEarly(boolean exitTasksEarly){
		this.mExitTasksEarly=exitTasksEarly;
	}
	public void setPauseWork(boolean pauseWork){
		synchronized(mPauseWorkLock){
			this.mPauseWork=pauseWork;
			if(!mPauseWork){
				mPauseWorkLock.notifyAll();
			}
		}
	}
	
	/**
	 * Return true if the current work has been canceled or if there was no work in progress on this in this image view.
	 * Return false if the work in progress deal with the same data. The work is not stopped in that case.
	 * @param data
	 * @param imageView
	 * @return 除非请求的image和是同一个ImageView的BitmapWorkerTask否则迟早soon or later要返回true
	 */
	public static boolean cancelPotentialWork(Object data, ImageView imageView){
		final BitmapWorkerTask bitmapWorkerTask=getBitmapWorkerTask(imageView);
		if(bitmapWorkerTask!=null){
			final Object bitmapData=bitmapWorkerTask.data;
			if(bitmapData==null||!bitmapData.equals(data)){
				//如果当前的任务的data==null(还没有执行过doInBackground)或者与请求的参数data不相等(not same work)
				//那就attempt to interrupt the thread executing this task
				bitmapWorkerTask.cancel(true);//**************很关键***************
				if(BuildConfig.DEBUG){
					Log.d(TAG, "cancelPotentialWork--cancelled work for "+ data);
				}
			}else{
				// The same work is already in progress.
				return false;
			}
		}
		return true;//task==null --> there was no work in progress on this image view
	}
	
	/**
	 * Cancel any pending work attached to the provided ImageView
	 * @param imageView
	 */
	public static void cancelWork(ImageView imageView){
		final BitmapWorkerTask bitmapWorkerTask=getBitmapWorkerTask(imageView);
		if(bitmapWorkerTask!=null){
			bitmapWorkerTask.cancel(true);
			if(BuildConfig.DEBUG){
				Log.d(TAG, "cancelWork--cancelled work for "+bitmapWorkerTask.data);
			}
		}
	}
	
	/**
	 * The actual AsyncTask that will asynchronously process the image.
	 * @author Administrator
	 *
	 */
	private class BitmapWorkerTask extends AsyncTask<Object, Void, Bitmap>{
		private Object data;
		private final WeakReference<ImageView> imageViewReference;

		public BitmapWorkerTask(ImageView imageView){
			imageViewReference=new WeakReference<ImageView>(imageView);
		}
		
		@Override
		protected Bitmap doInBackground(Object... params) {
			// TODO Auto-generated method stub
			data=params[0];
			if(BuildConfig.DEBUG){
				Log.d(TAG, "doInBackground--starting work "+data);
			}
			final String dataString=String.valueOf(data);
			Bitmap bitmap=null;
			// Wait here if work is paused and the task is not cancelled.
			// setPauseWork(true) is invoked when the thumbnails gridView is flinging (onScrollStateChanged)
			// After invoking the bitmapWorkerTask.cancel(boolean) in cancelPotentialTask(Object, ImageView), you should check
			// the value returned by isCancelled() periodically from doInBackground(Object[]) to end the task as soon as possible.
			synchronized(mPauseWorkLock){
				while(mPauseWork&&!isCancelled()){
					try {
						mPauseWorkLock.wait();
					} catch (InterruptedException e){
						//do nothing
					}
				}
			}
			
			// If the image cache is available and this task has not been cancelled by another thread
			// and the ImageView that was originally bound to this task is still bound back to this task
			// and our "exit early" flag is not set then try and fetch the bitmap from the cache.
			if(mImageCache!=null&&!isCancelled()
					&&getAttachedImageView()!=null&&!mExitTasksEarly){
				bitmap=mImageCache.getBitmapFromDiskCache(dataString);
			}
			
			// If the bitmap was not found in the cache and this task has not been cancelled by another thread
			// and the ImageView that was originally bound to this task is still bound back to this task 
			// and the "exit early" flag is not set then call the main process method (as implemented by subclass)
			if(bitmap==null&&!isCancelled()
					&&getAttachedImageView()!=null&&!mExitTasksEarly){
			//	bitmap=processBitmap(params[0]);
				bitmap=processBitmap(data);
			}
			
			// If we get the processed bitmap and the image cache is available, then add the processed bitmap to the
			// cache for future use. Note we don't check if the task was cancelled here, if it was indeed, and the thread is 
			// still running, we may as well add the processed bitmap to our cache as it might be used again in the future.
			if(bitmap!=null&&mImageCache!=null){
				mImageCache.addBitmapToCache(dataString, bitmap);
			}
			if(BuildConfig.DEBUG){
				Log.d(TAG, "doInBackground--finished work~~~~");
			}
			return bitmap;
		}
		
		/**
		 * Once the image is processed, associated it to the imageView
		 */
		@Override
		protected void onPostExecute(Bitmap bitmap) {
			// TODO Auto-generated method stub
			if(isCancelled()||mExitTasksEarly){
				bitmap=null;
			}

			final ImageView imageView=getAttachedImageView();
			if(bitmap!=null&&imageView!=null){
				if(BuildConfig.DEBUG){
					Log.d(TAG, "onPostExecute--setting Bitmap--");
				}
				setImageBitmap(imageView, bitmap);
			}
		}
		
		// Application should preferably override this method.
		// Run on the UI thread after cancel(boolean) is invoked and doInBackground(Object[]) has finished
		@Override
		protected void onCancelled() {
			// TODO Auto-generated method stub
			super.onCancelled();
			synchronized(mPauseWorkLock){
				mPauseWorkLock.notifyAll();
			}
		}
		
		/**
		 * Returns the ImageView associated with this task as long as the ImageView's task
		 * still points to this task as well (this== bitmapWorkerTask). Returns null otherwise.
		 * @return
		 */
		private ImageView getAttachedImageView(){
			final ImageView imageView=imageViewReference.get();
			final BitmapWorkerTask bitmapWorkerTask=getBitmapWorkerTask(imageView);
			if(this==bitmapWorkerTask){
				return imageView;
			}
			return null;
		}
	}
	
	/**
	 * 
	 * @param imageView   Any ImageView
	 * @return Retrieve the currently active work task (if any) associated with this imageView, null if there is no such task.
	 */
	public static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView){
		if(imageView!=null){
			final Drawable drawable=imageView.getDrawable();
			if(drawable instanceof AsyncDrawable){
				final AsyncDrawable asyncDrawable=(AsyncDrawable)drawable;
				return asyncDrawable.getBitmapWorkerTask();
			}
		}
		return null;
	}
	
	/**
	 * Called when the processing is complete and the final bitmap should be set on the ImageView
	 * @param imageView 
	 * @param bitmap
	 */
	@SuppressWarnings("deprecation")
	private void setImageBitmap(ImageView imageView, Bitmap bitmap){
		if(mFadeInBitmap){
			// TransitionDrawable with a transparent drawable and the final bitmap
			final TransitionDrawable td=new TransitionDrawable(new Drawable[]{
					new ColorDrawable(android.R.color.transparent),
					new BitmapDrawable(mResources, bitmap)
			});
			
			imageView.setBackgroundDrawable(new BitmapDrawable(mResources, mLoadingBitmap));//set background to loading bitmap  placeholder
			imageView.setImageDrawable(td);// I forget to code this line so the thumbnails doesn't show when first loading from network. What a shame!!
			td.startTransition(FADE_IN_TIME);
	//		td.startTransition(8000);
		}else{
			imageView.setImageBitmap(bitmap);
		}
	}
	
	/**
	 * A custom Drawable that will be attached to the imageView while the work is in progress.
	 * Contains a reference to the actual worker task, so that it can be stopped if a new binding
	 * is required, and makes sure that only the last started worker process can bind its result.
	 * independently of the finish order.
	 * @author Administrator
	 *
	 */
	private class AsyncDrawable extends BitmapDrawable{
		final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;
		public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask){
			super(res, bitmap);
			bitmapWorkerTaskReference=new WeakReference<BitmapWorkerTask>(bitmapWorkerTask);
		}
		public BitmapWorkerTask getBitmapWorkerTask(){
			return bitmapWorkerTaskReference.get();
		}
	}
	
	protected class CacheAsyncTask extends AsyncTask<Object, Void, Void>{

		@Override
		protected Void doInBackground(Object... params) {
			// TODO Auto-generated method stub
			switch((Integer)params[0]){
			case MESSAGE_CLEAR:
				clearCacheInternal();
				break;
			case MESSAGE_INIT_DISK_CACHE:
				initDiskCacheInternal();
				break;
			case MESSAGE_FLUSH:
				flushCacheInternal();
				break;
			case MESSAGE_CLOSE:
				closeCacheInternal();
				break;
				default:
					break;
			}
			return null;
		}
		
	}
	
	protected void clearCacheInternal(){
		if(mImageCache!=null){
			mImageCache.clearCache();
		}
	}
	
	protected void initDiskCacheInternal(){
		if(mImageCache!=null){
			mImageCache.initDiskCache();
		}
	}
	
	protected void flushCacheInternal(){
		if(mImageCache!=null){
			mImageCache.flush();
		}
	}
	
	protected void closeCacheInternal(){
		if(mImageCache!=null){
			mImageCache.close();
			mImageCache=null;
		}
	}
	
	public void clearCache(){
		new CacheAsyncTask().execute(MESSAGE_CLEAR);
	}
	
	public void flushCache(){
		new CacheAsyncTask().execute(MESSAGE_FLUSH);
	}
	
	public void closeCache(){
		new CacheAsyncTask().execute(MESSAGE_CLOSE);
	}
	/**
	 * Subclasses should override this to define any processing or work that must happen to 
	 * produce the final bitmap. This will be executed in a background thread and be long running.
	 * For example, you would resize a large bitmap here, or pull down an image from the network.
	 * The Object parameter guarantee the most polymorphism works.
	 * @param data The data to identify which image to process, as provided by loadImage(Object, ImageView)
	 * @return The processed bitmap
	 */
	protected abstract Bitmap processBitmap(Object data);
	
}
