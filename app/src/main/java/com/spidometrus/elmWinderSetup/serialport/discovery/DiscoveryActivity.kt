package com.spidometrus.elmWinderSetup.serialport.discovery

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.permissionx.guolindev.PermissionX
import kotlinx.android.synthetic.main.activity_discovery.*
import kotlinx.android.synthetic.main.device_cell.view.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import com.spidometrus.elmWinderSetup.R
import com.spidometrus.elmWinderSetup.serialport.SerialPort
import com.spidometrus.elmWinderSetup.serialport.strings.SerialPortToast
import com.spidometrus.elmWinderSetup.serialport.discovery.SerialPortDiscovery.pairedDevicesListBD
import com.spidometrus.elmWinderSetup.serialport.discovery.SerialPortDiscovery.unPairedDevicesListBD
import com.spidometrus.elmWinderSetup.serialport.tools.LogUtil
import com.spidometrus.elmWinderSetup.serialport.tools.ToastUtil
import java.util.*
import kotlin.collections.ArrayList

/**
 * DiscoveryActivity 搜索页面Activity
 * @Author Shanya
 * @Date 2021-5-28
 * @Version 3.1.0
 */
@SuppressLint("MissingPermission")
class DiscoveryActivity : AppCompatActivity() {

    //Диалоговое окно хода подключения
    private lateinit var connectProcessDialog: Dialog

    /**
    * Activity创建
    * @param savedInstanceState
    * @Author Shanya
    * @Date 2021/5/28
    * @Version 3.1.0
    */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discovery)

        LogUtil.log("Создание встроенной страницы поиска")

        //Проверьте, включен ли Bluetooth
        if (!SerialPort.bluetoothAdapter.isEnabled) {
            SerialPort.bluetoothAdapter.enable()
        }

        //Инициализируйте диалоговое окно хода подключения
        connectProcessDialog = Dialog(this)
        connectProcessDialog.setContentView(R.layout.progress_dialog_layout)
        connectProcessDialog.setCancelable(false)



        //Мониторинг статуса поиска
        SerialPortDiscovery.discoveryStatusLiveData.observe(this) {
            if (it) {
                swipeRedreshLayout.isRefreshing = true
                title = getString(R.string.discovery_discovering)
            } else {
                swipeRedreshLayout.isRefreshing = false
                title = getString(R.string.discovery_select_device_connect)
            }
        }

        //Мониторинг подключения
        SerialPort.setConnectListener {
            finish()
        }

        //Выпадающий монитор поиска
        swipeRedreshLayout.setOnRefreshListener {
            doDiscovery()
        }

        val requestList = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestList.add(Manifest.permission.BLUETOOTH_SCAN)
            requestList.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            requestList.add(Manifest.permission.BLUETOOTH_CONNECT)
            requestList.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestList.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (requestList.isNotEmpty()) {
            PermissionX.init(this)
                .permissions(requestList)
                .explainReasonBeforeRequest()
                .onExplainRequestReason { scope, deniedList ->
                    val message = getString(R.string.discovery_permission_message)
                    scope.showRequestReasonDialog(deniedList, message,
                        getString(R.string.discovery_permission_allow),
                        getString(R.string.discovery_permission_deny))
                }
                .request { allGranted, grantedList, deniedList ->
                    if (allGranted) {
                        LogUtil.log("Разрешение Bluetooth было успешно получено")
                        //Инициализация списка устройств
                        recyclerViewInit()
                        //Начните поиск
                        doDiscovery()
                    } else {
                        ToastUtil.toast(this, SerialPortToast.permission)
                        LogUtil.log("Не удалось получить разрешение Bluetooth")
                        finish()
                    }
                }
        }
    }

    /**
    * Начните выполнять поиск устройства
    * @Author Shanya
    * @Date 2021/5/28
    * @Version 3.1.0
    */
    private fun doDiscovery() {
        title = getString(R.string.discovery_discovering)
        SerialPortDiscovery.startLegacyScan(this)
        SerialPortDiscovery.startBleScan()
        SerialPort.discoveryTimeOut = false
        Timer().schedule(object: TimerTask(){
            override fun run() {
                SerialPort.discoveryTimeOut = true
                MainScope().launch {
                    SerialPortDiscovery.stopLegacyScan(this@DiscoveryActivity)
                    SerialPortDiscovery.stopBleScan()
                    SerialPortDiscovery.discoveryStatusWithTypeCallback?.invoke(SerialPort.DISCOVERY_LEGACY, false)
                    SerialPortDiscovery.discoveryStatusCallback?.invoke(false)
                    SerialPortDiscovery.discoveryStatusLiveData.value = false
                }
            }
        }, SerialPort.discoveryTime)
    }

    /**
    * Инициализация списка устройств
    * @Author Shanya
    * @Date 2021/5/28
    * @Version 3.1.0
    */
    private fun recyclerViewInit() {
        val pairedDevicesAdapter = DevicesAdapter(this, true)
        val unpairedDevicesAdapter = DevicesAdapter(this,false)

        recyclerViewPaired.apply {
            adapter = pairedDevicesAdapter
            layoutManager = LinearLayoutManager(this@DiscoveryActivity)
            addItemDecoration(
                DividerItemDecoration(this@DiscoveryActivity,DividerItemDecoration.VERTICAL)
            )
        }

        recyclerViewUnpaired.apply {
            adapter = unpairedDevicesAdapter
            layoutManager = LinearLayoutManager(this@DiscoveryActivity)
            addItemDecoration(
                DividerItemDecoration(this@DiscoveryActivity,DividerItemDecoration.VERTICAL)
            )
        }

        SerialPort.setFindDeviceListener {
            unpairedDevicesAdapter.setDevice(unPairedDevicesListBD)
        }
    }

    /**
    * Создайте меню в правом верхнем углу
    * @param menu
    * @Author Shanya
    * @Date 2021/5/28
    * @Version 3.1.0
    */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.discovery_menu,menu)
        return super.onCreateOptionsMenu(menu)
    }

    /**
    * Пункт меню мониторинг в правом верхнем углу
    * @param item
    * @Author Shanya
    * @Date 2021/5/28
    * @Version 3.1.0
    */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.discovery_menu_item -> {
                doDiscovery()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
    * Activity уничтожать
    * @Author Shanya
    * @Date 2021/5/28
    * @Version 3.1.0
    */
    override fun onDestroy() {
        super.onDestroy()
        try {
            SerialPortDiscovery.stopLegacyScan(this)
            SerialPortDiscovery.stopBleScan()
        } catch (e: SecurityException) {
            e.printStackTrace()
            LogUtil.log("Не получил разрешение Bluetooth！")
        }

        connectProcessDialog.dismiss()
        LogUtil.log("Уничтожение встроенной страницы поиска")
    }

    /**
    * Адаптер списка устройств
    * @Author Shanya
    * @Date 2021-8-13
    * @Version 4.0.3
    */
    inner class DevicesAdapter internal constructor(context: Context, private val pairingStatus: Boolean) :
        RecyclerView.Adapter<DevicesAdapter.DevicesViewHolder>() {

        private val inflater: LayoutInflater = LayoutInflater.from(context)
        private var pairedDevices = ArrayList<BluetoothDevice>()
        private var unpairedDevices = ArrayList<BluetoothDevice>()

        inner class DevicesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textViewDeviceName: TextView = itemView.findViewById(R.id.textViewDeviceName)
            val textViewDeviceAddress: TextView = itemView.findViewById(R.id.textViewDeviceAddress)
            val imageViewDeviceLogo: ImageView = itemView.findViewById(R.id.imageViewDeviceLogo)
//            val textViewDeviceType: TextView = itemView.findViewById(R.id.textViewDeviceType)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DevicesViewHolder {
            val holder = DevicesViewHolder(inflater.inflate(R.layout.device_cell,parent,false))
            holder.itemView.setOnClickListener {
                SerialPortDiscovery.stopLegacyScan(this@DiscoveryActivity)
                SerialPortDiscovery.stopBleScan()
                val device = SerialPort.bluetoothAdapter.getRemoteDevice(it.textViewDeviceAddress.text.toString())
                if (SerialPort.openConnectionTypeDialogFlag) {
                    AlertDialog.Builder(this@DiscoveryActivity)
                        .setTitle(getString(R.string.discovery_select_discovery_method))
                        .setItems(R.array.connect_string) { dialog, which ->
                            connectProcessDialog.show()
                            if (which == 0) {
                                SerialPort._connectBleDevice(device)
                            }else if (which == 1) {
                                SerialPort._connectLegacyDevice(device)
                            }
                        }
                        .create().show()
                }else{
                    connectProcessDialog.show()
                    SerialPort._connectDevice(device, this@DiscoveryActivity)
                }
            }
            return holder
        }

        override fun getItemCount(): Int {
            return if (pairingStatus)
                pairedDevicesListBD.size
            else
                unPairedDevicesListBD.size
        }


        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: DevicesViewHolder, position: Int) {
            val settings = getSharedPreferences("lastConnectedDevice", MODE_PRIVATE);
            if (pairingStatus) {
                val current = pairedDevicesListBD[position]
                holder.textViewDeviceName.text = current.name
                val addressDev = current.address
                val addressSaved = settings.getString("lastConnectedDevice","не знаю")
                if(addressDev==addressSaved)
                    holder.itemView.setBackgroundColor(0x55009900.toInt())
                else
                    holder.itemView.setBackgroundColor(0x55FFFFFF.toInt())
                holder.textViewDeviceAddress.text = current.address
                when (current.type) {
                    BluetoothDevice.DEVICE_TYPE_UNKNOWN -> {
//                        holder.textViewDeviceType.text = getString(R.string.discovery_unknow)
                        holder.imageViewDeviceLogo.setImageResource(R.mipmap.device_logo)
                    }

                    BluetoothDevice.DEVICE_TYPE_CLASSIC -> {
//                        holder.textViewDeviceType.text = getString(R.string.discovery_traditional)
                        holder.imageViewDeviceLogo.setImageResource(R.mipmap.device_logo_l)
                    }
                    BluetoothDevice.DEVICE_TYPE_LE -> {
//                        holder.textViewDeviceType.text = getString(R.string.discovery_ble)
                        holder.imageViewDeviceLogo.setImageResource(R.mipmap.device_logo_b)
                    }
                    BluetoothDevice.DEVICE_TYPE_DUAL -> {
//                        holder.textViewDeviceType.text = getString(R.string.discovery_dual)
                        holder.imageViewDeviceLogo.setImageResource(R.mipmap.device_logo_l_b)
                    }
                }
            }
            else {
                val current = unPairedDevicesListBD[position]
                holder.textViewDeviceName.text = current.name
                holder.textViewDeviceAddress.text = current.address
                when (current.type) {
                    BluetoothDevice.DEVICE_TYPE_UNKNOWN -> {
//                        holder.textViewDeviceType.text = getString(R.string.discovery_unknow)
                        holder.imageViewDeviceLogo.setImageResource(R.mipmap.device_logo)
                    }

                    BluetoothDevice.DEVICE_TYPE_CLASSIC -> {
//                        holder.textViewDeviceType.text = getString(R.string.discovery_traditional)
                        holder.imageViewDeviceLogo.setImageResource(R.mipmap.device_logo_l)
                    }
                    BluetoothDevice.DEVICE_TYPE_LE -> {
//                        holder.textViewDeviceType.text = getString(R.string.discovery_ble)
                        holder.imageViewDeviceLogo.setImageResource(R.mipmap.device_logo_b)
                    }
                    BluetoothDevice.DEVICE_TYPE_DUAL -> {
//                        holder.textViewDeviceType.text = getString(R.string.discovery_dual)
                        holder.imageViewDeviceLogo.setImageResource(R.mipmap.device_logo_l_b)
                    }
                }
            }
        }

        internal fun setDevice(devices: ArrayList<BluetoothDevice>) {
            if (pairingStatus) {
                pairedDevices = devices
            } else {
                unpairedDevices = devices
            }
            notifyDataSetChanged()
        }
    }
}