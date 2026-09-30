package com.portal.listener;

import com.portal.dao.MongoIndexes;
import com.portal.dao.PodDao;
import com.portal.dao.VmDao;
import com.portal.model.PodDetails;
import com.portal.model.VmDetails;
import com.portal.service.ServiceRegistry;
import com.portal.util.AppConfig;
import com.portal.util.WebUtil;
import io.fabric8.kubernetes.api.model.Pod;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Background reconciler. Every 30s it re-reads each tracked VM and pod from
 * its provider and updates MongoDB when the status drifted, which catches
 * resources changed outside the portal (kubectl, the OpenStack CLI, a crash).
 */
public class SyncWorkerListener implements ServletContextListener {

    private static final Logger LOG = LoggerFactory.getLogger(SyncWorkerListener.class);

    private ScheduledExecutorService scheduler;
    private volatile boolean indexesReady;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        VmDao vmDao = new VmDao();
        PodDao podDao = new PodDao();
        long periodSeconds = AppConfig.getInt("sync.period_seconds", 30);

        if (MongoIndexes.ensure()) {
            sce.getServletContext().log("MongoDB indexes verified");
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "portal-sync-worker");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        // Retried here too, so a database that was down at boot
                        // still ends up indexed without a redeploy.
                        if (!indexesReady) {
                            indexesReady = MongoIndexes.ensure();
                        }
                    } catch (RuntimeException e) {
                        LOG.warn("Index retry failed: {}", e.toString());
                    }
                    try {
                        syncVms(vmDao);
                    } catch (RuntimeException e) {
                        LOG.warn("VM sync pass failed: {}", e.toString());
                    }
                    try {
                        syncPods(podDao);
                    } catch (RuntimeException e) {
                        LOG.warn("Pod sync pass failed: {}", e.toString());
                    }
                },
                0, periodSeconds, TimeUnit.SECONDS);

        sce.getServletContext().log("Sync worker started, period " + periodSeconds + "s");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void syncVms(VmDao vmDao) {
        for (VmDetails vm : vmDao.findAll()) {
            try {
                var openStack = ServiceRegistry.openStack();
                var server = openStack.refreshServer(vm.getVmId());

                if (server == null) {
                    // Deleted outside the portal: keep the row but mark it, so
                    // the user sees the truth rather than a phantom VM. The
                    // quota slot stays consumed until they delete it here.
                    if (!"error".equals(vm.getStatus())) {
                        vmDao.updateStatusUnscoped(vm.getVmId(), "error", null, null);
                    }
                    continue;
                }

                String raw = server.getStatus() == null ? null : server.getStatus().name();
                String mapped = WebUtil.mapOpenStackStatus(raw);
                String floating = openStack.findFloatingIp(vm.getVmId(), vm.getVmIp());

                if (!mapped.equals(vm.getStatus()) || !equalsOrBothNull(floating, vm.getFloatIp())) {
                    vmDao.updateStatusUnscoped(vm.getVmId(), mapped, vm.getVmIp(), floating);
                }
            } catch (RuntimeException e) {
                LOG.debug("VM {} sync failed: {}", vm.getVmId(), e.toString());
            }
        }
    }

    private void syncPods(PodDao podDao) {
        for (PodDetails stored : podDao.findAll()) {
            try {
                var kubernetes = ServiceRegistry.kubernetes();
                Pod live = kubernetes.get(stored.getNamespace(), stored.getPodName());

                if (live == null) {
                    if (!"error".equals(stored.getStatus())) {
                        podDao.updateStatusUnscoped(stored.getPodId(), "error", null, null, null);
                    }
                    continue;
                }

                String phase = live.getStatus() == null ? null : live.getStatus().getPhase();
                String mapped = WebUtil.mapPodPhase(phase);
                String ip = live.getStatus() == null ? null : live.getStatus().getPodIP();
                String node = live.getSpec() == null ? null : live.getSpec().getNodeName();
                int restarts = kubernetes.restartCount(live);

                if (!mapped.equals(stored.getStatus())
                        || !equalsOrBothNull(ip, stored.getPodIp())
                        || !equalsOrBothNull(node, stored.getNodeName())
                        || restarts != stored.getRestartCount()) {
                    podDao.updateStatusUnscoped(stored.getPodId(), mapped, ip, node, restarts);
                }
            } catch (RuntimeException e) {
                LOG.debug("Pod {} sync failed: {}", stored.getPodId(), e.toString());
            }
        }
    }

    private static boolean equalsOrBothNull(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
