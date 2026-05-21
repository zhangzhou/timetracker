package com.timetracker.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WebAppInterface {
    private final Context context;
    private final SharedPreferences prefs;
    private final Gson gson;
    private static final String PREFS_NAME = "TimeTrackerData";
    private static final String KEY_RECORDS = "records";

    public WebAppInterface(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    @JavascriptInterface
    public void saveRecord(String json) {
        try {
            List<TimeRecord> records = getRecordsList();
            TimeRecord newRecord = gson.fromJson(json, TimeRecord.class);
            if (newRecord.id == null || newRecord.id.isEmpty()) {
                newRecord.id = UUID.randomUUID().toString();
            }
            records.removeIf(r -> r.periodStart.equals(newRecord.periodStart));
            records.add(0, newRecord);
            saveRecordsList(records);
        } catch (Exception e) {
            showToast("保存失败: " + e.getMessage());
        }
    }

    @JavascriptInterface
    public String getRecords() {
        List<TimeRecord> records = getRecordsList();
        return gson.toJson(records);
    }

    @JavascriptInterface
    public void deleteRecord(String id) {
        List<TimeRecord> records = getRecordsList();
        records.removeIf(r -> r.id.equals(id));
        saveRecordsList(records);
    }

    @JavascriptInterface
    public void updateRecord(String json) {
        try {
            List<TimeRecord> records = getRecordsList();
            TimeRecord updated = gson.fromJson(json, TimeRecord.class);
            for (int i = 0; i < records.size(); i++) {
                if (records.get(i).id.equals(updated.id)) {
                    records.set(i, updated);
                    break;
                }
            }
            saveRecordsList(records);
        } catch (Exception e) {
            showToast("更新失败: " + e.getMessage());
        }
    }

    @JavascriptInterface
    public void clearAllData() {
        prefs.edit().remove(KEY_RECORDS).apply();
    }

    @JavascriptInterface
    public void scheduleReminder(int intervalMinutes) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("interval", intervalMinutes);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
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

        prefs.edit().putInt("interval", intervalMinutes).apply();
    }

    @JavascriptInterface
    public void cancelReminder() {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(pendingIntent);
        prefs.edit().remove("interval").apply();
    }

    @JavascriptInterface
    public void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        );
    }

    @JavascriptInterface
    public void exportData(String format) {
        try {
            List<TimeRecord> records = getRecordsList();
            String content;
            String ext;
            if ("json".equals(format)) {
                content = gson.toJson(records);
                ext = "json";
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("ID,开始时间,结束时间,记录时间,分类,活动\n");
                for (TimeRecord r : records) {
                    sb.append(String.format("%s,%s,%s,%s,%s,"%s"\n",
                        r.id, r.periodStart, r.periodEnd, r.recordedAt, r.category,
                        r.activity.replace(""", """")));
                }
                content = sb.toString();
                ext = "csv";
            }

            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            String fileName = "timetracker_" + System.currentTimeMillis() + "." + ext;
            File file = new File(downloads, fileName);
            FileWriter writer = new FileWriter(file);
            if ("csv".equals(format)) writer.write("\uFEFF");
            writer.write(content);
            writer.close();

            showToast("已导出到 Downloads: " + fileName);
        } catch (Exception e) {
            showToast("导出失败: " + e.getMessage());
        }
    }

    @JavascriptInterface
    public String getSettings() {
        int interval = prefs.getInt("interval", 30);
        return "{\"interval\": " + interval + ", "notifications\": true}";
    }

    private List<TimeRecord> getRecordsList() {
        String json = prefs.getString(KEY_RECORDS, "[]");
        Type type = new TypeToken<List<TimeRecord>>(){}.getType();
        List<TimeRecord> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    private void saveRecordsList(List<TimeRecord> records) {
        prefs.edit().putString(KEY_RECORDS, gson.toJson(records)).apply();
    }

    private static class TimeRecord {
        String id;
        String periodStart;
        String periodEnd;
        String recordedAt;
        String activity;
        String category;
    }
}
