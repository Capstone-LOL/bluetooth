package org.tensorflow.lite.examples.detection;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.env.Utils;
import org.tensorflow.lite.examples.detection.tflite.Classifier;
import org.tensorflow.lite.examples.detection.tflite.YoloV4Classifier;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
//블루 투스 참고 https://webnautes.tistory.com/849
public class MainActivity extends AppCompatActivity {

    public static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;


    private static final Logger LOGGER = new Logger();

    public static final int TF_OD_API_INPUT_SIZE = 416;

    private static final boolean TF_OD_API_IS_QUANTIZED = false;

    private static final String TF_OD_API_MODEL_FILE = "yolov4-tiny-416.tflite";

    private static final String TF_OD_API_LABELS_FILE = "yolov4-labelmap.txt";

    // Minimum detection confidence to track a detection.
    private static final boolean MAINTAIN_ASPECT = false;
    private Integer sensorOrientation = 90;

    private Classifier detector;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private MultiBoxTracker tracker;
    private OverlayView trackingOverlay;

    protected int previewWidth = 0;
    protected int previewHeight = 0;

    private Bitmap sourceBitmap;
    private Bitmap cropBitmap;

    private Button cameraButton, BtButton;
    private ImageView imageView;
    //블루 투스 통신 관련 변수 선언
//    ConnectedTask mConnectedTask = null;
//    static BluetoothAdapter mBluetoothAdapter;
//    private String mConnectedDeviceName = null;
//    private ArrayAdapter<String> mConversationArrayAdapter;
//    static boolean isConnectionError = false;
//    private static final String TAG = "BluetoothClient";
//    private final int REQUEST_BLUETOOTH_ENABLE = 100;
    //////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraButton = findViewById(R.id.cameraButton);





//        BtButton.setOnClickListener(v -> {
//            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//            if (mBluetoothAdapter == null) {
//                showErrorDialog("This device is not implement Bluetooth.");
//                return;
//            }
//
//            if (!mBluetoothAdapter.isEnabled()) {
//                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//                startActivityForResult(intent, REQUEST_BLUETOOTH_ENABLE);
//            }
//            else {
//                Log.d(TAG, "Initialisation successful.");
//
//                showPairedDevicesListDialog();
//            }
//        });

        Intent intent = new Intent(MainActivity.this, DetectorActivity.class);



//        intent.putExtra("bluetoothname",mConnectedDeviceName);

        cameraButton.setOnClickListener(v -> startActivity(intent));


        this.sourceBitmap = Utils.getBitmapFromAsset(MainActivity.this, "kite.jpg");

        this.cropBitmap = Utils.processBitmap(sourceBitmap, TF_OD_API_INPUT_SIZE);

//        this.imageView.setImageBitmap(cropBitmap);

        initBox();
    }

//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//
//        if ( mConnectedTask != null ) {
//
//            mConnectedTask.cancel(true);
//        }
//    }
//    private class ConnectTask extends AsyncTask<Void, Void, Boolean> {
//
//        private BluetoothSocket mBluetoothSocket = null;
//        private BluetoothDevice mBluetoothDevice = null;
//
//        ConnectTask(BluetoothDevice bluetoothDevice) {
//            mBluetoothDevice = bluetoothDevice;
//            mConnectedDeviceName = bluetoothDevice.getName();
//            Log.d("test12", mConnectedDeviceName);
//            //SPP
//            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
//
//            try {
//                mBluetoothSocket = mBluetoothDevice.createRfcommSocketToServiceRecord(uuid);
//                Log.d( TAG, "create socket for "+mConnectedDeviceName);
//
//            } catch (IOException e) {
//                Log.e( TAG, "socket create failed " + e.getMessage());
//            }
//
////            mConnectionStatus.setText("connecting...");
//        }
//
//
//        @Override
//        protected Boolean doInBackground(Void... params) {
//
//            // Always cancel discovery because it will slow down a connection
//            mBluetoothAdapter.cancelDiscovery();
//
//            // Make a connection to the BluetoothSocket
//            try {
//                // This is a blocking call and will only return on a
//                // successful connection or an exception
//                mBluetoothSocket.connect();
//            } catch (IOException e) {
//                // Close the socket
//                try {
//                    mBluetoothSocket.close();
//                } catch (IOException e2) {
//                    Log.e(TAG, "unable to close() " +
//                            " socket during connection failure", e2);
//                }
//
//                return false;
//            }
//
//            return true;
//        }
//
//
//        @Override
//        protected void onPostExecute(Boolean isSucess) {
//
//            if ( isSucess ) {
//                connected(mBluetoothSocket);
//            }
//            else{
//
//                isConnectionError = true;
//                Log.d( TAG,  "Unable to connect device");
//                showErrorDialog("Unable to connect device");
//            }
//        }
//    }
//
//
//    public void connected( BluetoothSocket socket ) {
//        mConnectedTask = new ConnectedTask(socket);
//        mConnectedTask.execute();
//    }
//
//
//
//    private class ConnectedTask extends AsyncTask<Void, String, Boolean> {
//
//        private InputStream mInputStream = null;
//        private OutputStream mOutputStream = null;
//        private BluetoothSocket mBluetoothSocket = null;
//
//        ConnectedTask(BluetoothSocket socket){
//
//            mBluetoothSocket = socket;
//            try {
//                mInputStream = mBluetoothSocket.getInputStream();
//                mOutputStream = mBluetoothSocket.getOutputStream();
//            } catch (IOException e) {
//                Log.e(TAG, "socket not created", e );
//            }
//
//            Log.d( TAG, "connected to "+mConnectedDeviceName);
////            mConnectionStatus.setText( "connected to "+mConnectedDeviceName);
//        }
//
//
//        @Override
//        protected Boolean doInBackground(Void... params) {
//
//            byte [] readBuffer = new byte[1024];
//            int readBufferPosition = 0;
//
//
//            while (true) {
//
//                if ( isCancelled() ) return false;
//
//                try {
//
//                    int bytesAvailable = mInputStream.available();
//
//                    if(bytesAvailable > 0) {
//
//                        byte[] packetBytes = new byte[bytesAvailable];
//
//                        mInputStream.read(packetBytes);
//
//                        for(int i=0;i<bytesAvailable;i++) {
//
//                            byte b = packetBytes[i];
//                            if(b == '\n')
//                            {
//                                byte[] encodedBytes = new byte[readBufferPosition];
//                                System.arraycopy(readBuffer, 0, encodedBytes, 0,
//                                        encodedBytes.length);
//                                String recvMessage = new String(encodedBytes, "UTF-8");
//
//                                readBufferPosition = 0;
//
//                                Log.d(TAG, "recv message: " + recvMessage);
//                                publishProgress(recvMessage);
//                            }
//                            else
//                            {
//                                readBuffer[readBufferPosition++] = b;
//                            }
//                        }
//                    }
//                } catch (IOException e) {
//
//                    Log.e(TAG, "disconnected", e);
//                    return false;
//                }
//            }
//
//        }
//
//        @Override
//        protected void onProgressUpdate(String... recvMessage) {
//
//            mConversationArrayAdapter.insert(mConnectedDeviceName + ": " + recvMessage[0], 0);
//        }
//
//        @Override
//        protected void onPostExecute(Boolean isSucess) {
//            super.onPostExecute(isSucess);
//
//            if ( !isSucess ) {
//
//
//                closeSocket();
//                Log.d(TAG, "Device connection was lost");
//                isConnectionError = true;
//                showErrorDialog("Device connection was lost");
//            }
//        }
//
//        @Override
//        protected void onCancelled(Boolean aBoolean) {
//            super.onCancelled(aBoolean);
//
//            closeSocket();
//        }
//
//        void closeSocket(){
//
//            try {
//
//                mBluetoothSocket.close();
//                Log.d(TAG, "close socket()");
//
//            } catch (IOException e2) {
//
//                Log.e(TAG, "unable to close() " +
//                        " socket during connection failure", e2);
//            }
//        }
//
//        void write(String msg){
//
//            msg += "\n";
//
//            try {
//                mOutputStream.write(msg.getBytes());
//                mOutputStream.flush();
//            } catch (IOException e) {
//                Log.e(TAG, "Exception during send", e );
//            }
//
//        }
//    }
//
//
//    public void showPairedDevicesListDialog()
//    {
//        Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
//        final BluetoothDevice[] pairedDevices = devices.toArray(new BluetoothDevice[0]);
//
//        if ( pairedDevices.length == 0 ){
//            showQuitDialog( "No devices have been paired.\n"
//                    +"You must pair it with another device.");
//            return;
//        }
//
//        String[] items;
//        items = new String[pairedDevices.length];
//        for (int i=0;i<pairedDevices.length;i++) {
//            items[i] = pairedDevices[i].getName();
//        }
//
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle("Select device");
//        builder.setCancelable(false);
//        builder.setItems(items, new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                dialog.dismiss();
//
//                ConnectTask task = new ConnectTask(pairedDevices[which]);
//
//                task.execute();
//            }
//        });
//        builder.create().show();
//    }
//
//
//
//    public void showErrorDialog(String message)
//    {
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle("Quit");
//        builder.setCancelable(false);
//        builder.setMessage(message);
//        builder.setPositiveButton("OK",  new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                dialog.dismiss();
//                if ( isConnectionError  ) {
//                    isConnectionError = false;
//                    finish();
//                }
//            }
//        });
//        builder.create().show();
//    }
//
//
//    public void showQuitDialog(String message)
//    {
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle("Quit");
//        builder.setCancelable(false);
//        builder.setMessage(message);
//        builder.setPositiveButton("OK",  new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                dialog.dismiss();
//                finish();
//            }
//        });
//        builder.create().show();
//    }
//
//    void sendMessage(String msg){
//
//        if ( mConnectedTask != null ) {
//            mConnectedTask.write(msg);
//            Log.d(TAG, "send message: " + msg);
//            mConversationArrayAdapter.insert("Me:  " + msg, 0);
//        }
//    }
//
//
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//
//        super.onActivityResult(requestCode, resultCode, data);
//
//        if (requestCode == REQUEST_BLUETOOTH_ENABLE) {
//            if (resultCode == RESULT_OK) {
//                //BlueTooth is now Enabled
//                showPairedDevicesListDialog();
//            }
//            if (resultCode == RESULT_CANCELED) {
//                showQuitDialog("You need to enable bluetooth");
//            }
//        }
//    }

    private void initBox() {
        previewHeight = TF_OD_API_INPUT_SIZE;
        previewWidth = TF_OD_API_INPUT_SIZE;
        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        tracker = new MultiBoxTracker(this);
        trackingOverlay = findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                canvas -> tracker.draw(canvas));

        tracker.setFrameConfiguration(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, sensorOrientation);

        try {
            detector =
                    YoloV4Classifier.create(
                            getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_IS_QUANTIZED);
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing classifier!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }
    }

    private void handleResult(Bitmap bitmap, List<Classifier.Recognition> results) {
        final Canvas canvas = new Canvas(bitmap);
        final Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);

        final List<Classifier.Recognition> mappedRecognitions =
                new LinkedList<Classifier.Recognition>();

        for (final Classifier.Recognition result : results) {
            final RectF location = result.getLocation();
            if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE_TF_OD_API) {
                canvas.drawRect(location, paint);
//                cropToFrameTransform.mapRect(location);
//
//                result.setLocation(location);
//                mappedRecognitions.add(result);
            }
        }
//        tracker.trackResults(mappedRecognitions, new Random().nextInt());
//        trackingOverlay.postInvalidate();
        imageView.setImageBitmap(bitmap);
    }
}
