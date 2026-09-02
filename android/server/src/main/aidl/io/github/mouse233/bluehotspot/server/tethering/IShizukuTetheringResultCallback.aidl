package io.github.mouse233.bluehotspot.server.tethering;

interface IShizukuTetheringResultCallback {
    oneway void onResult(int errorCode, int uid, String detail) = 1;
}
