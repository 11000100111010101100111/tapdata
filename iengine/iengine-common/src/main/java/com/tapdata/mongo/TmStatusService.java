package com.tapdata.mongo;

import com.tapdata.entity.AppType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.ThreadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
public class TmStatusService {

  private final static AppType appType = AppType.init();
  private final static AtomicBoolean available = new AtomicBoolean(true);

  private final static Map<String, AtomicBoolean> taskReportStatusMap = new ConcurrentHashMap<>();

  private final static List<Handler> toAvailableHandler = new ArrayList<>();

  public static boolean isNotAvailable() {
    return !isAvailable();
  }
  public static boolean isAvailable() {
    if (!appType.isCloud()) {
      return true;
    }
    return available.get();
  }

  public static void setNotAvailable() {
    if (!appType.isCloud()) {
      return;
    }
    available.set(false);
    for (AtomicBoolean taskReportStatus : taskReportStatusMap.values()) {
      taskReportStatus.set(false);
    }
  }

  /**
   * to available, do something...
   */
  public static void setAvailable() {
    if (!appType.isCloud()) {
      return;
    }
    available.set(true);
    if (CollectionUtils.isNotEmpty(toAvailableHandler)) {
      toAvailableHandler.forEach(Handler::run);
    }
  }

  public static void addNewTask(String taskId) {
    if (!appType.isCloud()) {
      return;
    }
    if (isAvailable()) {
      taskReportStatusMap.put(taskId, new AtomicBoolean(true));
    } else {
      taskReportStatusMap.put(taskId, new AtomicBoolean(false));
    }
  }

  public static void setAllowReport(String taskId) {
    if (!appType.isCloud()) {
      return;
    }
    taskReportStatusMap.computeIfAbsent(taskId, k -> new AtomicBoolean()).set(true);
  }

  public static boolean isNotAllowReport() {
    return !isAllowReport();
  }

  public static boolean isAllowReport() {
    String taskId = ThreadContext.get("taskId");
    return isAllowReport(taskId);
  }

  public static boolean isNotAllowReport(String taskId) {
    return !isAllowReport(taskId);
  }

  public static boolean isAllowReport(String taskId) {
    if (!appType.isCloud()) {
      return true;
    }
    AtomicBoolean status = taskReportStatusMap.get(taskId);
    return status == null || status.get();
  }

  public static void registeredTmAvailableHandler(Handler handler) {
    if (!appType.isCloud()) {
      return;
    }
    toAvailableHandler.add(handler);
  }

  public static void removeTask(String taskId) {
    if (!appType.isCloud()) {
      return;
    }
    taskReportStatusMap.remove(taskId);
  }
}
