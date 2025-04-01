package com.dji.sdk.sample.demo.missionoperator;


import android.content.Context;
import android.util.Log;
import android.view.View;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.demo.missionmanager.MissionBaseView;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
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
import dji.sdk.flightcontroller.FlightController;

import dji.sdk.mission.MissionControl;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;

import static dji.keysdk.FlightControllerKey.HOME_LOCATION_LATITUDE;
import static dji.keysdk.FlightControllerKey.HOME_LOCATION_LONGITUDE;

public class WaypointMissionOperatorView extends MissionBaseView {

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


    public WaypointMissionOperatorView(Context context) {
        super(context);
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
                List<LocationCoordinate2D> coords = new ArrayList<>();
                coords.add(new LocationCoordinate2D(22.0, 113.0));
                coords.add(new LocationCoordinate2D(22.0003, 113.0003));
                coords.add(new LocationCoordinate2D(22.0006, 113.0001));
                coords.add(new LocationCoordinate2D(22.0004, 113.0000));

                mission = createWaypointMissionFromCoordinates(coords);
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
        waypointMissionOperator = MissionControl.getInstance().getWaypointMissionOperator();
        setUpListener();
    }

    @Override
    protected void onDetachedFromWindow() {
        tearDownListener();
        if (flightController != null) {
            flightController.getSimulator().stop(null);
            flightController.setStateCallback(null);
        }
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
}
