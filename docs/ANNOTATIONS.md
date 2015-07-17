## Framework default annotations


### `@Controller` 

Indicates that this class is a controller and contains routes.

* Retention : Class
* Attributes :
	* value : the base path for every route defined in this controller
  

### `@CONNECT`, `@DELETE`, `@GET`, `@HEAD`, `@OPTIONS`, `@POST`, `@PUT`, `@TRACE` 

Indicates that this route is accessible by this http method.

* Retention : Method
* Attributes : 
	* value (mandatory) : the path this route is accessible from

### `@AfterFilter`

A method which will be executed before every route defined in this controller. (see it as an pre-interceptor, or pre-filter).

* Retention : Method
* Attributes : 
	* value : an Integer : the execution order of this filter (the smallest the first in execution order)

### `@BeforeFilter`

A method which will be executed after every route defined in this controller. (see it as a post-interceptor, or post-filter).

* Retention : Method
* Attributes : 
	* value : an Integer : the execution order of this filter (the smallest the first in execution order)

### `@View`

Indicates that this route is a view, and should delegate to a template handler.

* Retention : Method
* Attributes :
	* value : the name of the view (suffixed by the template extension : `@View("users.hbs")`) if it's static. If it's dynamic (i.e. depends on the request), you should use `ViewResolver` as a parameter of your method. 

### `@ContentType`

This route (or every route in the controller) can read/write the following content-types.

If a request is made to one of this routes with a non-acceptable accept header or content-type header, an http 406 error code will be returned.
The accept header is parsed in order to determine (and inject in routingContext.data()) the best content-type for this request. 

* Retention : Method or Class
* Attributes : 
	* value (mandatory) : a list of content-types you know how to handle. Example : `@ContentType({"application/json", "text/xml"})`


### `@Auth`

This route (or every route in the controller) is protected by authentication.

[Read the authentication documentation](AUTH.md).

* Retention : Method or Class
* Attributes :
	* method (mandatory) : the method to use as protection. Supported methods are : BASIC, JWT and REDIRECT.
	* authority : the authority (i.e. "level-access") needed to access this route
	* redirectUrl : The redirection url needed when using REDIRECT method.

### `@User`

Tells Nubes to inject the current User (making the request) as parameter of the method.

* Retention : method parameter
* Attributes : none

### `@Cookies`

This route (or every route in the controller) makes use of cookies. (Either read or write cookies)

* Retention : Method or Class
* Attributes : none

### `@CookieValue`

Tells Nubes to inject a specific cookie value as parameter of the method.

* Retention : method parameter
* Attributes : 
	* value (mandatory) : the name of the cookie
	
### `@Before`

Injects a specific handler (named) right before your method is executed.

[Read the interceptor documentation](AOP.md)

* Retention : Method 
* Attributes :
	* value (mandatory): the name of the interceptor
	
### `@After`

Injects a specific handler (named) right after your method is executed.

[Read the interceptor documentation](AOP.md)

* Retention : Method 
* Attributes :
	* value (mandatory) : the name of the interceptor
	
### `@Throttled`

This route as a rate limitation (a single client can't access the route more than N times in a M second window), as defined in the configuration.

* Retention : Method or Class
* Attributes : none

### `@Header`

Injects some header as parameter of your method.

* Retention : Method
* Attributes :
	* value (mandatory) : the name of the header you're interested in
	
### `@Params`

Tries to populate a Java bean with all the request parameters. And inject it in your method.

* Retention : method parameter
* Attributes : none

### `@Param`

Injects a specific request parameter as parameter of the method.

* Retention : method parameter
* Attributes :
	* value (mandatory) : the name of the request (or routing) parameter

### `@VertxLocalMap`

Injects a `LocalMap` instance as parameter of a method so that you can access it.

* Retention : method parameter
* Attributes :
	* value (mandatory) : the name of the map
	
### `LocalMapValue`

Injects a `LocalMap` specific value as parameter of a method.

* Retention : method parameter
* Attributes :
	* mapName (mandatory) : the name of the map
	* key (mandatory) : the key of the item in the local map
	
### `@RequestBody`

Tries to inject the request's body as a Java bean as a method parameter.

* Retention : method parameter
* Attributes : none


### `@ClientRedirect`

Indicates that the method will send a client redirect (HTTP status code 301) after being invoked.

* Retention : Method
* Attributes :
	* value (mandatory) : the URL of the redirect
	
### `@ServerRedirect`

Indicates that the server will perform a server (silent for the client) redirect.
Once the method has returned, another route will be invoked without the client knowing it.

* Retention : Method
* Attributes : 
	* controller (mandatory) : the class of the controller holding the redirect route
	* method (mandatory) : the redirect route method

### `@Disabled`

Indicates that this route (or every route in the controller) is disabled. Trying to reach this path will end up in a 404. The method won't be called.
It can be useful to disable a route but keep the code in place to reactivate it in a further version for example.

* Retention : Method or Class
* Attributes : none


### `@Blocking`

Indicates that the method is blocking (and takes a significant amount of time to process) and should be handled in a non-eventloop thread.
Even though it's tempting to mark every method as Blocking "just in case" keep in mind that if your method is async or really doesn't do a lot of complicated stuff, delegating the work to a new thread is simply unefficient.

* Retention : Method
* Attributes : none 

### `@File`

This method returns a file.

[Read the file resolving documentation](FILES.md)

* Retention : Method
* Attributes : 
	* value : the name of the file, if it's not dynamic. If not, you should add a `FileResolver` parameter in your method.

## Services


### `@Service`

Injects a service (or a simple Java bean) as a field into the controller at runtime.

[Read the service documentation](SERVICES.md)

* Retention : Field
* Attributes : 
	* value : the name you registered the service under
	
### `@Consumer` (for a service)

This method will be called everytime a message is published on the EventBus at the address specified as parameter.
Note that the method annotated with `@Consumer("some address")` can take only one parameter of type : `io.vertx.core.eventbus.Message`


* Retention : Method (on a service)
* Attributes :
	* value : the address to listen to on the event bus
	
### `@PeriodicTask` (for a service)

This method will be invoked on a periodic basis.
These methods should have no parameter.

* Retention : Method (on a service)
* Attributes :
	* value : the period (in ms)
	
