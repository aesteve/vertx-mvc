package com.github.aesteve.vertx.nubes;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.jdbc.JDBCAuth;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.shiro.ShiroAuth;
import io.vertx.ext.auth.shiro.ShiroAuthRealmType;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;
import io.vertx.ext.web.templ.HandlebarsTemplateEngine;
import io.vertx.ext.web.templ.JadeTemplateEngine;
import io.vertx.ext.web.templ.MVELTemplateEngine;
import io.vertx.ext.web.templ.TemplateEngine;
import io.vertx.ext.web.templ.ThymeleafTemplateEngine;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.github.aesteve.vertx.nubes.auth.AuthMethod;
import com.github.aesteve.vertx.nubes.context.RateLimit;
import com.github.aesteve.vertx.nubes.handlers.AnnotationProcessorRegistry;
import com.github.aesteve.vertx.nubes.handlers.Processor;
import com.github.aesteve.vertx.nubes.marshallers.PayloadMarshaller;
import com.github.aesteve.vertx.nubes.reflections.RouteRegistry;
import com.github.aesteve.vertx.nubes.reflections.injectors.annot.AnnotatedParamInjectorRegistry;
import com.github.aesteve.vertx.nubes.reflections.injectors.typed.TypedParamInjectorRegistry;
import com.github.aesteve.vertx.nubes.services.ServiceRegistry;

public class Config {

	private static final Logger log = LoggerFactory.getLogger(Config.class);

	private Config() {
		bundlesByLocale = new HashMap<>();
		globalHandlers = new ArrayList<>();
		templateEngines = new HashMap<>();
		sockJSOptions = new SockJSHandlerOptions();
		marshallers = new HashMap<>();
	}

	public JsonObject json;
	public String srcPackage;
	public List<String> controllerPackages;
	public List<String> fixturePackages;
	public String verticlePackage;
	public String domainPackage;
	public RateLimit rateLimit;
	public String webroot;
	public String assetsPath;
	public String tplDir;
	public boolean displayErrors;
	public Vertx vertx;
	public AuthProvider authProvider;
	public AuthMethod authMethod;
	public String i18nDir;

	public AnnotationProcessorRegistry apRegistry;
	public Map<Class<? extends Annotation>, Set<Handler<RoutingContext>>> annotationHandlers;
	public Map<Class<?>, Processor> typeProcessors;
	public TypedParamInjectorRegistry typeInjectors;
	public AnnotatedParamInjectorRegistry annotInjectors;
	public ServiceRegistry serviceRegistry;
	public RouteRegistry routeRegistry;
	public Map<Class<?>, Handler<RoutingContext>> paramHandlers;
	public Map<String, Handler<RoutingContext>> aopHandlerRegistry;
	public Map<Locale, ResourceBundle> bundlesByLocale;
	public List<Handler<RoutingContext>> globalHandlers;
	public Map<String, TemplateEngine> templateEngines;
	public SockJSHandlerOptions sockJSOptions;
	public Map<String, PayloadMarshaller> marshallers;

	/**
	 * TODO : check config instead of throwing exceptions
	 * TODO : we should be consistent on single/multiple values
	 * (controllers is an array, fixtures is a list, domain is a single value, verticle is a single value) : this is wrong
	 * 
	 * @param json
	 * @return config
	 */
	@SuppressWarnings("unchecked")
	public static Config fromJsonObject(JsonObject json, Vertx vertx) {
		Config instance = new Config();

		instance.json = json;
		instance.vertx = vertx;
		instance.srcPackage = json.getString("src-package");
		instance.i18nDir = json.getString("i18nDir", "web/i18n/");
		if (!instance.i18nDir.endsWith("/")) {
			instance.i18nDir = instance.i18nDir + "/";
		}
		JsonArray controllers = json.getJsonArray("controller-packages");
		if (instance.srcPackage != null && controllers == null) {
			controllers = new JsonArray().add(instance.srcPackage + ".controllers");
		}
		instance.controllerPackages = controllers.getList();

		instance.verticlePackage = json.getString("verticle-package");
		if (instance.verticlePackage == null && instance.srcPackage != null) {
			instance.verticlePackage = instance.srcPackage + ".verticles";
		}

		instance.domainPackage = json.getString("domain-package", instance.srcPackage + ".domains");
		JsonArray fixtures = json.getJsonArray("fixture-packages");
		if (fixtures == null) {
			fixtures = new JsonArray();
			if (instance.srcPackage != null) {
				fixtures.add(instance.srcPackage + ".fixtures");
			}
		}
		instance.fixturePackages = fixtures.getList();

		// Register services included in config
		JsonObject services = json.getJsonObject("services", new JsonObject());
		instance.serviceRegistry = new ServiceRegistry(vertx);
		services.forEach(entry -> {
			String name = entry.getKey();
			String className = (String) entry.getValue();
			try {
				Class<?> clazz = Class.forName(className);
				instance.serviceRegistry.registerService(name, clazz.newInstance());
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		});

		// Register templateEngines for extensions added in config
		JsonArray templates = json.getJsonArray("templates", new JsonArray());
		if (templates.contains("hbs")) {
			instance.templateEngines.put("hbs", HandlebarsTemplateEngine.create());
		}
		if (templates.contains("jade")) {
			instance.templateEngines.put("jade", JadeTemplateEngine.create());
		}
		if (templates.contains("templ")) {
			instance.templateEngines.put("templ", MVELTemplateEngine.create());
		}
		if (templates.contains("thymeleaf")) {
			instance.templateEngines.put("html", ThymeleafTemplateEngine.create());
		}

		JsonObject rateLimitJson = json.getJsonObject("throttling");
		if (rateLimitJson != null) {
			int count = rateLimitJson.getInteger("count");
			int value = rateLimitJson.getInteger("time-frame");
			TimeUnit timeUnit = TimeUnit.valueOf(rateLimitJson.getString("time-unit"));
			instance.rateLimit = new RateLimit(count, value, timeUnit);
		}

		String auth = json.getString("auth-type");
		JsonObject authProperties = json.getJsonObject("auth-properties");

		// TODO : discuss it. I'm really not convinced about all the boilerplate needed in config (dbName only for JDBC, etc.)
		if (auth != null && authProperties != null) {
			// For now, only JWT,Shiro and JDBC supported (same as for Vert.x web)
			switch (auth) {
				case "JWT":// For now only allow properties realm
					instance.authProvider = JWTAuth.create(vertx, authProperties);
					break;
				case "Shiro":
					instance.authProvider = ShiroAuth.create(vertx, ShiroAuthRealmType.PROPERTIES, authProperties);
					break;
				case "JDBC":
					String dbName = json.getString("db-name");
					if (dbName == null) {
						throw new IllegalArgumentException("You cannot use JDBC authentication without a DB");
					}
					JDBCClient client = JDBCClient.createShared(vertx, authProperties, dbName);
					instance.authProvider = JDBCAuth.create(client);
					break;
				default:
					log.warn("Unknown type of auth : " + auth + " . Ignoring.");
			}
		} else if (auth != null) {
			log.warn("You have defined " + auth + " as auth type, but didn't provide any configuration, can't create authProvider");
		}

		instance.webroot = json.getString("webroot", "web/assets");
		instance.assetsPath = json.getString("static-path", "/assets");
		instance.tplDir = json.getString("views-dir", "web/views");
		instance.displayErrors = json.getBoolean("display-errors", Boolean.FALSE);
		// TODO : read sockJSOptions from config
		return instance;
	}

	public ResourceBundle getResourceBundle(Locale loc) {
		return bundlesByLocale.get(loc);
	}
}
