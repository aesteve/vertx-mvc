## Authentication with Nubes


### Protect your route with `@Auth` annotation

If you want a route to be protected, you just have to add an `@Auth` annotation, and Nubes will do all the work!

```java
	@GET("/private")
	@Auth(method = AuthMethod.JWT, authority = "secret")
	@File
	public String listView() {
		return "some/private/page.html";
	}
```

In this example, we protected the `/private` url with an authentication system which uses the `JWT` method and requires the `secret` authority to access the private page.  
It is not more complicated than that to protect a route!

### Set the authentication provider

The last thing you have to do if you want to use authentication with Nubes is to set an authentication provider to your instance.

```java

		jwt = JWTAuth.create(vertx, someConfiguration);
		nubes.setAuthProvider(jwt);

```

You can see the [Vert.x Documentation](http://vertx.io/docs/#authentication_and_authorisation) for more information on authentication providers. You can either use the implemented providers (JWT, JDBC, SHIRO) or implement your own customed authentication provider, as explained [here](http://vertx.io/docs/vertx-auth-common/java/#_creating_your_own_auth_implementation).  
Once you have created your provider, you just have to set it to your Nubes instance, and that's all!

### The `REDIRECT` method

If you want to use the `REDIRECT` method, you will also have to set the `redirectUrl` attribute in the `@Auth` annotation.  
Then when the user will try to access the private route, if he does not have the required authority, he will be redirected to the chosen url.  
Let's see how it works with a small example :

```java

	@GET("/private")
	@Auth(method = AuthMethod.REDIRECT, authority = "secret", redirectURL = "/your/redirection/url")
	@File
	public String redirectMethod(){
		return "some/private/page.html";
	}

```

Here, the user will be redirected to `/your/redirection/url` if he does not have the `secret` authority.  
The redirection url must lead to a login page with a form. Then, when the user is successfully logged, Nubes automatically redirect him to the private page he wanted to access at the beginning.


###Authentication use cases

If you want to see complete examples using Nubes authentication systems, have a look at [`Nubes Use Cases repository`](https://github.com/ldallen/Nubes-UseCases), and more particularly the [`jwt`](https://github.com/ldallen/Nubes-UseCases/tree/master/jwtAuth) and [`jdbc`](https://github.com/ldallen/Nubes-UseCases/tree/master/jdbcAuth) examples.  
The `jwt` example uses the `JWT` method and the `jdbc` example uses `REDIRECT`.

