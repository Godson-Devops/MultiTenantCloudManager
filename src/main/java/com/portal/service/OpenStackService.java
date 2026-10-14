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
import org.openstack4j.model.compute.Flavor;
import org.openstack4j.model.compute.Image;
import org.openstack4j.model.compute.Keypair;
import org.openstack4j.model.compute.RebootType;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.compute.ServerCreate;
import org.openstack4j.model.compute.builder.ServerCreateBuilder;
import org.openstack4j.model.network.NetFloatingIP;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.AttachInterfaceType;
import org.openstack4j.model.network.ExternalGateway;
import org.openstack4j.model.network.IPVersionType;
import org.openstack4j.openstack.OSFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OpenStackService {

    private static final Logger LOG = LoggerFactory.getLogger(OpenStackService.class);

    private static final int IPV4 = 4;

    private static final long REFRESH_MARGIN_MS = 60_000L;

    private final ThreadLocal<OSClient.OSClientV3> clients =
            ThreadLocal.withInitial(this::authenticate);

    public OpenStackService() {
    }

    private OSClient.OSClientV3 authenticate() {
        String domain = AppConfig.get("openstack.domain", "Default");
        String project = AppConfig.get("openstack.project", "service");
        return OSFactory.builderV3()
                .endpoint(AppConfig.get("openstack.auth_url", "http://localhost:5000/v3"))
                .credentials(AppConfig.get("openstack.username", "admin"),
                        AppConfig.get("openstack.password", ""),
                        Identifier.byName(domain))
                .scopeToProject(Identifier.byName(project), Identifier.byName(domain))
                .authenticate();
    }

    private OSClient.OSClientV3 client() {
        OSClient.OSClientV3 current = clients.get();
        Date expires = current.getToken() == null ? null : current.getToken().getExpires();
        if (expires != null && expires.getTime() - System.currentTimeMillis() < REFRESH_MARGIN_MS) {
            current = authenticate();
            clients.set(current);
        }
        return current;
    }

    public VmDetails createVm(String vmName, String imageName, String flavorName,
                              String keyPair, String projectName) {
        String imageId = resolveImageId(imageName);
        String flavorId = resolveFlavorId(flavorName);
        String keyName = resolveKeypair(keyPair);
        String networkId = resolveNetworkId();

        ServerCreateBuilder builder = Builders.server()
                .name(vmName)
                .flavor(flavorId)
                .image(imageId)
                .networks(List.of(networkId));
        if (keyName != null) {
            builder.keypairName(keyName);
        }
        ServerCreate spec = builder.build();

        Server server = client().compute().servers().boot(spec);
        if (server == null) {
            throw new IllegalStateException("OpenStack returned no server for " + vmName);
        }

        VmDetails vm = new VmDetails();
        vm.setVmId(server.getId());
        vm.setVmName(vmName);
        vm.setSshKey(keyPair);
        vm.setProjectName(projectName);
        vm.setFlavor(flavorName);
        vm.setImage(imageName);
        vm.setVmIp(resolvePrivateIp(server));
        vm.setStatus(server.getStatus() == null ? Status.CREATING : statusOf(server));
        vm.setFloatIp(ensureFloatingIp(vm.getVmId(), vm.getVmIp()));
        return vm;
    }

    public Server refreshServer(String vmId) {
        return client().compute().servers().get(vmId);
    }

    public static Status statusOf(Server server) {
        if (server == null || server.getStatus() == null) {
            return Status.ERROR;
        }
        return Status.fromOpenStack(server.getStatus().name());
    }

    public void start(String vmId) {
        check(client().compute().servers().action(vmId, Action.START), "start");
    }

    public void stop(String vmId) {
        check(client().compute().servers().action(vmId, Action.STOP), "stop");
    }

    public void restart(String vmId) {
        check(client().compute().servers().reboot(vmId, RebootType.SOFT), "restart");
    }

    public void delete(String vmId) {
        check(client().compute().servers().delete(vmId), "delete");
    }

    public String findFloatingIp(String vmId, String privateIp) {
        try {
            String portId = resolvePortId(vmId);
            for (NetFloatingIP fip : client().networking().floatingip().list()) {
                if (fip.getFloatingIpAddress() == null) {
                    continue;
                }
                if (privateIp != null && privateIp.equals(fip.getFixedIpAddress())) {
                    return fip.getFloatingIpAddress();
                }
                if (portId != null && portId.equals(fip.getPortId())) {
                    return fip.getFloatingIpAddress();
                }
                // getDeviceId/getInstanceId may not exist in this OpenStack4j version

            }
        } catch (RuntimeException e) {
            LOG.warn("Could not read floating IPs: {}", e.toString());
        }
        return null;
    }

    private String resolvePortId(String vmId) {
        if (vmId == null) {
            return null;
        }
        try {
            var ports = client().networking().port().list();
            for (var port : ports) {
                if (port.getDeviceId() != null && port.getDeviceId().equals(vmId)) {
                    return port.getId();
                }
                if (port.getDeviceOwner() != null
                        && port.getDeviceOwner().contains("compute")
                        && port.getName() != null
                        && port.getName().contains(vmId)) {
                    return port.getId();
                }
            }
        } catch (RuntimeException e) {
            LOG.debug("Could not resolve port id for {}: {}", vmId, e.toString());
        }
        return null;
    }

    private String resolvePortByIp(String privateIp) {
        if (privateIp == null) {
            return null;
        }
        try {
            for (var port : client().networking().port().list()) {
                if (port.getFixedIps() == null) {
                    continue;
                }
                for (var ip : port.getFixedIps()) {
                    if (privateIp.equals(ip.getIpAddress())) {
                        return port.getId();
                    }
                }
            }
        } catch (RuntimeException e) {
            LOG.debug("Could not resolve port by ip {}: {}", privateIp, e.toString());
        }
        return null;
    }

    public String ensureFloatingIp(String vmId, String privateIp) {
        if (vmId == null) {
            return null;
        }
        String existing = findFloatingIp(vmId, privateIp);
        if (existing != null) {
            return existing;
        }
        String portId = resolvePortId(vmId);
        if (portId == null) {
            portId = resolvePortByIp(privateIp);
        }
        if (portId == null) {
            LOG.debug("Port for VM {} not ready yet; will retry", vmId);
            return null;
        }
        try {
            String externalNetworkId = resolveExternalNetworkId();
            if (externalNetworkId == null) {
                LOG.warn("No external network available; cannot allocate floating IP for {}", vmId);
                return null;
            }
            ensureExternalRouter(externalNetworkId);
            NetFloatingIP created = client().networking().floatingip().create(
                    Builders.netFloatingIP()
                            .floatingNetworkId(externalNetworkId)
                            .portId(portId)
                            .build());
            String address = created == null ? null : created.getFloatingIpAddress();
            if (address != null) {
                LOG.info("Allocated floating IP {} for VM {}", address, vmId);
            }
            return address;
        } catch (RuntimeException e) {
            LOG.warn("Floating IP allocation failed for {}: {}", vmId, e.toString());
            return null;
        }
    }

    private String resolveExternalNetworkId() {
        String configured = AppConfig.get("openstack.floating_network", "public");
        List<? extends Network> networks = client().networking().network().list();
        if (configured != null && !configured.isBlank()) {
            for (Network candidate : networks) {
                if (configured.equals(candidate.getId())
                        || configured.equalsIgnoreCase(candidate.getName())) {
                    return candidate.getId();
                }
            }
        }
        for (Network candidate : networks) {
            if (candidate.isRouterExternal()) {
                return candidate.getId();
            }
        }
        return null;
    }

    private void ensureExternalRouter(String externalNetworkId) {
        try {
            for (var router : client().networking().router().list()) {
                ExternalGateway gateway = router.getExternalGatewayInfo();
                if (gateway != null && externalNetworkId.equals(gateway.getNetworkId())) {
                    return;
                }
            }
            String subnetId = resolveTenantSubnetId();
            if (subnetId == null) {
                LOG.warn("No tenant subnet found; skipping router creation");
                return;
            }
            var created = client().networking().router().create(Builders.router()
                    .name("portal-router")
                    .adminStateUp(true)
                    .externalGateway(externalNetworkId)
                    .build());
            if (created == null) {
                return;
            }
            try {
                client().networking().router().attachInterface(
                        created.getId(), AttachInterfaceType.SUBNET, subnetId);
                LOG.info("Created router {} and attached subnet {}", created.getId(), subnetId);
            } catch (RuntimeException e) {
                LOG.warn("Could not attach subnet {} to router {}: {}",
                        subnetId, created.getId(), e.toString());
            }
        } catch (RuntimeException e) {
            LOG.warn("Could not ensure external router: {}", e.toString());
        }
    }

    private String resolveTenantSubnetId() {
        try {
            String networkId = resolveNetworkId();
            for (var subnet : client().networking().subnet().list()) {
                if (networkId.equals(subnet.getNetworkId())
                        && subnet.getIpVersion() == IPVersionType.V4) {
                    return subnet.getId();
                }
            }
        } catch (RuntimeException e) {
            LOG.debug("Could not resolve tenant subnet: {}", e.toString());
        }
        return null;
    }

    public static String resolvePrivateIp(Server server) {
        if (server == null || server.getAddresses() == null) {
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

    private String resolveImageId(String image) {
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("Image name is required");
        }
        List<? extends Image> images = client().compute().images().list();
        for (Image candidate : images) {
            if (image.equals(candidate.getId())) {
                return candidate.getId();
            }
        }
        for (Image candidate : images) {
            if (image.equalsIgnoreCase(candidate.getName())) {
                return candidate.getId();
            }
        }
        String normalized = normalize(image);
        for (Image candidate : images) {
            if (normalized.equals(normalize(candidate.getName()))) {
                return candidate.getId();
            }
        }
        throw new IllegalArgumentException(
                "No image matching '" + image + "'. Available images: " + names(images));
    }

    private String resolveFlavorId(String flavor) {
        if (flavor == null || flavor.isBlank()) {
            throw new IllegalArgumentException("Flavor name is required");
        }
        List<? extends Flavor> flavors = client().compute().flavors().list();
        for (Flavor candidate : flavors) {
            if (flavor.equals(candidate.getId())) {
                return candidate.getId();
            }
        }
        for (Flavor candidate : flavors) {
            if (flavor.equalsIgnoreCase(candidate.getName())) {
                return candidate.getId();
            }
        }
        throw new IllegalArgumentException(
                "No flavor matching '" + flavor + "'. Available flavors: " + flavorNames(flavors));
    }

    private String resolveNetworkId() {
        String configured = AppConfig.get("openstack.network", "");
        List<? extends Network> networks = client().networking().network().list();
        if (configured != null && !configured.isBlank()) {
            for (Network candidate : networks) {
                if (configured.equals(candidate.getId())
                        || configured.equalsIgnoreCase(candidate.getName())) {
                    return candidate.getId();
                }
            }
            throw new IllegalArgumentException("configured openstack.network '"
                    + configured + "' not found. Available networks: " + networkNames(networks));
        }
        List<String> candidates = new ArrayList<>();
        for (Network candidate : networks) {
            if (!candidate.isRouterExternal()) {
                candidates.add(candidate.getId());
            }
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        throw new IllegalArgumentException("Multiple networks available; set "
                + "openstack.network to a network name or id in portal.properties. Available: "
                + networkNames(networks));
    }

    private String resolveKeypair(String keyPair) {
        if (keyPair == null || keyPair.isBlank()) {
            return null;
        }
        List<String> available = new ArrayList<>();
        for (Keypair candidate : client().compute().keypairs().list()) {
            if (keyPair.equals(candidate.getName())) {
                return candidate.getName();
            }
            available.add(candidate.getName());
        }
        throw new IllegalArgumentException(
                "Key pair '" + keyPair + "' does not exist. Available key pairs: " + available);
    }

    private static String normalize(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String names(List<? extends Image> images) {
        List<String> values = new ArrayList<>();
        for (Image image : images) {
            values.add(image.getName());
        }
        return values.toString();
    }

    private static String flavorNames(List<? extends Flavor> flavors) {
        List<String> values = new ArrayList<>();
        for (Flavor flavor : flavors) {
            values.add(flavor.getName());
        }
        return values.toString();
    }

    private static String networkNames(List<? extends Network> networks) {
        List<String> values = new ArrayList<>();
        for (Network network : networks) {
            values.add(network.getName());
        }
        return values.toString();
    }

    private void check(ActionResponse response, String action) {
        if (response != null && Boolean.FALSE.equals(response.isSuccess())) {
            throw new IllegalStateException("OpenStack " + action + " failed: " + response.getFault());
        }
    }
}
