package org.treant.treantimagegrid.ui;

import org.treant.treantimagegrid.R;
import org.treant.treantimagegrid.util.ImageFetcher;
import org.treant.treantimagegrid.util.ImageWorker;
import org.treant.treantimagegrid.util.Utils;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

public class ImageDetailFragment extends Fragment {

	private static final String EXTRA_IMAGE_DATA="extra_image_data";
	
	private String mImageUrl;
	private ImageView mImageView;
	private ImageFetcher mImageFetcher;
	
	/**
	 * Empty constructor as per Fragment documentation
	 */
	public ImageDetailFragment(){
		
	}
	
	/**
	 * Factory method to generate a new instance of the fragment given an image number.
	 * @param imageUrl The image url to load
	 * @return A new instance of ImageDetailFragment with a imageNum extras
	 */
	public static ImageDetailFragment newInstance(String imageUrl){
		final ImageDetailFragment fragment=new ImageDetailFragment();
		final Bundle args=new Bundle();
		args.putString(EXTRA_IMAGE_DATA, imageUrl);
		fragment.setArguments(args);
		return fragment;
	}
	
	/**
	 * Populate image using a url from extras, use the convenience factory method to create this fragment
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		mImageUrl=getArguments()!=null?getArguments().getString(EXTRA_IMAGE_DATA):null;
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		final View v=inflater.inflate(R.layout.image_detail_fragment, container, false);
		mImageView=(ImageView)v.findViewById(R.id.imageView);
		return v;
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		// Use the parent activity to load the image asynchronously into the ImageView
		// so a single cache can be used over all pages in the ViewPager
		if(ImageDetailActivity.class.isInstance(getActivity())){
			mImageFetcher=((ImageDetailActivity)getActivity()).getImageFetcher();
			mImageFetcher.loadImage(mImageUrl, mImageView);
		}
		// Pass clicks on the ImageView to the parent activity to handle
		if(View.OnClickListener.class.isInstance(getActivity())&&Utils.hasHoneycomb()){
			mImageView.setOnClickListener((View.OnClickListener)getActivity());
		}
		
		super.onActivityCreated(savedInstanceState);
	}
	
	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		if(mImageView!=null){
			// Cancel any pending image work
			ImageWorker.cancelWork(mImageView);
			mImageView.setImageDrawable(null);
		}
	}
}
