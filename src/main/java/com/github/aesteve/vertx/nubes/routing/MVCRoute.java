package com.github.aesteve.vertx.nubes.routing;

import com.github.aesteve.vertx.nubes.Config;
import com.github.aesteve.vertx.nubes.annotations.Blocking;
import com.github.aesteve.vertx.nubes.handlers.Processor;
import com.github.aesteve.vertx.nubes.handlers.impl.DefaultMethodInvocationHandler;
import com.github.aesteve.vertx.nubes.handlers.impl.PayloadTypeProcessor;
import com.github.aesteve.vertx.nubes.reflections.Filter;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.sstore.LocalSessionStore;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;

public class MVCRoute {

  private final String path;
  private final HttpMethod httpMethod;
  private final Object instance;
  private final Set<Filter> beforeFilters;
  private final Set<Filter> afterFilters;
  private Method mainHandler;
  private final Set<Handler<RoutingContext>> handlers;
  private Set<Processor> processors;
  private MVCRoute redirectRoute;
  private final Handler<RoutingContext> authHandler;
  private String loginRedirect;
  private Handler<RoutingContext> preInterceptor;
  private Handler<RoutingContext> postInterceptor;
  private final Config config;
  private final boolean disabled;
  private BiConsumer<RoutingContext, ?> returnHandler;
  private final boolean usesSession;

  public MVCRoute(Object instance, String path, HttpMethod method, Config config, Handler<RoutingContext> authHandler, boolean disabled, final boolean usesSession) {
    this.instance = instance;
    this.config = config;
    this.path = path;
    this.httpMethod = method;
    this.beforeFilters = new TreeSet<>();
    this.afterFilters = new TreeSet<>();
    this.handlers = new LinkedHashSet<>();
    this.processors = new LinkedHashSet<>();
    this.authHandler = authHandler;
    this.disabled = disabled;
    this.usesSession = usesSession;
  }

  public boolean isEnabled() {
    return !disabled;
  }

  public void redirectTo(MVCRoute anotherRoute) {
    redirectRoute = anotherRoute;
  }

  public void setLoginRedirect(String loginRedirect) {
    this.loginRedirect = loginRedirect;
  }

  public void addProcessor(Processor processor) {
    processors.add(processor);
  }

  public void addProcessors(Set<Processor> processors) {
    this.processors.addAll(processors);
  }

  public void addProcessorsFirst(Set<Processor> processors) {
    Set<Processor> oldProcessors = new LinkedHashSet<>(this.processors);
    this.processors = new LinkedHashSet<>(oldProcessors.size() + processors.size());
    this.processors.addAll(processors);
    this.processors.addAll(oldProcessors);
  }

  public void attachInterceptor(Handler<RoutingContext> handler, boolean before) {
    if (before) {
      this.preInterceptor = handler;
    } else {
      this.postInterceptor = handler;
    }
  }

  public void attachReturnHandler(BiConsumer<RoutingContext, ?> handler) {
    returnHandler = handler;
  }

  public void attachHandlers(Set<Handler<RoutingContext>> newHandlers) {
    handlers.addAll(newHandlers);
  }

  public void setMainHandler(Method mainHandler) {
    this.mainHandler = mainHandler;
  }

  public void addBeforeFilters(Set<Filter> beforeFilters) {
    this.beforeFilters.addAll(beforeFilters);
  }

  public void addAfterFilters(Set<Filter> afterFilters) {
    this.afterFilters.addAll(afterFilters);
  }

  public String path() {
    return path;
  }

  public HttpMethod method() {
    return httpMethod;
  }

  public void attachHandlersToRouter(Router router) {
    config.forEachGlobalHandler(handler -> router.route(httpMethod, path).handler(handler));
    final Vertx vertx = config.getVertx();
    if (authHandler != null) {
      attachAuthHandler(router, vertx);
    } else if (usesSession) {
      router.route(httpMethod, path).handler(SessionHandler.create(LocalSessionStore.create(vertx)));
    }
    handlers.forEach(handler ->
      router.route(httpMethod, path).handler(handler)
    );
    attachPreProcessingHandlers(router);
    boolean hasPostProcessors = redirectRoute != null || postInterceptor != null || !afterFilters.isEmpty()|| !processors.isEmpty();
    setHandler(router, mainHandler, hasPostProcessors);
    if (redirectRoute != null) {
      // intercepted -> redirected => do not call post processing handlers
      router.route(httpMethod, path).handler(ctx ->
        ctx.reroute(redirectRoute.method(), redirectRoute.path())
      );
    }
    attachPostProcessingHandlers(router);
  }

  private void attachPreProcessingHandlers(Router router) {
    processors.forEach(processor -> router.route(httpMethod, path).handler(processor::preHandle));
    int i = 0;
    boolean beforeFiltersHaveNext = mainHandler != null;
    for (Filter filter : beforeFilters) {
      boolean hasNext = beforeFiltersHaveNext || i < beforeFilters.size() - 1;
      setHandler(router, filter.method(), hasNext);
      i++;
    }
    if (preInterceptor != null) {
      router.route(httpMethod, path).handler(preInterceptor);
    }
  }

  private void attachPostProcessingHandlers(Router router) {
    if (postInterceptor != null) {
      router.route(httpMethod, path).handler(postInterceptor);
    }
    int i = 0;
    boolean afterFiltersHaveNext = !processors.isEmpty();
    for (Filter filter : afterFilters) {
      boolean hasNext = afterFiltersHaveNext || i < afterFilters.size() - 1;
      setHandler(router, filter.method(), hasNext);
      i++;
    }
    if (!mainHandler.getReturnType().equals(Void.TYPE) && returnHandler == null) { // try to set as payload
      processors.add(new PayloadTypeProcessor(config.getMarshallers()));
    }
    processors.forEach(processor -> router.route(httpMethod, path).handler(processor::postHandle));
    processors.forEach(processor -> router.route(httpMethod, path).handler(processor::afterAll));

  }

  private void attachAuthHandler(Router router, Vertx vertx) {
    final AuthProvider authProvider = config.getAuthProvider();
    router.route(httpMethod, path).handler(CookieHandler.create());
    router.route(httpMethod, path).handler(UserSessionHandler.create(authProvider));
    router.route(httpMethod, path).handler(SessionHandler.create(LocalSessionStore.create(vertx)));
    router.route(httpMethod, path).handler(authHandler);
    if (loginRedirect != null && !"".equals(loginRedirect)) {
      router.post(loginRedirect).handler(CookieHandler.create());
      router.post(loginRedirect).handler(BodyHandler.create());
      router.post(loginRedirect).handler(UserSessionHandler.create(authProvider));
      router.post(loginRedirect).handler(SessionHandler.create(LocalSessionStore.create(vertx)));
      router.post(loginRedirect).handler(FormLoginHandler.create(authProvider));
    }
  }

  private void setHandler(Router router, Method method, boolean hasNext) {
    Handler<RoutingContext> handler = new DefaultMethodInvocationHandler<>(instance, method, config, hasNext, returnHandler);
    if (method.isAnnotationPresent(Blocking.class)) {
      router.route(httpMethod, path).blockingHandler(handler, method.getAnnotation(Blocking.class).ordered());
    } else {
      router.route(httpMethod, path).handler(handler);
    }
  }

  @Override
  public String toString() {
    return "Route : " + httpMethod.toString() + " " + path();
  }
}
