package io.github.mouse233.bluehotspot.server.tethering;

import io.github.mouse233.bluehotspot.server.tethering.IShizukuTetheringResultCallback;

interface IShizukuTetheringService {
    int getUid() = 1;
    String getOpPackageName() = 2;
    boolean hasTetheringPermission() = 3;
    oneway void start(IShizukuTetheringResultCallback callback) = 4;
    oneway void stop(IShizukuTetheringResultCallback callback) = 5;
    oneway void check(IShizukuTetheringResultCallback callback) = 6;

    // Reserved by Shizuku for removing a UserService after an app update.
    void destroy() = 16777114;
}
