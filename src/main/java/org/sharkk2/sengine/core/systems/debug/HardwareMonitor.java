package org.sharkk2.sengine.core.systems.debug;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

public class HardwareMonitor {
    private static volatile int gpuUtilization = 0;
    private static volatile int gpuTemperature = 0;
    private static Thread monitorThread;
    private static OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    public static void start() {
        monitorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Process p = Runtime.getRuntime().exec(
                            "nvidia-smi --query-gpu=utilization.gpu,temperature.gpu --format=csv,noheader,nounits"
                    );
                    String out = new String(p.getInputStream().readAllBytes()).trim();
                    String[] parts = out.split(",");
                    gpuUtilization = Integer.parseInt(parts[0].trim());
                    gpuTemperature = Integer.parseInt(parts[1].trim());
                    Thread.sleep(1000);
                } catch (Exception e) {
                    break;
                }
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    public static void stop() {if (monitorThread != null) monitorThread.interrupt();}

    public static int getGPULoad() {return gpuUtilization;}
    public static int getGpuTemperature() {return gpuTemperature;}

    public static int getCPULoad() {
        return (int)Math.round(os.getCpuLoad() * 100);
    }
}