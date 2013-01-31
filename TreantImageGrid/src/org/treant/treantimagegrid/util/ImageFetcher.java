package org.treant.treantimagegrid.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import org.treant.treantimagegrid.BuildConfig;
import org.treant.treantimagegrid.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

/**
 * A simple subclass of ImageResizer that fetches and resizes images fetched from a URL.
 * @author Administrator
 *
 */
public class ImageFetcher extends ImageResizer {

	private static final String TAG="ImageFetcher";
	private static final String HTTP_CACHE_DIR="http";
	
	private static final int HTTP_CACHE_SIZE=1024*1024*10; //10MB
	private static final int IO_BUFFER_SIZE = 8 * 1024; //8KB
	
	private File mHttpCacheDir;
	private Object mHttpDiskCacheLock=new Object();
	private DiskLruCache mHttpDiskCache;
	private boolean mHttpDiskCacheStarting=true;
	private static final int DISK_CACHE_INDEX=0;
	
	/**
	 * Initialize providing a single target image size (used both width and height)
	 * @param context
	 * @param imageSize
	 */
	public ImageFetcher(Context context, int imageSize) {
		super(context, imageSize);
		// TODO Auto-generated constructor stub
		init(context);
	}

	/**
	 * Initialize providing a target image width and height for the processing images
	 * @param context
	 * @param imageWidth
	 * @param imageHeight
	 */
	public ImageFetcher(Context context, int imageWidth, int imageHeight){
		super(context, imageWidth, imageHeight);
		init(context);
	}
	
	private void init(Context context){
		checkConnection(context);
		mHttpCacheDir=ImageCache.getDiskCacheDir(context, HTTP_CACHE_DIR);
	}
	
	
	//各种Override不解释
	@Override
	protected void initDiskCacheInternal() {
		// TODO Auto-generated method stub
		super.initDiskCacheInternal();
		initHttpDiskCache();//在继承的基础上继续拓展
	}
	
	private void initHttpDiskCache(){
		if(!mHttpCacheDir.exists()){
			mHttpCacheDir.mkdirs();
		}
		synchronized(mHttpDiskCacheLock){
			if(ImageCache.getUsableSpace(mHttpCacheDir)>HTTP_CACHE_SIZE){
				try {
					mHttpDiskCache=DiskLruCache.open(mHttpCacheDir, 1, 1, HTTP_CACHE_SIZE);
					if(BuildConfig.DEBUG){
						Log.d(TAG, "HTTPDiskCache initialized");
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					mHttpDiskCache=null;
				}
			}
			mHttpDiskCacheStarting=false;
			mHttpDiskCacheLock.notifyAll();
		}

	}
	
	@Override
	protected void clearCacheInternal() {
		// TODO Auto-generated method stub
		super.clearCacheInternal();
		synchronized(mHttpDiskCacheLock){
			if(mHttpDiskCache!=null && !mHttpDiskCache.isClosed()){
				try {
					mHttpDiskCache.delete();
					if(BuildConfig.DEBUG){
						Log.d(TAG, "Http Cache Cleared!");
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					Log.e(TAG, "clearHttpDiskCacheInternal--"+e);
				}
				mHttpDiskCache=null;
				mHttpDiskCacheStarting=true;//reset the flag
				initHttpDiskCache();
			}
		}
	}
	
	@Override
	protected void flushCacheInternal() {
		// TODO Auto-generated method stub
		super.flushCacheInternal();
		synchronized(mHttpDiskCacheLock){
			if(mHttpDiskCache!=null){
				try {
					mHttpDiskCache.flush();
					if(BuildConfig.DEBUG){
						Log.d(TAG, "HttpDiskCache flushed");
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					Log.e(TAG, "HttpDiskCache flush---"+e);
				}
			}
		}
	}
	
	@Override
	protected void closeCacheInternal() {
		// TODO Auto-generated method stub
		super.closeCacheInternal();
		synchronized(mHttpDiskCacheLock){
			if(mHttpDiskCache!=null){
				try {
					if(!mHttpDiskCache.isClosed()){
						mHttpDiskCache.close();
						if(BuildConfig.DEBUG){
							Log.d(TAG, "HttpDiskCache closed");
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					Log.e(TAG, "HttpDiskCache close----"+e);
				}
			}
		}
	}
	
	@Override
	protected Bitmap processBitmap(Object data) {
		// TODO Auto-generated method stub
		return processBitmap(String.valueOf(data));
	}
	
	/**
	 * The main process method, which will be called by the ImageWorker in the BitmapWorkerTask background thread.
	 * @param data The data to load the Bitmap. In this case, it refers a regular HTTP URL.
	 * @return The downloaded and resized bitmap.
	 */
	private Bitmap processBitmap(String data){
		if(BuildConfig.DEBUG){
			Log.d(TAG, "processBitmap--"+data);
		}
		
		final String key=ImageCache.hashKeyForDisk(data);
		// FileDescriptor -> The main practical use for a file descriptor is to create a FileInputStream or
		// FileOutputStream to contain it.  Applications should not create their own file descriptor.
		FileDescriptor fileDescriptor=null;  //Wraps a Unix file descriptor
		FileInputStream fileInputStream=null;
		DiskLruCache.Snapshot snapshot;
		synchronized(mHttpDiskCacheLock){
			// wait for disk cache to initialize 
			// until then mHttpDiskCacheStarting turns false and mHttpDiskCacheLock.notifyAll() is invoked
			while(mHttpDiskCacheStarting){
				try {
					mHttpDiskCacheLock.wait();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
				}
			}
			
			if(mHttpDiskCache!=null){
				try {
					snapshot=mHttpDiskCache.get(key);
					if(snapshot==null){
						if(BuildConfig.DEBUG){
							Log.d(TAG, "processBitmap works,--not found in http cache, downloading~~");
						}
						DiskLruCache.Editor editor=mHttpDiskCache.edit(key);
						if(editor!=null){
							if(downloadUrlToStream(data, editor.newOutputStream(DISK_CACHE_INDEX))){
								editor.commit();
							}else{
								editor.abort();
							}
						}
						snapshot=mHttpDiskCache.get(key);     //reacquire snapshot
					}
					
					if(snapshot!=null){
						fileInputStream=(FileInputStream)snapshot.getInputStream(DISK_CACHE_INDEX);
						fileDescriptor=fileInputStream.getFD();
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					Log.e(TAG, "processBitmap--error"+e);
				} catch (IllegalStateException e){
					//Thrown when an action is attempted at a time when the VM is not in the correct state.
					Log.e(TAG, "processBitmap--error"+e);
				} finally{
					if(fileDescriptor==null&&fileInputStream!=null){
						try {
							fileInputStream.close();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}
			
		}
		
		Bitmap bitmap=null;
		if(fileDescriptor!=null){
			bitmap=decodeSampledBitmapFromDescriptor(fileDescriptor, mImageWidth, mImageHeight);
		}
		if(fileInputStream!=null){
			try {
				fileInputStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return bitmap;
	}
	
	/**
	 * Download a bitmap from a URL and write the content to a output stream.
	 * @param urlString The URL from which a bitmap will be fetched
	 * @param outputStream The output stream the bitmap content will be write to
	 * @return true if successful, false otherwise
	 */
	private boolean downloadUrlToStream(String urlString, OutputStream outputStream){
		disableConnectionReuseIfNecessary();
		HttpURLConnection urlConnection=null;
		BufferedInputStream in=null;
		BufferedOutputStream out=null;
		
		try {
			URL url=new URL(urlString);
				urlConnection=(HttpURLConnection)url.openConnection();
				in=new BufferedInputStream(urlConnection.getInputStream(), IO_BUFFER_SIZE);
				out=new BufferedOutputStream(outputStream, IO_BUFFER_SIZE);
				int b;
				while((b=in.read())!=-1){
					out.write(b);
				}
				return true;
		} catch (MalformedURLException e) {
			// Actually, MalformedURLException extends IOException
			Log.e(TAG, "downloadUrlToStream---urlString couldn't be parsed!");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Log.e(TAG, "IO Error in download bitmap--"+e);
		} finally{
			// Releases this connection so that its resources may be either reused or closed.
			if(urlConnection!=null){
				urlConnection.disconnect();
			}
			try {
				if(out!=null){
					out.close();
				}
				if(in!=null){
					in.close();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return false;
	}
	
	/**
	 * Work-around for bug prior to Froyo, see here for more info:
	 * http://android-developers.blogspot.com/2011/09/android-http-clients.html
	 */
	private void disableConnectionReuseIfNecessary(){
		// HTTP connection reuse which was buggy pre-froyo
		if(!Utils.hasFroyo()){
			System.setProperty("http.keepAlive", "false");
		}
	}
	
	/**
	 * Simple network connection check
	 * @param context
	 */
	private void checkConnection(Context context){
		final ConnectivityManager connManager=(ConnectivityManager)context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo=connManager.getActiveNetworkInfo();
		if(networkInfo==null||!networkInfo.isConnected()){
			Toast.makeText(context, R.string.no_network_connection_toast, Toast.LENGTH_LONG).show();
			Log.e(TAG, "checkConnectivity--No Connection Found!!");
		}
	}
			
}
