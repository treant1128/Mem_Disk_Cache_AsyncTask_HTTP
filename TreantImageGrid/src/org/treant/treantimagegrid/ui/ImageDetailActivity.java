package org.treant.treantimagegrid.ui;

import org.treant.treantimagegrid.BuildConfig;
import org.treant.treantimagegrid.ImagesURL;
import org.treant.treantimagegrid.R;
import org.treant.treantimagegrid.util.ImageCache;
import org.treant.treantimagegrid.util.ImageFetcher;
import org.treant.treantimagegrid.util.Utils;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.NavUtils;
import android.support.v4.view.ViewPager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

public class ImageDetailActivity extends FragmentActivity implements OnClickListener{

	public static final String EXTRA_IMAGE="extra_image";
	private static final String IMAGE_CACHE_DIR="images";
	private static final int OFF_SCREEN_PAGE_LIMIT=2;
	
	private ImageFetcher mImageFetcher;
	private ImagePagerAdapter mAdapter;
	private ViewPager mPager;
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		if(BuildConfig.DEBUG){
			Utils.enableStrictMode();
		}
		super.onCreate(savedInstanceState);
		setContentView(R.layout.image_detail_pager);
		
		//Fetch screen parameters, use as max size when loading images as this activity runs full screen
		final DisplayMetrics displayMetrics=new DisplayMetrics();
		this.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
		final int screenWidth=displayMetrics.widthPixels;
		final int screenHeight=displayMetrics.heightPixels;
		// 使用最长边的一半For this simple sample we'll use half of the longest width to resize our images.  
		// As the image scaling ensures the image is large than this, we should be left with a resolution that is
		// appropriate for both portrait and landscape.  For best image quality we shouldn't divide by 2, but this
		// will use more memory and require a large memory cache.权衡图像质量与内存占用
		final int longest=(screenWidth>screenHeight?screenWidth:screenHeight)/2;
		ImageCache.ImageCacheParams cacheParams=new ImageCache.ImageCacheParams(this, IMAGE_CACHE_DIR);
		cacheParams.setMemCacheSizePercent(0.25f);
		
		//The ImageFetcher takes care of loading images into ImageView children asynchronously
		mImageFetcher=new ImageFetcher(this, longest);
		mImageFetcher.addImageCache(this.getSupportFragmentManager(), cacheParams);
		mImageFetcher.setImageFadeIn(false);
		
		// Set up ViewPager and backing adapter
		mAdapter=new ImagePagerAdapter(getSupportFragmentManager(),ImagesURL.imageUrls.length);
		mPager=(ViewPager)findViewById(R.id.pager);
		mPager.setAdapter(mAdapter);
		// Set the margin between pages.          (marginPixels->distance between adjacent pages in pixels)
		mPager.setPageMargin((int)getResources().getDimension(R.dimen.image_detail_pager_margin));
		// Set the number of pages that should be retained to either side of the current page in the view hierarchy
		// in an idle state. Pages beyond this limit will be recreated from the adapter when needed.
		// This is offered as an optimization. If you know in advance the number of pages you will need to support or
		// have lazy-loading mechanisms in place on your pages, tweaking this setting can have benefits in perceived
		// smoothness of paging animations and interaction. If you have a small number of pages(3~4) that you can keep active
		// all at once, less time will be spent in layout for newly created view subtrees as the user pages back and forth.
		// You should keep this limit low, especially if your pages have complex layouts. This setting defaults to 1.
		mPager.setOffscreenPageLimit(OFF_SCREEN_PAGE_LIMIT);
		// Set up activity to full screen
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		//getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
		// Enable some additional newer visibility and ActionBar features to create a more immersive photo viewing experience
		if(Utils.hasHoneycomb()){
			final ActionBar actionBar=getActionBar();if(actionBar==null){Log.e("null","actionBar-null");}
			// Hide title text and set home as up
			actionBar.setDisplayShowTitleEnabled(false);
			actionBar.setDisplayHomeAsUpEnabled(true);
			
			// Hide and show the ActionBar as the visibility changes
			// (ViewPager extends ViewGroup)  set a listener to receive callbacks when the visibility of the system bar changes
			
			// View.OnSystemUiVisibilityChangeListener-->Interface definition for a callback to be invoked when the status bar
			// change visibility. This reports global changes to the system UI state, not what the application is requesting.
			mPager.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener(){

				@Override
				public void onSystemUiVisibilityChange(int visibility) {
					// TODO Auto-generated method stub
					if((visibility & View.SYSTEM_UI_FLAG_LOW_PROFILE)!=0){
						actionBar.hide();
					}else{
						actionBar.show();
					}
				}
				
			});
			
			// Start low profile mode and hide ActionBar
			mPager.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
			actionBar.hide();
		}
		
		// Set the current item based on the extra passed in to this activity
		final int extraCurrentItem=this.getIntent().getIntExtra(EXTRA_IMAGE, -1);
		if(extraCurrentItem!=-1){
			mPager.setCurrentItem(extraCurrentItem);
		}
	}
	
	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		mImageFetcher.setExitTasksEarly(false);
	}
	
	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		mImageFetcher.setExitTasksEarly(true);
		mImageFetcher.flushCache();
	}
	
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		mImageFetcher.closeCache();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// TODO Auto-generated method stub
		MenuInflater menuInflater=getMenuInflater();
		menuInflater.inflate(R.menu.main_menu, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// TODO Auto-generated method stub
		switch(item.getItemId()){
		case R.id.clear_cache:
			mImageFetcher.clearCache();
			Toast.makeText(this,R.string.clear_cache_complete_toast, Toast.LENGTH_LONG).show();
			return true;
		case android.R.id.home:
			NavUtils.navigateUpFromSameTask(this);
			return true;
			default:
				break;
		}
		return super.onOptionsItemSelected(item);
	}
	
	/**
	 * The main adapter that backs the ViewPager. A subclass of FragmentStatePagerAdapter
	 * as there could be a large number of items in the ViewPager and we don't want to retain
	 * all in the memory at once but create/destroy them on the fly.
	 * @author Administrator
	 *
	 */
	private class ImagePagerAdapter extends FragmentStatePagerAdapter{

		private int mSize;
		
		public ImagePagerAdapter(FragmentManager fm, int size) {
			super(fm);
			// TODO Auto-generated constructor stub
			mSize=size;
		}

		@Override
		public Fragment getItem(int position) {
			// TODO Auto-generated method stub
			return ImageDetailFragment.newInstance(ImagesURL.imageUrls[position]);
		}

		@Override
		public int getCount() {
			// TODO Auto-generated method stub
			return mSize;
		}
		
	}
	
	/**
	 * Set on the ImageView in the ViewPager child fragments, to enable/disable low profile mode when the ImageView is touched 
	 */
	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		final int visibility=mPager.getSystemUiVisibility();
		if((visibility&View.SYSTEM_UI_FLAG_LOW_PROFILE)!=0){
			mPager.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
		}else{
			mPager.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
		}
	}
	
	/**
	 * Called by the ViewPager child fragments to load image via the one ImageFetcher
	 * @return
	 */
	public ImageFetcher getImageFetcher(){
		return this.mImageFetcher;
	}
	
}
