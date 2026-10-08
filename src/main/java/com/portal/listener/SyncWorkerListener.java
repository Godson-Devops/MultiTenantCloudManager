package com.portal.listener;

import com.portal.dao.SchemaManager;
import com.portal.dao.PodDao;
import com.portal.dao.VmDao;
import com.portal.model.PodDetails;
import com.portal.model.Status;
import com.portal.model.VmDetails;
import com.portal.service.KubernetesService;
import com.portal.service.OpenStackService;
import com.portal.service.ServiceRegistry;
import com.portal.util.AppConfig;
import io.fabric8.kubernetes.api.model.Pod;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SyncWorkerListener implements ServletContextListener {

    private static final Logger LOG = LoggerFactory.getLogger(SyncWorkerListener.class);

    private final VmDao vmDao = new VmDao();
    private final PodDao podDao = new PodDao();

    private ScheduledExecutorService scheduler;
    private volatile boolean schemaReady;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        schemaReady = ensureSchema();

        int periodSeconds = AppConfig.getInt("sync.period_seconds", 30);
        scheduler = startScheduler(periodSeconds);

        sce.getServletContext().log("Sync worker started, period " + periodSeconds + "s");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private ScheduledExecutorService startScheduler(int periodSeconds) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        Runnable periodicTask = new Runnable() {
            @Override
            public void run() {
                syncPass();
            }
        };
        executor.scheduleWithFixedDelay(periodicTask, 0, periodSeconds, TimeUnit.SECONDS);
        return executor;
    }

    private void syncPass() {
        try {
            ensureSchema();
        } catch (RuntimeException e) {
            LOG.warn("schema retry failed: {}", e.toString());
        }

        try {
            syncVms(ServiceRegistry.openStack());
        } catch (RuntimeException e) {
            LOG.warn("VM sync failed: {}", e.toString());
        }

        try {
            syncPods(ServiceRegistry.kubernetes());
        } catch (RuntimeException e) {
            LOG.warn("Pod sync failed: {}", e.toString());
        }
    }

    private boolean ensureSchema() {
        if (!schemaReady) {
            schemaReady = SchemaManager.ensure();
        }
        return schemaReady;
    }

    private void syncVms(OpenStackService openStack) {
        for (VmDetails vm : vmDao.findAll()) {
            try {
                refreshVm(openStack, vm);
            } catch (RuntimeException e) {
                LOG.debug("VM {} sync failed: {}", vm.getVmId(), e.toString());
            }
        }
    }

    private void refreshVm(OpenStackService openStack, VmDetails vm) {
        var server = openStack.refreshServer(vm.getVmId());

        if (server == null) {

            if (vm.getStatus() != Status.ERROR) {
                vmDao.updateStatusUnscoped(vm.getVmId(), Status.ERROR, null, null);
            }
            return;
        }

        Status liveStatus = OpenStackService.statusOf(server);
        String floatingIp = openStack.findFloatingIp(vm.getVmId(), vm.getVmIp());

        boolean changed = liveStatus != vm.getStatus()
                || !Objects.equals(floatingIp, vm.getFloatIp());

        if (changed) {
            vmDao.updateStatusUnscoped(vm.getVmId(), liveStatus, vm.getVmIp(), floatingIp);
        }
    }

    private void syncPods(KubernetesService kubernetes) {
        for (PodDetails stored : podDao.findAll()) {
            try {
                refreshPod(kubernetes, stored);
            } catch (RuntimeException e) {
                LOG.debug("Pod {} sync failed: {}", stored.getPodId(), e.toString());
            }
        }
    }

    private void refreshPod(KubernetesService kubernetes, PodDetails stored) {
        Pod livePod = kubernetes.get(stored.getNamespace(), stored.getPodName());

        if (livePod == null) {
            if (stored.getStatus() != Status.ERROR) {
                podDao.updateStatusUnscoped(
                        stored.getPodId(), Status.ERROR, null, null, null);
            }
            return;
        }

        Status status = KubernetesService.statusOf(livePod);
        String podIp = KubernetesService.ipOf(livePod);
        String nodeName = nodeNameOf(livePod);
        int restarts = KubernetesService.restartCount(livePod);

        boolean changed = status != stored.getStatus()
                || !Objects.equals(podIp, stored.getPodIp())
                || !Objects.equals(nodeName, stored.getNodeName())
                || restarts != stored.getRestartCount();

        if (changed) {
            podDao.updateStatusUnscoped(
                    stored.getPodId(), status, podIp, nodeName, restarts);
        }
    }

    private static String nodeNameOf(Pod pod) {
        return pod.getSpec() == null ? null : pod.getSpec().getNodeName();
    }
}
