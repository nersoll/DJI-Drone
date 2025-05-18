package com.dji.sdk.sample.demo.missionoperator;


import static com.google.android.gms.internal.zzahn.runOnUiThread;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.TextureView;
import android.view.View;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.demo.missionmanager.MissionBaseView;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.onnxrun.BoundingBoxOverlayView;
import com.dji.sdk.sample.internal.utils.ToastUtils;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.simulator.InitializationData;

import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointAction;
import dji.common.mission.waypoint.WaypointActionType;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionGotoWaypointMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionState;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.mission.waypoint.WaypointTurnMode;
import dji.common.model.LocationCoordinate2D;
import dji.common.util.CommonCallbacks;

import dji.sdk.base.BaseProduct;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.flightcontroller.FlightController;

import dji.sdk.mission.MissionControl;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;

import com.dji.sdk.sample.internal.onnxrun.OnnxDetector;
import com.dji.sdk.sample.internal.view.BaseCameraView;

import android.graphics.*;
import android.os.Bundle;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.Handler;
import android.os.Looper;
public class WaypointMissionOperatorView extends MissionBaseView {

    public static final String TAG = "WaypointMissionOperator";
    private static final double BASE_LATITUDE = 22;
    private static final double BASE_LONGITUDE = 113;
    private static final int REFRESH_FREQ = 10;
    private static final int SATELLITE_COUNT = 10;
    private static final int MAX_HEIGHT = 500;
    private static final int MAX_RADIUS = 500;
    private static final double ONE_METER_OFFSET = 0.00000899322;
    private static final double HORIZONTAL_DISTANCE = 30;
    private static final double VERTICAL_DISTANCE = 30;
    private static final int WAYPOINT_COUNT = 4;


    private WaypointMissionOperator waypointMissionOperator = null;
    private FlightController flightController = null;
    private WaypointMission mission = null;
    private WaypointMissionOperatorListener listener;
    private float calculateTotalTime = 0.0f;

    private TextureView videoTextureView;
    private Handler frameCaptureHandler = new Handler(Looper.getMainLooper());
    private Runnable frameCaptureRunnable;
    private OnnxDetector detector;
    private DJICodecManager codecManager;
    private VideoFeeder.VideoDataListener videoDataListener;
    private final ExecutorService inferenceExecutor = Executors.newSingleThreadExecutor();


    public WaypointMissionOperatorView(Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

    }



    @Override
    public void onClick(View view) {
        waypointMissionOperator = getWaypointMissionOperator();



        switch(view.getId()) {
            case R.id.btn_simulator:
                startSimulator();
                break;
            case R.id.btn_set_maximum_altitude:
                if (null != getFlightController()) {
                    flightController.setMaxFlightHeight(MAX_HEIGHT, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            ToastUtils.setResultToToast(djiError == null ? "The maximum height is set to 500m!" : djiError.getDescription());
                        }
                    });
                }
                break;
            case R.id.btn_set_maximum_radius:
                if (null != getFlightController()) {
                    flightController.setMaxFlightRadius(MAX_RADIUS, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            ToastUtils.setResultToToast(djiError == null ? "The maximum radius is set to 500m!" : djiError.getDescription());
                        }
                    });
                }
                break;
            case R.id.btn_load:


                if (mission != null) {
                    DJIError djiError = waypointMissionOperator.loadMission(mission);
                    if (djiError == null) {
                        ToastUtils.setResultToToast("Mission is loaded successfully, estimated execution time is " + calculateTotalTime + " seconds.");
                    } else {
                        ToastUtils.setResultToToast(djiError.getDescription());
                    }
                }
                if (WaypointMissionState.READY_TO_RETRY_UPLOAD.equals(waypointMissionOperator.getCurrentState()) || WaypointMissionState.READY_TO_UPLOAD.equals(waypointMissionOperator.getCurrentState())) {
                waypointMissionOperator.uploadMission(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        ToastUtils.setResultToToast(djiError != null ? djiError.getDescription() : "upload success");
                    }
                });

                } else {
                ToastUtils.setResultToToast("Wait for mission to be loaded");

                }
                if (null != mission) {
                    startWaypointMissionWithTakeoffIfNeeded();
                } else {
                    ToastUtils.setResultToToast("Wait for mission to be uploaded");
                }
                break;
            case R.id.btn_upload:

                break;
            case R.id.btn_start:

                break;
            case R.id.btn_stop:
                waypointMissionOperator.stopMission(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        ToastUtils.setResultToToast(djiError != null ? "" : djiError.getDescription());
                    }
                });
                break;
            case R.id.btn_pause:
                waypointMissionOperator.pauseMission(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        ToastUtils.setResultToToast(djiError == null ? "The mission has been paused" : djiError.getDescription());
                    }
                });
                break;
            case R.id.btn_resume:
                waypointMissionOperator.resumeMission(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        ToastUtils.setResultToToast(djiError == null ? "The mission has been resumed" : djiError.getDescription());
                    }
                });
                break;
            case R.id.btn_download:
                if (WaypointMissionState.EXECUTING.equals(waypointMissionOperator.getCurrentState()) || WaypointMissionState.EXECUTION_PAUSED.equals(waypointMissionOperator.getCurrentState())) {
                    waypointMissionOperator.downloadMission(new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            ToastUtils.setResultToToast(djiError != null ? "" : djiError.getDescription());
                        }
                    });
                } else {
                    ToastUtils.setResultToToast("Mission can be downloaded when the mission state is EXECUTING or EXECUTION_PAUSED!");
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        BaseCameraView cameraView = findViewById(R.id.base_camera_view);
        if (cameraView != null) {
            cameraView.stopFrameCapture();
        }

        frameCaptureHandler.removeCallbacks(frameCaptureRunnable);

        if (videoDataListener != null) {
            VideoFeeder.getInstance().getPrimaryVideoFeed().removeVideoDataListener(videoDataListener);
            videoDataListener = null;
        }

        if (codecManager != null) {
            codecManager.cleanSurface();
            codecManager = null;
        }

        inferenceExecutor.shutdownNow();
        try {
            detector = new OnnxDetector(getContext(), "model_n.onnx");
        } catch (Exception e) {
            Log.e(TAG, "Ошибка инициализации модели: ", e);
        }
        BaseProduct product = DJISampleApplication.getProductInstance();

        if (product == null || !product.isConnected()) {
            ToastUtils.setResultToToast("Disconnect");
            return;
        } else {
            if (product instanceof Aircraft) {
                flightController = ((Aircraft) product).getFlightController();
            }
            if (flightController != null) {
                flightController.setStateCallback(new FlightControllerState.Callback() {
                    @Override
                    public void onUpdate(@NonNull FlightControllerState flightControllerState) {
                        homeLatitude = flightControllerState.getHomeLocation().getLatitude();
                        homeLongitude = flightControllerState.getHomeLocation().getLongitude();
                        flightState = flightControllerState.getFlightMode();

                        if (flightControllerState.isLandingConfirmationNeeded()) {
                            flightController.confirmLanding(new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    ToastUtils.setResultToToast(djiError == null ? "confirmLanding OK" : djiError.getDescription());
                                }
                            });
                        }

                        updateWaypointMissionState();
                    }
                });
            }
        }
        if (cameraView != null) {
            cameraView.setOnFrameAvailableListener(bitmap -> {
                inferenceExecutor.execute(() -> {
                    Log.i(TAG, "AI attempt to analyze image");
                    try {
                        if (detector != null) {
                            List<float[]> boxes = detector.runModel(bitmap);
                            List<RectF> overlayBoxes = new ArrayList<>();
                            for (float[] box : boxes) {
                                Log.i(TAG, String.format("Class: %d | Conf: %.2f | Center: [%.1f, %.1f] | Size: [%.1f, %.1f]",
                                        (int) box[5], box[4], box[0], box[1], box[2], box[3]));
                                float cx = box[0];  // центр x
                                float cy = box[1];  // центр y
                                float w = box[2];
                                float h = box[3];

                                float left = cx - w / 2;
                                float top = cy - h / 2;
                                float right = cx + w / 2;
                                float bottom = cy + h / 2;

                                overlayBoxes.add(new RectF(left, top, right, bottom));

                            }
                            BoundingBoxOverlayView overlay = findViewById(R.id.overlay_view);
                            runOnUiThread(() -> overlay.updateBoxes(overlayBoxes));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Ошибка инференса", e);
                    } finally {
                        bitmap.recycle();
                    }
                });
            });

            cameraView.startFrameCapture();
        }
        setUpListener();
    }

    private Bitmap loadImageFromAssets(String filename) throws Exception {
        InputStream is = getContext().getAssets().open(filename);
        return BitmapFactory.decodeStream(is);
    }


    @Override
    protected void onDetachedFromWindow() {
        BaseCameraView cameraView = findViewById(R.id.base_camera_view);
        if (cameraView != null) {
            cameraView.stopFrameCapture();
        }
        inferenceExecutor.shutdownNow();
        super.onDetachedFromWindow();

    }


    private WaypointMission createWaypointMissionFromCoordinates(List<LocationCoordinate2D> coordinates) {
        if (coordinates == null || coordinates.size() < 2) {
            ToastUtils.setResultToToast("Not enough coordinates to create a mission.");
            return null;
        }

        WaypointMission.Builder builder = new WaypointMission.Builder();

        // Настройка параметров миссии
        builder.autoFlightSpeed(15.0f)
                .maxFlightSpeed(15.0f)
                .setExitMissionOnRCSignalLostEnabled(true)
                .finishedAction(WaypointMissionFinishedAction.NO_ACTION)
                .headingMode(WaypointMissionHeadingMode.AUTO)
                .gotoFirstWaypointMode(WaypointMissionGotoWaypointMode.SAFELY)
                .flightPathMode(WaypointMissionFlightPathMode.NORMAL)
                .repeatTimes(1);

        // Начальная высота
        float altitude = 30f;

        for (int i = 0; i < coordinates.size(); i++) {
            LocationCoordinate2D coord = coordinates.get(i);
            Waypoint waypoint = new Waypoint(coord.getLatitude(), coord.getLongitude(), altitude);
            waypoint.turnMode = WaypointTurnMode.CLOCKWISE;

            if (i == 0) {
                // На первой точке опустить камеру
                waypoint.addAction(new WaypointAction(WaypointActionType.GIMBAL_PITCH, -90));
            }
            builder.addWaypoint(waypoint);
        }

        calculateTotalTime = builder.calculateTotalTime();
        return builder.build();
    }

    private void startWaypointMissionWithTakeoffIfNeeded() {
        if (flightController == null) {
            ToastUtils.setResultToToast("FlightController is not available");
            return;
        }

        FlightControllerState state = flightController.getState();

        if (!state.isFlying()) {
            // Дрон на земле — взлетаем
            flightController.startTakeoff(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        ToastUtils.setResultToToast("Takeoff successful. Starting mission...");
                        startMission();
                    } else {
                        ToastUtils.setResultToToast("Takeoff failed: " + djiError.getDescription());
                    }
                }
            });
        } else {
            // Уже в полёте — сразу запускаем
            startMission();
        }
    }

    private void startMission() {
        waypointMissionOperator.startMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                ToastUtils.setResultToToast(djiError == null ? "Mission started" : djiError.getDescription());
            }
        });
    }



    private void updateWaypointMissionState(){
        if (waypointMissionOperator != null && waypointMissionOperator.getCurrentState() != null) {
            ToastUtils.setResultToText(FCPushInfoTV,
                    "home point latitude: "
                            + homeLatitude
                            + "\nhome point longitude: "
                            + homeLongitude
                            + "\nFlight state: "
                            + flightState.name()
                            + "\nCurrent Waypointmission state : "
                            + waypointMissionOperator.getCurrentState().getName());
        } else {
            ToastUtils.setResultToText(FCPushInfoTV,
                    "home point latitude: "
                            + homeLatitude
                            + "\nhome point longitude: "
                            + homeLongitude
                            + "\nFlight state: "
                            + flightState.name());
        }
    }

    private void setUpListener() {
        // Example of Listener
        listener = new WaypointMissionOperatorListener() {
            @Override
            public void onDownloadUpdate(@NonNull WaypointMissionDownloadEvent waypointMissionDownloadEvent) {
                // Example of Download Listener
                if (waypointMissionDownloadEvent.getProgress() != null
                        && waypointMissionDownloadEvent.getProgress().isSummaryDownloaded
                        && waypointMissionDownloadEvent.getProgress().downloadedWaypointIndex == (WAYPOINT_COUNT - 1)) {
                    ToastUtils.setResultToToast("Mission is downloaded successfully");
                }
                updateWaypointMissionState();


            }

            @Override
            public void onUploadUpdate(@NonNull WaypointMissionUploadEvent waypointMissionUploadEvent) {
                // Example of Upload Listener
                if (waypointMissionUploadEvent.getProgress() != null
                        && waypointMissionUploadEvent.getProgress().isSummaryUploaded
                        && waypointMissionUploadEvent.getProgress().uploadedWaypointIndex == (WAYPOINT_COUNT - 1)) {
                    ToastUtils.setResultToToast("Mission is uploaded successfully");
                }
                updateWaypointMissionState();
            }

            @Override
            public void onExecutionUpdate(@NonNull WaypointMissionExecutionEvent waypointMissionExecutionEvent) {
                // Example of Execution Listener
                Log.d("TAG",
                        (waypointMissionExecutionEvent.getPreviousState() == null
                                ? ""
                                : waypointMissionExecutionEvent.getPreviousState().getName())
                                + ", "
                                + waypointMissionExecutionEvent.getCurrentState().getName()
                                + (waypointMissionExecutionEvent.getProgress() == null
                                ? ""
                                : waypointMissionExecutionEvent.getProgress().targetWaypointIndex));
                updateWaypointMissionState();
            }

            @Override
            public void onExecutionStart() {
                ToastUtils.setResultToToast("Mission started");
                updateWaypointMissionState();
            }

            @Override
            public void onExecutionFinish(@Nullable DJIError djiError) {
                ToastUtils.setResultToToast("Mission finished");
                updateWaypointMissionState();

                // После завершения миссии — посадка
                if (flightController != null) {
                    flightController.startLanding(new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError error) {
                            ToastUtils.setResultToToast(error == null ? "Landing initiated" : "Landing failed: " + error.getDescription());
                        }
                    });
                }
            }
        };

        if (waypointMissionOperator != null && listener != null) {
            // Example of adding listeners
            waypointMissionOperator.addListener(listener);
        }
    }

    private void tearDownListener() {
        if (waypointMissionOperator != null && listener != null) {
            // Example of removing listeners
            waypointMissionOperator.removeListener(listener);
        }
    }

    private int calculateTurnAngle() {
        return Math.round((float)Math.toDegrees(Math.atan(VERTICAL_DISTANCE/ HORIZONTAL_DISTANCE)));
    }

    private WaypointMissionOperator getWaypointMissionOperator() {
        if (null == waypointMissionOperator) {
            if (null != MissionControl.getInstance()) {
                return MissionControl.getInstance().getWaypointMissionOperator();
            }
        }
        return waypointMissionOperator;
    }

    private FlightController getFlightController() {
        if (null == flightController) {
            if (null != DJISampleApplication.getAircraftInstance()) {
                return DJISampleApplication.getAircraftInstance().getFlightController();
            }
            ToastUtils.setResultToToast("Product is disconnected!");
        }
        return flightController;
    }

    private void startSimulator() {
        if (null != getFlightController()) {
            flightController.getSimulator().start(InitializationData.createInstance(new LocationCoordinate2D(BASE_LATITUDE, BASE_LONGITUDE),REFRESH_FREQ, SATELLITE_COUNT), new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    ToastUtils.setResultToToast(djiError != null ?  djiError.getDescription():"Simulator started");
                }
            });
        }
    }

    @Override
    public int getDescription() {
        return R.string.component_listview_waypoint_mission_operator;
    }

    private void initVideoFrameCapture() {
        videoTextureView = findViewById(R.id.base_camera_view); // убедитесь, что этот ID есть в layout XML

        if (videoTextureView.isAvailable()) {
            startCapturingFrames();
        } else {
            videoTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    startCapturingFrames();
                }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { return false; }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
            });
        }
    }

    private void startCapturingFrames() {
        frameCaptureRunnable = new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Camera screenshot");
                if (videoTextureView.isAvailable()) {
                    Bitmap frame = videoTextureView.getBitmap(640, 640);
                    if (frame != null) {
                        Log.i(TAG, "Кадр получен: " + frame.getWidth() + "x" + frame.getHeight());
                        inferenceExecutor.execute(() -> {
                            try {
                                List<float[]> boxes = detector.runModel(frame);
                                for (float[] box : boxes) {
                                    Log.i(TAG, String.format("Class: %d | Conf: %.2f | Center: [%.1f, %.1f] | Size: [%.1f, %.1f]",
                                            (int) box[5], box[4], box[0], box[1], box[2], box[3]));
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Ошибка инференса", e);
                            } finally {
                                frame.recycle();
                            }
                        });
                    }
                }
                frameCaptureHandler.postDelayed(this, 2000);
            }
        };
        frameCaptureHandler.post(frameCaptureRunnable);
    }

    private void initVideoFeed() {
        videoTextureView = findViewById(R.id.base_camera_view);

        if (videoTextureView.isAvailable()) {
            initCodec(videoTextureView.getSurfaceTexture(), videoTextureView.getWidth(), videoTextureView.getHeight());
        } else {
            videoTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    initCodec(surface, width, height);
                }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    if (codecManager != null) {
                        codecManager.cleanSurface();
                        codecManager = null;
                    }
                    return true;
                }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
            });
        }

        videoDataListener = (videoBuffer, size) -> {
            if (codecManager != null) {
                codecManager.sendDataToDecoder(videoBuffer, size);
            }
        };

        VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(videoDataListener);
    }

    private void initCodec(SurfaceTexture surface, int width, int height) {
        codecManager = new DJICodecManager(getContext(), surface, width, height);
    }
}
