package com.portal.servlet;

import com.portal.dao.VmDao;
import com.portal.model.VmDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VmListServlet extends BaseServlet {

    private final VmDao vmDao = new VmDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!requireSession(request, response)) {
            return;
        }
        List<VmDetails> vms = vmDao.findByUser(currentUser(request));

        List<Map<String, Object>> rows = new ArrayList<>(vms.size());
        for (VmDetails vm : vms) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("vmId", vm.getVmId());
            row.put("vmName", vm.getVmName());
            row.put("status", vm.getStatus());
            row.put("projectName", vm.getProjectName());
            row.put("vmIp", vm.getVmIp());
            row.put("floatIp", vm.getFloatIp());
            rows.add(row);
        }
        sendOk(response, Map.of("vms", rows));
    }
}
