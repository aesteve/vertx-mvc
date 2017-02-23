# Vert.x Nubes 


Provides an annotation layer on top of vertx-web. 

Declare your Vert.x routes with annotated methods and controllers, in a Spring MVC-ish way.

```groovy
repositories {
  jcenter()
}

dependencies {
  compile 'com.github.aesteve:nubes:1.3'
}
```


## Declarative


Nubes automatically injects method parameters at runtime so that you can express your routes in a declarative manner. By reading the signature of the method, you should be able to have a quick glimpse at what your route uses (query parameters, request body, ...) and produces (void => status 204, object => marshalled).


`public PeanutsCharacter get(@Param CharacterType type)` lets us know that the method uses a request parameter named `type` and that the response will contain a marshalled `PeanutsCharacter` POJO.


Nubes comes with a controller layer, but also a service layer. You can declare services as simple POJOS and they'll be injected within your controllers, you can also declare your services as async, they'll be started when your application starts, stopped when the application stops.


## Extensible

The framework is designed to be fully extensible so that you can register and use your own annotations, whether it's an interceptor, a type-adapter, ... 

A good example on how to extend the framework is [Nubes Mongo](http://github.com/aesteve/nubes-mongo), a set of additionnal utilities (annotations, interceptors, ...) designed to help you deal with Mongo on top of Nubes. 
For example, nubes-mongo registers a `@Create` annotation against Nubes framework, meaning that the result of the method should be saved against the Mongo database.

Basically, an annotation will be tied to a set of Vert.x's Handlers, executed before and/or after the 'route' method is executed. 

## Non-blocking (obviously) but also non-stumbling

Even though Nubes looks opinionated (declaring your routes in a single way : the controller/method way), keep in mind that at the end of the day, vertx-web's router is still there and fully accessible if you find yourself stuck in an edge case Nubes isn't designed to handle. This way, you should never, ever be stucked. 

You just have a set of additional utilities at your disposal to declare your web routes in another way (a SpringMVC-ish way), to declare and inject services if you don't have a DI framework at your disposal, or to write Verticles differently. 

The `Router` can also be injected as a field within your controllers as an easy way to deal with it, and maybe register routes at runtime if you need to. 

## Examples

(Work in Progress)

If you're interested in how a real-life project built with Nubes would look like, you can have a look at [Nubes Chat](http://github.com/aesteve/nubes-chat) a very simple chat relying on a REST API and Vert.x's event-bus bridge which shows both REST and Socket controllers in action. On top of that, it uses a MongoDB as a persistent store so you can get a nice view of the service layer.

## Basic example :

### A controller : 

```java
package com.peanuts.controllers;

@Controller("/peanuts")
public class PeanutsPages {
  
  public ENUM CharacterType {
    DOG, GIRL, BOY, BIRD;
  }
  
  @GET("/character")
  @View
  public String getCharacter(@ContextData Map<String, Object> data, @Param CharacterType type) {
    switch(type) {
      case DOG: 
        data.put("name", "Snoopy");
        break;
      case BOY:
        data.put("name", "Charlie Brown");
        break;
      // ...
    }
    return "character.hbs";
  }
}
```

### The view : 

`web/views/character.hbs`

```html
<html>
  <body>
    Hello, I'm a Peanuts character, and my name is {{name}}
  </body>
</html>
```


`GET "/peanuts/character?type=DOG"` will return the following html view:

`Hello, I'm a Peanuts character, and my name is Snoopy`



### A JSON api example

```java
package com.peanuts.controllers;

@Controller("/api/1/peanuts")
@ContentType("application/json")
public class CharactersController {
  
  @Service("mongo")
  private MongoService mongo;
  
  @GET("/character")
  public PeanutsCharacter getCharacter(@Param CharacterType type) {
    switch(type) {
      case DOG: 
        return new PeanutsCharacter(CharacterType.DOG, "Snoopy", snoopysBirthDate);
      // etc. 
    }
  }
  
  @POST("/character") // declaring RoutingContext as parameter means your method is async
  public void createCharacter(RoutingContext context, @RequestBody PeanutsCharacter character) {
    mongo.save(character, handler -> {
      context.next();  
    }); // save it using JDBC service, mongo service, hibernate service, etc.
  }
}
```

With this an example of domain object :

```java
package com.peanuts.model;

public class PeanutsCharacter {

  public ENUM CharacterType {
    DOG, GIRL, BOY, BIRD;
  }

  private CharacterType type;
  private String name;
  private Date birthDate;
  
  // getters, setters, constructors and stuff
}
```


`GET "/api/1/peanuts/characters?type=DOG"` with an `Accept` header containing `application/json` will return : 

```json
{
  "name":"Snoopy",
  "type":"DOG",
  "birthDate":"1950-11-04T08:00:00.000Z"
}
```


`POST "/api/1/peanuts/characters"` with the following request body : 

```json
{
  "name":"Snoopy",
  "type":"DOG",
  "birthDate":"1950-11-04T08:00:00.000Z"
}
```

Will save our favorite cartoon dog into the database, then return an HTTP 204.



## How it works

### VertxNubes as the entry point

The entry point of every work with the framwork is creatning a `VertxNubes` instance.

You'll notice the constructor takes two arguments : 

* a Vertx instance
* a JsonObject containing the configuration

Please take a look at [the configuration documentation](/docs/CONFIG.md) for the available, mandatory or not, options.

Once you've created the VertxNubes instance, you need to `bootstrap` it. What it's gonna do is scanning your application classes (annotated with `@Controller`) in order to create the approriate Web routes/handlers and attach it to a vertx-web `Router`.

You can provide your own `Router`, if you want to to add custom routes and stuff in the standard vertx way. 

You can also let `VertxNubes` instanciate a `Router`. It's gonna return it to you once it's done bootstrapping. And you'll be able to do pretty much whatever you need with it.

```java
VertxNubes nubes = new VertxNubes(vertx, config);
nubes.bootstrap(res -> {
  if (res.succeeded()) {
    Router yourRouter = res.result();
    System.out.println("Everything's ready");
  } else {
    System.err.println("Something went wrong");
    res.cause().printStackTrace();
  }
});

```

You'll find a ton of examples in the tests of the project.

If you take a look at [the mock controllers](/src/test/java/mock/controllers), you'll pretty much find everything that's possible to do with Nubes out of the box.

## The Controller layer

### What is a `@Controller` ?

A controller is a Java singleton (per Nubes instance) defining a set of methods which will be translated into vertx-web handlers. (~= express middlewares).

It's important that your controller defines a no-argument constructor, VertxNubes expect that.

In a controller you'll find routes, annotated with `@GET`, `@POST`, `@PUT`, ... but also filters of two differents types : `@BeforeFilter` and `@AfterFilter`.

For each route in your controller, before filters will be executed before your actual route method, and after filters, well... after.


### Annotations

Nubes provides some default annotations. [Here's the list](/docs/ANNOTATIONS.md#framework-default-annotations).

But you can also define your own annotations, and attach vertx-web handlers to it.

In this case, you can register what Nubes calls "Annotation processors" which will be called before, after (or both) your method is invoked.

Nubes itself registers its own annotations using this API. For example, the `@ContentType({"application/json", "application/xml"})` annotation is bound to a `ContentTypeProcessor` which will : 

- check the `Accept` header of the request, and if it doesn't matches the MIME type you're handling, return a 406 status to the client
- find the most suitable MIME among the ones the client specified in its `Accept` header and the ones you specified in the body of the `ContentType` annotation
- inject this ContentType as a variable in the RoutingContext so that you can also benefit from it
- position the `Content-Type` response header so that you don't have to care about it


[Read the annotations document](/docs/ANNOTATIONS.md)

### Parameters

Parameters are automatically injected into every method at runtime, depending on the context of the request (parameters, body, ...).

For a complete list of available parameters (by default), see [the parameters documentation](/docs/PARAMETERS.md).

But you can also register your own parameter resolvers by telling nubes : "When you find this type of parameter, resolve it like this".

Parameters can be resolved simply by their types (that's how Nubes injects the `RoutingContext` or the `EventBus` parameters if your method asks for it) or by a custom annotation you define.

[Read the parameters injection documentation](/docs/PARAMETERS.MD)


## The View Layer

TODO : explain that template engines are created by the user, and bound to a file extension. Then how views are resolved, either by `@View("viewName.extension")` or through the `ViewResolver` parameter.

## The Service Layer

Services in Nubes are simple POJOs you register on the Nubes instance using `nubes.registerService(String name, Object serviceInstance)`. This way, you'll be able to access them from your controllers using the `@Service("someName")` annotation. 

### Standard services

Any POJO can be a service. Simply register it against Nubes.

### Async services

In some case, services take time to startup or to be closed. If you want your service to be started when Nubes bootstraps, just implements Nubes `Service` interface and register it, the same way you would register a POJO.

### RPC and service proxies

TODO : Explain how to use vertx's service proxy.

## SockJS

Nubes provides a simple way to declare SockJS handlers with annotations instead of handlers defined in vertx-web.


For example, to handle incoming sockets you would do the following.


```java
@SockJS("/sockjs/*")
public class SockJSController {
  @OnMessage
  public void onMessage(SockJSSocket emitter, Buffer message) {
    emitter.write(Buffer.buffer(message)); // simply echo the message back
  }
}
```

You can also use vertx-web's event-bus bridge if you want your users to access some addresses over the event-bus from client-side using SockJS.


```java
@EventBusBridge("/sockjs/*")
@InboundPermitted("some-allowed-address")
public class BridgeController {

  @SOCKET_CREATED
  public void whenSocketIsCreated(BridgeEvent event) {
    // do what you want...
  }
  
  @REGISTER
  public void whenSomeUserRegisters(BridgeEvent event) {
    // do what you want...
  }
  
}
```

