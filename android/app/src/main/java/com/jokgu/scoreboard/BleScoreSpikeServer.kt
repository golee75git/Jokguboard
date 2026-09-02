package com.jokgu.scoreboard

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

/* JK_BLE_SPIKE: 화면 좌·우 게임/세트 점수를 BLE read·notify.
 * UUID·패킷 v2는 Score 시계와 맞추기 위해 동일. 되돌리: 이 파일과 Manifest/MainActivity BLE 블록 삭제.
 */
class BleScoreSpikeServer(context: Context) {
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("7c1b1000-9235-4d8e-a801-5b79d15f3a10")
        val SCORE_UUID: UUID = UUID.fromString("7c1b1001-9235-4d8e-a801-5b79d15f3a10")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        /* BT_SCORE_BLE_LIVE_v1: v2 = [2, leftGame, rightGame, leftSet, rightSet] */
        private const val PACKET_VERSION: Byte = 2
    }

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter?
        get() = manager?.adapter
    private var gattServer: BluetoothGattServer? = null
    private var scoreCharacteristic: BluetoothGattCharacteristic? = null
    private var advertising = false
    @Volatile private var currentPacket: ByteArray = byteArrayOf(PACKET_VERSION, 0, 0, 0, 0)
    private val subscribedDevices = CopyOnWriteArraySet<BluetoothDevice>()

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            advertising = true
        }

        override fun onStartFailure(errorCode: Int) {
            advertising = false
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (status == BluetoothGatt.GATT_SUCCESS) startAdvertising()
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED &&
                device.bondState != BluetoothDevice.BOND_BONDED
            ) {
                gattServer?.cancelConnection(device)
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                subscribedDevices.remove(device)
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION,
                    offset,
                    null,
                )
                return
            }
            val packet = currentPacket
            if (characteristic.uuid != SCORE_UUID || offset !in 0..packet.size) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_FAILURE,
                    offset,
                    null,
                )
                return
            }
            gattServer?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                packet.copyOfRange(offset, packet.size),
            )
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (descriptor.uuid != CCCD_UUID ||
                descriptor.characteristic?.uuid != SCORE_UUID
            ) {
                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        offset,
                        null,
                    )
                }
                return
            }
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION,
                        offset,
                        null,
                    )
                }
                return
            }
            val enable = value != null &&
                value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            if (enable) subscribedDevices.add(device) else subscribedDevices.remove(device)
            if (responseNeeded) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value,
                )
            }
            if (enable) notifyDevice(device, currentPacket)
        }
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val bluetoothAdapter = adapter ?: return false
        if (!bluetoothAdapter.isEnabled || bluetoothAdapter.bluetoothLeAdvertiser == null) {
            return false
        }
        if (gattServer != null) {
            if (!advertising) startAdvertising()
            return true
        }
        val server = manager?.openGattServer(appContext, serverCallback) ?: return false
        val characteristic = BluetoothGattCharacteristic(
            SCORE_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED,
        )
        characteristic.value = currentPacket
        val cccd = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or
                BluetoothGattDescriptor.PERMISSION_WRITE,
        )
        characteristic.addDescriptor(cccd)
        val service = BluetoothGattService(
            SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )
        service.addCharacteristic(characteristic)
        gattServer = server
        scoreCharacteristic = characteristic
        if (!server.addService(service)) {
            stop()
            return false
        }
        return true
    }

    /* BT_SCORE_BLE_LIVE_v1: 화면 좌·우 게임/세트 점수 갱신·notify. 되돌리: 이 메서드·CCCD/구독 로직 삭제 */
    /* BT_SCORE_BLE_FORCE_PUSH_v1: force=true면 동일 패킷도 재notify(새 경기). 되돌리: force 파라미터·분기 삭제 */
    @SuppressLint("MissingPermission")
    fun updateScore(
        leftGame: Int,
        rightGame: Int,
        leftSet: Int,
        rightSet: Int,
        force: Boolean = false,
    ) {
        val packet = byteArrayOf(
            PACKET_VERSION,
            clampByte(leftGame),
            clampByte(rightGame),
            clampByte(leftSet),
            clampByte(rightSet),
        )
        if (!force && packet.contentEquals(currentPacket)) return
        currentPacket = packet
        scoreCharacteristic?.value = packet
        for (device in subscribedDevices) {
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                notifyDevice(device, packet)
            } else {
                subscribedDevices.remove(device)
            }
        }
    }

    private fun clampByte(value: Int): Byte =
        value.coerceIn(0, 255).toByte()

    @SuppressLint("MissingPermission")
    private fun notifyDevice(device: BluetoothDevice, packet: ByteArray) {
        val server = gattServer ?: return
        val characteristic = scoreCharacteristic ?: return
        characteristic.value = packet
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                server.notifyCharacteristicChanged(device, characteristic, false, packet)
            } else {
                @Suppress("DEPRECATION")
                server.notifyCharacteristicChanged(device, characteristic, false)
            }
        } catch (_: SecurityException) {
            subscribedDevices.remove(device)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (advertising) return
        val advertiser = adapter?.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        try {
            advertiser.startAdvertising(settings, data, advertiseCallback)
        } catch (_: SecurityException) {
            advertising = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        subscribedDevices.clear()
        try {
            adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (_: SecurityException) {
            // Permission may have been revoked while the app was running.
        }
        advertising = false
        try {
            gattServer?.clearServices()
            gattServer?.close()
        } catch (_: SecurityException) {
            // Permission may have been revoked while the app was running.
        }
        gattServer = null
        scoreCharacteristic = null
    }
}
