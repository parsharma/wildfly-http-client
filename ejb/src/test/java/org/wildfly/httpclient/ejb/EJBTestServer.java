package org.wildfly.httpclient.ejb;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.jboss.ejb.client.SessionID;
import org.jboss.ejb.server.Association;
import org.jboss.ejb.server.CancelHandle;
import org.jboss.ejb.server.ClusterTopologyListener;
import org.jboss.ejb.server.InvocationRequest;
import org.jboss.ejb.server.ListenerHandle;
import org.jboss.ejb.server.ModuleAvailabilityListener;
import org.jboss.ejb.server.SessionOpenRequest;
import org.junit.runners.model.InitializationError;
import org.wildfly.common.annotation.NotNull;
import org.wildfly.httpclient.common.HTTPTestServer;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.handlers.PathHandler;

/**
 * @author Stuart Douglas
 */
public class EJBTestServer extends HTTPTestServer {

    private static volatile TestEJBHandler handler;

    public EJBTestServer(Class<?> klass) throws InitializationError {
        super(klass);
    }

    public static TestEJBHandler getHandler() {
        return handler;
    }

    public static void setHandler(TestEJBHandler handler) {
        EJBTestServer.handler = handler;
    }

    @Override
    protected void registerPaths(PathHandler servicesHandler) {
        servicesHandler.addPrefixPath("/ejb", new EjbHttpService(new Association() {
            @Override
            public <T> CancelHandle receiveInvocationRequest(@NotNull InvocationRequest invocationRequest) {
                TestCancelHandle handle = new TestCancelHandle();
                try {
                    InvocationRequest.Resolved request = invocationRequest.getRequestContent(getClass().getClassLoader());
                    HttpInvocationHandler.ResolvedInvocation resolvedInvocation = (HttpInvocationHandler.ResolvedInvocation) request;
                    TestEjbOutput out = new TestEjbOutput();
                    getWorker().execute(() -> {
                        try {
                            Object result = handler.handle(request, resolvedInvocation.getSessionAffinity(), out, invocationRequest.getMethodLocator(), handle);
                            if (out.getSessionAffinity() != null) {
                                resolvedInvocation.getExchange().getResponseCookies().put("JSESSIONID", new CookieImpl("JSESSIONID", out.getSessionAffinity()));
                            }
                            request.writeInvocationResult(result);
                        } catch (Exception e) {
                            invocationRequest.writeException(e);
                        }
                    });
                } catch (Exception e) {
                    invocationRequest.writeException(e);
                }
                return handle;
            }

            @Override
            public CancelHandle receiveSessionOpenRequest(@NotNull SessionOpenRequest sessionOpenRequest) {
                sessionOpenRequest.convertToStateful(SessionID.createSessionID("SFSB_ID".getBytes(StandardCharsets.UTF_8)));
                return null;
            }

            @Override
            public ListenerHandle registerClusterTopologyListener(@NotNull ClusterTopologyListener clusterTopologyListener) {
                return null;
            }

            @Override
            public ListenerHandle registerModuleAvailabilityListener(@NotNull ModuleAvailabilityListener moduleAvailabilityListener) {
                return null;
            }
        }, null, null).createHttpHandler());

    }

    public static class TestCancelHandle implements CancelHandle {

        private final LinkedBlockingDeque<Boolean> resultQueue = new LinkedBlockingDeque<>();

        @Override
        public void cancel(boolean aggressiveCancelRequested) {
            resultQueue.add(aggressiveCancelRequested);
        }

        public Boolean awaitResult() {
            try {
                return resultQueue.poll(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
