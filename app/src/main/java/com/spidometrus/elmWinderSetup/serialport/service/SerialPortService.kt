package com.spidometrus.elmWinderSetup.serialport.service

import android.app.IntentService
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import com.spidometrus.elmWinderSetup.serialport.SerialPort
import com.spidometrus.elmWinderSetup.serialport.connect.SerialPortConnect
import com.spidometrus.elmWinderSetup.serialport.tools.LogUtil
import com.spidometrus.elmWinderSetup.serialport.tools.SerialPortTools
import java.io.IOException

/**
 * SerialPortService Служба приема данных
 * @UpdateContent
 * 1. Исправлена проблема с искаженным китайским кодом
 * @Author Shanya
 * @Date 2021-12-10
 * @Version 4.1.2
 */
class SerialPortService : IntentService("SerialPortService") {

    override fun onCreate() {
        super.onCreate()
        LogUtil.log("Включена традиционная служба приема сообщений по Bluetooth")
    }

    override fun onHandleIntent(intent: Intent?) {
        var len: Int
        var receivedData: String
        var buffer = ByteArray(0)
        var flag = false

        while (SerialPortConnect.connectStatus) {
            Thread.sleep(100)
            if (SerialPortConnect.connectStatus){
                try{
                    len = SerialPortConnect.inputStream?.available()!!
                }
                catch (e: IOException){
                    Log.d("Exeption","Вылет по закрытому сокету")
                    Log.d("Exeption",e.message.toString())
                    return
                }
                while (len != 0) {
                    flag = true
                    buffer = ByteArray(len)
                    SerialPortConnect.inputStream?.read(buffer)
                    Thread.sleep(10)
                    len = SerialPortConnect.inputStream?.available()!!
                }
            }
            if (flag) {
                receivedData = if (SerialPort.readDataType == SerialPort.READ_STRING) {
                    SerialPortTools.bytes2string(buffer, "GBK")
                } else {
                    val sb = StringBuilder()
                    for (i in buffer) {
                        sb.append("${String.format("%2X", i)} ")
                    }

                    if (SerialPort.hexStringToStringFlag) {
                        SerialPort._hexStringToString(sb.toString()).toString()
                    } else {
                        sb.toString()
                    }
                }
                LogUtil.log("传统设备收到数据", receivedData)
                MainScope().launch {
                    SerialPort.receivedDataCallback?.invoke(receivedData)
                    SerialPort.receivedBytesCallback?.invoke(buffer)
                }
                flag = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtil.log("传统蓝牙收消息服务关闭")
        SerialPortConnect.disconnectResult(this)
    }
}