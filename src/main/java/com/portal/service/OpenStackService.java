package com.portal.service;

import com.portal.model.VmDetails;
import com.portal.util.AppConfig;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.compute.Action;
import org.openstack4j.model.compute.Address;
import org.openstack4j.model.compute.RebootType;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.compute.ServerCreate;
import org.openstack4j.model.network.NetFloatingIP;
import org.openstack4j.openstack.OSFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Thin wrapper over openstack4j. Holds one authenticated client; the token is
 * reused across calls and re-authenticated transparently by openstack4j.
 */
public class OpenStackService {

    private static final Logger LOG = LoggerFactory.getLogger(OpenStackService.class);

    private final OSClient client;

    public OpenStackService() {
        String authUrl = AppConfig.get("openstack.auth_url", "http://localhost:5000/v3");
        String username = AppConfig.get("openstack.username", "admin");
        String password = AppConfig.get("openstack.password", "");
        String domain = AppConfig.get("openstack.domain", "Default");
        String project = AppConfig.get("openstack.project", "service");

        this.client = OSFactory.builderV3()
                .endpoint(authUrl)
                .credentials(username, password, Identifier.byName(domain))
                .scopeToProject(Identifier.byName(project))
                .authenticate();
    }

    /**
     * Boots a VM. The returned VmDetails carries the OpenStack id, private IP
     * and the raw provider status, which the caller maps to portal vocabulary.
     */
    public VmDetails createVm(String vmName, String imageId, String flavorId,
                              String keyPair, String projectName) {
        ServerCreate sc = Builders.server()
                .name(vmName)
                .flavor(flavorId)
                .image(imageId)
                .keypairName(keyPair)
                .build();

        Server server = client.compute().servers().boot(sc);
        if (server == null) {
            throw new IllegalStateException("OpenStack returned no server for " + vmName);
        }

        VmDetails vm = new VmDetails();
        vm.setVmId(server.getId());
        vm.setVmName(vmName);
        vm.setSshKey(keyPair);
        vm.setProjectName(projectName);
        vm.setFlavor(flavorId);
        vm.setImage(imageId);
        vm.setVmIp(resolvePrivateIp(server));
        vm.setFloatIp(null);
        vm.setStatus(server.getStatus() == null ? "ERROR" : server.getStatus().name());
        return vm;
    }

    /** Current provider status for a VM, or null if it no longer exists. */
    public String getVmStatus(String vmId) {
        Server server = client.compute().servers().get(vmId);
        return server == null ? null : (server.getStatus() == null ? null : server.getStatus().name());
    }

    /** Refreshes a VM from the provider so the details card shows live IPs. */
    public Server refreshServer(String vmId) {
        return client.compute().servers().get(vmId);
    }

    public void start(String vmId) {
        check(client.compute().servers().action(vmId, Action.START), "start");
    }

    public void stop(String vmId) {
        check(client.compute().servers().action(vmId, Action.STOP), "stop");
    }

    /**
     * Reboot. This calls servers().reboot(...) because openstack4j's Action enum
     * has no REBOOT constant -- the spec's Action.REBOOT does not compile.
     */
    public void restart(String vmId) {
        check(client.compute().servers().reboot(vmId, RebootType.SOFT), "restart");
    }

    public void delete(String vmId) {
        check(client.compute().servers().delete(vmId), "delete");
    }

    /** Finds a floating IP already attached to this VM, if any. */
    public String findFloatingIp(String vmId, String privateIp) {
        if (privateIp == null) {
            return null;
        }
        try {
            for (NetFloatingIP fip : client.networking().floatingip().list()) {
                if (privateIp.equals(fip.getFixedIpAddress())) {
                    return fip.getFloatingIpAddress();
                }
            }
        } catch (RuntimeException e) {
            LOG.warn("Could not read floating IPs: {}", e.toString());
        }
        return null;
    }

    /**
     * Nova returns addresses as Map&lt;networkName, List&lt;Address&gt;&gt;.
     * Prefers an IPv4 address, which is what the Prometheus queries key on.
     */
    private String resolvePrivateIp(Server server) {
        if (server.getAddresses() == null) {
            return null;
        }
        for (Map.Entry<String, List<? extends Address>> entry
                : server.getAddresses().getAddresses().entrySet()) {
            for (Address address : entry.getValue()) {
                if (address.getVersion() == 4) {
                    return address.getAddr();
                }
            }
        }
        return null;
    }

    private void check(ActionResponse response, String action) {
        if (response != null && Boolean.FALSE.equals(response.isSuccess())) {
            throw new IllegalStateException("OpenStack " + action + " failed: " + response.getFault());
        }
    }
}
