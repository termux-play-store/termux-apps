package com.termux.app.api;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.util.JsonWriter;

public class BatteryStatusAPI {

    public static void onReceive(final Context context, Intent intent) {
        ResultReturner.returnData(intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

                int batteryLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int batteryScale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                int health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_STATUS_UNKNOWN);
                String batteryHealth = switch (health) {
                    case BatteryManager.BATTERY_HEALTH_COLD -> "COLD";
                    case BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD";
                    case BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD";
                    case BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT";
                    case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE";
                    case BatteryManager.BATTERY_HEALTH_UNKNOWN -> "UNKNOWN";
                    case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "UNSPECIFIED_FAILURE";
                    default -> "Unknown_" + health;
                };

                // BatteryManager.EXTRA_PLUGGED: "Extra for ACTION_BATTERY_CHANGED: integer indicating whether the
                // device is plugged in to a power source; 0 means it is on battery, other constants are different types
                // of power sources."
                int pluggedInt = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                String batteryPlugged = switch (pluggedInt) {
                    case 0 -> "UNPLUGGED";
                    case BatteryManager.BATTERY_PLUGGED_AC -> "PLUGGED_AC";
                    case BatteryManager.BATTERY_PLUGGED_DOCK -> "PLUGGED_DOCK";
                    case BatteryManager.BATTERY_PLUGGED_USB -> "PLUGGED_USB";
                    case BatteryManager.BATTERY_PLUGGED_WIRELESS -> "PLUGGED_WIRELESS";
                    default -> "PLUGGED_" + pluggedInt;
                };

                var batteryTemperatureInt = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
                // Android returns battery temperature as int in tenths of degrees Celsius, like 255, so convert it to a decimal like 25.5°C.
                var batteryTemperatureDouble = (batteryTemperatureInt == Integer.MIN_VALUE) ? null : batteryTemperatureInt / 10.;

                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
                var batteryStatusString = switch (status) {
                    case BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING";
                    case BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING";
                    case BatteryManager.BATTERY_STATUS_FULL -> "FULL";
                    case BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING";
                    case BatteryManager.BATTERY_STATUS_UNKNOWN -> "UNKNOWN";
                    default -> "UNKNOWN_" + status;
                };

                int batteryVoltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                if (batteryVoltage < 100) {
                    // If in V, convert to mV:
                    // https://stackoverflow.com/questions/24500795/android-battery-voltage-unit-discrepancies
                    batteryVoltage = batteryVoltage * 1000;
                }

                var batteryManager = context.getSystemService(BatteryManager.class);

                // > Instantaneous battery current in microamperes, as an integer.
                // > Positive values indicate net current entering the battery from a charge source,
                // > negative values indicate net current discharging from the battery.
                // However, some devices may return negative values while charging, and positive
                // values while discharging. Inverting sign based on charging state is not a
                // possibility as charging current may be lower than current being used by device if
                // charger does not output enough current, and will result in false inversions.
                // - https://developer.android.com/reference/android/os/BatteryManager#BATTERY_PROPERTY_CURRENT_NOW
                // - https://issuetracker.google.com/issues/37131318
                Integer batteryCurrentNow = getIntProperty(batteryManager, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                // - https://stackoverflow.com/questions/64532112/batterymanagers-battery-property-current-now-returning-0-or-incorrect-current-v
                if (batteryCurrentNow != null && Math.abs(batteryCurrentNow / 1000) < 1.0) {
                    batteryCurrentNow = batteryCurrentNow * 1000;
                }

                out.beginObject();
                out.name("present").value(batteryStatus.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false));
                out.name("technology").value(batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY));
                out.name("health").value(batteryHealth);
                out.name("plugged").value(batteryPlugged);
                out.name("status").value(batteryStatusString);
                out.name("temperature").value(batteryTemperatureDouble);
                out.name("voltage").value(batteryVoltage);
                out.name("current").value(batteryCurrentNow);
                out.name("current_average").value(getIntProperty(batteryManager, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE));
                out.name("percentage").value(getIntProperty(batteryManager, BatteryManager.BATTERY_PROPERTY_CAPACITY));
                out.name("level").value(batteryLevel);
                out.name("scale").value(batteryScale);
                out.name("charge_counter").value(getIntProperty(batteryManager, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER));
                out.name("energy").value(getLongProperty(batteryManager, BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    int batteryCycle = batteryStatus.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1);
                    out.name("cycle").value(batteryCycle == -1 ? null : batteryCycle);
                }
                out.endObject();
            }
        });

    }

    /**
     * https://developer.android.com/reference/android/os/BatteryManager.html#getIntProperty(int)
     */
    private static Integer getIntProperty(BatteryManager batteryManager, int id) {
        int value = batteryManager.getIntProperty(id);
        return value != Integer.MIN_VALUE ? value : null;
    }

    /**
     * https://developer.android.com/reference/android/os/BatteryManager.html#getLongProperty(int)
     */
    private static Long getLongProperty(BatteryManager batteryManager, int id) {
        long value = batteryManager.getLongProperty(id);
        return value != Long.MIN_VALUE ? value : null;
    }
}
