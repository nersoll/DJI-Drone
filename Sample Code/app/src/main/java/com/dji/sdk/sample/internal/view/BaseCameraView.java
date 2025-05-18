package com.dji.sdk.sample.internal.view;

import android.app.Service;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.widget.FrameLayout;
import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.onnxrun.OnnxDetector;

import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;

/**
 * This class is designed for showing the camera video feed from the camera.
 */
public class BaseCameraView extends FrameLayout implements TextureView.SurfaceTextureListener {


    private Handler frameHandler = new Handler();
    private Runnable frameRunnable;
    private OnnxDetector detector;
    private boolean isFrameCaptureEnabled = false;

    private VideoFeeder.VideoDataListener videoDataListener = null;
    private DJICodecManager codecManager = null;

    public BaseCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);

        initUI();
    }

    private void initUI() {
        LayoutInflater layoutInflater = (LayoutInflater) getContext().getSystemService(Service.LAYOUT_INFLATER_SERVICE);

        layoutInflater.inflate(R.layout.view_fpv_and_camera_display, this, true);

        Log.v("TAG", "Start to test");

        TextureView mVideoSurface = (TextureView) findViewById(R.id.texture_video_previewer_surface);

        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(this);

            // This callback is for

            videoDataListener = new VideoFeeder.VideoDataListener() {
                @Override
                public void onReceive(byte[] bytes, int size) {
                    if (null != codecManager) {
                        codecManager.sendDataToDecoder(bytes, size);
                    }
                }
            };
        }

        initSDKCallback();
    }

    public interface OnFrameAvailableListener {
        void onFrameAvailable(Bitmap bitmap);
    }

    private OnFrameAvailableListener frameListener;

    public void setOnFrameAvailableListener(OnFrameAvailableListener listener) {
        this.frameListener = listener;
    }

    public void stopFrameCapture() {
        isFrameCaptureEnabled = false;
        frameHandler.removeCallbacks(frameRunnable);
    }

    public void startFrameCapture() {
        isFrameCaptureEnabled = true;

        frameRunnable = new Runnable() {
            @Override
            public void run() {
                TextureView mVideoSurface = findViewById(R.id.texture_video_previewer_surface);
                if (mVideoSurface != null && mVideoSurface.isAvailable()) {
                    Bitmap bitmap = mVideoSurface.getBitmap();
                    if (bitmap != null && frameListener != null) {
                        frameListener.onFrameAvailable(bitmap);
                    }
                }
                frameHandler.postDelayed(this, 2000);
            }
        };

        frameHandler.post(frameRunnable);
    }


    private void initSDKCallback() {
        try {
            VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(videoDataListener);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (codecManager == null) {
            codecManager = new DJICodecManager(getContext(), surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (codecManager != null) {
            codecManager.cleanSurface();
            codecManager = null;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }
}
