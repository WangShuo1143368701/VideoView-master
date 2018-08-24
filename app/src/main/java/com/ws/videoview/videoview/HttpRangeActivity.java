package com.ws.videoview.videoview;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class HttpRangeActivity extends AppCompatActivity
{

    private Button mButton;
    private TextView mTvMsg;

    private String result = "";
    private long start = 0;
    private long stop = 1024 * 200;
    private int times = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_http_range);
        initView();
    }

    private void initView()
    {
        mTvMsg = (TextView) findViewById(R.id.text);
        mButton = (Button) findViewById(R.id.btnRange);
        mButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                Toast.makeText(HttpRangeActivity.this,"btn",Toast.LENGTH_SHORT).show();
                new Thread(moreThread).start();
            }
        });
    }


    private Thread moreThread = new Thread(){
        public void run() {
            Log.d("wangshuo", "moreThread");
            HttpURLConnection connection = null;
            try {
                URL url = new URL("http://1252507790.vod2.myqcloud.com/ada6ba06vodtranssgp1252507790/8e7cdaf47447398156226560497/v.f830.mp4");
                connection = (HttpURLConnection) url.openConnection();
                //connection.setRequestMethod("GET");
                //connection.setDoInput(true);
                // 设置开始下载的位置和结束下载的位置，单位为字节
                connection.setRequestProperty("Range", "bytes=" + start + "-" + stop);
                //connection.connect();
                Log.d("wangshuo",  "Range");
                String path = Environment.getExternalStorageDirectory().getPath()  + "/1/a.mp4.download";
                // 断点下载使用的文件对象RandomAccessFile
                Log.d("wangshuo",  "getPath");
                RandomAccessFile access = new RandomAccessFile(path, "rw");
                Log.d("wangshuo",  "RandomAccessFile");
                // 移动指针到开始位置
                access.seek(start);
                Log.d("wangshuo",  "seek");
                InputStream is = null;
                Log.e("wangshuo", connection.getResponseCode() + "");
                if(connection.getResponseCode() == 206){
                    Log.e("wangshuo", 206 + "");
                    is = connection.getInputStream();
                    int count = 0;
                    byte[] buffer = new byte[1024];
                    while((count = is.read(buffer)) != -1){
                        access.write(buffer, 0, count);
                    }
                }

                if(access != null){
                    access.close();
                }
                if(is != null){
                    is.close();
                }

//                start = stop + 1;
//                stop += 1024*10;   // 每次下载1M

                Message msg = Message.obtain();
                msg.what = 0;
                result += "文件" + times + "下载成功" + ":" + start + "---" + stop + "\n";
                moreHandler.sendMessage(msg);
            } catch (MalformedURLException e) {
                e.printStackTrace();
                Log.e("wangshuo", "MalformedURLException");
            } catch (IOException e) {
                Log.e("wangshuo", "IOException");
                e.printStackTrace();
            } finally {
                if(connection != null){
                    connection.disconnect();
                }
            }
        };
    };

    private Handler moreHandler = new Handler(){
        public void handleMessage(android.os.Message msg) {
//            if(msg.what == 0 && result!=null){
//                if(times >= 10){
//                    Message msg1 = Message.obtain();
//                    msg1.what = 1;
//                    moreHandler.sendMessage(msg1);
//                }else{
//                    new Thread(moreThread).start();
//                    times += 1;
//                }
//
//                mTvMsg.setText(result);
//            }
            mTvMsg.setText(result);
        };
    };
}
