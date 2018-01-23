package io.elastest.etm.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.eti.kinoshita.testlinkjavaapi.model.*;
import br.eti.kinoshita.testlinkjavaapi.util.Util;

@Service
public class TestLinkDBService {

    private static final Logger logger = LoggerFactory
            .getLogger(TestLinkService.class);

    @Value("${et.etm.testlink.db}")
    public String testlinkDB;

    @Value("${et.etm.testlink.db.user}")
    public String testlinkDBUser;

    @Value("${et.etm.testlink.db.pass}")
    public String testlinkDBPass;

    @Value("${et.edm.mysql.host}")
    public String mysqlHost;

    @Value("${et.edm.mysql.port}")
    public String mysqlport;

    Connection conn;
    Statement stmt;

    @PostConstruct
    public void init() {
        String url = "jdbc:mysql://" + mysqlHost + ":" + mysqlport + "/"
                + testlinkDB + "?autoReconnect=true&useSSL=false";

        try {
            conn = DriverManager.getConnection(url, testlinkDBUser,
                    testlinkDBPass);
            stmt = conn.createStatement();
        } catch (SQLException e) {
            logger.error(e.getMessage());
        }

    }

    /* ************************************************************/
    /* ************************** Utils ***************************/
    /* ************************************************************/

    public List<HashMap<String, Object>> convertResultSetToList(ResultSet rs)
            throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int columns = md.getColumnCount();
        List<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

        while (rs.next()) {
            HashMap<String, Object> row = new HashMap<String, Object>(columns);
            for (int i = 1; i <= columns; ++i) {
                row.put(md.getColumnName(i), rs.getObject(i));
            }
            list.add(row);
        }

        return list;
    }

    public Execution[] getExecutionListFromResultList(
            List<HashMap<String, Object>> resultList) {
        Execution[] executions = null;

        for (HashMap<String, Object> execution : resultList) {
            Execution exec = Util.getExecution(execution);
            executions = (Execution[]) ArrayUtils.add(executions, exec);
        }

        return executions;
    }

    /* ************************************************************/
    /* *************************** Api ****************************/
    /* ************************************************************/

    public Execution[] getAllExecs() {
        Execution[] executions = null;
        try {
            ResultSet rs = stmt.executeQuery("SELECT * FROM executions");
            List<HashMap<String, Object>> resultList = this
                    .convertResultSetToList(rs);

            executions = this.getExecutionListFromResultList(resultList);
        } catch (SQLException e) {
            logger.error(e.getMessage());
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        return executions;
    }

    public Execution[] getExecsByCase(Integer testPlanId, Integer buildId,
            Integer testCaseId, Integer testCaseExternalId,
            Integer platformId) {
        Execution[] executions = null;
        if (testCaseId != null) {
            String query = "SELECT * FROM executions";
            String testCaseQuery = " WHERE tcversion_id IN ("
                    + "SELECT id FROM nodes_hierarchy" + " WHERE parent_id = "
                    + testCaseId + ")";
            query += testCaseQuery;

            if (testPlanId != null) {
                String testPlanQuery = " AND testplan_id = " + testPlanId;
                query += testPlanQuery;
            }

            if (buildId != null) {
                String buildQuery = " AND build_id = " + buildId;
                query += buildQuery;
            }

            if (platformId != null) {
                String platformQuery = " AND platform_id = " + platformId;
                query += platformQuery;
            }

            try {
                ResultSet rs = stmt.executeQuery(query);
                List<HashMap<String, Object>> resultList = this
                        .convertResultSetToList(rs);

                executions = this.getExecutionListFromResultList(resultList);
            } catch (SQLException e) {
                logger.error(e.getMessage());
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
        return executions;
    }

    public Execution[] getExecsByPlanCase(Integer testCaseId,
            Integer testPlanId) {
        Execution[] executions = this.getExecsByCase(testPlanId, null,
                testCaseId, null, null);
        return executions;
    }

    public Execution[] getExecsByBuildCase(Build build, Integer testCaseId) {
        Execution[] executions = this.getExecsByCase(build.getTestPlanId(),
                build.getId(), testCaseId, null, null);
        return executions;
    }

}
