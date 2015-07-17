package com.github.aesteve.vertx.nubes.routing;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.FormLoginHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.UserSessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;

import com.github.aesteve.vertx.nubes.Config;
import com.github.aesteve.vertx.nubes.annotations.Blocking;
import com.github.aesteve.vertx.nubes.handlers.Processor;
import com.github.aesteve.vertx.nubes.handlers.impl.DefaultMethodInvocationHandler;
import com.github.aesteve.vertx.nubes.handlers.impl.PayloadTypeProcessor;
import com.github.aesteve.vertx.nubes.utils.Filter;

public class MVCRoute {

	private final String path;
	private final HttpMethod httpMethod;
	private final Object instance;
	private Set<Filter> beforeFilters;
	private Set<Filter> afterFilters;
	private Method mainHandler;
	private Set<Handler<RoutingContext>> handlers;
	private Set<Processor> processors;
	private MVCRoute redirectRoute;
	private Handler<RoutingContext> authHandler;
	private String loginRedirect;
	private String logoutRedirect;
	private Handler<RoutingContext> preInterceptor;
	private Handler<RoutingContext> postInterceptor;
	private Config config;
	private boolean disabled;
	private BiConsumer<RoutingContext, ?> returnHandler;
	private String afterRedirectUrl;

	public MVCRoute(Object instance, String path, HttpMethod method, Config config, Handler<RoutingContext> authHandler, boolean disabled) {
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

	public void setLogoutRedirect(String logoutRedirect) {
		this.logoutRedirect = logoutRedirect;
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

	public void attachHandler(Handler<RoutingContext> handler) {
		handlers.add(handler);
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

	public void attachHandlersToRouter(Router router, HttpMethod httpMethod, String path) {
		boolean isRedirect = httpMethod != null && path != null;
		if (httpMethod == null) {
			httpMethod = this.httpMethod;
		}
		if (path == null) {
			path = this.path;
		}
		final HttpMethod httpMethodFinal = httpMethod;
		final String pathFinal = path;
		if (!isRedirect) {
			config.globalHandlers.forEach(handler -> {
				router.route(httpMethodFinal, pathFinal).handler(handler);
			});
		}
		if (authHandler != null) {
			//Maybe could be replaced by some regex like pathFinal + "*" ?
			router.route(httpMethodFinal, pathFinal).handler(CookieHandler.create());
			router.route(httpMethodFinal, pathFinal).handler(SessionHandler.create(LocalSessionStore.create(config.vertx)));
			router.route(httpMethodFinal, pathFinal).handler(UserSessionHandler.create(config.authProvider));
			router.route(httpMethodFinal, pathFinal).handler(authHandler);

			if (loginRedirect != null && !"".equals(loginRedirect)) {
				afterRedirectUrl = pathFinal; //We set the afterRedirectUrl only if there is a loginRedirect
				router.post(loginRedirect).handler(CookieHandler.create());
				router.post(loginRedirect).handler(BodyHandler.create());
				router.post(loginRedirect).handler(SessionHandler.create(LocalSessionStore.create(config.vertx)));
				router.post(loginRedirect).handler(UserSessionHandler.create(config.authProvider));
				router.post(loginRedirect).handler(FormLoginHandler.create(config.authProvider));
			}
		}

		if (logoutRedirect != null){
			router.get(pathFinal).handler(CookieHandler.create());
			router.get(pathFinal).handler(BodyHandler.create());
			router.get(pathFinal).handler(SessionHandler.create(LocalSessionStore.create(config.vertx)));
			router.get(pathFinal).handler(UserSessionHandler.create(config.authProvider));
			router.get(pathFinal).handler(context -> {
				context.clearUser();
				context.response().putHeader("location", logoutRedirect).setStatusCode(302).end();
			});
		}

		processors.forEach(processor -> {
			router.route(httpMethodFinal, pathFinal).handler(processor::preHandle);
		});
		handlers.forEach(handler -> {
			if (isRedirect) {
				if (!(handler instanceof BodyHandler)) { // we can't attach this handler twice
					router.route(httpMethodFinal, pathFinal).handler(handler);
				}
			} else {
				if(!(pathFinal.equals(afterRedirectUrl) && (handler instanceof BodyHandler))) //We don't attach the BodyHandler
					router.route(httpMethodFinal, pathFinal).handler(handler);				  //when path is same as afterRedirectUrl
			}																				  //to avoid "body already read" exception

		});
		int i = 0;
		boolean beforeFiltersHaveNext = mainHandler != null;
		for (Filter filter : beforeFilters) {
			boolean hasNext = beforeFiltersHaveNext || i < beforeFilters.size() - 1;
			setHandler(router, filter.method(), httpMethodFinal, pathFinal, hasNext);
			i++;
		}
		if (preInterceptor != null) {
			router.route(httpMethodFinal, pathFinal).handler(preInterceptor);
		}
		boolean mainHasNext = redirectRoute != null || postInterceptor != null || afterFilters.size() > 0 || processors.size() > 0;
		setHandler(router, mainHandler, httpMethodFinal, pathFinal, mainHasNext);
		if (redirectRoute != null) {
			// intercepted -> redirected => do not call post processing handlers
			redirectRoute.attachHandlersToRouter(router, httpMethod, path);
		}
		if (postInterceptor != null) {
			router.route(httpMethodFinal, pathFinal).handler(postInterceptor);
			// FIXME ?? : return;
		}
		i = 0;
		boolean afterFiltersHaveNext = processors.size() > 0;
		for (Filter filter : afterFilters) {
			boolean hasNext = afterFiltersHaveNext || i < afterFilters.size() - 1;
			setHandler(router, filter.method(), httpMethodFinal, pathFinal, hasNext);
			i++;
		}
		if (!mainHandler.getReturnType().equals(Void.TYPE) && returnHandler == null) { // try to set as payload
			processors.add(new PayloadTypeProcessor(config.marshallers));
		}
		processors.forEach(processor -> {
			router.route(httpMethodFinal, pathFinal).handler(processor::postHandle);
		});
	}

	private void setHandler(Router router, Method method, HttpMethod httpMethod, String path, boolean hasNext) {
		Handler<RoutingContext> handler = new DefaultMethodInvocationHandler<>(instance, method, config, hasNext, returnHandler);
		if (method.isAnnotationPresent(Blocking.class)) {
			router.route(httpMethod, path).blockingHandler(handler);
		} else {
			router.route(httpMethod, path).handler(handler);
		}
	}

	@Override
	public String toString() {
		return "Route : " + httpMethod.toString() + " " + path();
	}
}
