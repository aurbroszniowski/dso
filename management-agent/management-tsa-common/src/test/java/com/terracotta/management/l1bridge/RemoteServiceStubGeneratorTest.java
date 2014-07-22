package com.terracotta.management.l1bridge;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.terracotta.management.l1bridge.RemoteCallDescriptor;
import org.terracotta.management.resource.Representable;

import com.terracotta.management.security.impl.NullContextService;
import com.terracotta.management.security.impl.NullRequestTicketMonitor;
import com.terracotta.management.security.impl.NullUserService;
import com.terracotta.management.service.ActiveServerSource;
import com.terracotta.management.service.RemoteAgentBridgeService;
import com.terracotta.management.service.impl.TimeoutServiceImpl;
import com.terracotta.management.web.proxy.ProxyException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.ws.rs.WebApplicationException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ludovic Orban
 */
public class RemoteServiceStubGeneratorTest {


  private ExecutorService executorService;

  @Before
  public void setUp() throws Exception {
    executorService = Executors.newSingleThreadExecutor();
  }

  @After
  public void tearDown() throws Exception {
    executorService.shutdown();
  }

  @Test
  public void testPassiveProxyingToActive() throws Exception {
    RemoteRequestValidator remoteRequestValidator = mock(RemoteRequestValidator.class);
    RemoteAgentBridgeService remoteAgentBridgeService = mock(RemoteAgentBridgeService.class);
    ActiveServerSource activeServerSource = mock(ActiveServerSource.class);
    RemoteServiceStubGenerator remoteServiceStubGenerator = new RemoteServiceStubGenerator(new NullRequestTicketMonitor(), new NullUserService(), new NullContextService(), remoteRequestValidator, remoteAgentBridgeService, executorService, new TimeoutServiceImpl(1000), activeServerSource);

    when(activeServerSource.isCurrentServerActive()).thenReturn(false);
    when(activeServerSource.getActiveL2Urls()).thenReturn(Arrays.asList("http://some-host:1234"));

    DummyCacheService cacheService = remoteServiceStubGenerator.newRemoteService(DummyCacheService.class, "cache");
    try {
      cacheService.getCaches();
      fail("expected ProxyException");
    } catch (ProxyException pe) {
      assertThat(pe.getActiveL2Url(), equalTo("http://some-host:1234"));
    }
  }

  @Test
  public void testPassiveThrowing404WhenNoActive() throws Exception {
    RemoteRequestValidator remoteRequestValidator = mock(RemoteRequestValidator.class);
    RemoteAgentBridgeService remoteAgentBridgeService = mock(RemoteAgentBridgeService.class);
    ActiveServerSource activeServerSource = mock(ActiveServerSource.class);
    RemoteServiceStubGenerator remoteServiceStubGenerator = new RemoteServiceStubGenerator(new NullRequestTicketMonitor(), new NullUserService(), new NullContextService(), remoteRequestValidator, remoteAgentBridgeService, executorService, new TimeoutServiceImpl(1000), activeServerSource);

    when(activeServerSource.isCurrentServerActive()).thenReturn(false);
    when(activeServerSource.getActiveL2Urls()).thenReturn(Collections.<String>emptyList());

    DummyCacheService cacheService = remoteServiceStubGenerator.newRemoteService(DummyCacheService.class, "cache");
    try {
      cacheService.getCaches();
      fail("expected WebApplicationException");
    } catch (WebApplicationException wae) {
      assertThat(wae.getResponse().getStatus(), equalTo(404));
    }
  }

  @Test
  public void testAgencyFilteringAndMergingWithMultipleNodes() throws Exception {
    List<CacheRepresentable> caches = Arrays.asList(new CacheRepresentable("cache-1"), new CacheRepresentable("cache-2"), new CacheRepresentable("cache-3"));
    final byte[] serializedCaches = serialize(caches);
    List<SessionRepresentable> sessions1 = Arrays.asList(new SessionRepresentable("sess-1"), new SessionRepresentable("sess-2"));
    final byte[] serializedSessions1 = serialize(sessions1);
    List<SessionRepresentable> sessions2 = Arrays.asList(new SessionRepresentable("sess-3"), new SessionRepresentable("sess-4"));
    final byte[] serializedSessions2 = serialize(sessions2);

    RemoteRequestValidator remoteRequestValidator = mock(RemoteRequestValidator.class);
    RemoteAgentBridgeService remoteAgentBridgeService = mock(RemoteAgentBridgeService.class);
    ActiveServerSource activeServerSource = mock(ActiveServerSource.class);
    RemoteServiceStubGenerator remoteServiceStubGenerator = new RemoteServiceStubGenerator(new NullRequestTicketMonitor(), new NullUserService(), new NullContextService(), remoteRequestValidator, remoteAgentBridgeService, executorService, new TimeoutServiceImpl(1000), activeServerSource);

    when(activeServerSource.isCurrentServerActive()).thenReturn(true);
    when(remoteRequestValidator.getValidatedNodes()).thenReturn(new HashSet<String>(Arrays.asList("node_cache", "node1_session", "node2_session")));
    when(remoteRequestValidator.getSingleValidatedNode()).thenThrow(new RuntimeException("Multiple nodes were specified"));
    when(remoteAgentBridgeService.getRemoteAgentNodeDetails(eq("node_cache"))).thenReturn(new HashMap<String, String>() {{
      put("Agency", "cache");
    }});
    when(remoteAgentBridgeService.getRemoteAgentNodeDetails(eq("node1_session"))).thenReturn(new HashMap<String, String>() {{
      put("Agency", "session");
    }});
    when(remoteAgentBridgeService.getRemoteAgentNodeDetails(eq("node2_session"))).thenReturn(new HashMap<String, String>() {{
      put("Agency", "session");
    }});
    when(remoteAgentBridgeService.invokeRemoteMethod(eq("node_cache"), any(RemoteCallDescriptor.class))).thenReturn(serializedCaches);
    when(remoteAgentBridgeService.invokeRemoteMethod(eq("node1_session"), any(RemoteCallDescriptor.class))).thenReturn(serializedSessions1);
    when(remoteAgentBridgeService.invokeRemoteMethod(eq("node2_session"), any(RemoteCallDescriptor.class))).thenReturn(serializedSessions2);

    DummyCacheService cacheService = remoteServiceStubGenerator.newRemoteService(DummyCacheService.class, "cache");
    DummySessionService sessionService = remoteServiceStubGenerator.newRemoteService(DummySessionService.class, "session");

    assertThat(cacheService.getCaches(), CoreMatchers.<Collection<CacheRepresentable>>equalTo(caches));
    assertThatAgentIdIsIn(cacheService.getCaches(), "node_cache");
    assertThat(sessionService.getSessions(), CoreMatchers.<Collection<SessionRepresentable>>equalTo(merge(sessions1, sessions2)));
    assertThatAgentIdIsIn(sessionService.getSessions(), "node1_session", "node2_session");
  }

  @Test
  public void testAgencyFilteringWithSingleNode() throws Exception {
    List<CacheRepresentable> caches = Arrays.asList(new CacheRepresentable("cache-1"), new CacheRepresentable("cache-2"), new CacheRepresentable("cache-3"));
    final byte[] serializedCaches = serialize(caches);

    RemoteRequestValidator remoteRequestValidator = mock(RemoteRequestValidator.class);
    RemoteAgentBridgeService remoteAgentBridgeService = mock(RemoteAgentBridgeService.class);
    ActiveServerSource activeServerSource = mock(ActiveServerSource.class);
    RemoteServiceStubGenerator remoteServiceStubGenerator = new RemoteServiceStubGenerator(new NullRequestTicketMonitor(), new NullUserService(), new NullContextService(), remoteRequestValidator, remoteAgentBridgeService, executorService, new TimeoutServiceImpl(1000), activeServerSource);

    when(activeServerSource.isCurrentServerActive()).thenReturn(true);
    when(remoteRequestValidator.getValidatedNodes()).thenReturn(new HashSet<String>(Arrays.asList("node_cache")));
    when(remoteRequestValidator.getSingleValidatedNode()).thenReturn("node_cache");
    when(remoteAgentBridgeService.getRemoteAgentNodeDetails(eq("node_cache"))).thenReturn(new HashMap<String, String>() {{
      put("Agency", "cache");
    }});
    when(remoteAgentBridgeService.invokeRemoteMethod(eq("node_cache"), any(RemoteCallDescriptor.class))).thenReturn(serializedCaches);

    DummyCacheService cacheService = remoteServiceStubGenerator.newRemoteService(DummyCacheService.class, "cache");
    assertThat(cacheService.getCaches(), CoreMatchers.<Collection<CacheRepresentable>>equalTo(caches));
    assertThatAgentIdIsIn(cacheService.getCaches(), "node_cache");

    DummySessionService sessionService = remoteServiceStubGenerator.newRemoteService(DummySessionService.class, "session");
    assertThat(sessionService.getSessions(), CoreMatchers.<Collection<SessionRepresentable>>equalTo(new ArrayList<SessionRepresentable>()));
  }

  private static void assertThatAgentIdIsIn(Collection<? extends Representable> representables, String... ids) {
    for (Representable representable : representables) {
      assertThat(representable.getAgentId(), Matchers.isOneOf(ids));
    }
  }

  private static <T> List<T> merge(List<T> list1, List<T> list2) {
    List<T> result = new ArrayList<T>();
    result.addAll(list1);
    result.addAll(list2);
    return result;
  }

  private static byte[] serialize(Object obj) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(obj);
    oos.close();
    return baos.toByteArray();
  }

  static class CacheRepresentable implements Representable {
    private String agentId;
    private String cacheName;

    CacheRepresentable(String cacheName) {
      this.agentId = Representable.EMBEDDED_AGENT_ID;
      this.cacheName = cacheName;
    }

    public void setAgentId(String agentId) {
      this.agentId = agentId;
    }

    public void setCacheName(String cacheName) {
      this.cacheName = cacheName;
    }

    public String getAgentId() {
      return agentId;
    }

    public String getCacheName() {
      return cacheName;
    }

    @Override
    public int hashCode() {
      return cacheName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof CacheRepresentable) {
        CacheRepresentable other = (CacheRepresentable)obj;
        return other.cacheName.equals(cacheName);
      }
      return false;
    }
  }

  static class SessionRepresentable implements Representable {
    private String agentId;
    private String sessionName;

    SessionRepresentable(String sessionName) {
      this.agentId = Representable.EMBEDDED_AGENT_ID;
      this.sessionName = sessionName;
    }

    public String getAgentId() {
      return agentId;
    }

    public void setAgentId(String agentId) {
      this.agentId = agentId;
    }

    public String getSessionName() {
      return sessionName;
    }

    public void setSessionName(String sessionName) {
      this.sessionName = sessionName;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof SessionRepresentable) {
        SessionRepresentable other = (SessionRepresentable)obj;
        return other.sessionName.equals(sessionName);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return sessionName.hashCode();
    }
  }

  static interface DummyCacheService {
    Collection<CacheRepresentable> getCaches();
  }

  static interface DummySessionService {
    Collection<SessionRepresentable> getSessions();
  }
}
