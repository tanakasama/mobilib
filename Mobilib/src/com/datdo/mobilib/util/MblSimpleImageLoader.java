package com.datdo.mobilib.util;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.util.LruCache;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.*;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.nineoldandroids.animation.ObjectAnimator;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import android.widget.ListView;

/**
 * <pre>
 * Simplified version of deprecated {@link com.datdo.mobilib.util.MblImageLoader}
 *
 * Smart loader to display images for child views in a {@link ViewGroup}.
 * Features of this loader:
 *   1. Load images sequentially.
 *   2. Automatically scale images to match sizes of {@link ImageView}.
 *   3. Cache images using {@link LruCache}.
 *   4. Only load images for currently displayed cells, which is very useful for {@link android.widget.ListView}.
 *   5. Fading animation when bitmap is loaded successfully.
 * Override abstract methods to customize this loader. Additional configurations are set via {@link com.datdo.mobilib.util.MblSimpleImageLoader.MblOptions}
 *
 * Sample code:
 *
 * {@code
 *  public class UserAdapter extends BaseAdapter {
 *
 *      private MblSimpleImageLoader<User> mUserAvatarLoader = new MblSimpleImageLoader<User>() {
 *
 *          protected User getItemBoundWithView(View view) {
 *              return (User) view.getTag();
 *          }
 *
 *          protected ImageView getImageViewBoundWithView(View view) {
 *              return (ImageView) view.findViewById(R.id.avatar_image_view);
 *          }
 *
 *          protected String getItemId(User user) {
 *              return user.getId();
 *          }
 *
 *          protected void retrieveImage(User user, final MblRetrieveImageCallback cb) {
 *              MblApi.get(item, null, null, Long.MAX_VALUE, true, new MblApiCallback() {
 *
 *                  public void onSuccess(int statusCode, byte[] data) {
 *                      cb.onRetrievedByteArray(data);
 *                  };
 *
 *                  public void onFailure(int error, String errorMessage) {
 *                      cb.onRetrievedError();
 *                  }
 *              }, null);
 *          }
 *
 *          protected void onError(ImageView imageView, T item) {
 *              imageView.setImageResource(R.drawable.default_avatar);
 *          }
 *     };
 *
 *     public View getView(int pos, View convertView, ViewGroup parent) {
 *
 *         // ...
 *
 *         view.setTag(user);
 *         mUserImageLoader.loadImage(view);
 *
 *         return view;
 *     }
 * }
 * }
 * </pre>
 * @param <T> class of object bound with an child views of {@link ViewGroup}
 */
public abstract class MblSimpleImageLoader<T> {

    /**
     * <pre>
     * Get data object bound with each child view.
     * </pre>
     */
    protected abstract T getItemBoundWithView(View view);

    /**
     * <pre>
     * Extract {@link ImageView} used to display image from each child view
     * </pre>
     */
    protected abstract ImageView getImageViewBoundWithView(View view);

    /**
     * <pre>
     * Specify an ID for each data object. The ID is used for caching so please make it unique throughout the app.
     * </pre>
     */
    protected abstract String getItemId(T item);

    /**
     * <pre>
     * Do your own image loading here (from HTTP/HTTPS, from file, etc...).
     * This method is always invoked in main thread. Therefore, it is strongly recommended to do the loading asynchronously.
     * </pre>
     * @param item
     * @param cb call method of this callback when you finished the loading
     */
    protected abstract void retrieveImage(T item, MblRetrieveImageCallback cb);

    /**
     * <pre>
     * Handle error when bitmap loading fails.
     * </pre>
     */
    protected abstract void onError(ImageView imageView, T item);

    /**
     * <pre>
     * Callback class for {@link #retrieveImage(Object, MblRetrieveImageCallback)}
     * Choose appropriate method to invoke when bitmap data is successfully loaded, or {@link #onRetrievedError()} when fail
     * </pre>
     */
    public static interface MblRetrieveImageCallback {
        public void onRetrievedByteArray(byte[] bmData);
        public void onRetrievedBitmap(Bitmap bm);
        public void onRetrievedFile(String path);
        public void onRetrievedError();
    }

    /**
     * Additional configurations for {@link MblSimpleImageLoader}
     */
    public static class MblOptions {

        /**
         * Interface to customize progress view which indicates image loading
         */
        public static interface MblProgressViewGenerator {
            public View generate();
        }

        private boolean mSerializeImageLoading  = true;
        private long    mDelayedDurationInParallelMode = 500;
        private boolean mEnableProgressView     = true;
        private boolean mEnableFadingAnimation  = true;
        private MblProgressViewGenerator mProgressViewGenerator;

        /**
         * Configure whether progress view is displayed to indicate that image is being loaded. Default TRUE.
         */
        public MblOptions setEnableProgressView(boolean enableProgressView) {
            mEnableProgressView = enableProgressView;
            return this;
        }

        /**
         * Configure whether fading animation is played when image is fully loaded and displayed to ImageView. Default TRUE.
         */
        public MblOptions setEnableFadingAnimation(boolean enableFadingAnimation) {
            mEnableFadingAnimation = enableFadingAnimation;
            return this;
        }

        /**
         * Configure the progress view to indicate image loading. Default NULL.
         */
        public MblOptions setProgressViewGenerator(MblProgressViewGenerator progressViewGenerator) {
            mProgressViewGenerator = progressViewGenerator;
            return this;
        }

        /**
         * Configure whether image is loaded one by one. Default TRUE.
         */
        public MblOptions setSerializeImageLoading(boolean serializeImageLoading) {
            mSerializeImageLoading = serializeImageLoading;
            return this;
        }

        /**
         * <pre>
         * If you call {@link #setSerializeImageLoading(boolean)} with FALSE parameter, image loader will load images in parallel mode, which means multiple images are loaded at the same time.
         * Anyway, parallel mode will cause a problem: if user want to see last views of a very long {@link ListView}, he needs to scroll very fast to bottom, then all views of {@link ListView} will be loaded, which is very bad for performance.
         * Therefore, in parallel mode, image loader does not load image for view right away, but wait for a delayed duration before starting it. When use scrolls very fast, views are not loaded because their data are replaced by new data before delayed duration expires.
         * This method is to customize the delayed duration, in millisecond. Default 500
         * </pre>
         */
        public MblOptions setDelayedDurationInParallelMode(long delayedDurationInParallelMode) {
            mDelayedDurationInParallelMode = delayedDurationInParallelMode;
            return this;
        }
    }

    private static final String TAG = MblUtils.getTag(MblImageLoader.class);
    private static final int FRAME_ID = 1430125134;

    private static LruCache<String, Bitmap> sBitmapCache;
    private static Set<String>              sKeySet             = Collections.synchronizedSet(new HashSet<String>());
    private static boolean                  sDoubleCacheSize    = false;

    private MblSerializer   mSerializer;
    private MblOptions      mOptions;
    private int             mProgressViewFrameWidth;
    private int             mProgressViewFrameHeight;

    public MblSimpleImageLoader() {

        // set default options
        mOptions = new MblOptions();

        // initialize bitmap cache
        if (sBitmapCache == null) {
            Context context = MblUtils.getCurrentContext();
            int cacheSize = 2 * 1024 * 1024; // 2MB;
            if (context != null) {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                int memoryClassBytes = am.getMemoryClass() * 1024 * 1024;
                cacheSize = memoryClassBytes / 8;
            }
            if (sDoubleCacheSize) {
                cacheSize = cacheSize * 2;
            }
            sBitmapCache = new LruCache<String, Bitmap>(cacheSize) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getRowBytes() * value.getHeight();
                }

                @Override
                protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                    super.entryRemoved(evicted, key, oldValue, newValue);
                    sKeySet.remove(key);
                }
            };
        }

        // initialize serializer
        mSerializer = new MblSerializer();
    }

    /**
     * <pre>
     * Double memory-cache 's size to increase number of bitmap being kept in memory.
     * Call this method before creating any instance.
     * </pre>
     */
    public static void doubleCacheSize() {
        if (sBitmapCache != null) {
            throw new RuntimeException("doubleCacheSize() must be called before first instance of this class being created");
        }
        sDoubleCacheSize = true;
    }

    /**
     * <pre>
     * Request loading for a child view.
     * The loading request is put into a queue and executed sequentially.
     * </pre>
     * @param view the child view for which you want to load image
     */
    public void loadImage(final View view) {

        // check if this method is executed on main thread
        if (!MblUtils.isMainThread()) {
            MblUtils.executeOnMainThread(new Runnable() {
                @Override
                public void run() {
                    loadImage(view);
                }
            });
            return;
        }

        // check if item is available
        final T item = getItemBoundWithView(view);
        if (item == null) {
            onError(getImageViewBoundWithView(view), getItemBoundWithView(view));
            return;
        }

        // check if ImageView is available
        final ImageView imageView = getImageViewBoundWithView(view);
        if (imageView == null) {
            onError(getImageViewBoundWithView(view), getItemBoundWithView(view));
            return;
        }

        // check if bitmap is in cache
        int w = getImageViewWidth(imageView);
        int h = getImageViewHeight(imageView);
        if (isValidSizes(w, h)) {
            String cacheKey = generateCacheKey(item, w, h);
            final Bitmap bm = sBitmapCache.get(cacheKey);
            if (isValidBitmap(bm)) {
                imageView.setImageBitmap(bm);
                hideProgressBar(imageView);
                return;
            }
        }

        imageView.setImageBitmap(null);
        showProgressBar(imageView);
        final MblSerializer.Task task = new MblSerializer.Task() {
            @Override
            public void run(final Runnable finishCallback) {

                // check if view is still bound with original item
                if (!isStillBound(view, item)) {
                    finishCallback.run();
                    return;
                }

                // check if we need to wait until ImageView is fully displayed
                int w = getImageViewWidth(imageView);
                int h = getImageViewHeight(imageView);
                if (!isValidSizes(w, h)) {
                    final Runnable[] timeoutAction = new Runnable[]{null};
                    final ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            MblUtils.removeOnGlobalLayoutListener(imageView, this);
                            MblUtils.getMainThreadHandler().removeCallbacks(timeoutAction[0]);
                            if (isStillBound(view, item)) {
                                loadImage(view);
                            }
                        }
                    };
                    timeoutAction[0] = new Runnable() {
                        @Override
                        public void run() {
                            MblUtils.removeOnGlobalLayoutListener(imageView, globalLayoutListener);
                            if (isStillBound(view, item)) {
                                loadImage(view);
                            }
                        }
                    };
                    imageView.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);
                    MblUtils.getMainThreadHandler().postDelayed(timeoutAction[0], 500l);

                    finishCallback.run();
                    return;
                }

                // check if bitmap is in cache
                final String cacheKey = generateCacheKey(item, w, h);
                Bitmap bm = sBitmapCache.get(cacheKey);
                if (isValidBitmap(bm)) {
                    imageView.setImageBitmap(bm);
                    hideProgressBar(imageView);
                    finishCallback.run();
                    return;
                }

                // load bitmap from server/file
                retrieveImage(item, new MblRetrieveImageCallback() {

                    private void onRetrieved(final Object data) {

                        if (!isStillBound(view, item)) {
                            finishCallback.run();
                            return;
                        }

                        MblUtils.executeOnAsyncThread(new Runnable() {
                            @Override
                            public void run() {

                                if (!isStillBound(view, item)) {
                                    finishCallback.run();
                                    return;
                                }

                                try {
                                    int w = getImageViewWidth(imageView);
                                    int h = getImageViewHeight(imageView);
                                    final Bitmap bm;
                                    if (data instanceof byte[]) {
                                        bm = MblUtils.loadBitmapMatchSpecifiedSize(w, h, (byte[]) data);
                                    } else if (data instanceof Bitmap) {
                                        bm = (Bitmap) data;
                                    } else if (data instanceof String) {
                                        bm = MblUtils.loadBitmapMatchSpecifiedSize(w, h, (String) data);
                                    } else {
                                        bm = null;
                                    }
                                    if (isValidBitmap(bm)) {
                                        sBitmapCache.put(cacheKey, bm);
                                        sKeySet.add(cacheKey);
                                        MblUtils.executeOnMainThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (isStillBound(view, item)) {
                                                    imageView.setImageBitmap(bm);
                                                    hideProgressBar(imageView);
                                                    animateImageView(imageView);
                                                }
                                                finishCallback.run();
                                            }
                                        });
                                    } else {
                                        onRetrievedError();
                                    }
                                } catch (OutOfMemoryError e) {
                                    Log.e(TAG, "OutOfMemoryError", e);

                                    // release 1/2 of cache size for memory
                                    sBitmapCache.trimToSize(sBitmapCache.size() / 2);
                                    System.gc();

                                    // error
                                    onRetrievedError();
                                } catch (Throwable t) {
                                    Log.e(TAG, "", t);
                                    onRetrievedError();
                                }
                            }
                        });
                    }

                    @Override
                    public void onRetrievedByteArray(final byte[] bmData) {
                        onRetrieved(bmData);
                    }

                    @Override
                    public void onRetrievedBitmap(Bitmap bm) {
                        onRetrieved(bm);
                    }

                    @Override
                    public void onRetrievedFile(String path) {
                        onRetrieved(path);
                    }

                    @Override
                    public void onRetrievedError() {
                        MblUtils.executeOnMainThread(new Runnable() {
                            @Override
                            public void run() {
                                if (isStillBound(view, item)) {
                                    hideProgressBar(imageView);
                                    onError(imageView, item);
                                }
                                finishCallback.run();
                            }
                        });
                    }
                });
            }
        };
        if (mOptions.mSerializeImageLoading) {
            mSerializer.run(task);
        } else {
            MblUtils.getMainThreadHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    task.run(new Runnable() {
                        @Override
                        public void run() {}
                    });
                }
            }, mOptions.mDelayedDurationInParallelMode);
        }
    }

    @SuppressWarnings("ResourceType")
    private void showProgressBar(ImageView imageView) {

        if (!mOptions.mEnableProgressView) {
            return;
        }

        // get parent view of ImageView
        ViewGroup parent = (ViewGroup) imageView.getParent();

        // check if we need to show progress bar
        if (parent == null || parent.getId() == FRAME_ID) {
            return;
        }

        // create Frame
        final FrameLayout frame = new FrameLayout(MblUtils.getCurrentContext());
        frame.setId(FRAME_ID);
        frame.setLayoutParams(imageView.getLayoutParams());

        // change ImageView
        imageView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        imageView.setVisibility(View.INVISIBLE);

        // add/remove views
        int index = parent.indexOfChild(imageView);
        parent.removeView(imageView);
        frame.addView(imageView);
        parent.addView(frame, index);

        // create ProgressBar and add it to frame
        final View[] progressView = new View[]{null};
        final Runnable addProgressView = new Runnable() {
            @Override
            public void run() {
                progressView[0] = getProgressView();
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        Math.min(MblUtils.pxFromDp(50), mProgressViewFrameWidth / 2),
                        Math.min(MblUtils.pxFromDp(50), mProgressViewFrameHeight / 2));
                lp.gravity = Gravity.CENTER;
                progressView[0].setLayoutParams(lp);
                frame.addView(progressView[0]);
            }
        };
        if (mProgressViewFrameWidth > 0 && mProgressViewFrameHeight > 0) {
            addProgressView.run();
        }
        OnGlobalLayoutListener listener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                MblUtils.removeOnGlobalLayoutListener(frame, this);
                frame.setTag(null);
                if (frame.getWidth() == 0 || frame.getHeight() == 0) {
                    return;
                }
                if (progressView[0] == null) {
                    mProgressViewFrameWidth = frame.getWidth();
                    mProgressViewFrameHeight = frame.getHeight();
                    addProgressView.run();
                } else if (mProgressViewFrameWidth != frame.getWidth() || mProgressViewFrameHeight != frame.getHeight()) {
                    mProgressViewFrameWidth = frame.getWidth();
                    mProgressViewFrameHeight = frame.getHeight();
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)progressView[0].getLayoutParams();
                    lp.width = Math.min(MblUtils.pxFromDp(50), mProgressViewFrameWidth / 2);
                    lp.height = Math.min(MblUtils.pxFromDp(50), mProgressViewFrameHeight / 2);
                    progressView[0].setLayoutParams(lp);
                }

            }
        };
        frame.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        frame.setTag(listener);
    }

    @SuppressWarnings("ResourceType")
    private void hideProgressBar(ImageView imageView) {

        if (!mOptions.mEnableProgressView) {
            return;
        }

        // get parent view of ImageView
        ViewGroup temp = (ViewGroup) imageView.getParent();

        // check if we need to hide progress bar
        if (temp == null || temp.getId() != FRAME_ID) {
            return;
        }

        // check if ImageView has valid sizes
        int w = getImageViewWidth(imageView);
        int h = getImageViewHeight(imageView);
        if (!isValidSizes(w, h)) {
            return;
        }

        // frame & parent
        FrameLayout frame = (FrameLayout) temp;
        if (frame.getTag() != null) {
            MblUtils.removeOnGlobalLayoutListener(frame, (OnGlobalLayoutListener)frame.getTag());
            frame.setTag(null);
        }
        ViewGroup parent = (ViewGroup) frame.getParent();

        // change ImageView
        imageView.setVisibility(View.VISIBLE);
        imageView.setLayoutParams(frame.getLayoutParams());

        // add/remove views
        int index = parent.indexOfChild(frame);
        frame.removeView(imageView);
        parent.removeView(frame);
        parent.addView(imageView, index);
    }

    private String generateCacheKey(T item, int w, int h) {
        String key = TextUtils.join("#", new Object[]{
                generateCacheKey(item),
                w,
                h
        });
        return key;
    }

    private String generateCacheKey(T item) {
        return generateCacheKey(item.getClass(), getItemId(item));
    }

    private String generateCacheKey(Class clazz, String id) {
        String key = TextUtils.join("#", new Object[]{
                clazz,
                id,
        });
        return key;
    }

    private int getImageViewWidth(ImageView imageView) {
        ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        if (lp.width == ViewGroup.LayoutParams.WRAP_CONTENT) {
            return -1; // do not care
        } else if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT){
            return imageView.getWidth(); // 0 or parent 's width
        } else {
            return lp.width; // specified width
        }
    }

    private int getImageViewHeight(ImageView imageView) {
        ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        if (lp.height == ViewGroup.LayoutParams.WRAP_CONTENT) {
            return -1; // do not care
        } else if (lp.height == ViewGroup.LayoutParams.MATCH_PARENT){
            return imageView.getHeight(); // 0 or parent 's height
        } else {
            return lp.height; // specified height
        }
    }

    private boolean isValidBitmap(Bitmap bm) {
        return bm != null && !bm.isRecycled() && bm.getWidth() != 0 && bm.getHeight() != 0;
    }

    private boolean isValidSizes(int w, int h) {
        return w != 0 || h != 0;
    }

    private boolean isStillBound(View view, T item) {
        return item == getItemBoundWithView(view);
    }

    private void animateImageView(ImageView imageView) {
        if (!mOptions.mEnableFadingAnimation) {
            return;
        }
        ObjectAnimator.ofFloat(imageView, "alpha", 0, 1)
                .setDuration(250)
                .start();
    }

    /**
     * <pre>
     * Remove bitmap of item from memory cache. Bitmap will be reloaded when it is required.
     * </pre>
     * @param item
     */
    public void invalidate(T item) {
        invalidate(item.getClass(), getItemId(item));
    }

    /**
     * Similar to {@link #invalidate(Object)}
     * @param clazz class of item
     * @param id id of item (similar to id retrieved by {@link #getItemId(Object)})
     */
    public void invalidate(final Class clazz, final String id) {
        MblUtils.executeOnMainThread(new Runnable() {
            @Override
            public void run() {
                String cacheKey = generateCacheKey(clazz, id);
                Set<String> keys = new HashSet<String>(sKeySet);
                for (String key : keys) {
                    if (key.startsWith(cacheKey)) {
                        sBitmapCache.remove(key);
                    }
                }
            }
        });
    }

    /**
     * Invalidate all item of specified class
     */
    public void invalidate(final Class clazz) {
        invalidate(clazz, "");
    }

    /**
     * Get options for this image loader.
     */
    public MblOptions getOptions() {
        return mOptions;
    }

    /**
     * Set options for this image loader.
     */
    public MblSimpleImageLoader setOptions(MblOptions options) {
        if (options != null) {
            mOptions = options;
        } else {
            mOptions = new MblOptions();
        }
        return this;
    }

    private View getProgressView() {
        if (mOptions.mProgressViewGenerator != null) {
            return mOptions.mProgressViewGenerator.generate();
        } else {
            ProgressBar progress = new ProgressBar(MblUtils.getCurrentContext());
            progress.setIndeterminate(true);
            return progress;
        }
    }
}
