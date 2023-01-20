package com.spidometrus.elmWinderSetup.serialport.connect

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import com.spidometrus.elmWinderSetup.serialport.SerialPort
import com.spidometrus.elmWinderSetup.serialport.discovery.Device
import com.spidometrus.elmWinderSetup.serialport.service.SerialPortService
import com.spidometrus.elmWinderSetup.serialport.strings.SerialPortToast
import com.spidometrus.elmWinderSetup.serialport.tools.LogUtil
import com.spidometrus.elmWinderSetup.serialport.tools.SPUtil
import com.spidometrus.elmWinderSetup.serialport.tools.SerialPortTools
import com.spidometrus.elmWinderSetup.serialport.tools.ToastUtil
import java.io.IOException
import java.io.InputStream
import java.lang.NullPointerException
import java.lang.RuntimeException
import java.util.*
import kotlin.collections.HashMap


//Новый интерфейс статуса подключения
typealias ConnectionStatusCallback = (status: Boolean, bluetoothDevice: BluetoothDevice?) -> Unit
//Старый интерфейс состояния подключения
@Deprecated(message = "该回调在4.0.0版本开始被弃用",replaceWith = ReplaceWith(expression = "ConnectionStatusCallback"))
typealias ConnectStatusCallback = (status: Boolean, device: Device) -> Unit
//Интерфейс подключения
typealias ConnectCallback = () -> Unit
//Интерфейс результата подключения
@Deprecated(message = "Этот метод устарел с версии 4.2.0",replaceWith = ReplaceWith("ConnectionStatusCallback"))
typealias ConnectionResultCallback = (result: Boolean, bluetoothDevice: BluetoothDevice?) -> Unit
//Ble device can work callback
typealias BleCanWorkCallback = () -> Unit



/**
 * SerialPortConnect Класс управления подключением
 * @UpdateContent
 * 1. Частичный рефакторинг кода
 * 2. Новая пара BLE Поддержка устройств
 * @Author Shanya
 * @Date 2021-7-21
 * @Version 4.0.0
 */
@SuppressLint("MissingPermission")
internal object SerialPortConnect {

    //Традиционное оборудование UUID
    internal var UUID_LEGACY = "00001101-0000-1000-8000-00805F9B34FB"
    //БЛЕДНЫЙ
    internal var UUID_BLE = "0000ffe1-0000-1000-8000-00805f9b34fb"
    //BLE получить UUID
    internal var UUID_BLE_READ = ""
    //BLE отправлять UUID
    internal var UUID_BLE_SEND = ""
    //Следует ли включать интервал для автоматического повторного подключения
    internal var autoReconnectAtIntervalsFlag = false
    //Включите интервал, автоматически повторно подключите единицу измерения временного интервала мс
    internal var autoReconnectIntervalsTime = 10000
    //Связь по Bluetooth Socket
    internal var bluetoothSocket: BluetoothSocket?= null
    //Связь по Bluetooth inputStream
    internal var inputStream: InputStream?= null
    //Статус подключения
    internal var connectStatus = false
    //Флаг автоматического подключения (после выполнения автоматического подключения он true）
    internal var autoConnectFlag = false
    //Подключенное традиционное оборудование
    internal var connectedLegacyDevice: BluetoothDevice ?= null
    //Уже подключенный BLE оборудование
    internal var connectedBleDevice: BluetoothDevice ?= null
    //Адрес последнего успешно подключенного устройства
    internal var lastDeviceAddress = ""

    internal var readGattCharacteristic: BluetoothGattCharacteristic? = null
    internal var sendGattCharacteristic: BluetoothGattCharacteristic? = null

    internal var bluetoothGatt: BluetoothGatt? = null

    internal var gattCharacteristicList = HashMap<String, Int>()

    internal var gattServiceList = HashMap<String, HashMap<String, Int>>()
    /**
     * bluetoothGattCallback BLE Обратный вызов подключения устройства
     * @Author Shanya
     * @Date 2021-7-21
     * @Version 4.0.0
     */
    private val bluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            LogUtil.log("MTU", mtu.toString())
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            SerialPort.bleCanWorkCallback?.invoke()
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            //Устройство BLE подключено успешно
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedResult(
                    SerialPort.newContext,
                    true,
                    gatt,
                    gatt?.device
                )
            }

            //Устройство BLE отключено
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (connectedBleDevice == null) {
                    connectedResult(
                        SerialPort.newContext,
                        false,
                        gatt,
                        gatt?.device
                    )
                }else {
                    disconnectResult(
                        SerialPort.newContext
                    )
                    gatt?.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gattCharacteristicList.clear()
                gatt?.services?.let {
                    for (gattService in it) {
                        val gattCharacteristics = gattService.characteristics
                        for (gattCharacteristic in gattCharacteristics) {
                            val uuid = gattCharacteristic.uuid.toString()
                            val properties = gattCharacteristic.properties
                            gattCharacteristicList[uuid] = properties
                            if (UUID_BLE_SEND == "") {
                                if (uuid == UUID_BLE) {
                                    sendGattCharacteristic = gattCharacteristic
                                }
                            } else {
                                if (uuid == UUID_BLE_SEND) {
                                    sendGattCharacteristic = gattCharacteristic
                                }
                            }
                            if (UUID_BLE_READ == "") {
                                if (uuid == UUID_BLE) {
                                    readGattCharacteristic = gattCharacteristic
                                }
                            }
                            else {
                                if (uuid == UUID_BLE_READ) {
                                    readGattCharacteristic = gattCharacteristic
                                }
                            }
                        }
                        gattServiceList[gattService.uuid.toString()] =
                            gattCharacteristicList
                    }
                }
            }
            try {
                val isEnableNotify = gatt?.setCharacteristicNotification(readGattCharacteristic, true)
                if (isEnableNotify == true) {
                    val descriptorsList = readGattCharacteristic?.descriptors
                    descriptorsList?.let {
                        if (it.size > 0) {
                            for (descriptors in it) {
                                descriptors.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                bluetoothGatt?.writeDescriptor(descriptors)
                            }
                        }
                    }
                }
            } catch (e: NullPointerException) {
                Log.e("SerialPort", "UUID, полученный BLE, неверен, пожалуйста, проверьте!")
                throw RuntimeException("BLE接收UUID不正确，请检查！")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                LogUtil.log("BLE设备发送数据成功")

            }
            else if (status == BluetoothGatt.GATT_FAILURE) {
                LogUtil.log("BLE设备发送数据失败")
            }
        }


        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            val value = characteristic?.value
            if (value != null && value.isNotEmpty()) {
                val receivedData = if (SerialPort.readDataType == SerialPort.READ_STRING) {
                    SerialPortTools.bytes2string(value, "GBK")
                } else {
                    val sb = StringBuilder()
                    for (i in value) {
                        sb.append("${String.format("%2X", i)} ")
                    }

                    if (SerialPort.hexStringToStringFlag) {
                        SerialPort._hexStringToString(sb.toString()).toString()
                    } else {
                        sb.toString()
                    }
                }
                LogUtil.log("BLE设备收到数据", receivedData)
                MainScope().launch {
                    SerialPort.receivedDataCallback?.invoke(receivedData)
                    SerialPort.receivedBytesCallback?.invoke(value)
                }

            }

        }
    }

    /**
     * connectBle 连接BLE设备
     * @param context 上下文
     * @param address 设备地址
     * @Author Shanya
     * @Date 2022-1-12
     * @Version 4.1.4
     */
    internal fun connectBle(context: Context, address: String) {

        val bluetoothDevice =
            SerialPort.bluetoothAdapter.getRemoteDevice(address)
        if (bluetoothDevice.type >= 2) {
            bluetoothDevice.connectGatt(context, false,
                bluetoothGattCallback
            )
        } else {
            connectedResult(
                context,
                false,
                null,
                bluetoothDevice
            )
        }
    }

    /**
     * connectLegacy 连接传统设备（处理大逻辑结构）
     * @param context 上下文
     * @param address 设备地址
     * @Author Shanya
     * @Date 2021-7-21
     * @Version 4.0.0
     */
    internal fun connectLegacy(context: Context, address: String) {
        Thread {
            bluetoothSocket?.isConnected?.let { bluetoothSocketIsConnected ->
                if (bluetoothSocketIsConnected) {
                    SerialPort.connectCallback?.invoke()
                    if (autoConnectFlag) {
                        ToastUtil.toast(context, SerialPortToast.disconnectFirst)
                    }
                } else {
                    _connectLegacy(
                        context,
                        address
                    )
                }
            } ?: _connectLegacy(
                context,
                address
            )
        }.start()
    }


    /**
     * _connectLegacy 连接传统设备（处理具体连接细节）
     * @param context 上下文
     * @param address 设备地址
     * @Author Shanya
     * @Date 2022-1-12
     * @Version 4.1.4
     */
    internal fun _connectLegacy(context: Context, address: String) {
        var bluetoothDevice:BluetoothDevice ?= null
        try {
            bluetoothDevice =
                SerialPort.bluetoothAdapter.getRemoteDevice(address)
            if (bluetoothDevice.type == BluetoothDevice.DEVICE_TYPE_CLASSIC ||
                bluetoothDevice.type == BluetoothDevice.DEVICE_TYPE_DUAL
            ) {
                bluetoothSocket =
                    bluetoothDevice.createRfcommSocketToServiceRecord(UUID.fromString(
                        UUID_LEGACY
                    ))
                bluetoothSocket?.connect()
                connectedResult(
                    context,
                    true,
                    null,
                    bluetoothDevice
                )
                inputStream = bluetoothSocket?.inputStream
                context.startService(Intent(context, SerialPortService::class.java))
            } else {
                connectedResult(
                    context,
                    false,
                    null,
                    bluetoothDevice
                )
            }

        } catch (e: IOException) {
            e.printStackTrace()
            connectedResult(
                context,
                false,
                null,
                bluetoothDevice
            )
            try {
                bluetoothSocket?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * bleDisconnect BLE设备断开连接
     * @Author Shanya
     * @Date 2021-7-21
     * @Version 4.0.0
     */
    internal fun bleDisconnect() {
        bluetoothGatt?.disconnect()
    }

    /**
     * legacyDisconnect 传统设备断开连接
     * @param context 上下文
     * @Author Shanya
     * @Date 2021-7-21
     * @Version 4.0.0
     */
    internal fun legacyDisconnect(context: Context) {
        context.stopService(Intent(context, SerialPortService::class.java))
    }

    /**
     * connectedResult 连接结果报告
     * @param context 上下文
     * @param result 连接结构
     * @param gatt gatt
     * @param bluetoothDevice 连接设备
     * @Author Shanya
     * @Date 2021-7-21
     * @Version 4.0.0
     */
    private fun connectedResult(
        context: Context?,
        result: Boolean,
        gatt: BluetoothGatt?,
        bluetoothDevice: BluetoothDevice?
    ) {
        if (result) {
            if (bluetoothDevice?.type == 2) {
                bluetoothGatt = gatt
                bluetoothGatt?.discoverServices()
            }
            val device = Device(
                bluetoothDevice?.name ?: "",
                bluetoothDevice?.address ?: "",
                bluetoothDevice?.type ?: 1)
            connectStatus = true
            SerialPort.connectCallback?.invoke()
            SerialPort.connectStatusCallback?.invoke(true, device)
            SerialPort.connectionStatusCallback?.invoke(true, bluetoothDevice)
            SerialPort.connectionResultCallback?.invoke(true, bluetoothDevice)
            if (bluetoothDevice?.type == 2) {
                connectedBleDevice = bluetoothDevice
                LogUtil.log("连接BLE设备成功","设备地址: ${bluetoothDevice.address}")
            } else {
                connectedLegacyDevice = bluetoothDevice
                LogUtil.log("连接传统设备成功","设备地址: ${bluetoothDevice?.address}")
            }
            context?.let {
                SPUtil.putDeviceInfo(it, bluetoothDevice)
                lastDeviceAddress = bluetoothDevice?.address?:""
                ToastUtil.toast(it, SerialPortToast.connectSucceeded)
            }
        } else {
            SerialPort.connectCallback?.invoke()
            SerialPort.connectStatusCallback?.invoke(false, Device("", "", 1))
            SerialPort.connectionResultCallback?.invoke(false, null)
            SerialPort.connectionStatusCallback?.invoke(false, null)
            LogUtil.log("连接失败")
            context?.let {
                ToastUtil.toast(it, SerialPortToast.connectFailed)
            }
        }

    }

    /**
     * disconnectResult 断开结果报告
     * @param context 上下文
     * @Author Shanya
     * @Date 2021-7-21
     * @Version 4.0.0
     */
    internal fun disconnectResult(context: Context?) {
        connectedLegacyDevice?.let {
            bluetoothSocket?.close()
        }
        SerialPort.connectCallback?.invoke()
        connectedBleDevice?.let {
            val device = Device(it.name, it.address, it.type)
            SerialPort.connectStatusCallback?.invoke(false, device)
            SerialPort.connectionStatusCallback?.invoke(false, it)
            connectStatus = false
            connectedBleDevice = null
            context?.let {context ->
                ToastUtil.toast(context, SerialPortToast.disconnect)
            }
            LogUtil.log("断开BLE设备连接","设备地址: ${it.address}")
        }
        connectedLegacyDevice?.let {
            val device = Device(it.name, it.address, it.type)
            SerialPort.connectStatusCallback?.invoke(false, device)
            SerialPort.connectionStatusCallback?.invoke(false, it)
            connectStatus = false
            connectedLegacyDevice = null
            context?.let {context ->
                ToastUtil.toast(context, SerialPortToast.disconnect)
            }
            LogUtil.log("断开传统设备连接","设备地址: ${it.address}")
        }
    }

    /**
     * 间隔自动重连相关
     * @param handler 定时任务的处理
     * @param runnable 具体的任务操作
     * @fun autoConnect 开始任务
     * @fun cancelAutoConnect 停止任务
     * @Author Shanya
     * @Date 2021-7-21
     * @Version 4.0.0
     */
    private val handler = Handler()
    private val runnable = object : Runnable {
        override fun run() {
            if (autoReconnectAtIntervalsFlag && !connectStatus) {
                SerialPort.newContext?.let { context ->
                    if (lastDeviceAddress != "") {
                        LogUtil.log("间隔自动重连","设备地址: $lastDeviceAddress")
                        SPUtil.getDeviceType(context)?.let { type ->
                            when (type) {
                                "1" -> {
                                    SPUtil.getDeviceAddress(context)?.let {
                                        lastDeviceAddress = it
                                        _connectLegacy(
                                            context,
                                            lastDeviceAddress
                                        )
                                    }
                                }
                                "2" -> {
                                    SPUtil.getDeviceAddress(context)?.let {
                                        lastDeviceAddress = it
                                        connectBle(
                                            context,
                                            lastDeviceAddress
                                        )
                                    }
                                }
                                else -> {

                                }
                            }
                        }
                    }
                }
            }
            handler.postDelayed(this, autoReconnectIntervalsTime.toLong())
        }
    }
    internal fun autoConnect() {
        handler.postDelayed(
            runnable,
            autoReconnectIntervalsTime.toLong())
    }
    internal fun cancelAutoConnect() {
        handler.removeCallbacks(
            runnable
        )
    }

    /**
     * Request an MTU size used for a given connection.
     * When performing a write request operation (write without response), the data sent is truncated to the MTU size. This function may be used to request a larger MTU size to be able to send more data at once.
     * A BluetoothGattCallback.onMtuChanged callback will indicate whether this operation was successful.
     * Requires android.Manifest.permission.BLUETOOTH permission.
     * Returns:true, if the new MTU value has been requested successfully
     */
    fun requestMtu(mtu: Int):Boolean {
        val boolean = bluetoothGatt?.requestMtu(mtu) == false
        Thread.sleep(600)
        return boolean
    }
}