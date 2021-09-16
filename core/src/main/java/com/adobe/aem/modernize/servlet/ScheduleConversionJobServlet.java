package com.adobe.aem.modernize.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.jcr.api.SlingRepository;

import com.adobe.aem.modernize.job.FullConversionJobExecutor;
import com.day.cq.commons.jcr.JcrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.apache.sling.api.SlingHttpServletResponse.*;
import static org.apache.sling.api.servlets.ServletResolverConstants.*;
import static com.adobe.aem.modernize.model.ConversionJobItem.*;

@Component(
    service = { Servlet.class },
    property = {
        SLING_SERVLET_PATHS + "=/bin/modernize.json"
    }
)
public class ScheduleConversionJobServlet extends SlingAllMethodsServlet {

  private static final Logger logger = LoggerFactory.getLogger(ScheduleConversionJobServlet.class);

  private static final String SERVICE_NAME = "schedule-job";

  private static final int MAX_PROCESS_PATHS = 500;

  @Reference
  private SlingRepository repository;

  @Reference
  private JobManager jobManager;

  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {

    JobData data = getJobData(request);
    if (data == null) {
      writeResponse(response, SC_BAD_REQUEST, false, "Error processing request parameters.");
      return;
    }
    ResourceResolver rr = request.getResourceResolver();
    Session systemSession = null;
    try {
      checkPermissions(rr.adaptTo(Session.class), data.getPaths());

      systemSession = repository.loginService(SERVICE_NAME, null);
      List<String[]> buckets = createBuckets(data);
      String tracking = createTrackingState(systemSession, data, rr.getUserID(), buckets);
      if (scheduleJobs(data, buckets, tracking)) {
        writeResponse(response, SC_OK, true, "Successfully scheduled conversions.");
      } else {
        writeResponse(response, SC_INTERNAL_SERVER_ERROR, false, "Creating one of the the conversion jobs failed.");
      }
    } catch (AccessDeniedException e) {
      writeResponse(response, SC_FORBIDDEN, false,"Missing permissions for modifying a requested path.");
    } catch (RepositoryException e) {
      writeResponse(response, SC_INTERNAL_SERVER_ERROR, false, "Unable to schedule job(s), check logs for details");
    } finally {
      if (systemSession != null) {
        systemSession.logout();
      }
    }
  }

  // Pull the JobData from the request parameters
  private JobData getJobData(SlingHttpServletRequest request) {
    try {
      return new ObjectMapper().readValue(request.getInputStream(), JobData.class);
    } catch (IOException e) {
      logger.error("Unable to parse job data from request: {}", e.getLocalizedMessage());
    }
    return null;
  }

  private void checkPermissions(Session session, String[] paths) throws AccessDeniedException {
    try {
      AccessControlManager acm = session.getAccessControlManager();
      Privilege[] privs = new Privilege[]{ acm.privilegeFromName(Privilege.JCR_WRITE) };
      for (String path : paths) {
        if (!acm.hasPrivileges(path, privs)) {
          throw new AccessDeniedException(path);
        }
      }
    } catch (RepositoryException e) {
      logger.error("Unable to check permissions: {}", e.getLocalizedMessage());
      throw new AccessDeniedException(e.getLocalizedMessage());
    }

  }

  private List<String[]> createBuckets(JobData jobData) {
    // Don't over load jobs, split up the request into buckets
    List<String[]> buckets = new ArrayList<>();
    String[] paths = jobData.getPaths();
    int noOfBuckets = paths.length / MAX_PROCESS_PATHS;
    if (noOfBuckets % MAX_PROCESS_PATHS != 0) {
      noOfBuckets++;
    }

    logger.warn("Processing {} paths exceeds the limit of {}, splitting it into {} distinct jobs.", paths.length, MAX_PROCESS_PATHS, noOfBuckets);
    int offset = 0;

    while (offset < paths.length) {
      int endOffset = offset + MAX_PROCESS_PATHS;
      if (endOffset > paths.length) {
        endOffset = paths.length;
      }
      String[] bucketPaths = Arrays.copyOfRange(paths, offset, endOffset);
      buckets.add(bucketPaths);
      offset = endOffset;
    }
    return buckets;
  }

  // Create the tree of data for tracking the state of the job.
  private String createTrackingState(Session session, JobData jobData, String userId, List<String[]> buckets) throws RepositoryException {
    Node tracking = null;
    try {
      tracking = createTrackingNode(session, jobData, userId);
      for (String[] bucket : buckets) {
        addBucketNode(session, tracking, bucket);
      }
      session.save();
      session.refresh(true);
      return tracking.getPath();
    } catch (RepositoryException e) {
      logger.error("Could not save tracking data state.", e);
      throw e;
    }
  }

  // Create the parent node for tracking.
  private Node createTrackingNode(Session session, JobData jobData, String userId) throws RepositoryException {
    Calendar today = Calendar.getInstance();
    String path = String.format("%s/%d/%d/%d/%s",
        JOB_DATA_LOCATION,
        today.get(Calendar.YEAR),
        today.get(Calendar.MONTH),
        today.get(Calendar.DAY_OF_MONTH),
        JcrUtil.createValidName(jobData.getName(), JcrUtil.HYPHEN_LABEL_CHAR_MAPPING, "-"));
    Node node = JcrUtils.getOrCreateByPath(path, true, JcrConstants.NT_UNSTRUCTURED, JcrConstants.NT_UNSTRUCTURED, session, false);
    node.setProperty(PN_TITLE, jobData.getName());
    node.setProperty(PN_TEMPLATE_RULES, jobData.getTemplateRules());
    node.setProperty(PN_COMPONENT_RULES, jobData.getComponentRules());
    node.setProperty(PN_POLICY_RULES, jobData.getPolicyRules());
    node.setProperty(PN_INITIATOR, userId);
    return node;
  }

  private void addBucketNode(Session session, Node parent, String[] paths) throws RepositoryException {
    Node bucket = JcrUtil.createUniqueNode(parent, "bucket", JcrConstants.NT_UNSTRUCTURED, session);
    bucket.setProperty(PN_PAGE_PATHS, paths);
  }

  private boolean scheduleJobs(JobData jobData, List<String[]> buckets, String trackingPath) {
    
    Map<String, Object> jobProperties;
    for (String[] bucket: buckets) {
      jobProperties = new HashMap<>();
      jobProperties.put(PN_TRACKING_PATH, trackingPath);
      jobProperties.put(PN_PAGE_PATHS, bucket);
      jobProperties.put(PN_TEMPLATE_RULES, jobData.getTemplateRules());
      jobProperties.put(PN_COMPONENT_RULES, jobData.getComponentRules());
      jobProperties.put(PN_POLICY_RULES, jobData.getPolicyRules());
      if (jobManager.addJob(FullConversionJobExecutor.JOB_TOPIC, jobProperties) == null) {
        logger.error("Unable to create job for topic: {}", FullConversionJobExecutor.JOB_TOPIC);
        return false;
      }
    }
    return true;
  }

  private void writeResponse(SlingHttpServletResponse response, int code, boolean success, String message) throws ServletException, IOException {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode result = mapper.createObjectNode();
    result.put("status", success ? "success" : "failure");
    result.put("message", message);
    response.setStatus(code);
    response.setContentType("application/json");
    response.getWriter().write(result.toString());
  }

  @Getter
  @Setter
  static final class JobData {
    private String name;
    private String[] paths;
    private String[] templateRules;
    private String[] componentRules;
    private String[] policyRules;
  }

}
