package io.elastest.etm.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.spotify.docker.client.messages.AttachedNetwork;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.Container.PortMapping;

public class CoreServiceInfo {
    String name;
    VersionInfo versionInfo;
    String imageName;
    Date imageDate;
    List<String> containerNames;
    List<PortMapping> ports;
    String status;
    List<String> networks;

    public CoreServiceInfo() {
        this.containerNames = new ArrayList<>();
        this.ports = new ArrayList<>();
        this.networks = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public VersionInfo getVersionInfo() {
        return versionInfo;
    }

    public void setVersionInfo(VersionInfo versionInfo) {
        this.versionInfo = versionInfo;
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public Date getImageDate() {
        return imageDate;
    }

    public void setImageDate(Date imageDate) {
        this.imageDate = imageDate;
    }

    public List<String> getContainerNames() {
        return containerNames;
    }

    public void setContainerNames(List<String> containerNames) {
        this.containerNames = containerNames;
    }

    public List<PortMapping> getPorts() {
        return ports;
    }

    public void setPorts(List<PortMapping> ports) {
        this.ports = ports;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getNetworks() {
        return networks;
    }

    public void setNetworks(List<String> networks) {
        this.networks = networks;
    }

    /* **** Others **** */

    public void setDataByContainer(Container container) {
        this.setContainerNames(container.names());
        this.setPorts(container.ports());
        this.setStatus(container.status());

        for (Map.Entry<String, AttachedNetwork> entry : container
                .networkSettings().networks().entrySet()) {
            this.getNetworks().add(entry.getKey());
        }
    }

    public String getFirstContainerNameCleaned() {
        String containerName = null;
        if (this.getContainerNames() != null
                && this.getContainerNames().size() > 0) {
            containerName = this.getContainerNames().get(0);
            if (containerName != null && containerName.startsWith("/")) {
                containerName = containerName.substring(1);
            }
        }
        return containerName;
    }

    @Override
    public String toString() {
        return "CoreServiceInfo [name=" + name + ", versionInfo="
                + versionInfo.toString() + ", imageName=" + imageName
                + ", imageDate=" + imageDate + ", containerNames="
                + containerNames + ", ports=" + ports + ", status=" + status
                + ", networks=" + networks + "]";
    }

}
