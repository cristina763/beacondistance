package com.example.beacondistance;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.altbeacon.beacon.*;

import java.io.OutputStream;
import java.net.Socket;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements BeaconConsumer {

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final String BEACON_UUID_1 = "b5b182c7-eab1-4988-aa99-b5c1517008d9"; // 第一個 Beacon UUID
    private static final String BEACON_UUID_2 = "b5b182c7-eab1-4988-aa99-b5c1517008d9"; // 第二個 Beacon UUID
    private static final String BEACON_UUID_3 = "b5b182c7-eab1-4988-aa99-b5c1517008d9"; // 第三個 Beacon UUID
    private static final String ZENBO_IP = "192.168.0.107"; // Zenbo 的 IP 地址
    private static final int ZENBO_PORT = 7777; // Zenbo 的 Port

    private static final Map<String, Integer> BEACON_UUID_MAP = new HashMap<String, Integer>() {{
        put("b5b182c7-eab1-4988-aa99-b5c1517008d9", R.id.textViewDistance1); // Beacon 1
        put("b5b182c7-eab1-4988-aa99-b5c1517008da", R.id.textViewDistance2); // Beacon 2
        put("b5b182c7-eab1-4988-aa99-b5c1517008db", R.id.textViewDistance3); // Beacon 3
    }};

    private BeaconManager beaconManager;
    private TextView rssiText, distanceText1,distanceText2,distanceText3, statusText, connectionStatusText;
    private Button sendButton;
    private double distance1 = 0.0;
    private double distance2 = 0.0;
    private double distance3 = 0.0;
    private boolean isZenboConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        rssiText = findViewById(R.id.textViewRssi);
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
        beaconManager.addRangeNotifier((beacons, region) -> {
            if (beacons.isEmpty()) {
                runOnUiThread(() -> statusText.setText("No beacons detected."));
                return;
            }

            for (Beacon beacon : beacons) {
                String beaconUUID = beacon.getId1().toString();
                int major = beacon.getId2().toInt();
                int minor = beacon.getId3().toInt();
                double distance = beacon.getDistance();
                int rssi = beacon.getRssi();

                runOnUiThread(() -> {
                    if (beaconUUID.equalsIgnoreCase(BEACON_UUID_1) && major == 1 && minor == 1) {
                        distance1 = distance;
                        rssiText.setText("Beacon 1 RSSI: " + rssi);
                        updateDistanceText(R.id.textViewDistance1, "Beacon 1 Distance", distance1);
                    } else if (beaconUUID.equalsIgnoreCase(BEACON_UUID_2) && major == 1 && minor == 2) {
                        distance2 = distance;
                        updateDistanceText(R.id.textViewDistance2, "Beacon 2 Distance", distance2);
                    } else if (beaconUUID.equalsIgnoreCase(BEACON_UUID_3) && major == 1 && minor == 56273) {
                        distance3 = distance;
                        updateDistanceText(R.id.textViewDistance3, "Beacon 3 Distance", distance3);
                    }
                });
            }
        });

        try {
            // 為每個 Beacon 開始 Ranging
            beaconManager.startRangingBeaconsInRegion(new Region("beaconRegion1", Identifier.parse(BEACON_UUID_1), Identifier.parse("1"), Identifier.parse("1")));
            beaconManager.startRangingBeaconsInRegion(new Region("beaconRegion2", Identifier.parse(BEACON_UUID_2), Identifier.parse("1"), Identifier.parse("2")));
            beaconManager.startRangingBeaconsInRegion(new Region("beaconRegion3", Identifier.parse(BEACON_UUID_3), Identifier.parse("1"), Identifier.parse("56273")));
        } catch (RemoteException e) {
            runOnUiThread(() -> statusText.setText("Error starting beacon ranging: " + e.getMessage()));
        }
    }

    private void updateDistanceText(int textViewId, String label, double distance) {
        TextView textView = findViewById(textViewId);
        String text = String.format("%s: %.2f meters", label, distance);
        textView.setText(text);
    }


    private void sendDistanceToZenbo() {
        new Thread(() -> {
            try (Socket socket = new Socket(ZENBO_IP, ZENBO_PORT);
                 OutputStream outputStream = socket.getOutputStream()) {
                String distanceData = String.format("Beacon 1 Distance: %.2f meters\nBeacon 2 Distance: %.2f meters\nBeacon 3 Distance: %.2f meters\n",
                        distance1, distance2, distance3);
                outputStream.write(distanceData.getBytes());
                outputStream.flush();

                isZenboConnected = true;
                runOnUiThread(() -> {
                    connectionStatusText.setText("Connected to Zenbo");
                    statusText.setText("Distance sent to Zenbo: \n" + distanceData);
                });
            } catch (Exception e) {
                isZenboConnected = false;
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