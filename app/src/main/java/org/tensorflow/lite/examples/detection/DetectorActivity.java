/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.detection;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Display;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.customview.OverlayView.DrawCallback;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Classifier;
import org.tensorflow.lite.examples.detection.tflite.YoloV4Classifier;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;

import static android.speech.tts.TextToSpeech.ERROR;


/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener, SensorEventListener {
    private static final Logger LOGGER = new Logger();
    private static final int TF_OD_API_INPUT_SIZE = 416;
    private static final boolean TF_OD_API_IS_QUANTIZED = false;
    private static final String TF_OD_API_MODEL_FILE = "yolov4-tiny-416.tflite";
    private static final String TF_OD_API_LABELS_FILE = "yolov4-labelmap.txt";
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;

    private Integer sensorOrientation;

    private Classifier detector;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    private BorderedText borderedText;


    //박스 크기 계산 및 프레임 처리
    double[][] area;
    int frame_num = 0;
    int send_frame = 3;
    int managed_frame_length = 400;


    // 블루투스 관련 변수 정의
//    https://ddangeun.tistory.com/59 참고 사이트
//    final static UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public BluetoothAdapter mBluetoothAdapter;
    public Set<BluetoothDevice> mDevices;
    private BluetoothSocket bSocket;
    private OutputStream mOutputStream;
    private InputStream mInputStream;
    private BluetoothDevice mRemoteDevice;
    public boolean onBT = false;
    public byte[] sendByte = new byte[4];
    public TextView tvBT;
    public ProgressDialog asyncDialog;
    private static final int REQUEST_ENABLE_BT = 1;
    Display display;
    Point size;
    float recog_center_x;


    String send="";
    // 자이로 센서
    private SensorManager sensorManager;
    private Sensor mAccelerometer = null;
    private float[] mAccelerometerReading = new float[3];
    private Sensor mMagnetometer = null;
    private float[] mMagnetometerReading = new float[3];
    private float[] rotationMatix = new float[9];
    private float[] I = new float[9];
    private float[] orientationAngles = new float[3];
    float azimuth;
    //complementary filter
    private float a = 0.2f;
    private static final float NS2S = 1.0f / 1000000000.0f;
    private double pitch = 0, roll = 0;
    private double times;
    private double dt;
    private double temp;
    private boolean running;
    private boolean gyroRunning;
    private boolean accRunning;

    // tts 처리
    private TextToSpeech tts;
    private int cross_state;
    private int traffic_state;
    private int car_state;
    private int bus_state;


    @Override
    public synchronized void onStart() {
        super.onStart();
        tts = new TextToSpeech(DetectorActivity.this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != ERROR){
                    //언어 선택
                    tts.setLanguage(Locale.KOREAN);
                    tts.setPitch(1.5f);         // 음성 톤을 2.0배 올려준다.
                    tts.setSpeechRate(1.0f);    // 읽는 속도는 기본 설정
                }
            }
        });

        cross_state=1;
        traffic_state=1;
        car_state=1;
        bus_state=1;

        display = getWindowManager().getDefaultDisplay();
        size = new Point();
        display.getSize(size);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);




        area = new double[managed_frame_length][2];
//        sensorManager.registerListener(DetectorActivity.this, mGyroscopeSensor, SensorManager.SENSOR_DELAY_UI);


//        Bundle extras = getIntent().getExtras();
//        if (extras !=null ){
//            mConnectedDeviceName = extras.getString("bluetoothname");
//            Log.d("blue",mConnectedDeviceName+"hello everyone");
//        }
        if (!onBT) { //Connect
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null) { //장치가 블루투스를 지원하지 않는 경우.
                Toast.makeText(getApplicationContext(), "Bluetooth 지원을 하지 않는 기기입니다.", Toast.LENGTH_SHORT).show();
            } else { // 장치가 블루투스를 지원하는 경우.
                if (!mBluetoothAdapter.isEnabled()) {
                    // 블루투스를 지원하지만 비활성 상태인 경우
                    // 블루투스를 활성 상태로 바꾸기 위해 사용자 동의 요청
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                } else {
                    // 블루투스를 지원하며 활성 상태인 경우
                    // 페어링된 기기 목록을 보여주고 연결할 장치를 선택.
                    Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
                    if (pairedDevices.size() > 0) {
                        // 페어링 된 장치가 있는 경우.
                        selectDevice();
                    } else {
                        // 페어링 된 장치가 없는 경우.
                        Toast.makeText(getApplicationContext(), "먼저 Bluetooth 설정에 들어가 페어링을 진행해 주세요.", Toast.LENGTH_SHORT).show();
                    }

                }

            }

        } else { //DisConnect

            try {

                mInputStream.close();
                mOutputStream.close();
                bSocket.close();
                onBT = false;
            } catch (Exception ignored) {
            }

        }

    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
    }

//    출처: https://everyshare.tistory.com/63 [에브리셰어]
    @Override
    public synchronized void onResume() {
        super.onResume();
        // Get updates from the accelerometer and magnetometer at a constant rate.
        // To make batch operations more efficient and reduce power consumption,
        // provide support for delaying updates to the application.
        //
        // In this example, the sensor reporting delay is small enough such that
        // the application receives an update before the system checks the sensor
        // readings again.
        mAccelerometer= sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (mAccelerometer != null) {
            sensorManager.registerListener(DetectorActivity.this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        mMagnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (mMagnetometer != null) {
            sensorManager.registerListener(DetectorActivity.this, mMagnetometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }
    @Override
    public void onSensorChanged(SensorEvent event) { //센서가 변경될 때마다 가속도 센서와 자기 센서 값을 갱신
        final float alpha = 0.97f;
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mAccelerometerReading[0] = alpha * mAccelerometerReading[0] + (1-alpha) * event.values[0];
            mAccelerometerReading[1] = alpha * mAccelerometerReading[1] + (1-alpha) * event.values[1];
            mAccelerometerReading[2] = alpha * mAccelerometerReading[2] + (1-alpha) * event.values[2];
//            System.arraycopy(event.values, 0, mAccelerometerReading,0, mAccelerometerReading.length);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mMagnetometerReading[0] = alpha * mMagnetometerReading[0] + (1 - alpha) * event.values[0];
            mMagnetometerReading[1] = alpha * mMagnetometerReading[1] + (1 - alpha) * event.values[1];
            mMagnetometerReading[2] = alpha * mMagnetometerReading[2] + (1 - alpha) * event.values[2];
//            System.arraycopy(event.values, 0, mMagnetometerReading,0, mMagnetometerReading.length);
        }
        updateOrientationAngles();
    }
    public void updateOrientationAngles() {// 갱신된 가속도 센서와 자기 센서 값으로 방향을 다시 계산
        // Update rotation matrix, which is needed to update orientation angles.
        boolean success = SensorManager.getRotationMatrix(rotationMatix, I,
                mAccelerometerReading, mMagnetometerReading);

        // "mRotationMatrix" now has up-to-date information.
        if(success){
            SensorManager.getOrientation(rotationMatix, orientationAngles);
            azimuth = (float) Math.toDegrees(orientationAngles[0]);
            azimuth = (azimuth + 0 + 360) % 360;
        }

        // "mOrientationAngles" now has up-to-date information.
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public void selectDevice() {
        mDevices = mBluetoothAdapter.getBondedDevices();
        final int mPairedDeviceCount = mDevices.size();

        if (mPairedDeviceCount == 0) {
            //  페어링 된 장치가 없는 경우
            Toast.makeText(getApplicationContext(),"장치를 페어링 해주세요!",Toast.LENGTH_SHORT).show();
        }

//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle("블루투스 장치 선택");


        // 페어링 된 블루투스 장치의 이름 목록 작성
        List<String> listItems = new ArrayList<>();
        for(BluetoothDevice device : mDevices) {
            listItems.add(device.getName());
        }
//        listItems.add("취소");    // 취소 항목 추가

//        final CharSequence[] items = listItems.toArray(new CharSequence[listItems.size()]);

//        builder.setItems(items,new DialogInterface.OnClickListener() {
//            public void onClick(DialogInterface dialog, int item) {
//                if(item == mPairedDeviceCount) {
//                    // 연결할 장치를 선택하지 않고 '취소'를 누른 경우
//                    //finish();
//                }
//                else {
//                    // 연결할 장치를 선택한 경우
//                    // 선택한 장치와 연결을 시도함
//                    connectToSelectedDevice(items[item].toString());
//                }
//            }
//        });
        for(int i = 0; i<listItems.size();i++){
            if(listItems.get(i).equals("HC-06")){
                Toast.makeText(getApplicationContext(),"안내견이 블루투스 목록에 있음!",Toast.LENGTH_SHORT).show();
                connectToSelectedDevice(listItems.get(i).toString());
            }
        }

//        builder.setCancelable(false);    // 뒤로 가기 버튼 사용 금지
//        AlertDialog alert = builder.create();
//        alert.show();

    }
    public void connectToSelectedDevice(final String selectedDeviceName) {
        mRemoteDevice = getDeviceFromBondedList(selectedDeviceName);

        //Progress Dialog
//        asyncDialog = new ProgressDialog(DetectorActivity.this);
//        asyncDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
//        asyncDialog.setMessage("블루투스 연결중..");
//        asyncDialog.show();
//        asyncDialog.setCancelable(false);

        Thread BTConnect = new Thread(new Runnable() {
            public void run() {

                try {
                    UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //HC-06 UUID
                    // 소켓 생성
                    bSocket = mRemoteDevice.createRfcommSocketToServiceRecord(uuid);


                    // RFCOMM 채널을 통한 연결
                    bSocket.connect();

                    // 데이터 송수신을 위한 스트림 열기
                    mOutputStream = bSocket.getOutputStream();
                    mInputStream = bSocket.getInputStream();


                    runOnUiThread(new Runnable() {
                        @SuppressLint({"ShowToast", "SetTextI18n"})
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), selectedDeviceName + " 연결 완료", Toast.LENGTH_LONG).show();
//                            tvBT.setText(selectedDeviceName + " Connected");
//                            BTButton.setText("disconnect");
//                            asyncDialog.dismiss();
                        }
                    });

                    onBT = true;


                } catch (Exception e) {
                    // 블루투스 연결 중 오류 발생
                    runOnUiThread(new Runnable() {
                        @SuppressLint({"ShowToast", "SetTextI18n"})
                        @Override
                        public void run() {

                            Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
                            Log.d("connect_error", e.toString());
                            Toast.makeText(getApplicationContext(), "블루투스 연결 오류", Toast.LENGTH_SHORT).show();
                        }
                    });

                }


            }
        });
        BTConnect.start();
    }
    public BluetoothDevice getDeviceFromBondedList(String name) {
        BluetoothDevice selectedDevice = null;

        for(BluetoothDevice device : mDevices) {
            if(name.equals(device.getName())) {
                selectedDevice = device;
                break;
            }
        }
        return selectedDevice;
    }

    // 터치시 휴대폰 좌표를 확인 하기 위함 - 테스트용
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        int action = event.getAction();
        float cur_x = event.getX();
        float cur_y = event.getY();

//        Log.d("yPoint:", String.valueOf(cur_y));


        return super.onTouchEvent(event);
    }

    void write(String msg){ // 블루투스 메시지를 전송하는 함수
        msg += "\n";
        try { //소켓 스트림으로 값을 전달 후 비움
            mOutputStream.write(msg.getBytes());
            mOutputStream.flush();
        } catch (IOException e) {
            Log.e("TEST", "Exception during send", e );
        }
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        sensorManager.unregisterListener((SensorEventListener) this);
    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        int cropSize = TF_OD_API_INPUT_SIZE;

        try {//classifier 객체 생성 
            detector =
                    YoloV4Classifier.create(
                            getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_IS_QUANTIZED);
//            detector = TFLiteObjectDetectionAPIModel.create(
//                    getAssets(),
//                    TF_OD_API_MODEL_FILE,
//                    TF_OD_API_LABELS_FILE,
//                    TF_OD_API_INPUT_SIZE,
//                    TF_OD_API_IS_QUANTIZED);
            cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing classifier!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                        if (isDebug()) {
                            tracker.drawDebug(canvas);
                        }
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    @Override
    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        //실제 예측하는 부분
        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();
                        //한 프레임에 대해서 예측한 클래스와 바운딩 박스 정보들을 results라는 리스트 변수에 담아줌
                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        //현제 장면과 다음 장면을 비교하기 위해 프레임을 구분해봄

                        frame_num++;//현제 프레임
                        frame_num = frame_num % managed_frame_length;
                        int before_frame = frame_num-1*send_frame;//바로 이전 프레임
                        int bbefore_frame = frame_num-2*send_frame;//값이 계속 쌓이지 않도록 초기화 해주는 역할
                        if(before_frame <0) before_frame = managed_frame_length + before_frame;
                        if(bbefore_frame <0) bbefore_frame = managed_frame_length + bbefore_frame;
                        /////////

//                        Log.e("CHECK", "run: " + results.size());

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        switch (MODE) {
                            case TF_OD_API:
                                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                                break;
                        }

                        final List<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<Classifier.Recognition>();

                        if(bSocket.isConnected()&&orientationAngles != null) {//블루투스가 연결되어 있고 각도 값이 계산되었을 때
                            if(frame_num % send_frame == 0){
                                int i = 0;
                                send ="";
                            for (final Classifier.Recognition result : results) {
                                i++;
                                final RectF location = result.getLocation();
                                if (location != null && result.getConfidence() >= minimumConfidence) {
                                    canvas.drawRect(location, paint);

                                    cropToFrameTransform.mapRect(location);

                                    result.setLocation(location);
                                    mappedRecognitions.add(result);
                                }
                                //블루투스로 보낼 메시지 작성
                                send += result.getDetectedClass() + " " + azimuth;
                                // 박스 가로길이에서 가운데 값
                                recog_center_x = location.centerX();

                                if (result.getDetectedClass() == 3) { // 점자 블록 인식인 경우
                                    if (size.x / 2 + 100 < recog_center_x) {// 점자 블록이 오른쪽에 치우 칠때
                                        send += " " + "1";
                                    } else if (size.x / 2 - 100 > recog_center_x) {// 점자블록이 왼쪽에 치우 칠때
                                        send += " " + "-1";
                                    } else {
                                        send += " " + "0";
                                    }
                                } else if (result.getDetectedClass() == 0) {// 자동차인 경우
                                    double new_area = (location.right - location.left) * (location.bottom - location.top);

                                    if (area[before_frame][0] == 0.0) {
                                        area[frame_num][0] = new_area;
                                        send += " " + "1";
                                    } else if (area[before_frame][0] * 1.5 <= new_area) {
                                        send += " " + "1";
                                        area[frame_num][0] = new_area;
                                    } else if (area[before_frame][0] * 1.5 > new_area) {
                                        send += " " + "0";
                                        cross_state = 1;
                                        area[frame_num][0] = new_area;
                                        tts.speak("차량정지",TextToSpeech.QUEUE_ADD,null);
                                    }
                                    area[bbefore_frame][0] = 0.0;

//                                    Log.d("test123:", String.valueOf(i));
                                } else if (result.getDetectedClass() == 1) {// 버스인 경우
                                    double new_area = (location.right - location.left) * (location.bottom - location.top);

                                    if (area[before_frame][1] == 0.0) {
                                        area[frame_num][1] = new_area;
                                        send += " " + "1";
                                    } else if (area[before_frame][1] * 1.5 <= new_area) {
                                        send += " " + "1";
                                        area[frame_num][1] = new_area;
                                    } else if (area[before_frame][1] * 1.5 > new_area) {
                                        send += " " + "0";
                                        area[frame_num][1] = new_area;
                                        tts.speak("차량정지",TextToSpeech.QUEUE_ADD,null);
                                    }
                                    area[bbefore_frame][1] = 0.0;
//                                    Log.d("frame:", String.valueOf(frame_num));
                                } else if (result.getDetectedClass() == 2 && cross_state == 1 && (location.right - location.left)>(size.x*(5/8))){
                                    cross_state = 0;
                                    Log.d("tts_test","testing");
                                    tts.speak("횡단보도입니다 정지해주세요",TextToSpeech.QUEUE_ADD,null);
                                } else if (result.getDetectedClass() == 4){
                                    tts.speak("장애물",TextToSpeech.QUEUE_ADD,null);
                                } else if (result.getDetectedClass() == 5 && cross_state == 0){
                                    cross_state = 1;
                                    tts.speak("초록불",TextToSpeech.QUEUE_ADD,null);
                                } else if (result.getDetectedClass() == 6 && cross_state == 0){

                                    tts.speak("빨간불",TextToSpeech.QUEUE_ADD,null);
                                }
                                send += "#";
//                                Toast.makeText(DetectorActivity.this, String.valueOf(results.size())+" "+String.valueOf(i) ,Toast.LENGTH_SHORT).show();

                            }

                                //인식 결과가 없을 때도 방위각 정보 전달
                                send += "-1 " + azimuth;
//                                Toast.makeText(DetectorActivity.this, send ,Toast.LENGTH_SHORT).show();
//                                String[] classes = send.split("#");


                        }
                            write(send);
//                            Toast.makeText(DetectorActivity.this,String.valueOf(frame_num),Toast.LENGTH_SHORT).show();


                        }

                        tracker.trackResults(mappedRecognitions, currTimestamp);
                        trackingOverlay.postInvalidate();

                        computingDetection = false;

                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        showFrameInfo(previewWidth + "x" + previewHeight);
                                        showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                                        showInference(lastProcessingTimeMs + "ms");
                                    }
                                });
                    }
                });
    }

    @Override
    public synchronized void onDestroy() {
        super.onDestroy();
        // TTS 객체가 남아있다면 실행을 중지하고 메모리에서 제거한다.
        if(tts != null){
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }

    @Override
    protected int getLayoutId() {
        return R.layout.tfe_od_camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(() -> detector.setUseNNAPI(isChecked));
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }
}
