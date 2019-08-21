package lyon.usb.usbdevicemanager.USBManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import lyon.usb.usbdevicemanager.MainActivity;
import lyon.usb.usbdevicemanager.ToastUtile;

public class UsbStateChangeReceiver extends BroadcastReceiver {
    private static final String TAG = "UsbStateChangeReceiver";

    private boolean isConnected;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
            isConnected = true;
            ToastUtile.showText(context,"onReceive: USB设备已连接");

            UsbDevice device_add = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device_add != null) {
                EventBus.getDefault().post(new UsbStatusChangeEvent(isConnected));
                if(context instanceof MainActivity){
                    MainActivity mainActivity = (MainActivity)context;
                    mainActivity.mHandler.sendEmptyMessage(300);
                    mainActivity.onNetworkChangeEvent(new UsbStatusChangeEvent(isConnected));
                }
            } else {
                ToastUtile.showText(context,"onReceive: device is null");
            }


        } else if (action.equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
            //Log.i(TAG, "onReceive: USB设备已分离");
            isConnected = false;
            ToastUtile.showText(context,"onReceive: USB设备已拔出");

            EventBus.getDefault().post(new UsbStatusChangeEvent(isConnected));
        } else if (action.equals(MainActivity.ACTION_USB_PERMISSION)) {

            UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            //允许权限申请
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                if (usbDevice != null) {
                    Log.i(TAG, "onReceive: 权限已获取");
                    EventBus.getDefault().post(new UsbStatusChangeEvent(true, usbDevice));
                } else {
                    ToastUtile.showText(context,"没有插入U盘");
                }
            } else {
                ToastUtile.showText(context,"未获取到U盘权限");
            }
        } else {
            //Log.i(TAG, "onReceive: action=" + action);
            ToastUtile.showText(context,"action= " + action);
        }


    }
}
