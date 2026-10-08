package com.portal.service;

import com.portal.model.Status;
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

public class OpenStackService {

    private static final Logger LOG = LoggerFactory.getLogger(OpenStackService.class);

    private static final int IPV4 = 4;

    private final OSClient client;

    public OpenStackService() {
        this.client = OSFactory.builderV3()
                .endpoint(AppConfig.get("openstack.auth_url", "http://localhost:5000/v3"))
                .credentials(AppConfig.get("openstack.username", "admin"),
                        AppConfig.get("openstack.password", ""),
                        Identifier.byName(AppConfig.get("openstack.domain", "Default")))
                .scopeToProject(Identifier.byName(AppConfig.get("openstack.project", "service")))
                .authenticate();
    }

    public VmDetails createVm(String vmName, String imageId, String flavorId,
                              String keyPair, String projectName) {
        ServerCreate spec = Builders.server()
                .name(vmName)
                .flavor(flavorId)
                .image(imageId)
                .keypairName(keyPair)
                .build();

        Server server = client.compute().servers().boot(spec);
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
        vm.setStatus(statusOf(server));
        return vm;
    }

    public Server refreshServer(String vmId) {
        return client.compute().servers().get(vmId);
    }

    public static Status statusOf(Server server) {
        if (server == null || server.getStatus() == null) {
            return Status.ERROR;
        }
        return Status.fromOpenStack(server.getStatus().name());
    }

    public void start(String vmId) {
        check(client.compute().servers().action(vmId, Action.START), "start");
    }

    public void stop(String vmId) {
        check(client.compute().servers().action(vmId, Action.STOP), "stop");
    }

    public void restart(String vmId) {
        check(client.compute().servers().reboot(vmId, RebootType.SOFT), "restart");
    }

    public void delete(String vmId) {
        check(client.compute().servers().delete(vmId), "delete");
    }

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

    private String resolvePrivateIp(Server server) {
        if (server.getAddresses() == null) {
            return null;
        }
        for (Map.Entry<String, List<? extends Address>> entry
                : server.getAddresses().getAddresses().entrySet()) {
            for (Address address : entry.getValue()) {
                if (address.getVersion() == IPV4) {
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
