package com.spidometrus.elmWinderSetup.serialport.connect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.spidometrus.elmWinderSetup.serialport.SerialPort
import com.spidometrus.elmWinderSetup.serialport.server.SerialPortServer
import com.spidometrus.elmWinderSetup.serialport.strings.SerialPortToast
import com.spidometrus.elmWinderSetup.serialport.tools.LogUtil
import com.spidometrus.elmWinderSetup.serialport.tools.ToastUtil

/**
 * BluetoothStatusBroadcastReceiver Изменение статуса подключения Bluetooth широковещательный приемник
 * Здесь обрабатывается только отключение традиционного Bluetooth
 * Мониторинг отключения Bluetooth в режиме реального времени, если Bluetooth отключен, он автоматически включится
 * @Author Shanya
 * @Date 2021-7-21
 * @Version 4.0.0
 */
@SuppressLint("MissingPermission")
class BluetoothStatusBroadcastReceiver:BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                SerialPortConnect.connectedLegacyDevice?.let {
                    SerialPort._legacyDisconnect()
                }
                SerialPortServer.connectedDevice?.let {
                    SerialPortServer.__disconnect()
                }
            }

            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0)) {
                    BluetoothAdapter.STATE_TURNING_ON -> {
                        LogUtil.log("Bluetooth успешно включен")
                        context?.let {
                            ToastUtil.toast(it, SerialPortToast.openBluetoothSucceeded)
                        }
                    }
                    BluetoothAdapter.STATE_TURNING_OFF -> {
                        if (!SerialPort.bluetoothAdapter.enable()) {
                            LogUtil.log("Bluetooth не удалось включить")
                            context?.let {
                                ToastUtil.toast(it, SerialPortToast.openBluetoothFailed)
                            }
                        }
                    }
                }
            }
        }
    }
}