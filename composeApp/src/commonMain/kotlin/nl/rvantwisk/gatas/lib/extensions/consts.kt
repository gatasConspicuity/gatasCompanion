package nl.rvantwisk.gatas.lib.extensions

import kotlin.math.PI

// Always add a new version, and comment put the deprecated version ONLY if we don't receive traffic anymore
enum class MessageType(val value: Int) {
    AIRCRAFT_POSITION_TYPE_V1(1),
    AIRCRAFT_POSITION_REQUEST_V1(2),
    AIRCRAFT_CONFIGURATIONS_V1(3),
    SET_ICAO_ADDRESS_V1(4),
    AIRCRAFT_CONFIGURATIONS_V2(5),
    GDL90_V1(6),
    SET_WIFI_MODE_V1(7),
    AIRCRAFT_POSITION_TYPE_V2(8),
    AIRCRAFT_POSITION_TYPE_V3(9),
    AIRCRAFT_POSITION_REQUEST_V2(10),
}

// Variables name in REDIS store
const val CRC16_BYTE_SIZE = 2
const val MAX_CALLSIGN_LENGTH = 12
const val SPEED = "speed"
const val TRACK = "track"
const val TURN_RATE = "turn"
const val VERTICAL_RATE = "vrate"
const val ALTITUDE = "alti"
const val DATA_SOURCE = "rsc" // Data Source
const val AIRCRAFT_CATEGORY = "aca"  // Aircraft Category
const val ADDRESS_TYPE = "aty"  // Adress Type
const val CALL_SIGN = "cas"  // Call Sign


const val MS_TO_FTPMIN = 196.850394         // meter/sec to feet/min
const val FTPMIN_TO_MS = 1.0 / MS_TO_FTPMIN // feet/min to meter/sec
const val KN_TO_MS = 0.514444444            // knots to meter/sec
const val RAD_TO_DEGREES = 180.0 / PI
const val FT_TO_METERS = 0.3048
const val NM_TO_METERS = 1.852  // Mautical miles to meters
const val METERS_TO_FT = 1.0 / FT_TO_METERS
const val STD_QNH = 1013.25
