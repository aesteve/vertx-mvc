package com.github.aesteve.vertx.nubes.reflections;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.UserSessionHandler;
import io.vertx.ext.web.handler.sockjs.BridgeEvent;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.vertx.ext.web.sstore.LocalSessionStore;
import org.reflections.Reflections;

import com.github.aesteve.vertx.nubes.Config;
import com.github.aesteve.vertx.nubes.annotations.sockjs.bridge.EventBusBridge;
import com.github.aesteve.vertx.nubes.annotations.sockjs.bridge.InboundPermitted;
import com.github.aesteve.vertx.nubes.annotations.sockjs.bridge.OutboundPermitted;

public class EventBusBridgeFactory extends AbstractInjectionFactory implements HandlerFactory {
	private static final Logger log = LoggerFactory.getLogger(SocketFactory.class);

	private Router router;

	public EventBusBridgeFactory(Router router, Config config) {
		this.router = router;
		this.config = config;
	}

	public void createHandlers() {
		config.controllerPackages.forEach(controllerPackage -> {
			Reflections reflections = new Reflections(controllerPackage);
			Set<Class<?>> controllers = reflections.getTypesAnnotatedWith(EventBusBridge.class);
			controllers.forEach(controller -> {
				createSocketHandlers(controller);
			});
		});
	}

	private void createSocketHandlers(Class<?> controller) {
		SockJSHandler sockJSHandler = SockJSHandler.create(config.vertx, config.sockJSOptions);
		EventBusBridge annot = controller.getAnnotation(EventBusBridge.class);
		BridgeOptions bridge = createBridgeOptions(controller);
		String path = annot.value();
		Object ctrlInstance = null;
		try {
			ctrlInstance = controller.newInstance();
			injectServicesIntoController(ctrlInstance);
		} catch (Exception e) {
			throw new RuntimeException("Could not instanciate socket controller : " + controller.getName(), e);
		}
		final Object instance = ctrlInstance;
		Map<BridgeEvent.Type, Method> handlers = BridgeEventFactory.createFromController(controller);
		sockJSHandler.bridge(bridge, be -> {
			Method method = handlers.get(be.type());
			if (method != null) {
				tryToInvoke(instance, method, be);
			} else {
				be.complete(true);
			}
		});
		if (!path.endsWith("/*")) {
			if (path.endsWith("/")) {
				path += "*";
			} else {
				path += "/*";
			}
		}
		//Needed handlers to allow the use of authentication through eventbus
		if(config.authProvider!=null) {
			router.route(path).handler(CookieHandler.create());
			router.route(path).handler(BodyHandler.create());
			router.route(path).handler(SessionHandler.create(LocalSessionStore.create(config.vertx)));
			router.route(path).handler(UserSessionHandler.create(config.authProvider));
		}
		router.route(path).handler(sockJSHandler);
	}

	private static BridgeOptions createBridgeOptions(Class<?> controller) {
		BridgeOptions options = new BridgeOptions();
		InboundPermitted[] inbounds = controller.getAnnotationsByType(InboundPermitted.class);
		if (inbounds.length > 0) {
			List<PermittedOptions> inboundPermitteds = new ArrayList<>(inbounds.length);
			for (InboundPermitted inbound : inbounds) {
				inboundPermitteds.add(createInboundPermitted(inbound));
			}
			options.setInboundPermitted(inboundPermitteds);

		} else {
			options.addInboundPermitted(new PermittedOptions());
		}
		OutboundPermitted[] outbounds = controller.getAnnotationsByType(OutboundPermitted.class);
		if (outbounds.length > 0) {
			List<PermittedOptions> outboundPermitteds = new ArrayList<>(outbounds.length);
			for (OutboundPermitted outbound : outbounds) {
				outboundPermitteds.add(createOutboundPermitted(outbound));
			}
			options.setOutboundPermitted(outboundPermitteds);
		} else {
			options.addOutboundPermitted(new PermittedOptions());
		}
		return options;
	}

	private static PermittedOptions createOutboundPermitted(OutboundPermitted outbound) {
		String address = outbound.address();
		String addressRegex = outbound.addressRegex();
		String requiredAuthority = outbound.requiredAuthority();
		return createPermittedOptions(address, addressRegex, requiredAuthority);
	}

	private static PermittedOptions createInboundPermitted(InboundPermitted inbound) {
		String address = inbound.address();
		String addressRegex = inbound.addressRegex();
		String requiredAuthority = inbound.requiredAuthority();
		return createPermittedOptions(address, addressRegex, requiredAuthority);
	}

	private static PermittedOptions createPermittedOptions(String address, String addressRegex, String requiredAuthority) {
		PermittedOptions options = new PermittedOptions();
		if (!"".equals(address)) {
			options.setAddress(address);
		}
		if (!"".equals(addressRegex)) {
			options.setAddressRegex(addressRegex);
		}
		if (!"".equals(requiredAuthority)) {
			options.setRequiredAuthority(requiredAuthority);
		}
		return options;
	}

	private void tryToInvoke(Object instance, Method method, BridgeEvent be) {
		List<Object> paramInstances = new ArrayList<>();
		for (Class<?> parameterClass : method.getParameterTypes()) {
			if (parameterClass.equals(BridgeEvent.class)) {
				paramInstances.add(be);
			} else if (parameterClass.equals(EventBus.class)) {
				paramInstances.add(config.vertx.eventBus());
			} else if (parameterClass.equals(Vertx.class)) {
				paramInstances.add(config.vertx);
			}
		}
		try {
			method.invoke(instance, paramInstances.toArray());
		} catch (Exception e) {
			log.error("Error while handling websocket", e);
			if (!be.failed() && !be.succeeded()) {
				be.fail(e);
			}
		}
	}

}
