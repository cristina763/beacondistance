package com.example.beacondistance;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.altbeacon.beacon.*;

import java.io.OutputStream;
import java.net.Socket;
import java.util.Collection;

public class MainActivity extends AppCompatActivity implements BeaconConsumer {

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final String TARGET_BEACON_UUID = "b5b182c7-eab1-4988-aa99-b5c1517008d9"; // 目標 Beacon UUID
    private static final String ZENBO_IP = "192.168.0.107"; // Zenbo 的 IP 地址
    private static final int ZENBO_PORT = 7777; // Zenbo 的 Port

    private BeaconManager beaconManager;
    private TextView rssiText, distanceText, statusText, connectionStatusText;
    private Button sendButton;
    private double currentDistance = 0.0; // 保存最新的 Beacon 距離值
    private boolean isZenboConnected = false; // 標記與 Zenbo 的連線狀態

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        rssiText = findViewById(R.id.textViewRssi);
        distanceText = findViewById(R.id.textViewDistance);
        statusText = findViewById(R.id.textViewStatus);
        connectionStatusText = findViewById(R.id.textViewConnectionStatus);
        sendButton = findViewById(R.id.buttonSend);

        // Initialize BeaconManager
        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().add(new BeaconParser()
                .setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

        // Request location permission if needed
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        } else {
            beaconManager.bind(this);
        }

        // Set up the button click listener
        sendButton.setOnClickListener(view -> sendDistanceToZenbo());
    }

    @Override
    public void onBeaconServiceConnect() {
        beaconManager.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                if (beacons.isEmpty()) {
                    runOnUiThread(() -> statusText.setText("No beacons detected."));
                    return;
                }

                for (Beacon beacon : beacons) {
                    if (beacon.getId1().toString().equalsIgnoreCase(TARGET_BEACON_UUID)) {
                        currentDistance = beacon.getDistance(); // 更新當前距離
                        int rssi = beacon.getRssi(); // RSSI 值

                        // 更新畫面
                        runOnUiThread(() -> {
                            rssiText.setText("RSSI: " + rssi);
                            distanceText.setText("Distance: " + String.format("%.2f", currentDistance) + " meters");
                            statusText.setText("Target beacon detected.");
                        });
                    }
                }
            }
        });

        try {
            // 只偵測目標 Beacon
            beaconManager.startRangingBeaconsInRegion(new Region("targetBeaconRegion",
                    Identifier.parse(TARGET_BEACON_UUID), Identifier.parse("1"), Identifier.parse("1")));
        } catch (RemoteException e) {
            runOnUiThread(() -> statusText.setText("Error starting beacon ranging: " + e.getMessage()));
        }
    }

    // 傳送距離數據到 Zenbo
    private void sendDistanceToZenbo() {
        new Thread(() -> {
            try (Socket socket = new Socket(ZENBO_IP, ZENBO_PORT);
                 OutputStream outputStream = socket.getOutputStream()) {
                String distanceData = String.format("Distance: %.2f meters\n", currentDistance);
                outputStream.write(distanceData.getBytes());
                outputStream.flush();

                isZenboConnected = true; // 標記連線成功
                runOnUiThread(() -> {
                    connectionStatusText.setText("Connected to Zenbo");
                    statusText.setText("Distance sent to Zenbo: " + distanceData);
                });
            } catch (Exception e) {
                isZenboConnected = false; // 標記連線失敗
                runOnUiThread(() -> {
                    connectionStatusText.setText("Failed to connect to Zenbo");
                    statusText.setText("Error sending data to Zenbo: " + e.getMessage());
                });
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        beaconManager.unbind(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                beaconManager.bind(this);
            } else {
                statusText.setText("Permission denied. Cannot scan for beacons.");
            }
        }
    }
}
