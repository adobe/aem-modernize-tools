package com.adobe.aem.modernize.servlet;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;
import javax.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.jcr.api.SlingRepository;

import com.adobe.aem.modernize.component.job.ComponentJobExecutor;
import com.adobe.aem.modernize.job.AbstractConversionJobExecutor;
import com.adobe.aem.modernize.job.FullConversionJobExecutor;
import com.adobe.aem.modernize.model.ConversionJob;
import com.day.cq.commons.jcr.JcrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.adobe.aem.modernize.model.ConversionJob.*;
import static com.adobe.aem.modernize.model.ConversionJobBucket.*;
import static org.apache.sling.api.SlingHttpServletResponse.*;
import static org.apache.sling.api.servlets.ServletResolverConstants.*;

@Component(
    service = { Servlet.class },
    property = {
        SLING_SERVLET_RESOURCE_TYPES + "=aem-modernize/content/job/create",
        SLING_SERVLET_METHODS + "=POST",
        SLING_SERVLET_EXTENSIONS + "=json",
    }
)
public class ScheduleConversionJobServlet extends SlingAllMethodsServlet {

  private static final Logger logger = LoggerFactory.getLogger(ScheduleConversionJobServlet.class);

  private static final String SERVICE_NAME = "schedule-job";
  private static final String PARAM_DATA = "data";
  private static final int MAX_PROCESS_PATHS = 500;

  private static final String NN_BUCKETS = "buckets";

  @Reference
  private SlingRepository repository;

  @Reference
  private JobManager jobManager;

  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {

    RequestData data = getJobData(request);
    ResponseData responseData = new ResponseData();

    if (data == null) {
      responseData.setMessage("Error processing request parameters.");
      writeResponse(response, SC_BAD_REQUEST, responseData);
      return;
    }
    ResourceResolver rr = request.getResourceResolver();
    Session systemSession = null;
    try {
      checkPermissions(rr.adaptTo(Session.class), data.getPaths());

      systemSession = repository.loginService(SERVICE_NAME, null);
      List<String[]> buckets = createBuckets(data);
      String tracking = createTrackingState(systemSession, data, rr.getUserID(), buckets);
      if (scheduleJobs(systemSession, data.getType(), tracking)) {
        responseData.setSuccess(true);
        responseData.setMessage("Successfully scheduled conversion job.");
        responseData.setJob(tracking);
        writeResponse(response, SC_OK, responseData);
      } else {
        responseData.setMessage("Creating one of the conversion jobs failed.");
        writeResponse(response, SC_INTERNAL_SERVER_ERROR, responseData);
      }
    } catch (AccessDeniedException e) {
      logger.error("Missing permissions on a path", e);
      responseData.setMessage("Missing permissions for modifying a requested path.");
      writeResponse(response, SC_FORBIDDEN, responseData);
    } catch (RepositoryException e) {
      logger.error("Repository error when creating job.", e);
      responseData.setMessage("Unable to schedule job(s), check logs for details");
      writeResponse(response, SC_INTERNAL_SERVER_ERROR, responseData);
    } finally {
      if (systemSession != null) {
        systemSession.logout();
      }
    }
  }

  // Pull the RequestData from the request parameters
  private RequestData getJobData(SlingHttpServletRequest request) {
    try {
      String data = request.getParameter(PARAM_DATA);
      if (StringUtils.isNotBlank(data)) {
        return new ObjectMapper().readValue(data, RequestData.class);
      }
    } catch (IOException e) {
      logger.error("Unable to parse job data from request: {}", e.getLocalizedMessage());
    }
    return null;
  }

  // TODO: Add Logic for checking write permissions in /conf and /etc
  private void checkPermissions(Session session, String[] paths) throws AccessDeniedException {
    try {
      AccessControlManager acm = session.getAccessControlManager();
      Privilege[] privs = new Privilege[] { acm.privilegeFromName(Privilege.JCR_WRITE) };
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

  private List<String[]> createBuckets(RequestData requestData) {
    // Don't over load jobs, split up the request into buckets
    List<String[]> buckets = new ArrayList<>();
    String[] paths = requestData.getPaths();
    int bucketCount = paths.length / MAX_PROCESS_PATHS;
    if (bucketCount % MAX_PROCESS_PATHS != 0) {
      bucketCount++;
    }

    if (bucketCount > 1) {
      logger.warn("Processing {} paths exceeds the limit of {}, splitting it into {} distinct jobs.", paths.length, MAX_PROCESS_PATHS, bucketCount);
    }
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
  private String createTrackingState(Session session, RequestData requestData, String userId, List<String[]> buckets) throws RepositoryException {
    try {
      Node tracking = createTrackingNode(session, requestData, userId);
      Node parent = tracking.addNode(NN_BUCKETS, JcrConstants.NT_UNSTRUCTURED);
      for (String[] bucket : buckets) {
        addBucketNode(session, parent, bucket);
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
  private Node createTrackingNode(Session session, RequestData requestData, String userId) throws RepositoryException {
    Calendar today = Calendar.getInstance();
    String path = String.format("%s/%s/%s",
        ConversionJob.JOB_DATA_LOCATION,
        new SimpleDateFormat("yyyy/MM/dd").format(today.getTime()),
        JcrUtil.createValidName(requestData.getName(), JcrUtil.HYPHEN_LABEL_CHAR_MAPPING, "-"));
    Node node = JcrUtils.getOrCreateByPath(path, true, JcrConstants.NT_UNSTRUCTURED, JcrConstants.NT_UNSTRUCTURED, session, false);
    node.setProperty(PN_TITLE, requestData.getName());
    node.setProperty(PN_TEMPLATE_RULES, requestData.getTemplateRules());
    node.setProperty(PN_COMPONENT_RULES, requestData.getComponentRules());
    node.setProperty(PN_POLICY_RULES, requestData.getPolicyRules());
    node.setProperty(PN_INITIATOR, userId);
    node.setProperty(PN_TYPE, requestData.getType().toString());
    return node;
  }

  private void addBucketNode(Session session, Node parent, String[] paths) throws RepositoryException {
    Node bucket = JcrUtil.createUniqueNode(parent, "bucket", JcrConstants.NT_UNSTRUCTURED, session);
    bucket.setProperty(PN_PATHS, paths);
  }

  private boolean scheduleJobs(Session session, Type type, String trackingNode) throws RepositoryException {

    Node tracking = session.getNode(trackingNode);
    Node buckets = tracking.getNode(NN_BUCKETS);
    Map<String, Object> jobProperties;
    NodeIterator it = buckets.getNodes();
    while (it.hasNext()) {
      jobProperties = new HashMap<>();
      jobProperties.put(AbstractConversionJobExecutor.PN_TRACKING_PATH, it.nextNode().getPath());
      if (jobManager.addJob(type.getTopic(), jobProperties) == null) {
        logger.error("Unable to create job for topic: {}", type.getTopic());
        return false;
      }
    }
    return true;
  }

  private void writeResponse(SlingHttpServletResponse response, int code, ResponseData responseData) throws IOException {
    response.setStatus(code);
    response.setContentType("application/json");
    new ObjectMapper().writeValue(response.getOutputStream(), responseData);
  }

  @Getter
  @Setter
  static final class RequestData {
    private String name;
    private String[] paths;
    private String[] templateRules;
    private String[] componentRules;
    private String[] policyRules;
    private Type type;
  }

  @Getter
  @Setter
  static final class ResponseData {
    private boolean success;
    private String message;
    private String job;
  }

}
