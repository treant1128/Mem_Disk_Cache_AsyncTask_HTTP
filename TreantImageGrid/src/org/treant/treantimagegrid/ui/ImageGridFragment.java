package org.treant.treantimagegrid.ui;

import org.treant.treantimagegrid.BuildConfig;
import org.treant.treantimagegrid.ImagesURL;
import org.treant.treantimagegrid.R;
import org.treant.treantimagegrid.util.ImageCache.ImageCacheParams;
import org.treant.treantimagegrid.util.ImageFetcher;
import org.treant.treantimagegrid.util.Utils;

import android.annotation.TargetApi;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * The main fragment that powers the ImageGridActivity screen. //用来填充ImageGridActivity屏幕的主要Fragment
 * Fairly straight forward GridView implementation with the key addition being the ImageWorker class.//相当直接实用GridView
 * ImageCache to load children asynchronously, keeping the UI nice and smooth and caching thumbnails for quick retrieval.
 * The cache is retained over configuration change like orientation change so the images are populated quickly if, for example.
 * the user rotates the device.
 * 缓存可被越过Configuration Change比如Orientation Change)而保持,因此图像被迅速填充,比如当用户旋转设备时.
 * @author Administrator
 *
 */
public class ImageGridFragment extends Fragment implements AdapterView.OnItemClickListener {

	private static final String TAG="ImageGridActivity";
	private static final String IMAGE_CACHE_DIR="thumbs";
	
	private int mImageThumbSize;
	private int mImageThumbSpacing;
	private ImageAdapter mAdapter;
	private ImageFetcher mImageFetcher;
	
	public ImageGridFragment(){  // Empty 
		
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		// Report that this fragment would like to participate in populating the options menu by
		// receiving a call to onCreateOptionsMenu(Menu MenuInflater) and related methods.
		setHasOptionsMenu(true);//表明Fragment可以参与OptionMenu的回调
		
		mImageThumbSize=getResources().getDimensionPixelSize(R.dimen.image_thumbnail_size);//100dp
		mImageThumbSpacing=getResources().getDimensionPixelSize(R.dimen.image_thumbnail_spacing);//1dp
		
		mAdapter=new ImageAdapter(getActivity());
		
		ImageCacheParams cacheParams=new ImageCacheParams(getActivity(), IMAGE_CACHE_DIR);
		
		cacheParams.setMemCacheSizePercent(0.25f);
	
		mImageFetcher=new ImageFetcher(getActivity(), mImageThumbSize);
		mImageFetcher.setLoadingImage(R.drawable.empty_photo);
		// getSupportFragmentManager()-->Return the FragmentManager for interacting with fragments associated with this activity
		mImageFetcher.addImageCache(getActivity().getSupportFragmentManager(), cacheParams);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		final View v=inflater.inflate(R.layout.image_grid_fragment, container, false);
		final GridView mGridView=(GridView)v.findViewById(R.id.gridView);
		mGridView.setAdapter(mAdapter);
		mGridView.setOnItemClickListener(this);
		mGridView.setOnScrollListener(new AbsListView.OnScrollListener(){

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem,
					int visibleItemCount, int totalItemCount) {
				// TODO Auto-generated method stub
				//Callback method to be invoked when the list or grid has been scrolled. 
				//This will be called after the scroll has completed
			}

			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
				// TODO Auto-generated method stub
				// Pause fetcher to ensure smoother scrolling when flinging
				if(scrollState==AbsListView.OnScrollListener.SCROLL_STATE_FLING){
					mImageFetcher.setPauseWork(true);
				}else{
					mImageFetcher.setPauseWork(false);
				}
			}
			
		});
		/**
		 * This listener is used to get the final width of the GridView and then calculate the number of columns
		 * and the width of each column. The width of each column is variable as the GridView has stretchMode=columnWidth.
		 * The column width is used to set the height of each view so we get nice square thumnnails.
		 * 用来获取GridView的最终宽度,然后计算列数和每一列宽度.每列宽度是可变的因为stretchMode=columnWidth,列宽用来当高度-->方形缩略图
		 */
		mGridView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){
//onGlobalLayout()--> Callback method to be invoked when the global layout state or visibility of views within the view tree changes
			@Override
			public void onGlobalLayout() {
				// TODO Auto-generated method stub
				if(mAdapter.getNumColumns()==0){
					final int numColumns=(int)Math.floor(mGridView.getWidth()/(mImageThumbSize+mImageThumbSpacing));//向下取整
					if(numColumns>0){
						final int columnWidth=(mGridView.getWidth()/numColumns)-mImageThumbSpacing;
						mAdapter.setNumColumns(numColumns);
						mAdapter.setItemHeight(columnWidth);
						if(BuildConfig.DEBUG){
							Log.d(TAG, "onCreateView->numColumns set to "+numColumns);
						}
					}
				}
			}
			
		});
		return v;
	}
	// The following three methods are generally tied to Activity.onResume/onPause/onDestroy of the containing Activity's life-cycle
	@Override
	public void onResume() {
		// Called when the Fragment is visible to the user and actively running. 
		super.onResume();
		mImageFetcher.setExitTasksEarly(false);
		mAdapter.notifyDataSetChanged();
	}
	@Override
	public void onPause() {
		// Called when the Fragment is no longer resumed.
		super.onPause();
		mImageFetcher.setExitTasksEarly(true);
		mImageFetcher.flushCache();
	}
	@Override
	public void onDestroy() {
		// Called when the Fragment is no longer in use.
		super.onDestroy();
		mImageFetcher.closeCache();
	}
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		// TODO Auto-generated method stub
		Intent intent=new Intent(getActivity(), ImageDetailActivity.class);
		intent.putExtra(ImageDetailActivity.EXTRA_IMAGE, (int)id);
		if(Utils.hasJellyBean()){
			//makeThumbnailScaleUpAnimation() looks kind of ugly here as loading spinner may show plus 
			//the thumbnail image in GridView is cropped. So using makeScaleUpAnimation instead.
			ActivityOptions options=
					ActivityOptions.makeScaleUpAnimation(view, 0, 0, view.getWidth(), view.getHeight());
			getActivity().startActivity(intent, options.toBundle());
		}else{
			startActivity(intent);
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		// TODO Auto-generated method stub
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.main_menu, menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// TODO Auto-generated method stub
		switch(item.getItemId()){
		case R.id.clear_cache:
			mImageFetcher.clearCache();
			Toast.makeText(getActivity(),R.string.clear_cache_complete_toast, Toast.LENGTH_LONG).show();
			return true;
		default :
			break;
		}
		return super.onOptionsItemSelected(item);
	}
	/**
	 * The main adapter that backs the GridView.
	 * This is fairly standard except the number of columns in the GridView is used to create a fake
	 * top row if empty views as we use a transparent ActionBar and don't want the real top row of images
	 * to start off covered by it.
	 * @author Administrator
	 *
	 */
	public class ImageAdapter extends BaseAdapter{

		private final Context mContext;
		private int mNumColumns=0;
		private int mItemHeight=0;
		private int mActionBarHeight=0;
		private GridView.LayoutParams mImageViewLayoutParams;
		
		public ImageAdapter(Context context){
			super();
			this.mContext=context;
			mImageViewLayoutParams=new GridView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
			// Calculate the height of the ActionBar
			TypedValue tv=new TypedValue();
			if(context.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)){
				mActionBarHeight=TypedValue.complexToDimensionPixelSize(tv.data, context.getResources().getDisplayMetrics());
			}
		}
		@Override
		public int getCount() {
			// Size + number of columns for top empty row
			return ImagesURL.imageThumbUrls.length+mNumColumns;
		}

		@Override
		public Object getItem(int position) {
			// 整体往后推移mNumColumns个位置
			return position<mNumColumns?null:ImagesURL.imageThumbUrls[position-mNumColumns];
		}

		@Override
		public long getItemId(int position) {
			// TODO Auto-generated method stub
			return position<mNumColumns?0:position-mNumColumns;
		}

		@Override
		public int getViewTypeCount() {
			// Returns the number of types of Views that will be created by getView(int, View, ViewGroup).
			// Each type represents a set of views that can be converted in getView(int, View, ViewGroup).
			// If the adapter always returns the same type of View for all items, this method should return 1.
			// This method will only be called when the adapter is set on the AdapterView.
			return 2;//One is the normal ImageView, the other refers to the top row of empty views
		}
		
		@Override
		public int getItemViewType(int position) {
			// Get the View that will be created by getView(int, View, ViewGroup).
			// Returns an integer(must in the range of 0 to getViewTypeCount()-1. IGNORE_ITEM_VIEW_TYPE can also be returned)
			// representing the type of View. Two views should share the same type if one can be converted to the other in  getView(int, View, ViewGroup)
			return position<mNumColumns?1:0;
		}
		@Override
		public boolean hasStableIds() {
			// Indicates whether the item ids are stable across changes to the underlying data.
			return true;
		}
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			// TODO Auto-generated method stub
			if(position<mNumColumns){
				if(convertView==null){
					convertView=new View(mContext);
				}
				// Set empty view with height of ActionBar
				convertView.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mActionBarHeight));
				return convertView;
			}
			// Now handle the main ImageView thumbnails
			ImageView imageView;
			if(convertView==null){   // if it's not recycled, instantiate and initialize
				imageView=new ImageView(mContext);
				imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
				imageView.setLayoutParams(mImageViewLayoutParams);
			}else{ // Otherwise re-use the converted view
				imageView=(ImageView)convertView;
			}
			
			// Check the height matches our calculate column width
			if(imageView.getLayoutParams().height!=mItemHeight){
				imageView.setLayoutParams(mImageViewLayoutParams);
			}
			
			// Finally load the image asynchronously into the ImageView, this also take
			// care of a placeholder image while the background thread running.
			mImageFetcher.loadImage(ImagesURL.imageThumbUrls[position-mNumColumns], imageView);
			return imageView;
		}
		
		/**
		 * Sets the item height. Useful for when we know the column width so the height can be set to match
		 * @param height
		 */
		public void setItemHeight(int height){
			if(this.mItemHeight==height){
				return;
			}
			this.mItemHeight=height;
			mImageViewLayoutParams=new GridView.LayoutParams(LayoutParams.MATCH_PARENT, mItemHeight);
			mImageFetcher.setImageSize(height);
			notifyDataSetChanged();
		}
		
		public void setNumColumns(int numColumns){
			this.mNumColumns=numColumns;
		}
		
		public int getNumColumns(){
			return this.mNumColumns;
		}
		
	}
	//////////////////No Use  Just For Fun//////////////////
	class DaSiDingGou implements Adapter{

		@Override
		public int getCount() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public Object getItem(int position) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public long getItemId(int position) {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public int getItemViewType(int position) {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int getViewTypeCount() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public boolean hasStableIds() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isEmpty() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void registerDataSetObserver(DataSetObserver observer) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void unregisterDataSetObserver(DataSetObserver observer) {
			// TODO Auto-generated method stub
			
		}
		
	}
}
