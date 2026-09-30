package com.portal.servlet;

import com.portal.dao.VmDao;
import com.portal.model.VmDetails;
import com.portal.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lists the caller's VMs. Reads MongoDB only -- never calls OpenStack, so the
 * dashboard stays fast. Live status is reconciled by the background sync
 * worker instead.
 */
public class VmListServlet extends BaseServlet {

    private final VmDao vmDao = new VmDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!requireSession(request, response)) {
            return;
        }
        String userId = currentUser(request);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (VmDetails vm : vmDao.findByUser(userId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("vmId", vm.getVmId());
            row.put("vmName", vm.getVmName());
            row.put("status", vm.getStatus());
            row.put("projectName", vm.getProjectName());
            row.put("vmIp", vm.getVmIp());
            row.put("floatIp", vm.getFloatIp());
            rows.add(row);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("vms", rows);
        sendOk(response, body);
    }
}
