package lyon.usb.usbdevicemanager;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.InputDevice;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileInputStream;
import com.github.mjdev.libaums.partition.Partition;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import lyon.usb.usbdevicemanager.USBManager.FileUtil2;
import lyon.usb.usbdevicemanager.USBManager.UsbStateChangeReceiver;
import lyon.usb.usbdevicemanager.USBManager.UsbStatusChangeEvent;

public class MainActivity extends AppCompatActivity {
    String TAG = MainActivity.class.getSimpleName();
    Context context;
    EditText mUDiskEdt;
    Button mUDiskWrite;
    Button mUDiskRead;
    TextView mUDiskShow;
    private UsbMassStorageDevice[] storageDevices;
    private UsbFile cFolder;

    //自定义U盘读写权限
    public static final String ACTION_USB_PERMISSION = "com.example.usbreadwriterdaemon.USB_PERMISSION";
    private final static String U_DISK_FILE_NAME = "u_disk.txt";


    @SuppressLint("HandlerLeak")
    public Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG,"handleMessage Message:"+msg.toString());
            switch (msg.what) {
                case 100:
                    ToastUtile.showText(context,"保存成功");
                    break;
                case 101:
                    String txt = msg.obj.toString();
                    if (!TextUtils.isEmpty(txt))
                        mUDiskShow.setText("读取到的数据是：" + txt);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        setContentView(R.layout.activity_main);

        /**
         *      UsbManager.ACTION_USB_ACCESSORY_ATTACHED;//配件插入
         * 		UsbManager.ACTION_USB_ACCESSORY_DETACHED;//附件已分离
         *      UsbManager.ACTION_USB_DEVICE_ATTACHED;//设备插入
         *      UsbManager.ACTION_USB_DEVICE_DETACHED;//设备分离
         *  ————————————————
         * 版权声明：本文为CSDN博主「jsjwangmingmin」的原创文章，遵循CC 4.0 by-sa版权协议，转载请附上原文出处链接及本声明。
         * 原文链接：https://blog.csdn.net/jsjwangmingmin/article/details/88854001
         */
        InputManager inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        int[] devIds = inputManager.getInputDeviceIds();
        for(int devId : devIds){
            InputDevice device = inputManager.getInputDevice(devId);
            if(device != null){
                Log.e(TAG, "Input device name : " + device.getName() + "---- info: " +device.toString());
            }
        }

        /**
         * https://blog.csdn.net/da_caoyuan/article/details/81408497
         */
        EventBus.getDefault().register(this);//EventBus 注册
        registerUDiskReceiver();//usb插拔广播 注册

        mUDiskRead = (Button)findViewById(R.id.u_disk_read);
        mUDiskWrite = (Button)findViewById(R.id.u_disk_write);
        mUDiskShow = (TextView) findViewById(R.id.u_disk_show);
        mUDiskEdt = (EditText)findViewById(R.id.u_disk_edt) ;
        mUDiskRead.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                readFromUDisk();
            }
        });

        mUDiskWrite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final String content = mUDiskEdt.getText().toString().trim();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        saveText2UDisk(content);
                    }
                });
            }
        });


    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onNetworkChangeEvent(UsbStatusChangeEvent event) {
        if (event.isConnected) {
            //接收到U盘插入广播，尝试读取U盘设备数据
            redUDiskDevsList();
        } else if (event.isGetPermission) {
            UsbDevice usbDevice = event.usbDevice;

            //用户已授权，可以进行读取操作
            Log.i(TAG, "onNetworkChangeEvent: ");
            ToastUtile.showText(context,"onReceive: 权限已获取");
            readDevice(getUsbMass(usbDevice));
        } else {

        }
    }


    /**
     * @description 保存数据到U盘，目前是保存到根目录的
     * @author ldm
     * @time 2017/9/1 17:17
     */
    private void saveText2UDisk(String content) {
        //项目中也把文件保存在了SD卡，其实可以直接把文本读取到U盘指定文件
        File file = FileUtil2.getSaveFile(getPackageName() + File.separator + FileUtil2.DEFAULT_BIN_DIR, U_DISK_FILE_NAME);
        try {
            FileWriter fw = new FileWriter(file);
            fw.write(content);
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (null != cFolder) {
            FileUtil2.saveSDFile2OTG(file, cFolder);
            mHandler.sendEmptyMessage(100);
        }
    }

    StringBuffer stringBuffer = new StringBuffer();

    private void readFromUDisk() {
        UsbFile[] usbFiles = new UsbFile[0];
        try {
            if (null != cFolder) {
                usbFiles = cFolder.listFiles();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        if (null != usbFiles && usbFiles.length > 0) {

            for (UsbFile usbFile : usbFiles) {
                stringBuffer.append(", " + usbFile.getName());
                if (usbFile.getName().equals(U_DISK_FILE_NAME)) {
                    readTxtFromUDisk(usbFile);
                }
            }
            //mUDiskShow.setText("文件名：" + stringBuffer.toString());
        }else{
            ToastUtile.showText(context,"the Usb is null");
        }
    }


    /**
     * @description U盘设备读取
     * @author ldm
     * @time 2017/9/1 17:20
     */
    private void redUDiskDevsList() {
        //设备管理器
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        //获取U盘存储设备
        storageDevices = UsbMassStorageDevice.getMassStorageDevices(this);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        //一般手机只有1个OTG插口
        for (UsbMassStorageDevice device : storageDevices) {
            //读取设备是否有权限，
            if (usbManager.hasPermission(device.getUsbDevice())) {
                ToastUtile.showText(context,"有权限");
                readDevice(device);
            } else {
                ToastUtile.showText(context,"没有权限，进行申请");
                //没有权限，进行申请，此时系统会有个弹框，询问你是否同意，当然我们应该同意啦！
                usbManager.requestPermission(device.getUsbDevice(), pendingIntent);
            }
        }
        if (storageDevices.length == 0) {
            ToastUtile.showText(context,"请插入可用的U盘");
        }
    }


    private UsbMassStorageDevice getUsbMass(UsbDevice usbDevice) {
        for (UsbMassStorageDevice device : storageDevices) {
            if (usbDevice.equals(device.getUsbDevice())) {
                return device;
            }
        }
        return null;
    }

    private void readDevice(UsbMassStorageDevice device) {
        try {
            device.init();//初始化
            //设备分区
            Partition partition = device.getPartitions().get(0);

            //文件系统
            FileSystem currentFs = partition.getFileSystem();
            currentFs.getVolumeLabel();//可以获取到设备的标识

            //通过FileSystem可以获取当前U盘的一些存储信息，包括剩余空间大小，容量等等
            Log.e("Capacity: ", currentFs.getCapacity() + "");
            Log.e("Occupied Space: ", currentFs.getOccupiedSpace() + "");
            Log.e("Free Space: ", currentFs.getFreeSpace() + "");
            Log.e("Chunk size: ", currentFs.getChunkSize() + "");

            ToastUtile.showText(context,"可用空间：" + currentFs.getFreeSpace());


            cFolder = currentFs.getRootDirectory();//设置当前文件对象为根目录


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void readTxtFromUDisk(UsbFile usbFile) {
        Log.i(TAG, "readTxtFromUDisk: ");
        UsbFile descFile = usbFile;
        //读取文件内容
        InputStream is = new UsbFileInputStream(descFile);
        //读取秘钥中的数据进行匹配
        StringBuilder sb = new StringBuilder();
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(is));
            String read;
            while ((read = bufferedReader.readLine()) != null) {
                sb.append(read);
            }
            Message msg = mHandler.obtainMessage();
            msg.what = 101;
            msg.obj = sb;
            mHandler.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * usb插拔广播 注册
     */
    private void registerUDiskReceiver() {
        IntentFilter usbDeviceStateFilter = new IntentFilter();
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        usbDeviceStateFilter.addAction("android.hardware.usb.action.USB_STATE");

        usbDeviceStateFilter.addAction(ACTION_USB_PERMISSION); //自定义广播

        registerReceiver(new UsbStateChangeReceiver(), usbDeviceStateFilter);


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }


    }
}
