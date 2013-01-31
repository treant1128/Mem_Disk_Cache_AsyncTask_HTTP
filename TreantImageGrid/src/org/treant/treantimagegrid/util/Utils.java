package org.treant.treantimagegrid.util;

import org.treant.treantimagegrid.ui.ImageDetailActivity;
import org.treant.treantimagegrid.ui.ImageGridActivity;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.StrictMode;

public class Utils {

	private Utils() {

	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static void enableStrictMode() {
		if (Utils.hasGingerbread()) { // public final class StrictMode 是在Level 9
										// 之后增加的
			StrictMode.ThreadPolicy.Builder threadPolicyBuilder = new StrictMode.ThreadPolicy.Builder()
					.detectAll().penaltyLog();
			StrictMode.VmPolicy.Builder vmPolicyBuilder = new StrictMode.VmPolicy.Builder()
					.detectAll().penaltyLog();
			if (Utils.hasHoneycomb()) {  // Add in API Level 11
				threadPolicyBuilder.penaltyFlashScreen();
				// Set an upper bound on how many instances of a class can be in
				// memory at once. Helps to prevent object leaks.
				vmPolicyBuilder
					.setClassInstanceLimit(ImageGridActivity.class,1)
					.setClassInstanceLimit(ImageDetailActivity.class, 1);
			}
			StrictMode.setThreadPolicy(threadPolicyBuilder.build());
			StrictMode.setVmPolicy(vmPolicyBuilder.build());
		}
	}

	/**
	 * Can use static final constants like FROYO, declared in later versions of
	 * the OS Since they are inlined at compile time, This is guaranteed behavior.
	 * 
	 * @return
	 */
	public static boolean hasFroyo() {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO;
	}

	public static boolean hasGingerbread() {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
	}

	public static boolean hasHoneycomb() {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
	}

	public static boolean hasHoneycombMR1() {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1;
	}

	public static boolean hasJellyBean() {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
	}
}
