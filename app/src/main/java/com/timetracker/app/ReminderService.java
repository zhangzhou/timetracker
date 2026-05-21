package com.timetracker.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class ReminderService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int interval = intent.getIntExtra("interval", 30);
        scheduleNext(interval);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void scheduleNext(int intervalMinutes) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("interval", intervalMinutes);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long intervalMillis = intervalMinutes * 60 * 1000L;
        long triggerAt = System.currentTimeMillis() + intervalMillis;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }

        Log.d("TimeTracker", "Next reminder scheduled in " + intervalMinutes + " minutes");
    }
}
