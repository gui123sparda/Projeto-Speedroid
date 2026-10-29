// GPS VSS extension for Speeduino. GPL-3.0-or-later, matching the firmware.
#ifndef TUNER_ANDROID_GPS_VSS_H
#define TUNER_ANDROID_GPS_VSS_H
#include <stdint.h>

struct GpsVssState {
    bool enabled = false;
    bool valid = false;
    uint16_t tenthsKmh = 0;
    uint32_t receivedAt = 0;

    bool update(uint8_t flags, uint16_t value, uint32_t now) {
        if ((flags & ~3U) != 0 || ((flags & 2U) && !(flags & 1U)) || value > 5000U) return false;
        enabled = (flags & 1U) != 0;
        valid = enabled && (flags & 2U);
        tenthsKmh = valid ? value : 0;
        receivedAt = now;
        return true;
    }
    uint16_t speed(uint32_t now) const {
        // Unsigned subtraction is safe across millis() rollover.
        return enabled && valid && (uint32_t)(now - receivedAt) <= 1500U
            ? (uint16_t)((tenthsKmh + 5U) / 10U) : 0;
    }
};
extern GpsVssState gpsVssState;
#endif
