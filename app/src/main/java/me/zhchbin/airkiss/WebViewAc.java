package me.zhchbin.airkiss;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * Author: Zlx on 2018/12/10/010.
 * Email:1170762202@qq.com
 */
public class WebViewAc extends AppCompatActivity {

    private WebView webView;

    private String url = "file:///android_asset/dist/index.html";

    private boolean isFirst = false;

    @SuppressLint("JavascriptInterface")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ac_web_view);
        webView = (WebView) findViewById(R.id.web_view);

        WebSettings settings = webView.getSettings();
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new JavaScriptinterface(this), "android");
        webView.loadUrl(url);


        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress == 100 && !isFirst) {
                    isFirst = true;
                    String ssid = getSSID();
                    String method = "javascript:getWifiName('" + ssid + "')";
                    webView.evaluateJavascript(method, new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String s) {
                            Log.e("TAG", "onPageFinished: " + s);
                        }
                    });
                }
            }
        });


    }

    private void show() {
        webView.post(new Runnable() {
            @Override
            public void run() {
                String a = "123";
                String method = "javascript:airkissCall(" + a + ")";
                webView.evaluateJavascript(method, new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String s) {
                        Log.e("TAG", "re: " + s);
                    }
                });
            }
        });

    }

    private String getSSID() {
        Context context = getApplicationContext();
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfo.isConnected()) {
            final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            if (connectionInfo != null) {
                String ssid = connectionInfo.getSSID();
                if (Build.VERSION.SDK_INT >= 17 && ssid.startsWith("\"") && ssid.endsWith("\""))
                    ssid = ssid.replaceAll("^\"|\"$", "");


                return ssid;
            }
        }
        return null;
    }



    class JavaScriptinterface {
        Context context;

        public JavaScriptinterface(Context c) {
            context = c;
        }

        /**
         * 与js交互时用到的方法，在js里直接调用的
         */
        @JavascriptInterface
        public void showToast(final String ssid, final String password) {
            Log.e("TAG", "showToast: " + ssid + "  " + password);
//            Toast.makeText(WebViewAc.this, "连接中.." + ssid + "\n" + password, Toast.LENGTH_SHORT).show();
            new AirKissTask(WebViewAc.this, new AirKissEncoder(ssid, password)).execute();
        }
    }


    private class AirKissTask extends AsyncTask<Void, Void, Void> implements DialogInterface.OnDismissListener {
        private static final int PORT = 10000;
        private final byte DUMMY_DATA[] = new byte[1500];
        private static final int REPLY_BYTE_CONFIRM_TIMES = 0;

        private ProgressDialog mDialog;
        private Context mContext;
        private DatagramSocket mSocket;

        private char mRandomChar;
        private AirKissEncoder mAirKissEncoder;

        private volatile boolean mDone = false;

        public AirKissTask(Activity activity, AirKissEncoder encoder) {
            mContext = activity;
            mDialog = new ProgressDialog(mContext);
            mDialog.setOnDismissListener(this);
            mRandomChar = encoder.getRandomChar();
            mAirKissEncoder = encoder;
        }

        @Override
        protected void onPreExecute() {
            this.mDialog.setMessage("Connecting :)");
//            this.mDialog.show();

            new Thread(new Runnable() {
                public void run() {
                    byte[] buffer = new byte[15000];
                    try {
                        DatagramSocket udpServerSocket = new DatagramSocket(12476);
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        int replyByteCounter = 0;
                        udpServerSocket.setSoTimeout(5000);

                        while (true) {
                            // if (getStatus() == Status.FINISHED)
                            //     break;

                            try {
                                udpServerSocket.receive(packet);
                                byte receivedData[] = packet.getData();
                                String receivedDataStr;
                                receivedDataStr = new String(receivedData);
                                Log.d("tag", "---------receive: " + receivedDataStr.length());
                                //for (byte b : receivedData) {
                                // if (receivedDataStr.length() > 10000)
                                mDone = true;
                                show();
                                break;
                                //}
                                // if (replyByteCounter > REPLY_BYTE_CONFIRM_TIMES) {
                                //     mDone = true;
                                //     break;
                                // }
                            } catch (SocketTimeoutException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        udpServerSocket.close();
                    } catch (SocketException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        private void sendPacketAndSleep(int length) {

            try {
                DatagramPacket pkg = new DatagramPacket(DUMMY_DATA,
                        length,
                        InetAddress.getByName("255.255.255.255"),
                        PORT);
                mSocket.send(pkg);
                Thread.sleep(4);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                mSocket = new DatagramSocket();
                mSocket.setBroadcast(true);
            } catch (Exception e) {
                e.printStackTrace();
            }

            int encoded_data[] = mAirKissEncoder.getEncodedData();

            for (int i = 0; i < encoded_data.length; ++i) {
                sendPacketAndSleep(encoded_data[i]);
                if (i % 200 == 0) {
                    if (isCancelled() || mDone)
                        return null;
                }
            }

            return null;
        }

        @Override
        protected void onCancelled(Void params) {
//            Toast.makeText(getApplicationContext(), "Air Kiss Cancelled.", Toast.LENGTH_LONG).show();
        }

        @Override
        protected void onPostExecute(Void params) {
            if (mDialog.isShowing()) {
                mDialog.dismiss();
            }

            String result;
            if (mDone) {
                result = "Air Kiss Successfully Done!";
            } else {
                result = "Air Kiss Timeout.";
            }
//            Toast.makeText(getApplicationContext(), result, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            if (mDone)
                return;

            this.cancel(true);
        }
    }

}
