package org.treant.treantimagegrid.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.treant.treantimagegrid.BuildConfig;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.util.LruCache;
import android.util.Log;
/**
 * This class holds the cache(both memory and disk) of our bitmap.
 * @author Administrator
 *
 */
public class ImageCache {

	private static final String TAG="ImageCache";
	// Default memory cache size in kilobytes
	private static final int DEFAULT_MEM_CACHE_SIZE=1024*5;  //unit is KB//默认5MB  
	// Default disk cache size in bytes
	private static final int DEFAULT_DISK_CACHE_SIZE=1024*1024*10; //unit is byte//默认10MB
	
	// Compression setting when writing images to disk cache
	private static final CompressFormat DEFAULT_COMPRESS_FORMAT=CompressFormat.JPEG;
	private static final int DISK_CACHE_INDEX=0;
	private static final int DEFAULT_COMPRESS_QUALITY=70;
	
	// Constants to easily toggle various caches
	private static final boolean DEFAULT_MEM_CACHE_ENABLED=true;
	private static final boolean DEFAULT_DISK_CACHE_ENABLED=true;
	private static final boolean DEFAULT_INIT_DISK_CACHE_ON_CREATE=false;
	private static final boolean DEFAULT_CLEAR_DISK_CACHE_ON_START=false;//这个变量暂且没用到
	
	private DiskLruCache mDiskLruCache;//磁盘cache
	private LruCache<String, Bitmap> mMemoryCache; //内存cache
	private ImageCacheParams mCacheParams;
	private final Object mDiskCacheLock=new Object();
	private boolean mDiskCacheStarting=true;
	
	/**
	 * Creating a new ImageCache object using the specified parameters.
	 * @param cacheParams The cache parameters to use to initialize the cache
	 */
	public ImageCache(ImageCacheParams cacheParams){
		init(cacheParams);
	}
	/**
	 * Initialize the cache, providing all parameters
	 * @param cacheParams The cache parameters to initialize the cache
	 */
	private void init(ImageCacheParams cacheParams){
		this.mCacheParams=cacheParams;
		// Set up memory cache
		if(mCacheParams.memoryCacheEnabled){
			if(BuildConfig.DEBUG){
				Log.d(TAG, "Memory cache created (size = "+ mCacheParams.memCacheSize+")");
			}
			// For caches that do not override sizeOf(K, V), this is the maximum number of the entries in the cache
			// For all other cashes, this is the maximum sum of the sizes of the entries in this cache.
			mMemoryCache=new LruCache<String, Bitmap>(mCacheParams.memCacheSize){
				@Override
				protected int sizeOf(String key, Bitmap bitmap) {
					// Returns the size of entry for key and value in user-defined units.
					// The default implementation returns 1 so that size is the number of entries and max size if the max number of the entries
					final int bitmapSize=getBitmapSize(bitmap)/1024;  //in kilobytes
					return bitmapSize==0?1:bitmapSize;
				}
			};
		}
		// By default the disk cache is not initialized here as it should be initialized in a separate thread due to disk access
		if(cacheParams.initDiskCacheOnCreate){
			initDiskCache();
		}
	}
	/**
	 * Find and return an existing ImageCache stored in a RetainFragment, if not found
	 * a new one is created using the supplied parameter and saved to a RetainFragment.
	 * @param fragmentManager  The fragment manager to use if creating the ImageCache
	 * @param cacheParams The cache parameters to use if creating the ImageCache
	 * @return An exist retained ImageCache object or a new one if one did not exist
	 */
	public static ImageCache findOrCreateCache(FragmentManager fragmentManager, ImageCacheParams cacheParams){
		//Search for, or create an instance of the non-UI RetainFragment
		final RetainFragment mRetainFragment=findOrCreateRetainFragment(fragmentManager);
		// Check if we already have an ImageCache stored in RetainFragment
		ImageCache imageCache=(ImageCache)mRetainFragment.getObject();
		// No existing ImageCache, create one and store it in RetainFragment
		if(imageCache==null){
			imageCache=new ImageCache(cacheParams);
			mRetainFragment.setObject(imageCache);
		}
		return imageCache;
	}
	/**
	 * A holder class that contains cache parameters
	 * @author Administrator
	 *
	 */
	public static class ImageCacheParams{
		public int memCacheSize=DEFAULT_MEM_CACHE_SIZE;
		public int diskCacheSize=DEFAULT_DISK_CACHE_SIZE;
		public File diskCacheDir;
		
		public CompressFormat compressFormat=DEFAULT_COMPRESS_FORMAT;
		public int compressQuality =DEFAULT_COMPRESS_QUALITY;
		
		public boolean memoryCacheEnabled=DEFAULT_MEM_CACHE_ENABLED;
		public boolean diskCacheEnabled=DEFAULT_DISK_CACHE_ENABLED;
		public boolean initDiskCacheOnCreate=DEFAULT_INIT_DISK_CACHE_ON_CREATE;
		public boolean clearDiskCacheOnStart=DEFAULT_CLEAR_DISK_CACHE_ON_START;
		
		public ImageCacheParams(Context context, String uniqueName){
			diskCacheDir=getDiskCacheDir(context, uniqueName);
		}
		/**
		 * Sets the memory cache size based on a percentage of the max available VM memory.
		 * Eg. setting percent to 0.2 would set the memory cache to one fifth of the available memory.
		 * Throw IllegalArgumentException if percent is <0.05 or >0.8
		 * memCacheSize is stored in kilobytes instead of bytes as this will eventually be passed to
		 * construct a LruCache which takes an int in its constructor.
		 * 
		 * This value should be chosen carefully based on a number of factors.
		 * Refer to the corresponding Android Training class for more discussion.
		 * @param percent Percent of available application memory to use to size memory cache
		 */
		public void setMemCacheSizePercent(float percent){
			if(percent<0.05f||percent>0.8f){
				throw new IllegalArgumentException("setMemCacheSizePercent --percent must be between 0.05 and 0.8");
			}//The result is equivalent to (int) Math.floor(f+0.5). 
			memCacheSize=Math.round(percent*Runtime.getRuntime().maxMemory()/1024);//unit is KB
		}
	}
	/**获取一个可用的缓存目录(如果可得到就用external, 否则internal)
	 * Get a usable cache directory (external if available, internal otherwise).
	 * @param context  The context to use
	 * @param uniqueName  A unique directory name to append to the cache dir
	 * @return The cache directory
	 */
	public static File getDiskCacheDir(Context context, String uniqueName){
		// Check if media is mounted or storage is built-in, if so, try and use external cache directory.
		// otherwise use an internal cache directory. 检测存储状态,尽量用external的,否则用internal
		final String cachePath=Environment.MEDIA_MOUNTED
				.equals(Environment.getExternalStorageState())||!isExternalStorageRemovable()?    //如果是built-in storage device则用之
						getExternalCacheDir(context).getPath():context.getCacheDir().getPath();
		
				return new File(cachePath+File.separator+uniqueName);
	}
	/**
	 * Get the size in bytes of a bitmap
	 * @param bitmap
	 * @return size in bytes
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
	public static int getBitmapSize(Bitmap bitmap){
		if(Utils.hasHoneycombMR1()){
			return bitmap.getByteCount();//Returns the number of bytes used to store this bitmap's pixels
		}
		// SDK Version prior to HoneycombMR1
		return bitmap.getRowBytes()*bitmap.getHeight();
	}
	
	/**
	 * Check if external storage is built-in or removable
	 * @return True if external storage is removable(like an SD card), false otherwise.
	 */
	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	public static boolean isExternalStorageRemovable(){
		if(Utils.hasGingerbread()){
			// Returns whether the primary "external" storage device is removable. 
			// If true is returned, this device is for example an SD card that the user can remove.
			// If false is returned, the storage is built into the device and can not be physically removed.
			return Environment.isExternalStorageRemovable(); //"外部"存储器件是可拆卸的还是内置的
		}
		return true;
	}
	/**
	 * Get the external App cache directory.
	 * @param context  The context to use
	 * @return  The external cache directory
	 */
	public static File getExternalCacheDir(Context context){
		if(Utils.hasFroyo()){
			return context.getExternalCacheDir();
		}
		// Before Froyo we have to construct the external cache directory ourselves
		final String cacheDir="/Android/data/"+context.getPackageName()+"/cache/";
		return new File(Environment.getExternalStorageDirectory().getPath()+cacheDir);
	}
	
	/**
	 * Adds a bitmap to both memory and disk cache
	 * @param data Unique identifier for the bitmap to store
	 * @param bitmap The bitmap to store
	 */
	public void addBitmapToCache(String data, Bitmap bitmap){
		if(data==null||bitmap==null){
			return;
		}
		// Add the bitmap to memory cache
		if(mMemoryCache!=null&&mMemoryCache.get(data)==null){
			mMemoryCache.put(data, bitmap);
		}
		// Add the bitmap to disk cache
		synchronized(mDiskCacheLock){
			final String key=hashKeyForDisk(data);
			OutputStream outputStream=null;
			try {
				DiskLruCache.Snapshot snapshot=mDiskLruCache.get(key);
				if(snapshot==null){
					final DiskLruCache.Editor editor=mDiskLruCache.edit(key);
					if(editor!=null){
						outputStream=editor.newOutputStream(DISK_CACHE_INDEX);
						bitmap.compress(mCacheParams.compressFormat, mCacheParams.compressQuality, outputStream);
						editor.commit();
						outputStream.close();
					}
				}else{
					snapshot.getInputStream(DISK_CACHE_INDEX).close();;
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally{
				if(outputStream!=null){
					try {
						outputStream.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
	}
	/**
	 * Get bitmap from memory cache
	 * @param data Unique identifier for which item to get
	 * @return The bitmap if found in memory cache, null otherwise
	 */
	public Bitmap getBitmapFromMemCache(String data){
		if(mMemoryCache!=null){
			final Bitmap memBitmap=mMemoryCache.get(data);
			if(memBitmap!=null){
				if(BuildConfig.DEBUG){
					Log.d(TAG, "Memory cache hit");
				}
				return memBitmap;
			}
		}
		return null;
	}
	/**
	 * Get bitmap from disk cache
	 * @param data Unique identifier for which item to get
	 * @return The bitmap if found in disk cache, null otherwise
	 */
	public Bitmap getBitmapFromDiskCache(String data){
		final String key=hashKeyForDisk(data);
		synchronized(mDiskCacheLock){
			while(mDiskCacheStarting){  //由clearCache()和initDiskCache()的状态而变化
				try {
					mDiskCacheLock.wait();
				} catch (InterruptedException e) {}
			}
			if(mDiskLruCache!=null){
				InputStream inputStream=null;
				try {
					final DiskLruCache.Snapshot snapshot=mDiskLruCache.get(key);
					if(snapshot!=null){
						if(BuildConfig.DEBUG){
							Log.d(TAG, "Disk Cache Hit!!");
						}
						inputStream =snapshot.getInputStream(DISK_CACHE_INDEX);
						if(inputStream!=null){
							final Bitmap bitmap=BitmapFactory.decodeStream(inputStream);
							return bitmap;
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					Log.e(TAG, "getBitmapFromDiskCache--"+e);
				} finally{
					try {
						if(inputStream!=null){
							inputStream.close();
						}
					} catch (IOException e) {}
				}
			}
			return null;
		}
	}
	
	/**
	 * Initializes the disk cache. Note that this includes disk access so this should not
	 * be executed on the main/UI thread. By default an ImageCache does not initialize the 
	 * disk cache when it is created, instead you should call initDiskCache() on a background thread
	 */
	public  void initDiskCache(){
		synchronized(mDiskCacheLock){
			if(mDiskLruCache==null||mDiskLruCache.isClosed()){
				File diskCacheDir=mCacheParams.diskCacheDir;
				if(mCacheParams.diskCacheEnabled&&diskCacheDir!=null){
					if(!diskCacheDir.exists()){
						diskCacheDir.mkdirs();
					}
					if(getUsableSpace(diskCacheDir)>mCacheParams.diskCacheSize){//缓存路径可用空间大于默认值
						try {
							mDiskLruCache=DiskLruCache.open(diskCacheDir, 1, 1, mCacheParams.diskCacheSize);
							if(BuildConfig.DEBUG){
								Log.d(TAG, "Disk cache initialized successfully");
							}
						} catch (IOException e) {
							// TODO Auto-generated catch block
							mCacheParams.diskCacheDir=null;
							Log.e(TAG, "initDiskCache-"+e);
						}
					}
				}
			}
			//改变mDiskStarting唤醒getBitmapFromDiskCache
			mDiskCacheStarting=false;
			mDiskCacheLock.notifyAll();
		}
	
	}
	
	/**
	 * Clears both memory and disk cache associated with this ImageCache object.
	 * Note that this includes disk access so this should not be executed on the main/UI thread.
	 */
	public void clearCache(){
		if(mMemoryCache!=null){
			mMemoryCache.evictAll();
			if(BuildConfig.DEBUG){
				Log.d(TAG, "Memory cache cleared!");
			}
		}
		
		synchronized(mDiskCacheLock){
			mDiskCacheStarting=true;//clear完后变为true使得任何调用getBitmapFromDiskCache()都进入wait
			if(mDiskLruCache!=null && !mDiskLruCache.isClosed()){
				try {
					mDiskLruCache.delete();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					Log.e(TAG, "clearCache---"+e);
				}
				mDiskLruCache=null;
				initDiskCache();//clear完后还要记得init
			}
		}
	}
	
	/**
	 * Flushes the disk cache associated with this ImageCache object.
	 * Note that this includes disk access and this should not be executed on the main/UI thread.
	 */
	public void flush(){
		synchronized(mDiskCacheLock){
			if(mDiskLruCache!=null){
				try {
					mDiskLruCache.flush();
					if(BuildConfig.DEBUG){
						Log.d(TAG, "Disk cache flushed!");
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					Log.e(TAG, "Disk cache flush---"+e);
				}
			}
		}
	}
	
	/**
	 * Closes the disk cache associated with this ImageCache object.
	 * Note that this includes disk access and this should not be executed on the main/UI thread.
	 */
	public void close(){
		synchronized(mDiskCacheLock){
			if(mDiskLruCache!=null){
				try {
					if(!mDiskLruCache.isClosed()){
						mDiskLruCache.close();
						mDiskLruCache=null;
						if(BuildConfig.DEBUG){
							Log.d(TAG, "Disk cache closed!");
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					Log.e(TAG, "Disk cache close--"+e);
				}
			}
		}
	}
	
	/**
	 *  Check how much usable space is available at a given path
	 * @param path The path to check
	 * @return The available space in bytes
	 */
	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	public static long getUsableSpace(File path){
		if(Utils.hasGingerbread()){
			return path.getUsableSpace();
		}
		final StatFs statFs=new StatFs(path.getPath());//Retrieve overall information about the space on a file system. This is a wrapper for Unix statfs(). 
		return (long)statFs.getBlockSize()*(long)statFs.getAvailableBlocks();
	}
	
	/**
	 * A hashing method that change a String (like a URL) into a hash suitable using as 
	 * a disk filename.  把String的URL变为Hash样式的文件名的哈希方法
	 * @param key
	 * @return
	 */
	public static String hashKeyForDisk(String key){
		String cacheKey;
		try {
			final MessageDigest mDigest=MessageDigest.getInstance("MD5");
			mDigest.update(key.getBytes());
			cacheKey=bytesToHexString(mDigest.digest());
		} catch (NoSuchAlgorithmException e) {
			// 实在不行就简单hash一下
			cacheKey=String.valueOf(key.hashCode());
		}
		return cacheKey;
	}
	// You could get associated references from http://www.stackoverflow.com
	private static String bytesToHexString(byte[] bytes){
		StringBuffer sb=new StringBuffer();
		for(int i=0;i<bytes.length;i++){
			String hex=Integer.toHexString(0xFF & bytes[i]);
			if(hex.length()==1){
				sb.append('0');
			}
			sb.append(hex);
		}
		return sb.toString();
	}

	public static RetainFragment findOrCreateRetainFragment(FragmentManager fragmentManager){
		// Check to see if we have retained the worker fragment
		RetainFragment mRetainFragment=(RetainFragment)fragmentManager.findFragmentByTag(TAG);
		// If not retained (or first time running), we need to create and add it
		if(mRetainFragment==null){
			mRetainFragment=new RetainFragment();
			//                              public abstract int commit()
			// Schedule a commit of this transaction. The commit does not happen immediately, it will be scheduled 
			// as work on the main thread to be done the next time that thread is ready.
			// A transaction can only be committed with this method prior to its containing activity saving its state.
			// If the commit is attempted after that point, an exception will be thrown. This is because the state after
			// the commit can be lost if the activity needs to be restored from its state.
			//                     public abstract int commitAllowStateLoss()
			// Like commit() but allows the commit to be executed after an activity's state is saved. This is dangerous because
			// the commit can be lost if the activity needs to be restored from its state. so this should only be used for
			// cases where it is okay for the UI state to change unexpectedly on the user.
			fragmentManager.beginTransaction().add(mRetainFragment, TAG).commitAllowingStateLoss();
		}
		return mRetainFragment;
	}
	
	/**
	 * A simple non-UI Fragment that stores a single Object and is retained over
	 * configuration changes. It will be used to retain the ImageCache object.
	 * @author Administrator
	 *
	 */
	public static class RetainFragment extends Fragment{
		private Object mObject;
		
		public RetainFragment(){
			//All subclasses of Fragment must include a public empty constructor
		}
		
		@Override
		public void onCreate(Bundle savedInstanceState) {
			// TODO Auto-generated method stub
			super.onCreate(savedInstanceState);
			// Make sure this Fragment is retained over a configuration change
			setRetainInstance(true);// Control whether a fragment instance is retained across Activity re-creation
		}
		// Store and get the stored object
		public void setObject(Object object){
			this.mObject=object;
		}
		public Object getObject(){
			return this.mObject;
		}
	}
}
